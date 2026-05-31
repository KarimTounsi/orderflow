package com.example.orderflow.order.event;

import java.time.Instant;

public record FulfillmentCompletedEvent(
        String orderId,
        String sessionId,
        String emailSentTo,
        Instant completedAt
) {}
