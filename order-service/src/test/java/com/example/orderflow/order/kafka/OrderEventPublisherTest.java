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
import tools.jackson.databind.ObjectMapper;

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
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private OrderEventPublisher publisher;

    private OrderPlacedEvent event;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(kafkaTemplate, objectMapper);
        ReflectionTestUtils.setField(publisher, "orderPlacedTopic", "order-service.order.placed");

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
    void shouldPublishOrderPlacedEvent() throws Exception {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("order-service.order.placed", 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(null, metadata);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"orderId\":\"order-uuid-123\"}");
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        publisher.publishOrderPlaced(event);

        verify(kafkaTemplate).send(eq("order-service.order.placed"), eq("order-uuid-123"), any(String.class));
    }

    @Test
    @DisplayName("should log error when Kafka publish fails")
    void shouldHandlePublishFailure() throws Exception {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka broker unavailable"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"orderId\":\"order-uuid-123\"}");
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        publisher.publishOrderPlaced(event);

        verify(kafkaTemplate).send(eq("order-service.order.placed"), eq("order-uuid-123"), any(String.class));
    }
}
