package com.brokers.api.config;

import com.brokers.channel.core.Channel;
import com.brokers.channel.core.ChannelClient;
import com.brokers.channel.core.webhook.ChannelWebhookHandler;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Looks up the client and webhook handler of a channel. Fails fast if one is missing. */
@Component
public class ChannelRegistry {

    private final Map<Channel, ChannelClient> clients = new EnumMap<>(Channel.class);
    private final Map<Channel, ChannelWebhookHandler> webhookHandlers = new EnumMap<>(Channel.class);

    public ChannelRegistry(List<ChannelClient> clients, List<ChannelWebhookHandler> webhookHandlers) {
        clients.forEach(client -> this.clients.put(client.channel(), client));
        webhookHandlers.forEach(handler -> this.webhookHandlers.put(handler.channel(), handler));
        for (Channel channel : Channel.values()) {
            if (!this.clients.containsKey(channel) || !this.webhookHandlers.containsKey(channel)) {
                throw new IllegalStateException("Channel " + channel + " is not fully configured");
            }
        }
    }

    public ChannelClient client(Channel channel) {
        return clients.get(channel);
    }

    public ChannelWebhookHandler webhookHandler(Channel channel) {
        return webhookHandlers.get(channel);
    }
}
