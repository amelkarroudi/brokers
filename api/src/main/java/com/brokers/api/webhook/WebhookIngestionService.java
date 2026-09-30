package com.brokers.api.webhook;

import com.brokers.api.common.ApiException;
import com.brokers.api.common.PageResponse;
import com.brokers.api.common.Pagination;
import com.brokers.api.config.ChannelRegistry;
import com.brokers.api.connection.ChannelConnection;
import com.brokers.api.connection.ChannelConnectionRepository;
import com.brokers.api.connection.ConnectionService;
import com.brokers.api.connection.ConnectionStatus;
import com.brokers.api.reservation.EventOutcome;
import com.brokers.api.reservation.ReservationService;
import com.brokers.api.webhook.WebhookDtos.WebhookEventView;
import com.brokers.api.webhook.WebhookDtos.WebhookReceipt;
import com.brokers.channel.core.Channel;
import com.brokers.channel.core.webhook.ChannelEvent;
import com.brokers.channel.core.webhook.ChannelWebhookHandler;
import com.brokers.channel.core.webhook.ReservationEvent;
import com.brokers.channel.core.webhook.UnsupportedEvent;
import com.brokers.channel.core.webhook.WebhookParseException;
import com.brokers.channel.core.webhook.WebhookRequest;
import com.brokers.channel.core.webhook.WebhookVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Receives channel webhooks: authenticate, deduplicate, store, then apply.
 *
 * <p>An authentic event is always stored before it is applied, and the channel always gets a
 * 200 once it is stored, even if applying it fails. Failed events stay visible and can be
 * replayed, so nothing is lost and the channel does not keep re-sending it.
 */
@Service
public class WebhookIngestionService {

    /** Channels send small JSON documents; anything larger is rejected before parsing. */
    public static final int MAX_BODY_BYTES = 64 * 1024;

    private static final Logger log = LoggerFactory.getLogger(WebhookIngestionService.class);

    private final ChannelConnectionRepository connections;
    private final ConnectionService connectionService;
    private final WebhookEventRepository events;
    private final ReservationService reservationService;
    private final ChannelRegistry registry;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public WebhookIngestionService(ChannelConnectionRepository connections, ConnectionService connectionService,
                                   WebhookEventRepository events, ReservationService reservationService,
                                   ChannelRegistry registry, TransactionTemplate transactions, Clock clock) {
        this.connections = connections;
        this.connectionService = connectionService;
        this.events = events;
        this.reservationService = reservationService;
        this.registry = registry;
        this.transactions = transactions;
        this.clock = clock;
    }

    public WebhookReceipt receive(Channel channel, UUID connectionId, Map<String, String> headers, byte[] body) {
        if (body.length > MAX_BODY_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "payload_too_large", "Webhook body is too large");
        }
        ChannelConnection connection = connections.findByIdAndChannel(connectionId, channel)
                .filter(candidate -> candidate.getStatus() != ConnectionStatus.DISCONNECTED)
                .orElseThrow(() -> ApiException.notFound("Webhook endpoint"));

        WebhookRequest request = new WebhookRequest(headers, body);
        ChannelWebhookHandler handler = registry.webhookHandler(channel);
        try {
            handler.verify(request, connectionService.webhookSecret(connection));
        } catch (WebhookVerificationException e) {
            log.warn("Rejected {} webhook for connection {}: {}", channel, connectionId, e.getMessage());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_signature", "Webhook signature verification failed");
        }

        ChannelEvent event = parse(handler, request);
        Optional<WebhookEvent> duplicate = events.findByConnectionIdAndExternalEventId(connectionId, event.eventId());
        if (duplicate.isPresent()) {
            return new WebhookReceipt(duplicate.get().getId(), duplicate.get().getStatus(), true);
        }

        WebhookEvent stored;
        try {
            stored = transactions.execute(status -> events.saveAndFlush(new WebhookEvent(connection.getOrganizationId(),
                    connectionId, channel, event.eventId(), eventType(event),
                    new String(body, StandardCharsets.UTF_8), clock.instant())));
        } catch (DataIntegrityViolationException e) {
            WebhookEvent concurrent = events.findByConnectionIdAndExternalEventId(connectionId, event.eventId())
                    .orElseThrow(() -> e);
            return new WebhookReceipt(concurrent.getId(), concurrent.getStatus(), true);
        }
        return new WebhookReceipt(stored.getId(), apply(stored.getId(), event), false);
    }

    public PageResponse<WebhookEventView> list(UUID organizationId, WebhookEventStatus status, Channel channel,
                                               int page, int size) {
        return PageResponse.of(events.search(organizationId, status, channel,
                        Pagination.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"))),
                event -> WebhookEventView.of(event, false));
    }

    public WebhookEventView get(UUID organizationId, UUID eventId) {
        return WebhookEventView.of(find(organizationId, eventId), true);
    }

    /** Applies a stored failed event again, e.g. after the missing listing was published. */
    public WebhookEventView replay(UUID organizationId, UUID eventId) {
        WebhookEvent stored = find(organizationId, eventId);
        if (stored.getStatus() != WebhookEventStatus.FAILED) {
            throw ApiException.conflict("not_replayable", "Only failed events can be replayed");
        }
        ChannelWebhookHandler handler = registry.webhookHandler(stored.getChannel());
        ChannelEvent event = parse(handler, new WebhookRequest(Map.of(), stored.getPayload().getBytes(StandardCharsets.UTF_8)));
        apply(stored.getId(), event);
        return WebhookEventView.of(find(organizationId, eventId), true);
    }

    private WebhookEventStatus apply(UUID storedId, ChannelEvent event) {
        try {
            return transactions.execute(status -> {
                WebhookEvent stored = events.findById(storedId).orElseThrow();
                ChannelConnection connection = connections.findById(stored.getConnectionId()).orElseThrow();
                EventOutcome outcome = switch (event) {
                    case ReservationEvent reservation -> reservationService.apply(connection, reservation);
                    case UnsupportedEvent unsupported -> EventOutcome.ignored("Unsupported event type " + unsupported.type());
                };
                if (outcome.applied()) {
                    stored.markProcessed(clock.instant());
                } else {
                    stored.markIgnored(outcome.note(), clock.instant());
                }
                return stored.getStatus();
            });
        } catch (RuntimeException e) {
            log.warn("Could not apply webhook event {}: {}", storedId, e.getMessage());
            return transactions.execute(status -> {
                WebhookEvent stored = events.findById(storedId).orElseThrow();
                stored.markFailed(e.getMessage(), clock.instant());
                return stored.getStatus();
            });
        }
    }

    private static ChannelEvent parse(ChannelWebhookHandler handler, WebhookRequest request) {
        try {
            return handler.parse(request);
        } catch (WebhookParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_payload", e.getMessage());
        }
    }

    private static String eventType(ChannelEvent event) {
        return switch (event) {
            case ReservationEvent reservation -> "reservation." + reservation.type().name().toLowerCase();
            case UnsupportedEvent unsupported -> truncate(unsupported.type(), 80);
        };
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private WebhookEvent find(UUID organizationId, UUID eventId) {
        return events.findByIdAndOrganizationId(eventId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Webhook event"));
    }
}
