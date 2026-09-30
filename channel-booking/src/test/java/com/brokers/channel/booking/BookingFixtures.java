package com.brokers.channel.booking;

import com.brokers.channel.core.ChannelCredentials;
import com.brokers.channel.core.model.FuelType;
import com.brokers.channel.core.model.Money;
import com.brokers.channel.core.model.PickupLocation;
import com.brokers.channel.core.model.Transmission;
import com.brokers.channel.core.model.VehicleListing;

import java.math.BigDecimal;

final class BookingFixtures {

    static final ChannelCredentials CREDENTIALS = new ChannelCredentials("SUP-42", "machine-user", "s3cret");

    private BookingFixtures() {
    }

    static VehicleListing listing() {
        return new VehicleListing(
                "FLEET-001", "Peugeot", "208", 2024, "EDMR",
                Transmission.MANUAL, FuelType.PETROL,
                5, 5, 2, true, null, 21,
                Money.of("39.90", "EUR"), Money.of("800", "EUR"),
                new PickupLocation("CMN-T1", "Casablanca Airport T1", "Aéroport Mohammed V", "Casablanca",
                        "20250", "MA", new BigDecimal("33.367500"), new BigDecimal("-7.589970"), "CMN"));
    }
}
