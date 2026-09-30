package com.brokers.api.organization;

import com.brokers.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A rental company: the tenant that owns a fleet and its channel connections. */
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 80, unique = true)
    private String slug;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationStatus status;

    protected Organization() {
    }

    public Organization(String name, String slug, String defaultCurrency, String timezone) {
        this.name = name;
        this.slug = slug;
        this.defaultCurrency = defaultCurrency;
        this.timezone = timezone;
        this.status = OrganizationStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public String getTimezone() {
        return timezone;
    }

    public OrganizationStatus getStatus() {
        return status;
    }
}
