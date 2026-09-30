package com.brokers.channel.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingVehicleRequest(
        String supplierReference,
        String title,
        String make,
        String model,
        int year,
        String acrissCode,
        String transmission,
        String fuelType,
        int seats,
        int doors,
        int luggage,
        boolean airConditioning,
        BookingMileage mileage,
        int minDriverAge,
        BookingMoney pricePerDay,
        BookingMoney deposit,
        BookingLocation pickupLocation) {
}
