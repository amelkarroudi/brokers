package com.brokers.api.availability;

import com.brokers.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A half-open period {@code [startsAt, endsAt)} during which a car cannot be rented. */
@Entity
@Table(name = "availability_blocks")
public class AvailabilityBlock extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private BlockReason reason;

    @Column(name = "reservation_id", updatable = false)
    private UUID reservationId;

    @Column(length = 500)
    private String note;

    protected AvailabilityBlock() {
    }

    private AvailabilityBlock(UUID organizationId, UUID vehicleId, Instant startsAt, Instant endsAt,
                              BlockReason reason, UUID reservationId, String note) {
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        this.organizationId = organizationId;
        this.vehicleId = vehicleId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
        this.reservationId = reservationId;
        this.note = note;
    }

    public static AvailabilityBlock manual(UUID organizationId, UUID vehicleId, Instant startsAt, Instant endsAt,
                                           BlockReason reason, String note) {
        if (reason == BlockReason.RESERVATION) {
            throw new IllegalArgumentException("Reservation blocks are created from reservations");
        }
        return new AvailabilityBlock(organizationId, vehicleId, startsAt, endsAt, reason, null, note);
    }

    public static AvailabilityBlock forReservation(UUID organizationId, UUID vehicleId, UUID reservationId,
                                                   Instant startsAt, Instant endsAt) {
        return new AvailabilityBlock(organizationId, vehicleId, startsAt, endsAt, BlockReason.RESERVATION, reservationId, null);
    }

    public void reschedule(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public BlockReason getReason() {
        return reason;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public String getNote() {
        return note;
    }
}
