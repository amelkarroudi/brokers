package com.brokers.channel.core.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalisesScaleAndCurrency() {
        Money money = new Money(new BigDecimal("49.5"), "eur");

        assertThat(money.amount()).isEqualByComparingTo("49.50");
        assertThat(money.currency()).isEqualTo("EUR");
        assertThat(money.minorUnits()).isEqualTo(4950);
    }

    @Test
    void roundTripsMinorUnits() {
        assertThat(Money.ofMinorUnits(12345, "USD")).isEqualTo(Money.of("123.45", "USD"));
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> Money.of("-1", "EUR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of("1", "EURO")).isInstanceOf(IllegalArgumentException.class);
    }
}
