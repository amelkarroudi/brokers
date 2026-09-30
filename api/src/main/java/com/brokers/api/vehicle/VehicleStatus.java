package com.brokers.api.vehicle;

public enum VehicleStatus {
    /** Being prepared; not offered on any channel. */
    DRAFT,
    /** Offered on every connected channel. */
    ACTIVE,
    /** Retired from the fleet; kept for reservation history. */
    ARCHIVED
}
