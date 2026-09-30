package com.brokers.api.webhook;

import com.brokers.channel.core.Channel;

import java.time.Instant;
import java.util.UUID;

public final class WebhookDtos {

    private WebhookDtos() {
    }

    /** Acknowledgement returned to the channel. */
    public record WebhookReceipt(UUID eventId, WebhookEventStatus status, boolean duplicate) {
    }

    public record WebhookEventView(
            UUID id,
            Channel channel,
            String externalEventId,
            String eventType,
            WebhookEventStatus status,
            int attempts,
            String lastError,
            Instant receivedAt,
            Instant processedAt,
            String payload) {

        static WebhookEventView of(WebhookEvent event, boolean includePayload) {
            return new WebhookEventView(event.getId(), event.getChannel(), event.getExternalEventId(),
                    event.getEventType(), event.getStatus(), event.getAttempts(), event.getLastError(),
                    event.getReceivedAt(), event.getProcessedAt(), includePayload ? event.getPayload() : null);
        }
    }
}
