package com.brokers.api.reservation;

import com.brokers.api.availability.AvailabilityBlock;
import com.brokers.api.availability.AvailabilityBlockRepository;
import com.brokers.api.common.ApiException;
import com.brokers.api.common.PageResponse;
import com.brokers.api.common.Pagination;
import com.brokers.api.connection.ChannelConnection;
import com.brokers.api.listing.ChannelListing;
import com.brokers.api.listing.ChannelListingRepository;
import com.brokers.api.reservation.ReservationDtos.ReservationView;
import com.brokers.api.sync.SyncJobScheduler;
import com.brokers.api.sync.SyncTrigger;
import com.brokers.channel.core.webhook.ReservationEvent;
import com.brokers.channel.core.webhook.ReservationEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Mirrors channel reservations locally and turns them into availability blocks, which are then
 * pushed to every other channel so the same car cannot be sold twice.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservations;
    private final AvailabilityBlockRepository blocks;
    private final ChannelListingRepository listings;
    private final SyncJobScheduler syncJobs;
    private final Clock clock;

    public ReservationService(ReservationRepository reservations, AvailabilityBlockRepository blocks,
                              ChannelListingRepository listings, SyncJobScheduler syncJobs, Clock clock) {
        this.reservations = reservations;
        this.blocks = blocks;
        this.listings = listings;
        this.syncJobs = syncJobs;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationView> list(UUID organizationId, ReservationStatus status, UUID vehicleId,
                                              boolean conflictOnly, int page, int size) {
        return PageResponse.of(reservations.search(organizationId, status, vehicleId, conflictOnly,
                        Pagination.of(page, size, Sort.by(Sort.Direction.DESC, "pickupAt"))),
                ReservationView::of);
    }

    @Transactional(readOnly = true)
    public ReservationView get(UUID organizationId, UUID reservationId) {
        return reservations.findByIdAndOrganizationId(reservationId, organizationId)
                .map(ReservationView::of)
                .orElseThrow(() -> ApiException.notFound("Reservation"));
    }

    /**
     * Applies a reservation event. Handles retries (same state twice), out-of-order delivery
     * (older events are ignored), cancellations of unknown reservations, and overbookings (the
     * reservation is kept, because the channel already sold it, and flagged as a conflict).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public EventOutcome apply(ChannelConnection connection, ReservationEvent event) {
        Optional<Reservation> existing = reservations.findByChannelAndExternalReservationId(
                connection.getChannel(), event.externalReservationId());
        if (existing.isPresent() && !existing.get().getOrganizationId().equals(connection.getOrganizationId())) {
            throw new UnprocessableEventException("Reservation belongs to another organization");
        }
        if (existing.isPresent() && existing.get().isNewerThan(event.occurredAt())) {
            return EventOutcome.ignored("A newer event was already applied to this reservation");
        }

        if (event.type() == ReservationEventType.CANCELLED) {
            return cancel(existing, event);
        }
        return upsert(connection, existing, event);
    }

    private EventOutcome cancel(Optional<Reservation> existing, ReservationEvent event) {
        if (existing.isEmpty()) {
            return EventOutcome.ignored("Cancellation for an unknown reservation");
        }
        Reservation reservation = existing.get();
        if (!reservation.isConfirmed()) {
            return EventOutcome.ignored("Reservation is already cancelled");
        }

        reservation.cancel(event.occurredAt());
        blocks.findByReservationId(reservation.getId()).ifPresent(blocks::delete);
        blocks.flush();
        refreshConflicts(reservation.getVehicleId());
        syncJobs.enqueueAvailabilitySync(reservation.getOrganizationId(), reservation.getVehicleId(),
                SyncTrigger.RESERVATION_CHANGED);
        log.info("Reservation {} cancelled on {}", reservation.getExternalReservationId(), reservation.getChannel());
        return EventOutcome.processed();
    }

    private EventOutcome upsert(ChannelConnection connection, Optional<Reservation> existing, ReservationEvent event) {
        ChannelListing listing = listings.findByChannelAndExternalId(connection.getChannel(), event.externalListingId())
                .filter(candidate -> candidate.getOrganizationId().equals(connection.getOrganizationId()))
                .orElseThrow(() -> new UnprocessableEventException(
                        "No listing with id " + event.externalListingId() + " on " + connection.getChannel()));
        UUID vehicleId = listing.getVehicleId();
        ReservationDetails details = new ReservationDetails(event.pickupAt(), event.returnAt(),
                event.customerName(), event.customerEmail(), event.totalPrice());

        UUID previousVehicleId = existing.map(Reservation::getVehicleId).orElse(null);
        Reservation reservation = existing.orElse(null);
        if (reservation == null) {
            reservation = reservations.save(new Reservation(connection.getOrganizationId(), vehicleId,
                    connection.getChannel(), event.externalReservationId(), details, event.occurredAt()));
        } else {
            reservation.apply(vehicleId, details, event.occurredAt());
        }

        replaceBlock(reservation);
        refreshConflicts(vehicleId);
        syncJobs.enqueueAvailabilitySync(reservation.getOrganizationId(), vehicleId, SyncTrigger.RESERVATION_CHANGED);
        if (previousVehicleId != null && !previousVehicleId.equals(vehicleId)) {
            refreshConflicts(previousVehicleId);
            syncJobs.enqueueAvailabilitySync(reservation.getOrganizationId(), previousVehicleId,
                    SyncTrigger.RESERVATION_CHANGED);
        }
        if (reservation.isHasConflict()) {
            log.warn("Overbooking: reservation {} on {} overlaps another block of vehicle {}",
                    reservation.getExternalReservationId(), reservation.getChannel(), vehicleId);
        }
        return EventOutcome.processed();
    }

    /** Keeps exactly one block matching the reservation's current car and dates. */
    private void replaceBlock(Reservation reservation) {
        Optional<AvailabilityBlock> block = blocks.findByReservationId(reservation.getId());
        boolean sameVehicle = block.map(existing -> existing.getVehicleId().equals(reservation.getVehicleId())).orElse(false);
        if (sameVehicle) {
            block.get().reschedule(reservation.getPickupAt(), reservation.getReturnAt());
            blocks.flush();
            return;
        }
        block.ifPresent(blocks::delete);
        blocks.flush();
        blocks.saveAndFlush(AvailabilityBlock.forReservation(reservation.getOrganizationId(), reservation.getVehicleId(),
                reservation.getId(), reservation.getPickupAt(), reservation.getReturnAt()));
    }

    /** Recomputes the overbooking flag of every current reservation of a car. */
    private void refreshConflicts(UUID vehicleId) {
        for (Reservation reservation : reservations.findConfirmedEndingAfter(vehicleId, clock.instant().minus(Duration.ofDays(1)))) {
            UUID ownBlockId = blocks.findByReservationId(reservation.getId()).map(AvailabilityBlock::getId).orElse(null);
            boolean overlaps = blocks.existsOverlapping(vehicleId, reservation.getPickupAt(), reservation.getReturnAt(), ownBlockId);
            reservation.flagConflict(overlaps);
        }
    }
}
