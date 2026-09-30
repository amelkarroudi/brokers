package com.brokers.api.reservation;

/** Result of applying a channel event: applied, or deliberately ignored with a reason. */
public record EventOutcome(boolean applied, String note) {

    public static EventOutcome processed() {
        return new EventOutcome(true, null);
    }

    public static EventOutcome ignored(String reason) {
        return new EventOutcome(false, reason);
    }
}
