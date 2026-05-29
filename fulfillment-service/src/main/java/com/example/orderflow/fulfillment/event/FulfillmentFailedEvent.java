package com.example.orderflow.fulfillment.event;

import java.time.Instant;

public record FulfillmentFailedEvent(
        String orderId,
        String sessionId,
        String reason,
        Instant failedAt
) {}
