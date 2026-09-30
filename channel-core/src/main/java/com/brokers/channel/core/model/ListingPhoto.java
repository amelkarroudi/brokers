package com.brokers.channel.core.model;

/**
 * @param position zero-based display order; position 0 is the cover photo
 */
public record ListingPhoto(String url, int position, String caption) {
}
