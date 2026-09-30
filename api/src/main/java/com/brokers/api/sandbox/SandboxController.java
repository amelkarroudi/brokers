package com.brokers.api.sandbox;

import com.brokers.api.common.ApiException;
import com.brokers.api.connection.ChannelConnectionRepository;
import com.brokers.api.sandbox.SandboxDtos.SandboxListingView;
import com.brokers.api.sandbox.SandboxDtos.SimulateReservationRequest;
import com.brokers.api.sandbox.SandboxDtos.SimulationResult;
import com.brokers.api.sandbox.SandboxDtos.SyncRunResult;
import com.brokers.api.security.AuthenticatedMember;
import com.brokers.api.sync.SyncWorker;
import com.brokers.channel.core.Channel;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Developer tools for trying the full flow locally. Only available when the sandbox is enabled. */
@RestController
@RequestMapping("/api/sandbox")
@ConditionalOnProperty(name = "brokers.sandbox.enabled", havingValue = "true")
class SandboxController {

    private final SandboxSimulationService simulationService;
    private final SandboxStore store;
    private final ChannelConnectionRepository connections;
    private final SyncWorker syncWorker;

    SandboxController(SandboxSimulationService simulationService, SandboxStore store,
                      ChannelConnectionRepository connections, SyncWorker syncWorker) {
        this.simulationService = simulationService;
        this.store = store;
        this.connections = connections;
        this.syncWorker = syncWorker;
    }

    /** Sends a signed reservation webhook as if a customer booked on the channel. */
    @PostMapping("/reservations")
    SimulationResult simulateReservation(@AuthenticationPrincipal AuthenticatedMember member,
                                         @Valid @RequestBody SimulateReservationRequest request) {
        return simulationService.simulate(member.organizationId(), request);
    }

    /** What the fake channel currently holds for this organization's account. */
    @GetMapping("/channels/{channel}/listings")
    List<SandboxListingView> channelListings(@AuthenticationPrincipal AuthenticatedMember member,
                                             @PathVariable Channel channel) {
        String accountId = connections.findByOrganizationIdAndChannel(member.organizationId(), channel)
                .orElseThrow(() -> ApiException.notFound(channel + " connection"))
                .getAccountId();
        return store.list(channel, accountId).stream().map(SandboxListingView::of).toList();
    }

    /** Processes every due sync job now instead of waiting for the background worker. */
    @PostMapping("/sync/run")
    SyncRunResult runSync() {
        return new SyncRunResult(syncWorker.runUntilIdle());
    }
}
