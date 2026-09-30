package com.brokers.api.reservation;

/** A valid, authentic event the platform cannot apply, e.g. it references an unknown listing. */
public class UnprocessableEventException extends RuntimeException {

    public UnprocessableEventException(String message) {
        super(message);
    }
}
