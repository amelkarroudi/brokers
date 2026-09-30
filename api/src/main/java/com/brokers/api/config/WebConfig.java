package com.brokers.api.config;

import com.brokers.channel.core.Channel;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Locale;

@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    /** Accepts {@code booking}, {@code BOOKING} or {@code Booking} in paths and query parameters. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Channel.class,
                (Converter<String, Channel>) value -> Channel.valueOf(value.trim().toUpperCase(Locale.ROOT)));
    }
}
