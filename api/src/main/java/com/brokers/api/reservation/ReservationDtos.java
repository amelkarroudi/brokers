package com.brokers.api.reservation;

import com.brokers.channel.core.Channel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ReservationDtos {

    private ReservationDtos() {
    }

    public record ReservationView(
            UUID id,
            UUID vehicleId,
            Channel channel,
            String externalReservationId,
            ReservationStatus status,
            Instant pickupAt,
            Instant returnAt,
            String customerName,
            String customerEmail,
            BigDecimal totalAmount,
            String currency,
            boolean hasConflict,
            Instant createdAt,
            Instant updatedAt) {

        static ReservationView of(Reservation reservation) {
            return new ReservationView(reservation.getId(), reservation.getVehicleId(), reservation.getChannel(),
                    reservation.getExternalReservationId(), reservation.getStatus(), reservation.getPickupAt(),
                    reservation.getReturnAt(), reservation.getCustomerName(), reservation.getCustomerEmail(),
                    reservation.getTotalAmount(), reservation.getCurrency(), reservation.isHasConflict(),
                    reservation.getCreatedAt(), reservation.getUpdatedAt());
        }
    }
}
