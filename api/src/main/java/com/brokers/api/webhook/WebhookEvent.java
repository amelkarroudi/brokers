package com.brokers.api.webhook;

import com.brokers.channel.core.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Inbox record of every authentic webhook delivery; also the idempotency key for retries. */
@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "connection_id", nullable = false, updatable = false)
    private UUID connectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Channel channel;

    @Column(name = "external_event_id", nullable = false, updatable = false, length = 160)
    private String externalEventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 80)
    private String eventType;

    @Column(nullable = false, updatable = false, length = 65536)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookEventStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected WebhookEvent() {
    }

    public WebhookEvent(UUID organizationId, UUID connectionId, Channel channel, String externalEventId,
                        String eventType, String payload, Instant receivedAt) {
        this.organizationId = organizationId;
        this.connectionId = connectionId;
        this.channel = channel;
        this.externalEventId = externalEventId;
        this.eventType = eventType;
        this.payload = payload;
        this.receivedAt = receivedAt;
        this.status = WebhookEventStatus.RECEIVED;
    }

    public void markProcessed(Instant now) {
        finish(WebhookEventStatus.PROCESSED, null, now);
    }

    public void markIgnored(String reason, Instant now) {
        finish(WebhookEventStatus.IGNORED, reason, now);
    }

    public void markFailed(String error, Instant now) {
        finish(WebhookEventStatus.FAILED, error, now);
    }

    private void finish(WebhookEventStatus status, String note, Instant now) {
        this.status = status;
        this.attempts++;
        this.lastError = note == null || note.length() <= MAX_ERROR_LENGTH ? note : note.substring(0, MAX_ERROR_LENGTH);
        this.processedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public WebhookEventStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
