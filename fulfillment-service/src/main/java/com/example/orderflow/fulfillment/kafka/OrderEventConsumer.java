package com.example.orderflow.fulfillment.kafka;

import com.example.orderflow.fulfillment.event.FulfillmentFailedEvent;
import com.example.orderflow.fulfillment.event.OrderPlacedEvent;
import com.example.orderflow.fulfillment.service.FulfillmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final FulfillmentService fulfillmentService;
    private final FulfillmentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ProcessedOrderStore processedOrderStore;

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 2000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "true"
    )
    @KafkaListener(
            topics = "${kafka.topics.order-placed:order-service.order.placed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        OrderPlacedEvent event;
        try {
            event = objectMapper.readValue(message, OrderPlacedEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize order.placed message, skipping: {}", e.getMessage());
            return;
        }
        log.info("Received order.placed: orderId={}, sessionId={}", event.orderId(), event.sessionId());

        if (!processedOrderStore.claim(event.orderId())) {
            log.info("Duplicate order.placed for orderId={} - already processed, skipping", event.orderId());
            return;
        }

        try {
            fulfillmentService.process(event);
        } catch (RuntimeException e) {
            processedOrderStore.release(event.orderId());
            throw e;
        }
    }

    @DltHandler
    public void handleDlt(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage
    ) {
        try {
            OrderPlacedEvent event = objectMapper.readValue(message, OrderPlacedEvent.class);
            log.error("Order {} moved to DLT from topic={}. Reason: {}",
                    event.orderId(), topic, exceptionMessage);
            eventPublisher.publishFailed(new FulfillmentFailedEvent(
                    event.orderId(),
                    event.sessionId(),
                    "All retry attempts exhausted: " + exceptionMessage,
                    Instant.now()
            ));
        } catch (Exception e) {
            log.error("Failed to process DLT message from topic={}: {}", topic, e.getMessage());
        }
    }
}
