package com.brokers.api.sandbox;

import com.brokers.api.webhook.WebhookDtos.WebhookReceipt;
import com.brokers.channel.core.Channel;
import com.brokers.channel.core.webhook.ReservationEventType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class SandboxDtos {

    private SandboxDtos() {
    }

    /**
     * A reservation to simulate as if a customer booked on the channel.
     *
     * @param externalReservationId required to modify or cancel; generated for new reservations when omitted
     */
    public record SimulateReservationRequest(
            @NotNull UUID vehicleId,
            @NotNull Channel channel,
            @NotNull ReservationEventType type,
            @Size(max = 120) String externalReservationId,
            Instant pickupAt,
            Instant returnAt,
            @Size(max = 200) String customerName,
            @Email String customerEmail,
            @DecimalMin("0") BigDecimal totalAmount) {
    }

    public record SimulationResult(String externalReservationId, String eventId, WebhookReceipt receipt) {
    }

    public record SandboxListingView(String externalId, boolean active, Instant updatedAt, JsonNode content,
                                     JsonNode photos, JsonNode availability) {

        static SandboxListingView of(SandboxStore.SandboxListing listing) {
            return new SandboxListingView(listing.externalId(), listing.active(), listing.updatedAt(), listing.content(),
                    listing.photos(), listing.availability());
        }
    }

    public record SyncRunResult(int processedJobs) {
    }
}
