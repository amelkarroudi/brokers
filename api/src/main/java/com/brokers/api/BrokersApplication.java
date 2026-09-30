package com.brokers.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BrokersApplication {

    public static void main(String[] args) {
        SpringApplication.run(BrokersApplication.class, args);
    }
}
