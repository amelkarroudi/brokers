package com.brokers.channel.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingLocation(
        String code,
        String name,
        String address,
        String city,
        String postalCode,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        String iata) {
}
