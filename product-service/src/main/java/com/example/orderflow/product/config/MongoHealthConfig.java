package com.example.orderflow.product.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoHealthConfig {

    @Bean
    HealthIndicator mongoHealthIndicator(MongoTemplate mongoTemplate) {
        return () -> {
            try {
                mongoTemplate.executeCommand("{ ping: 1 }");
                return Health.up().withDetail("command", "ping").build();
            } catch (RuntimeException ex) {
                return Health.down(ex).build();
            }
        };
    }
}
