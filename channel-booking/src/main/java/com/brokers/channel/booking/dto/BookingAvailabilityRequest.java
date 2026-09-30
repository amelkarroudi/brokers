package com.brokers.channel.booking.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BookingAvailabilityRequest(Instant from, Instant to, List<ClosedPeriod> closedPeriods) {

    public record ClosedPeriod(Instant start, Instant end) {
    }
}
