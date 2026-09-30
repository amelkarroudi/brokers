package com.brokers.api.location;

import java.math.BigDecimal;

/** Normalized location fields shared by create and update. */
public record LocationDetails(
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
