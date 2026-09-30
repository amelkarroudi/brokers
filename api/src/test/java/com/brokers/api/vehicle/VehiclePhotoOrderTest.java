package com.brokers.api.vehicle;

import com.brokers.channel.core.model.FuelType;
import com.brokers.channel.core.model.Transmission;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VehiclePhotoOrderTest {

    @Test
    void insertsRemovesAndReordersKeepingPositionsContiguous() {
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), new VehicleDetails(UUID.randomUUID(), "R1", "Kia", "Picanto", 2024,
                "MDMR", Transmission.MANUAL, FuelType.PETROL, 4, 5, 1, true, null, 21,
                new BigDecimal("20.00"), null, "EUR"));

        VehiclePhoto a = vehicle.addPhoto("https://x/a.jpg", null, null);
        VehiclePhoto b = vehicle.addPhoto("https://x/b.jpg", null, null);
        VehiclePhoto c = vehicle.addPhoto("https://x/c.jpg", null, 0);

        assertThat(urls(vehicle)).containsExactly("https://x/c.jpg", "https://x/a.jpg", "https://x/b.jpg");

        vehicle.removePhoto(a.getId());
        assertThat(urls(vehicle)).containsExactly("https://x/c.jpg", "https://x/b.jpg");
        assertThat(vehicle.getPhotos()).extracting(VehiclePhoto::getPosition).containsExactly(0, 1);

        vehicle.reorderPhotos(List.of(b.getId(), c.getId()));
        assertThat(urls(vehicle)).containsExactly("https://x/b.jpg", "https://x/c.jpg");
    }

    private static List<String> urls(Vehicle vehicle) {
        return vehicle.getPhotos().stream().map(VehiclePhoto::getUrl).toList();
    }
}
