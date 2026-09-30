package com.brokers.api.webhook;

public enum WebhookEventStatus {
    RECEIVED,
    PROCESSED,
    /** Authentic but intentionally not acted on (unsupported type, stale, already applied). */
    IGNORED,
    /** Could not be applied; can be replayed once the cause is fixed. */
    FAILED
}
