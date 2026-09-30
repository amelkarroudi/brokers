package com.brokers.channel.booking.webhook;

import com.brokers.channel.booking.dto.BookingMoney;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.Instant;

/** Wire format of a Booking.com webhook delivery. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record BookingWebhookPayload(String eventId, String eventType, Instant occurredAt, Reservation reservation) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Reservation(
            String reservationId,
            String vehicleId,
            Instant pickupDatetime,
            Instant dropoffDatetime,
            Driver driver,
            BookingMoney totalPrice) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Driver(String firstName, String lastName, String email) {

        String fullName() {
            String first = firstName == null ? "" : firstName.trim();
            String last = lastName == null ? "" : lastName.trim();
            return (first + " " + last).trim();
        }
    }
}
