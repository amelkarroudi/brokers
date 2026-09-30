package com.brokers.api.sync;

public enum SyncJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    /** Gave up: a permanent error or retries exhausted. Can be retried manually. */
    FAILED
}
