package com.brokers.api.vehicle;

import com.brokers.channel.core.model.FuelType;
import com.brokers.channel.core.model.Transmission;

import java.math.BigDecimal;
import java.util.UUID;

/** Normalized vehicle fields shared by create and update. */
public record VehicleDetails(
        UUID locationId,
        String reference,
        String make,
        String model,
        int year,
        String acrissCode,
        Transmission transmission,
        FuelType fuelType,
        int seats,
        int doors,
        int bags,
        boolean airConditioning,
        Integer mileageLimitKm,
        int minDriverAge,
        BigDecimal dailyRate,
        BigDecimal deposit,
        String currency) {
}
