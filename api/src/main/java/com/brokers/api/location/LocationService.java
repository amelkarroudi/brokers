package com.brokers.api.location;

import com.brokers.api.common.ApiException;
import com.brokers.api.location.LocationDtos.LocationRequest;
import com.brokers.api.location.LocationDtos.LocationView;
import com.brokers.api.sync.SyncJobScheduler;
import com.brokers.api.sync.SyncTrigger;
import com.brokers.api.vehicle.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LocationService {

    private final LocationRepository locations;
    private final VehicleRepository vehicles;
    private final SyncJobScheduler syncJobs;

    public LocationService(LocationRepository locations, VehicleRepository vehicles, SyncJobScheduler syncJobs) {
        this.locations = locations;
        this.vehicles = vehicles;
        this.syncJobs = syncJobs;
    }

    @Transactional(readOnly = true)
    public List<LocationView> list(UUID organizationId) {
        return locations.findByOrganizationIdOrderByNameAsc(organizationId).stream().map(LocationView::of).toList();
    }

    @Transactional(readOnly = true)
    public LocationView get(UUID organizationId, UUID locationId) {
        return LocationView.of(find(organizationId, locationId));
    }

    @Transactional
    public LocationView create(UUID organizationId, LocationRequest request) {
        LocationDetails details = request.toDetails();
        if (locations.existsByOrganizationIdAndCode(organizationId, details.code())) {
            throw ApiException.conflict("location_code_taken", "A location with code " + details.code() + " already exists");
        }
        return LocationView.of(locations.save(new Location(organizationId, details)));
    }

    /** Updates the branch and re-publishes every active car based there, since channels show its address. */
    @Transactional
    public LocationView update(UUID organizationId, UUID locationId, LocationRequest request) {
        Location location = find(organizationId, locationId);
        LocationDetails details = request.toDetails();
        if (locations.existsByOrganizationIdAndCodeAndIdNot(organizationId, details.code(), locationId)) {
            throw ApiException.conflict("location_code_taken", "A location with code " + details.code() + " already exists");
        }

        location.apply(details);
        vehicles.findActiveIdsByLocation(organizationId, locationId)
                .forEach(vehicleId -> syncJobs.enqueueListingUpsert(organizationId, vehicleId, SyncTrigger.LOCATION_UPDATED));
        return LocationView.of(location);
    }

    @Transactional
    public void delete(UUID organizationId, UUID locationId) {
        Location location = find(organizationId, locationId);
        if (vehicles.existsByLocationId(locationId)) {
            throw ApiException.conflict("location_in_use", "Move or delete the cars at this location first");
        }
        locations.delete(location);
    }

    Location find(UUID organizationId, UUID locationId) {
        return locations.findByIdAndOrganizationId(locationId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Location"));
    }
}
