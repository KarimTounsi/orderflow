package com.example.orderflow.order.event;

import java.time.Instant;

public record FulfillmentFailedEvent(
        String orderId,
        String sessionId,
        String reason,
        Instant failedAt
) {}
