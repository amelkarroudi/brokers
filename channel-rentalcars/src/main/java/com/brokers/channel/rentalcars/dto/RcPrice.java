package com.brokers.channel.rentalcars.dto;

/** Rentalcars.com expresses prices as integer minor units (cents). */
public record RcPrice(long valueMinor, String currency) {
}
