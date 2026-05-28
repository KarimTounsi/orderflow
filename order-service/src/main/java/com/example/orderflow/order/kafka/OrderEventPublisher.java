package com.example.orderflow.order.kafka;

import com.example.orderflow.order.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.order-placed:order.placed}")
    private String orderPlacedTopic;

    public void publishOrderPlaced(OrderPlacedEvent event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(orderPlacedTopic, event.orderId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish OrderPlacedEvent for orderId={}: {}", event.orderId(), ex.getMessage());
            } else {
                log.info("Published OrderPlacedEvent: orderId={}, topic={}, partition={}, offset={}",
                        event.orderId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
