package com.brokers.channel.core.error;

public enum ChannelErrorKind {
    /** Credentials were rejected. Retrying will not help until they are fixed. */
    AUTHENTICATION(false),
    /** The channel rejected the payload. Retrying the same payload will not help. */
    VALIDATION(false),
    /** The referenced remote resource does not exist. */
    NOT_FOUND(false),
    /** Too many requests; retry after the advertised delay. */
    RATE_LIMITED(true),
    /** Network failure, timeout or 5xx response. */
    UNAVAILABLE(true),
    /** Anything the integration does not know how to classify. */
    UNEXPECTED(false);

    private final boolean retryable;

    ChannelErrorKind(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
