package com.brokers.api.vehicle;

import com.brokers.api.common.BaseEntity;
import com.brokers.channel.core.model.FuelType;
import com.brokers.channel.core.model.Transmission;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A car in an organization's fleet, the single source of truth for every channel listing. */
@Entity
@Table(name = "vehicles")
public class Vehicle extends BaseEntity {

    public static final int MAX_PHOTOS = 20;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(nullable = false, length = 60)
    private String reference;

    @Column(nullable = false, length = 60)
    private String make;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(name = "model_year", nullable = false)
    private int year;

    @Column(name = "acriss_code", nullable = false, length = 4)
    private String acrissCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Transmission transmission;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false, length = 20)
    private FuelType fuelType;

    @Column(nullable = false)
    private int seats;

    @Column(nullable = false)
    private int doors;

    @Column(nullable = false)
    private int bags;

    @Column(name = "air_conditioning", nullable = false)
    private boolean airConditioning;

    @Column(name = "mileage_limit_km")
    private Integer mileageLimitKm;

    @Column(name = "min_driver_age", nullable = false)
    private int minDriverAge;

    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @Column(precision = 12, scale = 2)
    private BigDecimal deposit;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "vehicle_id", nullable = false, updatable = false)
    @OrderBy("position ASC")
    private List<VehiclePhoto> photos = new ArrayList<>();

    protected Vehicle() {
    }

    public Vehicle(UUID organizationId, VehicleDetails details) {
        this.organizationId = organizationId;
        this.status = VehicleStatus.DRAFT;
        apply(details);
    }

    public void apply(VehicleDetails details) {
        this.locationId = details.locationId();
        this.reference = details.reference();
        this.make = details.make();
        this.model = details.model();
        this.year = details.year();
        this.acrissCode = details.acrissCode();
        this.transmission = details.transmission();
        this.fuelType = details.fuelType();
        this.seats = details.seats();
        this.doors = details.doors();
        this.bags = details.bags();
        this.airConditioning = details.airConditioning();
        this.mileageLimitKm = details.mileageLimitKm();
        this.minDriverAge = details.minDriverAge();
        this.dailyRate = details.dailyRate();
        this.deposit = details.deposit();
        this.currency = details.currency();
    }

    public void activate() {
        status = VehicleStatus.ACTIVE;
    }

    public void deactivate() {
        status = VehicleStatus.DRAFT;
    }

    public void archive() {
        status = VehicleStatus.ARCHIVED;
    }

    public boolean isActive() {
        return status == VehicleStatus.ACTIVE;
    }

    public boolean isArchived() {
        return status == VehicleStatus.ARCHIVED;
    }

    /** Inserts a photo at {@code position} (or last when null), shifting later photos down. */
    public VehiclePhoto addPhoto(String url, String caption, Integer position) {
        int target = position == null ? photos.size() : Math.clamp(position, 0, photos.size());
        List<VehiclePhoto> ordered = sortedPhotos();
        VehiclePhoto photo = new VehiclePhoto(url, caption, target);
        ordered.add(target, photo);
        renumber(ordered);
        photos.add(photo);
        return photo;
    }

    public boolean removePhoto(UUID photoId) {
        boolean removed = photos.removeIf(photo -> photo.getId().equals(photoId));
        if (!removed) {
            return false;
        }
        renumber(sortedPhotos());
        return true;
    }

    /** Applies a new order. {@code photoIds} must contain every photo exactly once. */
    public void reorderPhotos(List<UUID> photoIds) {
        for (int index = 0; index < photoIds.size(); index++) {
            UUID photoId = photoIds.get(index);
            int position = index;
            findPhoto(photoId).ifPresent(photo -> photo.moveTo(position));
        }
    }

    public boolean hasPhotoUrl(String url) {
        return photos.stream().anyMatch(photo -> photo.getUrl().equals(url));
    }

    public Optional<VehiclePhoto> findPhoto(UUID photoId) {
        return photos.stream().filter(photo -> photo.getId().equals(photoId)).findFirst();
    }

    private List<VehiclePhoto> sortedPhotos() {
        List<VehiclePhoto> ordered = new ArrayList<>(photos);
        ordered.sort(Comparator.comparingInt(VehiclePhoto::getPosition));
        return ordered;
    }

    private static void renumber(List<VehiclePhoto> ordered) {
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).moveTo(index);
        }
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public String getReference() {
        return reference;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public int getYear() {
        return year;
    }

    public String getAcrissCode() {
        return acrissCode;
    }

    public Transmission getTransmission() {
        return transmission;
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    public int getSeats() {
        return seats;
    }

    public int getDoors() {
        return doors;
    }

    public int getBags() {
        return bags;
    }

    public boolean isAirConditioning() {
        return airConditioning;
    }

    public Integer getMileageLimitKm() {
        return mileageLimitKm;
    }

    public int getMinDriverAge() {
        return minDriverAge;
    }

    public BigDecimal getDailyRate() {
        return dailyRate;
    }

    public BigDecimal getDeposit() {
        return deposit;
    }

    public String getCurrency() {
        return currency;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    /** Photos in display order. */
    public List<VehiclePhoto> getPhotos() {
        return Collections.unmodifiableList(sortedPhotos());
    }
}
