package com.brokers.channel.rentalcars.dto;

import java.time.Instant;
import java.util.List;

/**
 * Rentalcars.com models unavailability as "stop sales" periods. The window bounds the range the
 * list replaces.
 */
public record RcStopSalesRequest(Period window, List<Period> stopSales) {

    public record Period(Instant start, Instant end) {
    }
}
