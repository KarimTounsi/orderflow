package com.example.orderflow.fulfillment.kafka;

import com.example.orderflow.fulfillment.event.FulfillmentCompletedEvent;
import com.example.orderflow.fulfillment.event.FulfillmentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class FulfillmentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.fulfillment-completed:fulfillment-service.order.completed}")
    private String completedTopic;

    @Value("${kafka.topics.fulfillment-failed:fulfillment-service.order.failed}")
    private String failedTopic;

    public void publishCompleted(FulfillmentCompletedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            CompletableFuture<?> future = kafkaTemplate.send(completedTopic, event.orderId(), json);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish FulfillmentCompletedEvent for order={}: {}",
                            event.orderId(), ex.getMessage());
                } else {
                    log.info("Published FulfillmentCompletedEvent: order={}", event.orderId());
                }
            });
        } catch (Exception e) {
            log.error("Failed to serialize FulfillmentCompletedEvent for order={}: {}",
                    event.orderId(), e.getMessage());
        }
    }

    public void publishFailed(FulfillmentFailedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            CompletableFuture<?> future = kafkaTemplate.send(failedTopic, event.orderId(), json);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish FulfillmentFailedEvent for order={}: {}",
                            event.orderId(), ex.getMessage());
                } else {
                    log.warn("Published FulfillmentFailedEvent: order={}, reason={}",
                            event.orderId(), event.reason());
                }
            });
        } catch (Exception e) {
            log.error("Failed to serialize FulfillmentFailedEvent for order={}: {}",
                    event.orderId(), e.getMessage());
        }
    }
}
