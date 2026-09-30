package com.brokers.channel.core.error;

import com.brokers.channel.core.Channel;

import java.time.Duration;
import java.util.Optional;

/** Failure while talking to a channel, classified so callers can decide how to react. */
public class ChannelException extends RuntimeException {

    private final Channel channel;
    private final ChannelErrorKind kind;
    private final Duration retryAfter;

    public ChannelException(Channel channel, ChannelErrorKind kind, String message) {
        this(channel, kind, message, null, null);
    }

    public ChannelException(Channel channel, ChannelErrorKind kind, String message, Duration retryAfter, Throwable cause) {
        super(channel + ": " + message, cause);
        this.channel = channel;
        this.kind = kind;
        this.retryAfter = retryAfter;
    }

    public Channel channel() {
        return channel;
    }

    public ChannelErrorKind kind() {
        return kind;
    }

    public boolean retryable() {
        return kind.retryable();
    }

    /** Delay the channel asked us to wait before retrying, when it advertised one. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
