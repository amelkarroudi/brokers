package com.brokers.channel.core.webhook;

import com.brokers.channel.core.Channel;

/** Authenticates and decodes webhooks sent by a channel. */
public interface ChannelWebhookHandler {

    Channel channel();

    /**
     * Checks that the request was signed by the channel with the organization's webhook secret.
     *
     * @throws WebhookVerificationException when the signature is missing, malformed, wrong or stale
     */
    void verify(WebhookRequest request, String secret);

    /**
     * Decodes a verified request into a channel-agnostic event.
     *
     * @throws WebhookParseException when the body is not a valid event
     */
    ChannelEvent parse(WebhookRequest request);
}
