package com.brokers.api.listing;

public enum ListingStatus {
    /** Waiting for its first successful push. */
    PENDING,
    PUBLISHED,
    /** The last push failed permanently; see lastError. */
    FAILED,
    /** Taken off sale on the channel. */
    UNPUBLISHED
}
