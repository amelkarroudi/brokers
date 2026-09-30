package com.brokers.api.reservation;

import com.brokers.api.common.BaseEntity;
import com.brokers.channel.core.Channel;
import com.brokers.channel.core.model.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A booking made by a customer on a channel, mirrored locally from webhooks. */
@Entity
@Table(name = "reservations")
public class Reservation extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Channel channel;

    @Column(name = "external_reservation_id", nullable = false, updatable = false, length = 120)
    private String externalReservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "pickup_at", nullable = false)
    private Instant pickupAt;

    @Column(name = "return_at", nullable = false)
    private Instant returnAt;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "customer_email", length = 254)
    private String customerEmail;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 3)
    private String currency;

    @Column(name = "has_conflict", nullable = false)
    private boolean hasConflict;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    protected Reservation() {
    }

    public Reservation(UUID organizationId, UUID vehicleId, Channel channel, String externalReservationId,
                       ReservationDetails details, Instant eventAt) {
        this.organizationId = organizationId;
        this.channel = channel;
        this.externalReservationId = externalReservationId;
        this.status = ReservationStatus.CONFIRMED;
        apply(vehicleId, details, eventAt);
    }

    /** Applies a create or modify event. A newer event also reinstates a cancelled reservation. */
    public void apply(UUID vehicleId, ReservationDetails details, Instant eventAt) {
        if (!details.returnAt().isAfter(details.pickupAt())) {
            throw new IllegalArgumentException("returnAt must be after pickupAt");
        }
        this.vehicleId = vehicleId;
        this.status = ReservationStatus.CONFIRMED;
        this.pickupAt = details.pickupAt();
        this.returnAt = details.returnAt();
        this.customerName = details.customerName();
        this.customerEmail = details.customerEmail();
        Money total = details.totalPrice();
        this.totalAmount = total == null ? null : total.amount();
        this.currency = total == null ? null : total.currency();
        this.lastEventAt = eventAt;
    }

    public void cancel(Instant eventAt) {
        status = ReservationStatus.CANCELLED;
        hasConflict = false;
        lastEventAt = eventAt;
    }

    /** True when this event is older than one already applied, i.e. it arrived out of order. */
    public boolean isNewerThan(Instant eventAt) {
        return lastEventAt.isAfter(eventAt);
    }

    public void flagConflict(boolean conflict) {
        this.hasConflict = conflict;
    }

    public boolean isConfirmed() {
        return status == ReservationStatus.CONFIRMED;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getExternalReservationId() {
        return externalReservationId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getPickupAt() {
        return pickupAt;
    }

    public Instant getReturnAt() {
        return returnAt;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isHasConflict() {
        return hasConflict;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }
}
