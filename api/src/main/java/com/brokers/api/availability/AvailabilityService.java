package com.brokers.api.availability;

import com.brokers.api.availability.AvailabilityDtos.BlockRequest;
import com.brokers.api.availability.AvailabilityDtos.BlockView;
import com.brokers.api.common.ApiException;
import com.brokers.api.config.BrokersProperties;
import com.brokers.api.sync.SyncJobScheduler;
import com.brokers.api.sync.SyncTrigger;
import com.brokers.api.vehicle.Vehicle;
import com.brokers.api.vehicle.VehicleRepository;
import com.brokers.channel.core.model.AvailabilityUpdate;
import com.brokers.channel.core.model.AvailabilityWindow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AvailabilityService {

    private static final Duration MAX_BLOCK_LENGTH = Duration.ofDays(366);

    private final AvailabilityBlockRepository blocks;
    private final VehicleRepository vehicles;
    private final SyncJobScheduler syncJobs;
    private final Duration horizon;
    private final Clock clock;

    public AvailabilityService(AvailabilityBlockRepository blocks, VehicleRepository vehicles, SyncJobScheduler syncJobs,
                               BrokersProperties properties, Clock clock) {
        this.blocks = blocks;
        this.vehicles = vehicles;
        this.syncJobs = syncJobs;
        this.horizon = properties.sync().availabilityHorizon();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BlockView> list(UUID organizationId, UUID vehicleId, Instant from, Instant to) {
        findVehicle(organizationId, vehicleId);
        Instant start = from != null ? from : clock.instant().truncatedTo(ChronoUnit.DAYS);
        Instant end = to != null ? to : start.plus(horizon);
        if (!end.isAfter(start)) {
            throw ApiException.unprocessable("invalid_period", "'to' must be after 'from'");
        }
        return blocks.findOverlapping(vehicleId, start, end).stream().map(BlockView::of).toList();
    }

    @Transactional
    public BlockView create(UUID organizationId, UUID vehicleId, BlockRequest request) {
        Vehicle vehicle = findVehicle(organizationId, vehicleId);
        if (vehicle.isArchived()) {
            throw ApiException.conflict("vehicle_archived", "Archived cars cannot be changed");
        }
        BlockReason reason = request.reason() == null ? BlockReason.MANUAL : request.reason();
        if (reason == BlockReason.RESERVATION) {
            throw ApiException.unprocessable("invalid_reason", "Reservation blocks are created automatically");
        }
        validatePeriod(request.startsAt(), request.endsAt());
        if (blocks.existsOverlapping(vehicleId, request.startsAt(), request.endsAt(), null)) {
            throw ApiException.conflict("availability_conflict", "The car is already blocked or reserved during this period");
        }

        AvailabilityBlock block = blocks.save(AvailabilityBlock.manual(organizationId, vehicleId,
                request.startsAt(), request.endsAt(), reason, blankToNull(request.note())));
        availabilityChanged(vehicle);
        return BlockView.of(block);
    }

    @Transactional
    public void delete(UUID organizationId, UUID vehicleId, UUID blockId) {
        AvailabilityBlock block = blocks.findByIdAndOrganizationId(blockId, organizationId)
                .filter(candidate -> candidate.getVehicleId().equals(vehicleId))
                .orElseThrow(() -> ApiException.notFound("Availability block"));
        if (block.getReason() == BlockReason.RESERVATION) {
            throw ApiException.conflict("reservation_block",
                    "This period is held by a reservation. Cancel the reservation on its channel instead.");
        }

        blocks.delete(block);
        availabilityChanged(findVehicle(organizationId, vehicleId));
    }

    /**
     * Availability to push to channels: every blocked period inside the horizon, clipped to it,
     * with overlapping or touching periods merged so channels never receive overlapping windows.
     */
    @Transactional(readOnly = true)
    public AvailabilityUpdate currentUpdate(UUID vehicleId) {
        Instant start = clock.instant().truncatedTo(ChronoUnit.DAYS);
        Instant end = start.plus(horizon);
        List<AvailabilityWindow> merged = new ArrayList<>();
        Instant windowStart = null;
        Instant windowEnd = null;
        for (AvailabilityBlock block : blocks.findOverlapping(vehicleId, start, end)) {
            Instant blockStart = block.getStartsAt().isBefore(start) ? start : block.getStartsAt();
            Instant blockEnd = block.getEndsAt().isAfter(end) ? end : block.getEndsAt();
            if (windowEnd != null && !blockStart.isAfter(windowEnd)) {
                windowEnd = blockEnd.isAfter(windowEnd) ? blockEnd : windowEnd;
                continue;
            }
            if (windowStart != null) {
                merged.add(new AvailabilityWindow(windowStart, windowEnd));
            }
            windowStart = blockStart;
            windowEnd = blockEnd;
        }
        if (windowStart != null) {
            merged.add(new AvailabilityWindow(windowStart, windowEnd));
        }
        return new AvailabilityUpdate(start, end, merged);
    }

    private void availabilityChanged(Vehicle vehicle) {
        if (!vehicle.isActive()) {
            return;
        }
        syncJobs.enqueueAvailabilitySync(vehicle.getOrganizationId(), vehicle.getId(), SyncTrigger.AVAILABILITY_CHANGED);
    }

    private void validatePeriod(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw ApiException.unprocessable("invalid_period", "endsAt must be after startsAt");
        }
        if (!endsAt.isAfter(clock.instant())) {
            throw ApiException.unprocessable("invalid_period", "The period is entirely in the past");
        }
        if (Duration.between(startsAt, endsAt).compareTo(MAX_BLOCK_LENGTH) > 0) {
            throw ApiException.unprocessable("invalid_period", "A block cannot be longer than 366 days");
        }
    }

    private Vehicle findVehicle(UUID organizationId, UUID vehicleId) {
        return vehicles.findByIdAndOrganizationId(vehicleId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Vehicle"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
