package com.brokers.api.vehicle;

import com.brokers.api.location.Location;
import com.brokers.channel.core.model.ListingPhoto;
import com.brokers.channel.core.model.Money;
import com.brokers.channel.core.model.PickupLocation;
import com.brokers.channel.core.model.VehicleListing;

import java.util.List;

/** Converts fleet entities into the channel-agnostic model the channel packages understand. */
public final class VehicleListings {

    private VehicleListings() {
    }

    public static VehicleListing toListing(Vehicle vehicle, Location location) {
        return new VehicleListing(
                vehicle.getReference(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getAcrissCode(),
                vehicle.getTransmission(),
                vehicle.getFuelType(),
                vehicle.getSeats(),
                vehicle.getDoors(),
                vehicle.getBags(),
                vehicle.isAirConditioning(),
                vehicle.getMileageLimitKm(),
                vehicle.getMinDriverAge(),
                new Money(vehicle.getDailyRate(), vehicle.getCurrency()),
                vehicle.getDeposit() == null ? null : new Money(vehicle.getDeposit(), vehicle.getCurrency()),
                new PickupLocation(
                        location.getCode(),
                        location.getName(),
                        location.getAddressLine(),
                        location.getCity(),
                        location.getPostalCode(),
                        location.getCountryCode(),
                        location.getLatitude(),
                        location.getLongitude(),
                        location.getIataCode()));
    }

    public static List<ListingPhoto> toPhotos(Vehicle vehicle) {
        return vehicle.getPhotos().stream()
                .map(photo -> new ListingPhoto(photo.getUrl(), photo.getPosition(), photo.getCaption()))
                .toList();
    }
}
