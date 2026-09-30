package com.brokers.api.location;

import com.brokers.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** A branch where cars are picked up and returned. */
@Entity
@Table(name = "locations")
public class Location extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "iata_code", length = 3)
    private String iataCode;

    protected Location() {
    }

    public Location(UUID organizationId, LocationDetails details) {
        this.organizationId = organizationId;
        apply(details);
    }

    public void apply(LocationDetails details) {
        this.code = details.code();
        this.name = details.name();
        this.addressLine = details.addressLine();
        this.city = details.city();
        this.postalCode = details.postalCode();
        this.countryCode = details.countryCode();
        this.latitude = details.latitude();
        this.longitude = details.longitude();
        this.iataCode = details.iataCode();
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCity() {
        return city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getIataCode() {
        return iataCode;
    }
}
