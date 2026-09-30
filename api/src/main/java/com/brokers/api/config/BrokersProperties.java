package com.brokers.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/** Typed configuration for everything under {@code brokers.*}. */
@Validated
@ConfigurationProperties("brokers")
public record BrokersProperties(
        @NotNull URI publicBaseUrl,
        @Valid @NotNull Security security,
        @Valid @NotNull Channels channels,
        @Valid @NotNull Sync sync,
        @Valid @NotNull Sandbox sandbox) {

    public record Security(
            /** Base64 encoded 256-bit AES key used to encrypt channel credentials at rest. */
            @NotBlank String credentialKey,
            @NotNull Duration sessionTtl,
            @Min(1) int maxFailedLogins,
            @NotNull Duration lockoutDuration,
            @NotNull List<String> corsAllowedOrigins) {
    }

    public record Channels(@Valid @NotNull Endpoint booking, @Valid @NotNull Endpoint rentalcars) {
    }

    public record Endpoint(@NotNull URI baseUrl, Duration connectTimeout, Duration readTimeout) {
    }

    public record Sync(
            boolean workerEnabled,
            @NotNull Duration pollInterval,
            @Min(1) int batchSize,
            @Min(1) int maxAttempts,
            @NotNull Duration initialBackoff,
            @NotNull Duration maxBackoff,
            @NotNull Duration staleLockTimeout,
            /** How far ahead availability is pushed to channels. */
            @NotNull Duration availabilityHorizon) {
    }

    public record Sandbox(boolean enabled) {
    }
}
