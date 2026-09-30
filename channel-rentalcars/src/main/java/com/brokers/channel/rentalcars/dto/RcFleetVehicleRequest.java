package com.brokers.channel.rentalcars.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RcFleetVehicleRequest(
        String supplierVehicleCode,
        String sippCode,
        Vehicle vehicle,
        Terms terms,
        RcPrice dailyRate,
        RcPrice deposit,
        Station station) {

    public record Vehicle(
            String makeModel,
            int modelYear,
            String gearbox,
            String fuel,
            int passengers,
            int doors,
            int largeBags,
            boolean airCon) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Terms(String mileagePolicy, Integer includedKmPerDay, int minimumDriverAge) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Station(
            String stationCode,
            String stationName,
            String street,
            String city,
            String zip,
            String countryIso,
            BigDecimal lat,
            BigDecimal lng,
            String airportCode) {
    }
}
