package com.brokers.api.connection;

import com.brokers.channel.core.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ConnectionDtos {

    private ConnectionDtos() {
    }

    /**
     * @param webhookSecret optional; when omitted a strong secret is generated, returned once, and
     *                      must be pasted into the channel's extranet
     */
    public record ConnectRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "must be a plain identifier")
            String accountId,
            @NotBlank @Size(max = 200) String apiKey,
            @NotBlank @Size(max = 500) String apiSecret,
            @Size(min = 16, max = 200, message = "must be between 16 and 200 characters") String webhookSecret) {
    }

    /**
     * A channel as seen by the organization. {@code status} is {@code NOT_CONNECTED} when no
     * connection exists yet. Secrets are never returned; only {@code apiKeyHint} (last 4 chars).
     */
    public record ConnectionView(
            Channel channel,
            String status,
            UUID connectionId,
            String accountId,
            String apiKeyHint,
            String webhookUrl,
            Instant lastVerifiedAt,
            String lastError,
            Instant updatedAt) {
    }

    /** Returned when a connection is created or its webhook secret is rotated. */
    public record ConnectionSecretView(ConnectionView connection, String webhookSecret) {
    }
}
