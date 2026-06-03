package com.soi.backend.global.metrics;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class MetricsConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
