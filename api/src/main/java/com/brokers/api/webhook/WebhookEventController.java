package com.brokers.api.webhook;

import com.brokers.api.common.PageResponse;
import com.brokers.api.security.AuthenticatedMember;
import com.brokers.api.webhook.WebhookDtos.WebhookEventView;
import com.brokers.channel.core.Channel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/webhook-events")
public class WebhookEventController {

    private final WebhookIngestionService ingestionService;

    public WebhookEventController(WebhookIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @GetMapping
    public PageResponse<WebhookEventView> list(@AuthenticationPrincipal AuthenticatedMember member,
                                               @RequestParam(required = false) WebhookEventStatus status,
                                               @RequestParam(required = false) Channel channel,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return ingestionService.list(member.organizationId(), status, channel, page, size);
    }

    @GetMapping("/{id}")
    public WebhookEventView get(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        return ingestionService.get(member.organizationId(), id);
    }

    @PostMapping("/{id}/replay")
    public WebhookEventView replay(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        return ingestionService.replay(member.organizationId(), id);
    }
}
