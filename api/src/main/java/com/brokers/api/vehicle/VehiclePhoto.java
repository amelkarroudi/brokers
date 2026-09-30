package com.brokers.api.vehicle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicle_photos")
public class VehiclePhoto {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 200)
    private String caption;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VehiclePhoto() {
    }

    VehiclePhoto(String url, String caption, int position) {
        this.url = url;
        this.caption = caption;
        this.position = position;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    void moveTo(int position) {
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getCaption() {
        return caption;
    }

    public int getPosition() {
        return position;
    }
}
