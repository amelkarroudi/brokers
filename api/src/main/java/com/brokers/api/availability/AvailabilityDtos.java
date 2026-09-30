package com.brokers.api.availability;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AvailabilityDtos {

    private AvailabilityDtos() {
    }

    public record BlockRequest(
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            /** MANUAL or MAINTENANCE; defaults to MANUAL. */
            BlockReason reason,
            @Size(max = 500) String note) {
    }

    public record BlockView(UUID id, Instant startsAt, Instant endsAt, BlockReason reason, UUID reservationId, String note) {

        static BlockView of(AvailabilityBlock block) {
            return new BlockView(block.getId(), block.getStartsAt(), block.getEndsAt(), block.getReason(),
                    block.getReservationId(), block.getNote());
        }
    }
}
