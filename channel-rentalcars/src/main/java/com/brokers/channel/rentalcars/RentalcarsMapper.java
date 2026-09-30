package com.brokers.channel.rentalcars;

import com.brokers.channel.core.model.AvailabilityUpdate;
import com.brokers.channel.core.model.ListingPhoto;
import com.brokers.channel.core.model.Money;
import com.brokers.channel.core.model.PickupLocation;
import com.brokers.channel.core.model.VehicleListing;
import com.brokers.channel.rentalcars.dto.RcFleetVehicleRequest;
import com.brokers.channel.rentalcars.dto.RcImagesRequest;
import com.brokers.channel.rentalcars.dto.RcPrice;
import com.brokers.channel.rentalcars.dto.RcStopSalesRequest;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/** Maps platform models to the Rentalcars.com wire format. */
final class RentalcarsMapper {

    private RentalcarsMapper() {
    }

    static RcFleetVehicleRequest toFleetVehicle(VehicleListing listing) {
        var vehicle = new RcFleetVehicleRequest.Vehicle(
                listing.make() + " " + listing.model(),
                listing.year(),
                listing.transmission().name(),
                listing.fuelType().name(),
                listing.seats(),
                listing.doors(),
                listing.bags(),
                listing.airConditioning());
        var terms = new RcFleetVehicleRequest.Terms(
                listing.unlimitedMileage() ? "UNLIMITED" : "LIMITED",
                listing.mileageLimitKm(),
                listing.minDriverAge());
        return new RcFleetVehicleRequest(
                listing.reference(),
                listing.acrissCode(),
                vehicle,
                terms,
                toPrice(listing.dailyRate()),
                toPrice(listing.deposit()),
                toStation(listing.location()));
    }

    static RcImagesRequest toImages(List<ListingPhoto> photos) {
        List<ListingPhoto> sorted = photos.stream()
                .sorted(Comparator.comparingInt(ListingPhoto::position))
                .toList();
        List<RcImagesRequest.Image> images = IntStream.range(0, sorted.size())
                .mapToObj(index -> new RcImagesRequest.Image(
                        sorted.get(index).url(), index + 1, index == 0, sorted.get(index).caption()))
                .toList();
        return new RcImagesRequest(images);
    }

    static RcStopSalesRequest toStopSales(AvailabilityUpdate update) {
        List<RcStopSalesRequest.Period> stopSales = update.blocked().stream()
                .map(window -> new RcStopSalesRequest.Period(window.from(), window.to()))
                .toList();
        return new RcStopSalesRequest(new RcStopSalesRequest.Period(update.horizonStart(), update.horizonEnd()), stopSales);
    }

    private static RcPrice toPrice(Money money) {
        if (money == null) {
            return null;
        }
        return new RcPrice(money.minorUnits(), money.currency());
    }

    private static RcFleetVehicleRequest.Station toStation(PickupLocation location) {
        return new RcFleetVehicleRequest.Station(
                location.code(),
                location.name(),
                location.addressLine(),
                location.city(),
                location.postalCode(),
                location.countryCode(),
                location.latitude(),
                location.longitude(),
                location.iataCode());
    }
}
