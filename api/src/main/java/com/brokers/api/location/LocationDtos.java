package com.brokers.api.location;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class LocationDtos {

    private LocationDtos() {
    }

    public record LocationRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "may contain letters, digits, '-' and '_'")
            String code,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 255) String addressLine,
            @NotBlank @Size(max = 120) String city,
            @Size(max = 20) String postalCode,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO-3166 alpha-2 code") String countryCode,
            @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3 letter IATA code") String iataCode) {

        LocationDetails toDetails() {
            return new LocationDetails(
                    code.trim().toUpperCase(Locale.ROOT),
                    name.trim(),
                    addressLine.trim(),
                    city.trim(),
                    blankToNull(postalCode),
                    countryCode.toUpperCase(Locale.ROOT),
                    latitude,
                    longitude,
                    iataCode == null ? null : iataCode.toUpperCase(Locale.ROOT));
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    public record LocationView(
            UUID id,
            String code,
            String name,
            String addressLine,
            String city,
            String postalCode,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String iataCode,
            Instant createdAt,
            Instant updatedAt) {

        static LocationView of(Location location) {
            return new LocationView(location.getId(), location.getCode(), location.getName(), location.getAddressLine(),
                    location.getCity(), location.getPostalCode(), location.getCountryCode(), location.getLatitude(),
                    location.getLongitude(), location.getIataCode(), location.getCreatedAt(), location.getUpdatedAt());
        }
    }
}
