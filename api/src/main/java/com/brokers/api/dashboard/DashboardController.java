package com.brokers.api.dashboard;

import com.brokers.api.connection.ChannelConnectionRepository;
import com.brokers.api.listing.ChannelListingRepository;
import com.brokers.api.listing.ListingStatus;
import com.brokers.api.reservation.ReservationRepository;
import com.brokers.api.security.AuthenticatedMember;
import com.brokers.api.sync.SyncJobRepository;
import com.brokers.api.sync.SyncJobStatus;
import com.brokers.api.vehicle.VehicleRepository;
import com.brokers.api.vehicle.VehicleStatus;
import com.brokers.api.webhook.WebhookEventRepository;
import com.brokers.api.webhook.WebhookEventStatus;
import com.brokers.channel.core.Channel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Health of an organization's integration at a glance. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final VehicleRepository vehicles;
    private final ChannelConnectionRepository connections;
    private final ChannelListingRepository listings;
    private final ReservationRepository reservations;
    private final SyncJobRepository syncJobs;
    private final WebhookEventRepository webhookEvents;
    private final Clock clock;

    public DashboardController(VehicleRepository vehicles, ChannelConnectionRepository connections,
                               ChannelListingRepository listings, ReservationRepository reservations,
                               SyncJobRepository syncJobs, WebhookEventRepository webhookEvents, Clock clock) {
        this.vehicles = vehicles;
        this.connections = connections;
        this.listings = listings;
        this.reservations = reservations;
        this.syncJobs = syncJobs;
        this.webhookEvents = webhookEvents;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public DashboardView get(@AuthenticationPrincipal AuthenticatedMember member) {
        UUID organizationId = member.organizationId();
        return new DashboardView(
                new VehicleCounts(
                        vehicles.countByOrganizationIdAndStatus(organizationId, VehicleStatus.DRAFT),
                        vehicles.countByOrganizationIdAndStatus(organizationId, VehicleStatus.ACTIVE),
                        vehicles.countByOrganizationIdAndStatus(organizationId, VehicleStatus.ARCHIVED)),
                connections.findByOrganizationId(organizationId).stream()
                        .map(connection -> new ConnectionState(connection.getChannel(), connection.getStatus().name()))
                        .toList(),
                new ListingCounts(
                        listings.countByOrganizationIdAndStatus(organizationId, ListingStatus.PUBLISHED),
                        listings.countByOrganizationIdAndStatus(organizationId, ListingStatus.PENDING),
                        listings.countByOrganizationIdAndStatus(organizationId, ListingStatus.FAILED)),
                new ReservationCounts(
                        reservations.countUpcoming(organizationId, clock.instant()),
                        reservations.countByOrganizationIdAndHasConflictTrue(organizationId)),
                new SyncCounts(
                        syncJobs.countByOrganizationIdAndStatus(organizationId, SyncJobStatus.PENDING)
                                + syncJobs.countByOrganizationIdAndStatus(organizationId, SyncJobStatus.RUNNING),
                        syncJobs.countByOrganizationIdAndStatus(organizationId, SyncJobStatus.FAILED)),
                webhookEvents.countByOrganizationIdAndStatus(organizationId, WebhookEventStatus.FAILED));
    }

    public record DashboardView(VehicleCounts vehicles, List<ConnectionState> connections, ListingCounts listings,
                                ReservationCounts reservations, SyncCounts sync, long failedWebhooks) {
    }

    public record VehicleCounts(long draft, long active, long archived) {
    }

    public record ConnectionState(Channel channel, String status) {
    }

    public record ListingCounts(long published, long pending, long failed) {
    }

    public record ReservationCounts(long upcoming, long conflicts) {
    }

    public record SyncCounts(long queued, long failed) {
    }
}
