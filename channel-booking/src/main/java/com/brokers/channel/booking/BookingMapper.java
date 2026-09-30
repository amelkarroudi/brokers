package com.brokers.channel.booking;

import com.brokers.channel.booking.dto.BookingAvailabilityRequest;
import com.brokers.channel.booking.dto.BookingLocation;
import com.brokers.channel.booking.dto.BookingMileage;
import com.brokers.channel.booking.dto.BookingMoney;
import com.brokers.channel.booking.dto.BookingPhotosRequest;
import com.brokers.channel.booking.dto.BookingVehicleRequest;
import com.brokers.channel.core.model.AvailabilityUpdate;
import com.brokers.channel.core.model.ListingPhoto;
import com.brokers.channel.core.model.Money;
import com.brokers.channel.core.model.PickupLocation;
import com.brokers.channel.core.model.VehicleListing;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/** Maps platform models to the Booking.com wire format. */
final class BookingMapper {

    private BookingMapper() {
    }

    static BookingVehicleRequest toVehicleRequest(VehicleListing listing) {
        return new BookingVehicleRequest(
                listing.reference(),
                listing.title(),
                listing.make(),
                listing.model(),
                listing.year(),
                listing.acrissCode(),
                lower(listing.transmission()),
                lower(listing.fuelType()),
                listing.seats(),
                listing.doors(),
                listing.bags(),
                listing.airConditioning(),
                new BookingMileage(listing.unlimitedMileage(), listing.mileageLimitKm()),
                listing.minDriverAge(),
                toMoney(listing.dailyRate()),
                toMoney(listing.deposit()),
                toLocation(listing.location()));
    }

    static BookingPhotosRequest toPhotosRequest(List<ListingPhoto> photos) {
        List<ListingPhoto> sorted = photos.stream()
                .sorted(Comparator.comparingInt(ListingPhoto::position))
                .toList();
        List<BookingPhotosRequest.Photo> mapped = IntStream.range(0, sorted.size())
                .mapToObj(index -> toPhoto(sorted.get(index), index == 0))
                .toList();
        return new BookingPhotosRequest(mapped);
    }

    static BookingAvailabilityRequest toAvailabilityRequest(AvailabilityUpdate update) {
        List<BookingAvailabilityRequest.ClosedPeriod> closed = update.blocked().stream()
                .map(window -> new BookingAvailabilityRequest.ClosedPeriod(window.from(), window.to()))
                .toList();
        return new BookingAvailabilityRequest(update.horizonStart(), update.horizonEnd(), closed);
    }

    private static BookingPhotosRequest.Photo toPhoto(ListingPhoto photo, boolean isMain) {
        return new BookingPhotosRequest.Photo(photo.url(), photo.position(), isMain, photo.caption());
    }

    private static BookingMoney toMoney(Money money) {
        if (money == null) {
            return null;
        }
        return new BookingMoney(money.amount(), money.currency());
    }

    private static BookingLocation toLocation(PickupLocation location) {
        return new BookingLocation(
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

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
