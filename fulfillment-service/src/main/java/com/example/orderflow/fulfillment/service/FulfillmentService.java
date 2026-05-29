package com.example.orderflow.fulfillment.service;

import com.example.orderflow.fulfillment.event.FulfillmentCompletedEvent;
import com.example.orderflow.fulfillment.event.FulfillmentFailedEvent;
import com.example.orderflow.fulfillment.event.OrderPlacedEvent;
import com.example.orderflow.fulfillment.exception.FulfillmentException;
import com.example.orderflow.fulfillment.kafka.FulfillmentEventPublisher;
import com.example.orderflow.fulfillment.model.FulfillmentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class FulfillmentService {

    private final EmailService emailService;
    private final FulfillmentEventPublisher eventPublisher;

    public void process(OrderPlacedEvent event) {
        log.info("Processing fulfillment for order={}", event.orderId());

        FulfillmentResult result = emailService.sendOrderConfirmation(event);

        switch (result) {
            case FulfillmentResult.Success s -> {
                log.info("Order {} fulfilled: email sent to {}", s.orderId(), s.emailSentTo());
                eventPublisher.publishCompleted(new FulfillmentCompletedEvent(
                        s.orderId(),
                        event.sessionId(),
                        s.emailSentTo(),
                        Instant.now()
                ));
            }
            case FulfillmentResult.Failure f -> {
                log.error("Order {} fulfillment failed: {}", f.orderId(), f.reason());
                eventPublisher.publishFailed(new FulfillmentFailedEvent(
                        f.orderId(),
                        event.sessionId(),
                        f.reason(),
                        Instant.now()
                ));
                throw new FulfillmentException("Email delivery failed: " + f.reason());
            }
        }
    }
}
