package com.brokers.api.sync;

/** The job cannot succeed as things stand (e.g. the channel is not connected); it is not retried. */
class SyncAbortedException extends RuntimeException {

    SyncAbortedException(String message) {
        super(message);
    }
}
