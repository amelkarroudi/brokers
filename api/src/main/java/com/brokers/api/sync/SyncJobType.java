package com.brokers.api.sync;

public enum SyncJobType {
    /** Create or update the listing, then its photos and availability. */
    UPSERT_LISTING,
    REPLACE_PHOTOS,
    REPLACE_AVAILABILITY,
    DEACTIVATE_LISTING
}
