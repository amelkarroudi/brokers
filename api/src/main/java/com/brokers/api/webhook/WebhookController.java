package com.brokers.api.webhook;

import com.brokers.api.webhook.WebhookDtos.WebhookReceipt;
import com.brokers.channel.core.Channel;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Public endpoint the channels call. It is unauthenticated at the HTTP level; every request is
 * authenticated by its HMAC signature instead. The body is read as raw bytes because the
 * signature covers the exact bytes sent.
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookIngestionService ingestionService;

    public WebhookController(WebhookIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/{channel}/{connectionId}")
    public WebhookReceipt receive(@PathVariable Channel channel, @PathVariable UUID connectionId,
                                  @RequestHeader HttpHeaders headers, @RequestBody(required = false) byte[] body) {
        return ingestionService.receive(channel, connectionId, headers.toSingleValueMap(), body == null ? new byte[0] : body);
    }
}
