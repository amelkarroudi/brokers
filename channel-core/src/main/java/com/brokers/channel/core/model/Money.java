package com.brokers.channel.core.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be an ISO-4217 code");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        currency = currency.toUpperCase();
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    /** Amount expressed in minor units (cents), as some channels expect integer prices. */
    public long minorUnits() {
        return amount.movePointRight(2).longValueExact();
    }

    public static Money ofMinorUnits(long minorUnits, String currency) {
        return new Money(BigDecimal.valueOf(minorUnits).movePointLeft(2), currency);
    }
}
