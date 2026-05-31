package com.example.orderflow.order.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxEvent Unit Tests")
class OutboxEventTest {

    @Test
    @DisplayName("prePersist - assigns id and createdAt when missing")
    void prePersistAssignsDefaults() {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateId("order-1")
                .topic("order-service.order.placed")
                .messageKey("order-1")
                .payload("{}")
                .build();

        event.prePersist();

        assertThat(event.getId()).isNotNull();
        assertThat(event.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("prePersist - keeps existing id and createdAt")
    void prePersistKeepsExisting() {
        Instant created = Instant.parse("2025-01-01T00:00:00Z");
        OutboxEvent event = OutboxEvent.builder()
                .id("fixed-id")
                .aggregateId("order-1")
                .topic("order-service.order.placed")
                .messageKey("order-1")
                .payload("{}")
                .createdAt(created)
                .build();

        event.prePersist();

        assertThat(event.getId()).isEqualTo("fixed-id");
        assertThat(event.getCreatedAt()).isEqualTo(created);
    }
}
