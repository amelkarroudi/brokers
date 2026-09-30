package com.brokers.api.config;

import com.brokers.channel.booking.BookingClient;
import com.brokers.channel.booking.webhook.BookingWebhookHandler;
import com.brokers.channel.core.ChannelClient;
import com.brokers.channel.core.http.ChannelHttpSettings;
import com.brokers.channel.core.webhook.ChannelWebhookHandler;
import com.brokers.channel.rentalcars.RentalcarsClient;
import com.brokers.channel.rentalcars.webhook.RentalcarsWebhookHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/** Wires the channel packages into the application. */
@Configuration(proxyBeanMethods = false)
public class ChannelConfig {

    @Bean
    ChannelClient bookingClient(RestClient.Builder builder, BrokersProperties properties) {
        return new BookingClient(builder, settings(properties.channels().booking()));
    }

    @Bean
    ChannelClient rentalcarsClient(RestClient.Builder builder, BrokersProperties properties, Clock clock) {
        return new RentalcarsClient(builder, settings(properties.channels().rentalcars()), clock);
    }

    @Bean
    ChannelWebhookHandler bookingWebhookHandler() {
        return new BookingWebhookHandler();
    }

    @Bean
    ChannelWebhookHandler rentalcarsWebhookHandler(Clock clock) {
        return new RentalcarsWebhookHandler(clock);
    }

    private static ChannelHttpSettings settings(BrokersProperties.Endpoint endpoint) {
        return new ChannelHttpSettings(endpoint.baseUrl(), endpoint.connectTimeout(), endpoint.readTimeout());
    }
}
