package com.example.orderflow.order.kafka;

import com.example.orderflow.order.model.OutboxEvent;
import com.example.orderflow.order.repository.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelay Unit Tests")
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay relay;

    private OutboxEvent newEvent() {
        return OutboxEvent.builder()
                .id("outbox-1")
                .aggregateId("order-1")
                .topic("order-service.order.placed")
                .messageKey("order-1")
                .payload("{\"orderId\":\"order-1\"}")
                .createdAt(Instant.now())
                .build();
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        relay = new OutboxRelay(outboxRepository, kafkaTemplate);
    }

    @Test
    @DisplayName("should do nothing when there are no pending events")
    void shouldDoNothingWhenEmpty() {
        when(outboxRepository.findUnpublishedBatch(anyInt())).thenReturn(List.of());

        relay.publishPending();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should publish pending event and mark it as published")
    void shouldPublishAndMark() {
        OutboxEvent event = newEvent();
        when(outboxRepository.findUnpublishedBatch(anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("order-service.order.placed"), eq("order-1"), eq("{\"orderId\":\"order-1\"}")))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.publishPending();

        // Po udanym wyslaniu published_at musi byc ustawione (dirty checking zapisze to przy commicie).
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("should leave event unpublished when Kafka send fails")
    void shouldLeaveUnpublishedOnFailure() {
        OutboxEvent event = newEvent();
        when(outboxRepository.findUnpublishedBatch(anyInt())).thenReturn(List.of(event));
        // .get() na failedFuture rzuci ExecutionException - relay loguje i przerywa, NIE oznaczajac wiersza.
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay.publishPending();

        assertThat(event.getPublishedAt()).isNull();
    }
}
