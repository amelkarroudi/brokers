package com.brokers.api.vehicle;

import com.brokers.api.common.ApiException;
import com.brokers.api.common.PageResponse;
import com.brokers.api.common.Pagination;
import com.brokers.api.listing.ChannelListingRepository;
import com.brokers.api.location.Location;
import com.brokers.api.location.LocationRepository;
import com.brokers.api.organization.OrganizationRepository;
import com.brokers.api.reservation.ReservationRepository;
import com.brokers.api.sync.SyncJobScheduler;
import com.brokers.api.sync.SyncTrigger;
import com.brokers.api.vehicle.VehicleDtos.PhotoRequest;
import com.brokers.api.vehicle.VehicleDtos.ReorderPhotosRequest;
import com.brokers.api.vehicle.VehicleDtos.VehicleRequest;
import com.brokers.api.vehicle.VehicleDtos.VehicleSummary;
import com.brokers.api.vehicle.VehicleDtos.VehicleView;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fleet management. Every change to an active car is turned into sync jobs so channels follow
 * automatically; the jobs are written in the same transaction as the change (transactional outbox).
 */
@Service
public class VehicleService {

    private final VehicleRepository vehicles;
    private final LocationRepository locations;
    private final OrganizationRepository organizations;
    private final ChannelListingRepository listings;
    private final ReservationRepository reservations;
    private final SyncJobScheduler syncJobs;
    private final Clock clock;

    public VehicleService(VehicleRepository vehicles, LocationRepository locations, OrganizationRepository organizations,
                          ChannelListingRepository listings, ReservationRepository reservations,
                          SyncJobScheduler syncJobs, Clock clock) {
        this.vehicles = vehicles;
        this.locations = locations;
        this.organizations = organizations;
        this.listings = listings;
        this.reservations = reservations;
        this.syncJobs = syncJobs;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<VehicleSummary> list(UUID organizationId, VehicleStatus status, String search, int page, int size) {
        String pattern = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return PageResponse.of(
                vehicles.search(organizationId, status, pattern, Pagination.of(page, size, Sort.by("reference"))),
                VehicleSummary::of);
    }

    @Transactional(readOnly = true)
    public VehicleView get(UUID organizationId, UUID vehicleId) {
        return view(find(organizationId, vehicleId));
    }

    @Transactional
    public VehicleView create(UUID organizationId, VehicleRequest request) {
        VehicleDetails details = toDetails(organizationId, request);
        if (vehicles.existsByOrganizationIdAndReference(organizationId, details.reference())) {
            throw ApiException.conflict("vehicle_reference_taken", "A car with reference " + details.reference() + " already exists");
        }
        return view(vehicles.save(new Vehicle(organizationId, details)));
    }

    @Transactional
    public VehicleView update(UUID organizationId, UUID vehicleId, VehicleRequest request) {
        Vehicle vehicle = findEditable(organizationId, vehicleId);
        VehicleDetails details = toDetails(organizationId, request);
        if (vehicles.existsByOrganizationIdAndReferenceAndIdNot(organizationId, details.reference(), vehicleId)) {
            throw ApiException.conflict("vehicle_reference_taken", "A car with reference " + details.reference() + " already exists");
        }

        vehicle.apply(details);
        if (vehicle.isActive()) {
            syncJobs.enqueueListingUpsert(organizationId, vehicleId, SyncTrigger.VEHICLE_UPDATED);
        }
        return view(vehicle);
    }

    /** Puts the car on sale on every connected channel. Idempotent. */
    @Transactional
    public VehicleView publish(UUID organizationId, UUID vehicleId) {
        Vehicle vehicle = findEditable(organizationId, vehicleId);
        if (vehicle.isActive()) {
            return view(vehicle);
        }
        if (vehicle.getPhotos().isEmpty()) {
            throw ApiException.unprocessable("photos_required", "Add at least one photo before publishing; channels require one");
        }

        vehicle.activate();
        syncJobs.enqueueListingUpsert(organizationId, vehicleId, SyncTrigger.VEHICLE_PUBLISHED);
        return view(vehicle);
    }

    /** Takes the car off sale everywhere. Existing reservations are kept. Idempotent. */
    @Transactional
    public VehicleView unpublish(UUID organizationId, UUID vehicleId) {
        Vehicle vehicle = findEditable(organizationId, vehicleId);
        if (!vehicle.isActive()) {
            return view(vehicle);
        }

        vehicle.deactivate();
        syncJobs.enqueueListingDeactivation(organizationId, vehicleId, SyncTrigger.VEHICLE_UNPUBLISHED);
        return view(vehicle);
    }

    /** Retires the car. Refused while it still has upcoming confirmed reservations. */
    @Transactional
    public void archive(UUID organizationId, UUID vehicleId) {
        Vehicle vehicle = find(organizationId, vehicleId);
        if (vehicle.isArchived()) {
            return;
        }
        if (reservations.existsUpcomingConfirmed(vehicleId, clock.instant())) {
            throw ApiException.conflict("vehicle_has_upcoming_reservations",
                    "This car has upcoming reservations. Cancel them on the channel before archiving.");
        }

        boolean wasActive = vehicle.isActive();
        vehicle.archive();
        if (wasActive) {
            syncJobs.enqueueListingDeactivation(organizationId, vehicleId, SyncTrigger.VEHICLE_ARCHIVED);
        }
    }

    /** Pushes the full listing again to every channel, ignoring change detection. */
    @Transactional
    public VehicleView resync(UUID organizationId, UUID vehicleId) {
        Vehicle vehicle = find(organizationId, vehicleId);
        if (!vehicle.isActive()) {
            throw ApiException.conflict("vehicle_not_active", "Only published cars can be synced");
        }
        syncJobs.enqueueForcedResync(organizationId, vehicleId, SyncTrigger.MANUAL_RESYNC);
        return view(vehicle);
    }

    @Transactional
    public VehicleView addPhoto(UUID organizationId, UUID vehicleId, PhotoRequest request) {
        Vehicle vehicle = findEditable(organizationId, vehicleId);
        String url = request.url().trim();
        if (vehicle.getPhotos().size() >= Vehicle.MAX_PHOTOS) {
            throw ApiException.unprocessable("too_many_photos", "A car can have at most " + Vehicle.MAX_PHOTOS + " photos");
        }
        if (vehicle.hasPhotoUrl(url)) {
            throw ApiException.conflict("duplicate_photo", "This photo is already attached to the car");
        }

        vehicle.addPhoto(url, blankToNull(request.caption()), request.position());
        photosChanged(vehicle);
        return view(vehicle);
    }

    @Transactional
    public VehicleView removePhoto(UUID organizationId, UUID vehicleId, UUID photoId) {
        Vehicle vehicle = findEditable(organizationId, vehicleId);
        if (vehicle.isActive() && vehicle.getPhotos().size() == 1 && vehicle.findPhoto(photoId).isPresent()) {
            throw ApiException.unprocessable("photos_required", "A published car must keep at least one photo");
        }
        if (!vehicle.removePhoto(photoId)) {
            throw ApiException.notFound("Photo");
        }

        photosChanged(vehicle);
        return view(vehicle);
    }

    @Transactional
    public VehicleView reorderPhotos(UUID organizationId, UUID vehicleId, ReorderPhotosRequest request) {
        Vehicle vehicle = findEditable(organizationId, vehicleId);
        Set<UUID> current = vehicle.getPhotos().stream().map(VehiclePhoto::getId).collect(Collectors.toSet());
        Set<UUID> requested = new HashSet<>(request.photoIds());
        if (requested.size() != request.photoIds().size() || !requested.equals(current)) {
            throw ApiException.unprocessable("invalid_photo_order", "photoIds must list every photo of the car exactly once");
        }

        vehicle.reorderPhotos(request.photoIds());
        photosChanged(vehicle);
        return view(vehicle);
    }

    private void photosChanged(Vehicle vehicle) {
        if (!vehicle.isActive()) {
            return;
        }
        syncJobs.enqueuePhotoSync(vehicle.getOrganizationId(), vehicle.getId(), SyncTrigger.PHOTOS_CHANGED);
    }

    private VehicleDetails toDetails(UUID organizationId, VehicleRequest request) {
        Location location = locations.findByIdAndOrganizationId(request.locationId(), organizationId)
                .orElseThrow(() -> ApiException.unprocessable("unknown_location", "The location does not exist"));
        String currency = request.currency() != null
                ? request.currency().toUpperCase(Locale.ROOT)
                : organizations.findById(organizationId).orElseThrow().getDefaultCurrency();
        return new VehicleDetails(
                location.getId(),
                request.reference().trim(),
                request.make().trim(),
                request.model().trim(),
                request.year(),
                request.acrissCode().toUpperCase(Locale.ROOT),
                request.transmission(),
                request.fuelType(),
                request.seats(),
                request.doors(),
                request.bags(),
                request.airConditioning(),
                request.mileageLimitKm(),
                request.minDriverAge(),
                request.dailyRate(),
                request.deposit(),
                currency);
    }

    private Vehicle find(UUID organizationId, UUID vehicleId) {
        return vehicles.findByIdAndOrganizationId(vehicleId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Vehicle"));
    }

    private Vehicle findEditable(UUID organizationId, UUID vehicleId) {
        Vehicle vehicle = find(organizationId, vehicleId);
        if (vehicle.isArchived()) {
            throw ApiException.conflict("vehicle_archived", "Archived cars cannot be changed");
        }
        return vehicle;
    }

    private VehicleView view(Vehicle vehicle) {
        Location location = locations.findById(vehicle.getLocationId()).orElseThrow();
        return VehicleView.of(vehicle, location, listings.findByVehicleIdOrderByChannel(vehicle.getId()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
