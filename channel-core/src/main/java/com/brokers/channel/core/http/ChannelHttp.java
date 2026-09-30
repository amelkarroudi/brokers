package com.brokers.channel.core.http;

import com.brokers.channel.core.Channel;
import com.brokers.channel.core.error.ChannelErrorKind;
import com.brokers.channel.core.error.ChannelException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Supplier;

/**
 * Translates Spring HTTP client failures into {@link ChannelException}s so each integration
 * reports errors the same way.
 */
public final class ChannelHttp {

    private static final int MAX_BODY_IN_MESSAGE = 500;

    private ChannelHttp() {
    }

    public static <T> T call(Channel channel, String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw fromResponse(channel, operation, e);
        } catch (ResourceAccessException e) {
            throw new ChannelException(channel, ChannelErrorKind.UNAVAILABLE,
                    operation + " failed: " + e.getMessage(), null, e);
        } catch (RestClientException e) {
            throw new ChannelException(channel, ChannelErrorKind.UNEXPECTED,
                    operation + " failed: " + e.getMessage(), null, e);
        }
    }

    public static void run(Channel channel, String operation, Runnable request) {
        call(channel, operation, () -> {
            request.run();
            return null;
        });
    }

    static ChannelException fromResponse(Channel channel, String operation, RestClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        ChannelErrorKind kind = classify(status);
        Duration retryAfter = kind == ChannelErrorKind.RATE_LIMITED || kind == ChannelErrorKind.UNAVAILABLE
                ? parseRetryAfter(e.getResponseHeaders())
                : null;
        String message = operation + " failed with HTTP " + status.value() + bodySuffix(e.getResponseBodyAsString());
        return new ChannelException(channel, kind, message, retryAfter, e);
    }

    static ChannelErrorKind classify(HttpStatusCode status) {
        int code = status.value();
        if (code == 401 || code == 403) {
            return ChannelErrorKind.AUTHENTICATION;
        }
        if (code == 404 || code == 410) {
            return ChannelErrorKind.NOT_FOUND;
        }
        if (code == 429) {
            return ChannelErrorKind.RATE_LIMITED;
        }
        if (code == 408 || status.is5xxServerError()) {
            return ChannelErrorKind.UNAVAILABLE;
        }
        if (status.is4xxClientError()) {
            return ChannelErrorKind.VALIDATION;
        }
        return ChannelErrorKind.UNEXPECTED;
    }

    /** Parses a {@code Retry-After} header given either in seconds or as an HTTP date. */
    static Duration parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            return Duration.ofSeconds(Long.parseLong(trimmed));
        }
        try {
            Instant at = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration delay = Duration.between(Instant.now(), at);
            return delay.isNegative() ? Duration.ZERO : delay;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String bodySuffix(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String trimmed = body.length() > MAX_BODY_IN_MESSAGE ? body.substring(0, MAX_BODY_IN_MESSAGE) + "…" : body;
        return ": " + trimmed;
    }
}
