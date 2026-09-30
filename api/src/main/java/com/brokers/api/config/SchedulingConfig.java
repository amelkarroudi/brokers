package com.brokers.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background scheduling is switched off in tests, which drive the sync worker explicitly. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "brokers.sync.worker-enabled", havingValue = "true")
public class SchedulingConfig {
}
