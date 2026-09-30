package com.brokers.channel.rentalcars.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record RcImagesRequest(List<Image> images) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Image(String url, int order, boolean primary, String altText) {
    }
}
