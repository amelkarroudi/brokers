package com.brokers.api.reservation;

import com.brokers.channel.core.model.Money;

import java.time.Instant;

public record ReservationDetails(Instant pickupAt, Instant returnAt, String customerName, String customerEmail,
                                 Money totalPrice) {
}
