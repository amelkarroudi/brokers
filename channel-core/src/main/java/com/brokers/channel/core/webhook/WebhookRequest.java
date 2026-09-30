package com.brokers.channel.core.webhook;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Raw inbound webhook. The body is kept as bytes because signatures are computed over the exact
 * bytes the channel sent.
 */
public record WebhookRequest(Map<String, String> headers, byte[] body) {

    public WebhookRequest {
        headers = headers.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue,
                        (first, second) -> first));
        body = body == null ? new byte[0] : body.clone();
    }

    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
    }
}
