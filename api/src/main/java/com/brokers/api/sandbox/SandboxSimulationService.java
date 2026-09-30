package com.brokers.api.sandbox;

import com.brokers.api.common.ApiException;
import com.brokers.api.connection.ChannelConnection;
import com.brokers.api.connection.ChannelConnectionRepository;
import com.brokers.api.connection.ConnectionService;
import com.brokers.api.listing.ChannelListing;
import com.brokers.api.listing.ChannelListingRepository;
import com.brokers.api.organization.OrganizationRepository;
import com.brokers.api.sandbox.SandboxDtos.SimulateReservationRequest;
import com.brokers.api.sandbox.SandboxDtos.SimulationResult;
import com.brokers.api.vehicle.VehicleRepository;
import com.brokers.api.webhook.WebhookDtos.WebhookReceipt;
import com.brokers.api.webhook.WebhookIngestionService;
import com.brokers.channel.booking.webhook.BookingWebhookHandler;
import com.brokers.channel.core.Channel;
import com.brokers.channel.core.webhook.ReservationEventType;
import com.brokers.channel.rentalcars.webhook.RentalcarsWebhookHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a webhook exactly as the channel would (native payload format, real signature) and
 * feeds it through the normal ingestion path, so the simulation exercises the production code.
 */
@Service
@ConditionalOnProperty(name = "brokers.sandbox.enabled", havingValue = "true")
class SandboxSimulationService {

    private final VehicleRepository vehicles;
    private final ChannelListingRepository listings;
    private final ChannelConnectionRepository connections;
    private final OrganizationRepository organizations;
    private final ConnectionService connectionService;
    private final WebhookIngestionService ingestionService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    SandboxSimulationService(VehicleRepository vehicles, ChannelListingRepository listings,
                             ChannelConnectionRepository connections, OrganizationRepository organizations,
                             ConnectionService connectionService, WebhookIngestionService ingestionService,
                             JsonMapper jsonMapper, Clock clock) {
        this.vehicles = vehicles;
        this.listings = listings;
        this.connections = connections;
        this.organizations = organizations;
        this.connectionService = connectionService;
        this.ingestionService = ingestionService;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    SimulationResult simulate(UUID organizationId, SimulateReservationRequest request) {
        vehicles.findByIdAndOrganizationId(request.vehicleId(), organizationId)
                .orElseThrow(() -> ApiException.notFound("Vehicle"));
        ChannelListing listing = listings.findByVehicleIdAndChannel(request.vehicleId(), request.channel())
                .filter(ChannelListing::isPublished)
                .orElseThrow(() -> ApiException.conflict("listing_not_published",
                        "The car is not published on " + request.channel() + " yet. Publish it and run the sync first."));
        ChannelConnection connection = connections.findByOrganizationIdAndChannel(organizationId, request.channel())
                .orElseThrow(() -> ApiException.notFound(request.channel() + " connection"));
        validate(request);

        String reservationId = request.externalReservationId() != null
                ? request.externalReservationId()
                : request.channel().name().substring(0, 2) + "-RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String eventId = "evt_" + UUID.randomUUID();
        String currency = organizations.findById(organizationId).orElseThrow().getDefaultCurrency();
        Map<String, Object> payload = request.channel() == Channel.BOOKING
                ? bookingPayload(request, eventId, reservationId, listing.getExternalId(), currency)
                : rentalcarsPayload(request, eventId, reservationId, listing.getExternalId(), currency);

        byte[] body = jsonMapper.writeValueAsBytes(payload);
        String secret = connectionService.webhookSecret(connection);
        Map<String, String> headers = request.channel() == Channel.BOOKING
                ? Map.of(BookingWebhookHandler.SIGNATURE_HEADER, BookingWebhookHandler.sign(secret, body))
                : Map.of(RentalcarsWebhookHandler.SIGNATURE_HEADER,
                RentalcarsWebhookHandler.sign(secret, clock.instant().getEpochSecond(), body));

        WebhookReceipt receipt = ingestionService.receive(request.channel(), connection.getId(), headers, body);
        return new SimulationResult(reservationId, eventId, receipt);
    }

    private static void validate(SimulateReservationRequest request) {
        if (request.type() != ReservationEventType.CREATED && request.externalReservationId() == null) {
            throw ApiException.unprocessable("reservation_id_required", "externalReservationId is required to modify or cancel");
        }
        if (request.type() == ReservationEventType.CANCELLED) {
            return;
        }
        if (request.pickupAt() == null || request.returnAt() == null || !request.returnAt().isAfter(request.pickupAt())) {
            throw ApiException.unprocessable("invalid_period", "pickupAt and returnAt are required and returnAt must be later");
        }
    }

    private Map<String, Object> bookingPayload(SimulateReservationRequest request, String eventId, String reservationId,
                                               String vehicleId, String currency) {
        String eventType = switch (request.type()) {
            case CREATED -> "reservation.created";
            case MODIFIED -> "reservation.modified";
            case CANCELLED -> "reservation.cancelled";
        };
        Map<String, Object> reservation = new LinkedHashMap<>();
        reservation.put("reservation_id", reservationId);
        reservation.put("vehicle_id", vehicleId);
        reservation.put("pickup_datetime", iso(request.pickupAt()));
        reservation.put("dropoff_datetime", iso(request.returnAt()));
        String[] names = splitName(request.customerName());
        reservation.put("driver", Map.of("first_name", names[0], "last_name", names[1],
                "email", request.customerEmail() == null ? "" : request.customerEmail()));
        if (request.totalAmount() != null) {
            reservation.put("total_price", Map.of("amount", request.totalAmount(), "currency", currency));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", eventId);
        payload.put("event_type", eventType);
        payload.put("occurred_at", clock.instant().toString());
        payload.put("reservation", reservation);
        return payload;
    }

    private Map<String, Object> rentalcarsPayload(SimulateReservationRequest request, String eventId, String reservationId,
                                                  String vehicleId, String currency) {
        String eventType = switch (request.type()) {
            case CREATED -> "booking.confirmed";
            case MODIFIED -> "booking.amended";
            case CANCELLED -> "booking.cancelled";
        };
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bookingReference", reservationId);
        data.put("vehicleId", vehicleId);
        data.put("pickUp", Map.of("dateTime", iso(request.pickupAt())));
        data.put("dropOff", Map.of("dateTime", iso(request.returnAt())));
        data.put("customer", Map.of("fullName", request.customerName() == null ? "" : request.customerName(),
                "email", request.customerEmail() == null ? "" : request.customerEmail()));
        if (request.totalAmount() != null) {
            long minor = request.totalAmount().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
            data.put("price", Map.of("valueMinor", minor, "currency", currency));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", eventId);
        payload.put("type", eventType);
        payload.put("createdAt", clock.instant().toString());
        payload.put("data", data);
        return payload;
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{"", ""};
        }
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[]{trimmed, ""};
        }
        return new String[]{trimmed.substring(0, space), trimmed.substring(space + 1)};
    }
}
