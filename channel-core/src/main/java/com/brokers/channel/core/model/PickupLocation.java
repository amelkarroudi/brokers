package com.brokers.channel.core.model;

import java.math.BigDecimal;

/**
 * A branch where the customer picks up and returns the car.
 *
 * @param iataCode airport code when the branch is at an airport, otherwise {@code null}
 */
public record PickupLocation(
        String code,
        String name,
        String addressLine,
        String city,
        String postalCode,
        String countryCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String iataCode) {
}
