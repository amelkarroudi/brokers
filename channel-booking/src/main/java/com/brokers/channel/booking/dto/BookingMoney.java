package com.brokers.channel.booking.dto;

import java.math.BigDecimal;

public record BookingMoney(BigDecimal amount, String currency) {
}
