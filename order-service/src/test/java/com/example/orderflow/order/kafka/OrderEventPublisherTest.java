package com.example.orderflow.order.kafka;

import com.example.orderflow.order.event.OrderPlacedEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventPublisher Unit Tests")
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OrderEventPublisher publisher;

    private OrderPlacedEvent event;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "orderPlacedTopic", "order.placed");

        event = new OrderPlacedEvent(
                "order-uuid-123",
                "session-abc",
                List.of(new OrderPlacedEvent.Item("prod-1", "Laptop", 1, new BigDecimal("2500.00"))),
                new BigDecimal("2500.00"),
                "ul. Testowa 1, Warszawa",
                Instant.now()
        );
    }

    @Test
    @DisplayName("should publish OrderPlacedEvent to Kafka topic with orderId as key")
    void shouldPublishOrderPlacedEvent() {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("order.placed", 0), 0, 0, 0, 0, 0);
        SendResult<String, Object> sendResult = new SendResult<>(null, metadata);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        publisher.publishOrderPlaced(event);

        verify(kafkaTemplate).send(eq("order.placed"), eq("order-uuid-123"), eq(event));
    }

    @Test
    @DisplayName("should log error when Kafka publish fails")
    void shouldHandlePublishFailure() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka broker unavailable"));

        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        publisher.publishOrderPlaced(event);

        verify(kafkaTemplate).send(eq("order.placed"), eq("order-uuid-123"), eq(event));
    }
}
