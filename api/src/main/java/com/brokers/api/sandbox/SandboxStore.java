package com.brokers.api.sandbox;

import com.brokers.channel.core.Channel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory state of the fake channels. Lost on restart, which is fine for a sandbox. */
@Component
@ConditionalOnProperty(name = "brokers.sandbox.enabled", havingValue = "true")
public class SandboxStore {

    private final Map<Channel, Map<String, SandboxListing>> listings = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public String create(Channel channel, String accountId, JsonNode content) {
        String prefix = channel == Channel.BOOKING ? "BK-" : "RC-";
        String id = prefix + String.format("%06d", sequence.incrementAndGet());
        byChannel(channel).put(id, new SandboxListing(id, accountId, content));
        return id;
    }

    public Optional<SandboxListing> find(Channel channel, String accountId, String id) {
        return Optional.ofNullable(byChannel(channel).get(id))
                .filter(listing -> listing.accountId().equals(accountId));
    }

    public List<SandboxListing> list(Channel channel, String accountId) {
        return byChannel(channel).values().stream()
                .filter(listing -> listing.accountId().equals(accountId))
                .sorted(Comparator.comparing(SandboxListing::externalId))
                .toList();
    }

    private Map<String, SandboxListing> byChannel(Channel channel) {
        return listings.computeIfAbsent(channel, ignored -> new ConcurrentHashMap<>());
    }

    /** One listing as the fake channel sees it. */
    public static final class SandboxListing {

        private final String externalId;
        private final String accountId;
        private volatile JsonNode content;
        private volatile JsonNode photos;
        private volatile JsonNode availability;
        private volatile boolean active = true;
        private volatile Instant updatedAt = Instant.now();

        SandboxListing(String externalId, String accountId, JsonNode content) {
            this.externalId = externalId;
            this.accountId = accountId;
            this.content = content;
        }

        public void replaceContent(JsonNode content) {
            this.content = content;
            this.active = true;
            touch();
        }

        public void replacePhotos(JsonNode photos) {
            this.photos = photos;
            touch();
        }

        public void replaceAvailability(JsonNode availability) {
            this.availability = availability;
            touch();
        }

        public void deactivate() {
            this.active = false;
            touch();
        }

        private void touch() {
            updatedAt = Instant.now();
        }

        public String externalId() {
            return externalId;
        }

        public String accountId() {
            return accountId;
        }

        public JsonNode content() {
            return content;
        }

        public JsonNode photos() {
            return photos;
        }

        public JsonNode availability() {
            return availability;
        }

        public boolean active() {
            return active;
        }

        public Instant updatedAt() {
            return updatedAt;
        }
    }
}
