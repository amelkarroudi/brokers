package com.brokers.channel.rentalcars.webhook;

import com.brokers.channel.rentalcars.dto.RcPrice;

import java.time.Instant;

/** Wire format of a Rentalcars.com webhook delivery. */
record RcWebhookPayload(String id, String type, Instant createdAt, Data data) {

    record Data(String bookingReference, String vehicleId, Leg pickUp, Leg dropOff, Customer customer, RcPrice price) {
    }

    record Leg(Instant dateTime) {
    }

    record Customer(String fullName, String email) {
    }
}
