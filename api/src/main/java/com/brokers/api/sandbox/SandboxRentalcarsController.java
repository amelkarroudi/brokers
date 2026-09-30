package com.brokers.api.sandbox;

import com.brokers.channel.core.Channel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Fake Rentalcars.com supplier API implementing the contract {@code RentalcarsClient} speaks,
 * including the OAuth2 token endpoint. A client secret of {@code invalid} is rejected.
 */
@RestController
@RequestMapping("/sandbox/rentalcars")
@ConditionalOnProperty(name = "brokers.sandbox.enabled", havingValue = "true")
class SandboxRentalcarsController {

    private final SandboxStore store;
    private final Set<String> issuedTokens = ConcurrentHashMap.newKeySet();

    SandboxRentalcarsController(SandboxStore store) {
        this.store = store;
    }

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Map<String, Object>> token(@RequestParam("client_id") String clientId,
                                              @RequestParam("client_secret") String clientSecret) {
        if (clientId.isBlank() || clientSecret.equals("invalid")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_client"));
        }
        String token = "sbx_" + UUID.randomUUID();
        issuedTokens.add(token);
        return ResponseEntity.ok(Map.of("access_token", token, "token_type", "Bearer", "expires_in", 3600));
    }

    @GetMapping("/v2/suppliers/{supplierCode}")
    ResponseEntity<Map<String, String>> supplier(@PathVariable String supplierCode,
                                                 @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("supplierCode", supplierCode, "state", "ACTIVE"));
    }

    @PostMapping("/v2/suppliers/{supplierCode}/fleet")
    ResponseEntity<Map<String, String>> create(@PathVariable String supplierCode, @RequestBody JsonNode body,
                                               @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String id = store.create(Channel.RENTALCARS, supplierCode, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("vehicleId", id, "state", "ACTIVE"));
    }

    @PutMapping("/v2/suppliers/{supplierCode}/fleet/{vehicleId}")
    ResponseEntity<Void> update(@PathVariable String supplierCode, @PathVariable String vehicleId, @RequestBody JsonNode body,
                                @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        return change(auth, supplierCode, vehicleId, listing -> listing.replaceContent(body));
    }

    @PutMapping("/v2/suppliers/{supplierCode}/fleet/{vehicleId}/images")
    ResponseEntity<Void> images(@PathVariable String supplierCode, @PathVariable String vehicleId, @RequestBody JsonNode body,
                                @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        return change(auth, supplierCode, vehicleId, listing -> listing.replacePhotos(body));
    }

    @PutMapping("/v2/suppliers/{supplierCode}/fleet/{vehicleId}/stop-sales")
    ResponseEntity<Void> stopSales(@PathVariable String supplierCode, @PathVariable String vehicleId,
                                   @RequestBody JsonNode body,
                                   @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        return change(auth, supplierCode, vehicleId, listing -> listing.replaceAvailability(body));
    }

    @PostMapping("/v2/suppliers/{supplierCode}/fleet/{vehicleId}/deactivate")
    ResponseEntity<Void> deactivate(@PathVariable String supplierCode, @PathVariable String vehicleId,
                                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        return change(auth, supplierCode, vehicleId, SandboxStore.SandboxListing::deactivate);
    }

    private ResponseEntity<Void> change(String auth, String supplierCode, String vehicleId,
                                        Consumer<SandboxStore.SandboxListing> change) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return store.find(Channel.RENTALCARS, supplierCode, vehicleId)
                .map(listing -> {
                    change.accept(listing);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean authorized(String header) {
        return header != null && header.startsWith("Bearer ") && issuedTokens.contains(header.substring(7));
    }
}
