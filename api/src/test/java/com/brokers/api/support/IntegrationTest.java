package com.brokers.api.support;

import com.brokers.api.sync.SyncWorker;
import com.brokers.channel.booking.webhook.BookingWebhookHandler;
import com.brokers.channel.rentalcars.webhook.RentalcarsWebhookHandler;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


/**
 * Base class for API tests: a full application on an in-memory database, channels replaced by
 * WireMock, and the sync worker driven explicitly so tests are deterministic.
 *
 * <p>Every test signs up its own organization and uses unique channel account ids, so tests never
 * see each other's data and need no cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    protected static final String PASSWORD = "Correct-horse-42";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected SyncWorker syncWorker;

    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void channelEndpoints(DynamicPropertyRegistry registry) {
        registry.add("brokers.channels.booking.base-url", FakeChannels.BOOKING::baseUrl);
        registry.add("brokers.channels.rentalcars.base-url", FakeChannels.RENTALCARS::baseUrl);
    }

    @BeforeEach
    void resetChannels() {
        FakeChannels.resetToHealthy();
    }

    // ---------------------------------------------------------------------------------------------
    // HTTP helpers
    // ---------------------------------------------------------------------------------------------

    protected Response call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        }
        MvcResult result = mvc.perform(request).andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = content.isBlank() ? NullNode.getInstance() : json.readTree(content);
        return new Response(result.getResponse().getStatus(), node);
    }

    protected Response get(String path, String token) throws Exception {
        return call(MockMvcRequestBuilders.get(path), token, null);
    }

    protected Response post(String path, String token, Object body) throws Exception {
        return call(MockMvcRequestBuilders.post(path), token, body);
    }

    protected Response put(String path, String token, Object body) throws Exception {
        return call(MockMvcRequestBuilders.put(path), token, body);
    }

    protected Response delete(String path, String token) throws Exception {
        return call(MockMvcRequestBuilders.delete(path), token, null);
    }

    // ---------------------------------------------------------------------------------------------
    // Domain helpers
    // ---------------------------------------------------------------------------------------------

    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected Tenant signup() throws Exception {
        String email = unique("owner") + "@example.com";
        Response response = post("/api/auth/signup", null, Map.of(
                "organizationName", "Atlas Cars",
                "fullName", "Amina Owner",
                "email", email,
                "password", PASSWORD,
                "defaultCurrency", "EUR"));
        response.expectStatus(201);
        return new Tenant(response.body().get("token").asString(), email,
                UUID.fromString(response.body().at("/me/organization/id").asString()));
    }

    protected String createLocation(Tenant tenant) throws Exception {
        Response response = post("/api/locations", tenant.token(), Map.of(
                "code", unique("loc"),
                "name", "Casablanca Airport",
                "addressLine", "Aéroport Mohammed V",
                "city", "Casablanca",
                "countryCode", "MA",
                "iataCode", "CMN"));
        response.expectStatus(201);
        return response.body().get("id").asString();
    }

    protected Connection connect(Tenant tenant, String channel) throws Exception {
        String accountId = unique("ACC");
        Response response = put("/api/connections/" + channel, tenant.token(), Map.of(
                "accountId", accountId,
                "apiKey", "api-user",
                "apiSecret", "api-secret"));
        response.expectStatus(200);
        return new Connection(UUID.fromString(response.body().at("/connection/connectionId").asString()),
                accountId, response.body().get("webhookSecret").asString());
    }

    protected Map<String, Object> vehicleRequest(String locationId, String reference) {
        return new HashMap<>(Map.ofEntries(
                Map.entry("locationId", locationId),
                Map.entry("reference", reference),
                Map.entry("make", "Peugeot"),
                Map.entry("model", "208"),
                Map.entry("year", 2024),
                Map.entry("acrissCode", "EDMR"),
                Map.entry("transmission", "MANUAL"),
                Map.entry("fuelType", "PETROL"),
                Map.entry("seats", 5),
                Map.entry("doors", 5),
                Map.entry("bags", 2),
                Map.entry("airConditioning", true),
                Map.entry("minDriverAge", 21),
                Map.entry("dailyRate", "39.90"),
                Map.entry("deposit", "800.00")));
    }

    protected String createVehicle(Tenant tenant, String locationId) throws Exception {
        Response response = post("/api/vehicles", tenant.token(), vehicleRequest(locationId, unique("FLEET")));
        response.expectStatus(201);
        return response.body().get("id").asString();
    }

    protected void addPhoto(Tenant tenant, String vehicleId, String url) throws Exception {
        post("/api/vehicles/" + vehicleId + "/photos", tenant.token(), Map.of("url", url)).expectStatus(201);
    }

    /** A fully connected organization with one published car, already synced to both channels. */
    protected Fleet publishedFleet() throws Exception {
        Tenant tenant = signup();
        Connection booking = connect(tenant, "booking");
        Connection rentalcars = connect(tenant, "rentalcars");
        String locationId = createLocation(tenant);
        String vehicleId = createVehicle(tenant, locationId);
        addPhoto(tenant, vehicleId, "https://cdn.example.com/" + vehicleId + "/front.jpg");
        post("/api/vehicles/" + vehicleId + "/publish", tenant.token(), null).expectStatus(200);
        syncWorker.runUntilIdle();

        JsonNode listings = get("/api/vehicles/" + vehicleId, tenant.token()).body().get("listings");
        return new Fleet(tenant, booking, rentalcars, locationId, vehicleId,
                externalId(listings, "BOOKING"), externalId(listings, "RENTALCARS"));
    }

    protected static String externalId(JsonNode listings, String channel) {
        for (JsonNode listing : listings) {
            if (listing.get("channel").asString().equals(channel)) {
                return listing.path("externalId").asString(null);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Webhook helpers
    // ---------------------------------------------------------------------------------------------

    protected Response sendBookingWebhook(Connection connection, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/webhooks/booking/" + connection.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(BookingWebhookHandler.SIGNATURE_HEADER, BookingWebhookHandler.sign(connection.webhookSecret(), bytes))
                        .content(bytes))
                .andReturn();
        return toResponse(result);
    }

    protected Response sendRentalcarsWebhook(Connection connection, String body, Instant signedAt) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/webhooks/rentalcars/" + connection.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(RentalcarsWebhookHandler.SIGNATURE_HEADER,
                                RentalcarsWebhookHandler.sign(connection.webhookSecret(), signedAt.getEpochSecond(), bytes))
                        .content(bytes))
                .andReturn();
        return toResponse(result);
    }

    protected static String bookingReservation(String eventId, String type, Instant occurredAt, String reservationId,
                                               String vehicleId, Instant pickup, Instant dropoff) {
        String dates = pickup == null
                ? ""
                : ",\"pickup_datetime\":\"" + pickup + "\",\"dropoff_datetime\":\"" + dropoff + "\"";
        return """
                {"event_id":"%s","event_type":"%s","occurred_at":"%s",
                 "reservation":{"reservation_id":"%s","vehicle_id":"%s"%s,
                   "driver":{"first_name":"Amina","last_name":"Benali","email":"amina@example.com"},
                   "total_price":{"amount":159.60,"currency":"EUR"}}}
                """.formatted(eventId, type, occurredAt, reservationId, vehicleId, dates);
    }

    protected static String rentalcarsBooking(String eventId, String type, Instant createdAt, String reference,
                                              String vehicleId, Instant pickup, Instant dropoff) {
        return """
                {"id":"%s","type":"%s","createdAt":"%s",
                 "data":{"bookingReference":"%s","vehicleId":"%s",
                   "pickUp":{"dateTime":"%s"},"dropOff":{"dateTime":"%s"},
                   "customer":{"fullName":"Youssef Alaoui","email":"y@example.com"},
                   "price":{"valueMinor":27750,"currency":"EUR"}}}
                """.formatted(eventId, type, createdAt, reference, vehicleId, pickup, dropoff);
    }

    private Response toResponse(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Response(result.getResponse().getStatus(), content.isBlank() ? NullNode.getInstance() : json.readTree(content));
    }

    // ---------------------------------------------------------------------------------------------
    // Test data holders
    // ---------------------------------------------------------------------------------------------

    public record Response(int status, JsonNode body) {

        public Response expectStatus(int expected) {
            if (status != expected) {
                throw new AssertionError("Expected HTTP " + expected + " but got " + status + ": " + body);
            }
            return this;
        }

        public String code() {
            return body.path("code").asString(null);
        }
    }

    public record Tenant(String token, String email, UUID organizationId) {
    }

    public record Connection(UUID id, String accountId, String webhookSecret) {
    }

    public record Fleet(Tenant tenant, Connection booking, Connection rentalcars, String locationId, String vehicleId,
                        String bookingListingId, String rentalcarsListingId) {
    }
}
