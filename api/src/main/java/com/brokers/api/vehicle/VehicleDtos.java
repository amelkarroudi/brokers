package com.brokers.api.vehicle;

import com.brokers.api.listing.ChannelListing;
import com.brokers.api.location.Location;
import com.brokers.channel.core.Channel;
import com.brokers.channel.core.model.FuelType;
import com.brokers.channel.core.model.Transmission;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VehicleDtos {

    private VehicleDtos() {
    }

    public record VehicleRequest(
            @NotNull UUID locationId,
            @NotBlank @Size(max = 60) @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may contain letters, digits, '.', '-' and '_'")
            String reference,
            @NotBlank @Size(max = 60) String make,
            @NotBlank @Size(max = 60) String model,
            @NotNull @Min(1990) @Max(2100) Integer year,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{4}$", message = "must be a 4 letter ACRISS code") String acrissCode,
            @NotNull Transmission transmission,
            @NotNull FuelType fuelType,
            @NotNull @Min(1) @Max(60) Integer seats,
            @NotNull @Min(2) @Max(6) Integer doors,
            @NotNull @Min(0) @Max(20) Integer bags,
            @NotNull Boolean airConditioning,
            /** Omit for unlimited mileage. */
            @Positive Integer mileageLimitKm,
            @NotNull @Min(18) @Max(99) Integer minDriverAge,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal dailyRate,
            @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal deposit,
            /** Defaults to the organization's currency. */
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be an ISO-4217 currency code") String currency) {
    }

    public record PhotoRequest(
            @NotBlank @Size(max = 2048) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String url,
            @Size(max = 200) String caption,
            @PositiveOrZero Integer position) {
    }

    public record ReorderPhotosRequest(@NotEmpty List<@NotNull UUID> photoIds) {
    }

    public record PhotoView(UUID id, String url, String caption, int position) {

        static PhotoView of(VehiclePhoto photo) {
            return new PhotoView(photo.getId(), photo.getUrl(), photo.getCaption(), photo.getPosition());
        }
    }

    public record LocationSummary(UUID id, String code, String name, String city) {

        static LocationSummary of(Location location) {
            return new LocationSummary(location.getId(), location.getCode(), location.getName(), location.getCity());
        }
    }

    public record ListingView(Channel channel, String status, String externalId, Instant lastSyncedAt, String lastError) {

        public static ListingView of(ChannelListing listing) {
            return new ListingView(listing.getChannel(), listing.getStatus().name(), listing.getExternalId(),
                    listing.getLastSyncedAt(), listing.getLastError());
        }
    }

    public record VehicleSummary(
            UUID id,
            String reference,
            String make,
            String model,
            int year,
            String acrissCode,
            BigDecimal dailyRate,
            String currency,
            VehicleStatus status,
            UUID locationId,
            String coverPhotoUrl,
            Instant updatedAt) {

        static VehicleSummary of(Vehicle vehicle) {
            String cover = vehicle.getPhotos().isEmpty() ? null : vehicle.getPhotos().getFirst().getUrl();
            return new VehicleSummary(vehicle.getId(), vehicle.getReference(), vehicle.getMake(), vehicle.getModel(),
                    vehicle.getYear(), vehicle.getAcrissCode(), vehicle.getDailyRate(), vehicle.getCurrency(),
                    vehicle.getStatus(), vehicle.getLocationId(), cover, vehicle.getUpdatedAt());
        }
    }

    public record VehicleView(
            UUID id,
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
            String currency,
            VehicleStatus status,
            LocationSummary location,
            List<PhotoView> photos,
            List<ListingView> listings,
            Instant createdAt,
            Instant updatedAt) {

        static VehicleView of(Vehicle vehicle, Location location, List<ChannelListing> listings) {
            return new VehicleView(vehicle.getId(), vehicle.getReference(), vehicle.getMake(), vehicle.getModel(),
                    vehicle.getYear(), vehicle.getAcrissCode(), vehicle.getTransmission(), vehicle.getFuelType(),
                    vehicle.getSeats(), vehicle.getDoors(), vehicle.getBags(), vehicle.isAirConditioning(),
                    vehicle.getMileageLimitKm(), vehicle.getMinDriverAge(), vehicle.getDailyRate(), vehicle.getDeposit(),
                    vehicle.getCurrency(), vehicle.getStatus(), LocationSummary.of(location),
                    vehicle.getPhotos().stream().map(PhotoView::of).toList(),
                    listings.stream().map(ListingView::of).toList(),
                    vehicle.getCreatedAt(), vehicle.getUpdatedAt());
        }
    }
}
