package com.example.orderflow.order.kafka;

import com.example.orderflow.order.event.FulfillmentCompletedEvent;
import com.example.orderflow.order.event.FulfillmentFailedEvent;
import com.example.orderflow.order.exception.InvalidOrderStatusTransitionException;
import com.example.orderflow.order.model.OrderStatus;
import com.example.orderflow.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class FulfillmentEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.fulfillment-failed:fulfillment-service.order.failed}",
            groupId = "order-service.saga-compensation.consumer"
    )
    public void handleFulfillmentFailed(String message) {
        FulfillmentFailedEvent event;
        try {
            event = objectMapper.readValue(message, FulfillmentFailedEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize fulfillment.failed message, skipping: {}", e.getMessage());
            return;
        }
        log.warn("Fulfillment failed for order={}, reason={} - executing compensating transaction",
                event.orderId(), event.reason());
        try {
            orderService.updateStatus(event.orderId(), OrderStatus.CANCELLED);
            log.info("Compensating transaction completed: order={} cancelled", event.orderId());
        } catch (Exception e) {
            log.error("Compensating transaction failed for order={}: {}", event.orderId(), e.getMessage());
        }
    }

    @KafkaListener(
            topics = "${kafka.topics.fulfillment-completed:fulfillment-service.order.completed}",
            groupId = "order-service.saga-confirmation.consumer"
    )
    public void handleFulfillmentCompleted(String message) {
        FulfillmentCompletedEvent event;
        try {
            event = objectMapper.readValue(message, FulfillmentCompletedEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize fulfillment.completed message, skipping: {}", e.getMessage());
            return;
        }
        log.info("Fulfillment completed for order={} - confirming order", event.orderId());
        try {
            orderService.updateStatus(event.orderId(), OrderStatus.CONFIRMED);
            log.info("Order {} confirmed", event.orderId());
        } catch (InvalidOrderStatusTransitionException e) {
            log.warn("Order {} already past PENDING, skipping duplicate confirmation: {}",
                    event.orderId(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to confirm order={}: {}", event.orderId(), e.getMessage());
        }
    }
}
