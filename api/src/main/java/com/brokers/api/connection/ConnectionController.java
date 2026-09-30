package com.brokers.api.connection;

import com.brokers.api.connection.ConnectionDtos.ConnectRequest;
import com.brokers.api.connection.ConnectionDtos.ConnectionSecretView;
import com.brokers.api.connection.ConnectionDtos.ConnectionView;
import com.brokers.api.security.AuthenticatedMember;
import com.brokers.channel.core.Channel;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public List<ConnectionView> list(@AuthenticationPrincipal AuthenticatedMember member) {
        return connectionService.list(member.organizationId());
    }

    /** Connects the channel, or replaces its credentials when already connected. */
    @PutMapping("/{channel}")
    public ConnectionSecretView connect(@AuthenticationPrincipal AuthenticatedMember member,
                                        @PathVariable Channel channel,
                                        @Valid @RequestBody ConnectRequest request) {
        return connectionService.connect(member.organizationId(), channel, request);
    }

    @PostMapping("/{channel}/verify")
    public ConnectionView verify(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Channel channel) {
        return connectionService.verify(member.organizationId(), channel);
    }

    @PostMapping("/{channel}/webhook-secret")
    public ConnectionSecretView rotateWebhookSecret(@AuthenticationPrincipal AuthenticatedMember member,
                                                    @PathVariable Channel channel) {
        return connectionService.rotateWebhookSecret(member.organizationId(), channel);
    }

    @DeleteMapping("/{channel}")
    public ConnectionView disconnect(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Channel channel) {
        return connectionService.disconnect(member.organizationId(), channel);
    }
}
