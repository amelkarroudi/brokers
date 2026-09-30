package com.brokers.channel.core.http;

import com.brokers.channel.core.Channel;
import com.brokers.channel.core.error.ChannelErrorKind;
import com.brokers.channel.core.error.ChannelException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelHttpTest {

    @Test
    void classifiesStatusCodes() {
        assertThat(ChannelHttp.classify(HttpStatus.UNAUTHORIZED)).isEqualTo(ChannelErrorKind.AUTHENTICATION);
        assertThat(ChannelHttp.classify(HttpStatus.FORBIDDEN)).isEqualTo(ChannelErrorKind.AUTHENTICATION);
        assertThat(ChannelHttp.classify(HttpStatus.NOT_FOUND)).isEqualTo(ChannelErrorKind.NOT_FOUND);
        assertThat(ChannelHttp.classify(HttpStatus.GONE)).isEqualTo(ChannelErrorKind.NOT_FOUND);
        assertThat(ChannelHttp.classify(HttpStatus.TOO_MANY_REQUESTS)).isEqualTo(ChannelErrorKind.RATE_LIMITED);
        assertThat(ChannelHttp.classify(HttpStatus.REQUEST_TIMEOUT)).isEqualTo(ChannelErrorKind.UNAVAILABLE);
        assertThat(ChannelHttp.classify(HttpStatus.BAD_GATEWAY)).isEqualTo(ChannelErrorKind.UNAVAILABLE);
        assertThat(ChannelHttp.classify(HttpStatus.UNPROCESSABLE_CONTENT)).isEqualTo(ChannelErrorKind.VALIDATION);
        assertThat(ChannelHttp.classify(HttpStatusCode.valueOf(302))).isEqualTo(ChannelErrorKind.UNEXPECTED);
    }

    @Test
    void parsesRetryAfterInSeconds() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "30");

        assertThat(ChannelHttp.parseRetryAfter(headers)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void parsesRetryAfterAsHttpDate() {
        HttpHeaders headers = new HttpHeaders();
        String inTwoMinutes = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(2).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        headers.set(HttpHeaders.RETRY_AFTER, inTwoMinutes);

        assertThat(ChannelHttp.parseRetryAfter(headers)).isBetween(Duration.ofSeconds(100), Duration.ofSeconds(121));
    }

    @Test
    void ignoresMalformedRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "soon");

        assertThat(ChannelHttp.parseRetryAfter(headers)).isNull();
        assertThat(ChannelHttp.parseRetryAfter(null)).isNull();
    }

    @Test
    void wrapsRateLimitResponsesWithRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "7");
        var error = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers,
                "slow down".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ChannelHttp.run(Channel.BOOKING, "create listing", () -> {
            throw error;
        }))
                .isInstanceOfSatisfying(ChannelException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ChannelErrorKind.RATE_LIMITED);
                    assertThat(e.retryable()).isTrue();
                    assertThat(e.retryAfter()).contains(Duration.ofSeconds(7));
                    assertThat(e.getMessage()).contains("HTTP 429").contains("slow down");
                });
    }

    @Test
    void wrapsNetworkFailuresAsRetryable() {
        assertThatThrownBy(() -> ChannelHttp.run(Channel.RENTALCARS, "push availability", () -> {
            throw new ResourceAccessException("connection refused");
        }))
                .isInstanceOfSatisfying(ChannelException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ChannelErrorKind.UNAVAILABLE);
                    assertThat(e.retryable()).isTrue();
                    assertThat(e.channel()).isEqualTo(Channel.RENTALCARS);
                });
    }
}
