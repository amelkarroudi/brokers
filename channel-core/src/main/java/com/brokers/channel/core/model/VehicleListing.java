package com.brokers.channel.core.model;

/**
 * Channel-agnostic description of a car offered for rent.
 *
 * @param reference      the organization's own stable identifier for the car (fleet number)
 * @param acrissCode     four letter industry vehicle classification, e.g. {@code CDMR}
 * @param mileageLimitKm daily mileage allowance, {@code null} for unlimited mileage
 */
public record VehicleListing(
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
        Money dailyRate,
        Money deposit,
        PickupLocation location) {

    public String title() {
        return make + " " + model + " " + year;
    }

    public boolean unlimitedMileage() {
        return mileageLimitKm == null;
    }
}
