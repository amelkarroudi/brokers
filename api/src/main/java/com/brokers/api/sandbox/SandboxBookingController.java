package com.brokers.api.sandbox;

import com.brokers.channel.core.Channel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fake Booking.com supplier API implementing the contract {@code BookingClient} speaks. Any
 * credentials are accepted except an API secret of {@code invalid}, to demo rejected credentials.
 */
@RestController
@RequestMapping("/sandbox/booking")
@ConditionalOnProperty(name = "brokers.sandbox.enabled", havingValue = "true")
class SandboxBookingController {

    private final SandboxStore store;

    SandboxBookingController(SandboxStore store) {
        this.store = store;
    }

    @GetMapping("/suppliers/{supplierId}")
    ResponseEntity<Map<String, String>> supplier(@PathVariable String supplierId,
                                                 @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("supplier_id", supplierId, "status", "active"));
    }

    @PostMapping("/suppliers/{supplierId}/vehicles")
    ResponseEntity<Map<String, String>> create(@PathVariable String supplierId, @RequestBody JsonNode body,
                                               @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String id = store.create(Channel.BOOKING, supplierId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("vehicle_id", id, "status", "active"));
    }

    @PutMapping("/suppliers/{supplierId}/vehicles/{vehicleId}")
    ResponseEntity<Void> update(@PathVariable String supplierId, @PathVariable String vehicleId, @RequestBody JsonNode body,
                                @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        return change(auth, supplierId, vehicleId, listing -> listing.replaceContent(body));
    }

    @PutMapping("/suppliers/{supplierId}/vehicles/{vehicleId}/photos")
    ResponseEntity<Void> photos(@PathVariable String supplierId, @PathVariable String vehicleId, @RequestBody JsonNode body,
                                @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        return change(auth, supplierId, vehicleId, listing -> listing.replacePhotos(body));
    }

    @PutMapping("/suppliers/{supplierId}/vehicles/{vehicleId}/availability")
    ResponseEntity<Void> availability(@PathVariable String supplierId, @PathVariable String vehicleId,
                                      @RequestBody JsonNode body,
                                      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        return change(auth, supplierId, vehicleId, listing -> listing.replaceAvailability(body));
    }

    @DeleteMapping("/suppliers/{supplierId}/vehicles/{vehicleId}")
    ResponseEntity<Void> delete(@PathVariable String supplierId, @PathVariable String vehicleId,
                                @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        return change(auth, supplierId, vehicleId, SandboxStore.SandboxListing::deactivate);
    }

    private ResponseEntity<Void> change(String auth, String supplierId, String vehicleId,
                                        Consumer<SandboxStore.SandboxListing> change) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return store.find(Channel.BOOKING, supplierId, vehicleId)
                .map(listing -> {
                    change.accept(listing);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static boolean authorized(String header) {
        if (header == null || !header.startsWith("Basic ")) {
            return false;
        }
        String decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
        int separator = decoded.indexOf(':');
        return separator > 0 && !decoded.substring(separator + 1).equals("invalid");
    }
}
