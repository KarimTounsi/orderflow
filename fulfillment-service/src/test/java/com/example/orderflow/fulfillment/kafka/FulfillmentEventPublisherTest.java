package com.example.orderflow.fulfillment.kafka;

import com.example.orderflow.fulfillment.event.FulfillmentCompletedEvent;
import com.example.orderflow.fulfillment.event.FulfillmentFailedEvent;
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

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FulfillmentEventPublisher Unit Tests")
class FulfillmentEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private FulfillmentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new FulfillmentEventPublisher(kafkaTemplate, objectMapper);
        ReflectionTestUtils.setField(publisher, "completedTopic", "fulfillment-service.order.completed");
        ReflectionTestUtils.setField(publisher, "failedTopic", "fulfillment-service.order.failed");
    }

    @Test
    @DisplayName("should publish FulfillmentCompletedEvent to correct topic")
    void shouldPublishCompletedEvent() throws Exception {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("fulfillment-service.order.completed", 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(null, metadata);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"orderId\":\"order-123\"}");
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        FulfillmentCompletedEvent event = new FulfillmentCompletedEvent(
                "order-123", "session-abc", "test@test.com", Instant.now());

        publisher.publishCompleted(event);

        verify(kafkaTemplate).send(eq("fulfillment-service.order.completed"), eq("order-123"), any(String.class));
    }

    @Test
    @DisplayName("should publish FulfillmentFailedEvent to correct topic")
    void shouldPublishFailedEvent() throws Exception {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("fulfillment-service.order.failed", 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(null, metadata);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"orderId\":\"order-123\"}");
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        FulfillmentFailedEvent event = new FulfillmentFailedEvent(
                "order-123", "session-abc", "SMTP failed", Instant.now());

        publisher.publishFailed(event);

        verify(kafkaTemplate).send(eq("fulfillment-service.order.failed"), eq("order-123"), any(String.class));
    }

    @Test
    @DisplayName("should log error when completed publish fails")
    void shouldHandleCompletedPublishFailure() throws Exception {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Broker unavailable"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        FulfillmentCompletedEvent event = new FulfillmentCompletedEvent(
                "order-123", "session-abc", "test@test.com", Instant.now());

        publisher.publishCompleted(event);

        verify(kafkaTemplate).send(any(), any(), any());
    }

    @Test
    @DisplayName("should log error when failed publish fails")
    void shouldHandleFailedPublishFailure() throws Exception {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Broker unavailable"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        FulfillmentFailedEvent event = new FulfillmentFailedEvent(
                "order-123", "session-abc", "SMTP failed", Instant.now());

        publisher.publishFailed(event);

        verify(kafkaTemplate).send(any(), any(), any());
    }
}
