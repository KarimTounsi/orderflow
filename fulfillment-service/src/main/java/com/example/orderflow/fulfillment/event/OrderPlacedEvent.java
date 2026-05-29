package com.example.orderflow.fulfillment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderPlacedEvent(
        String orderId,
        String sessionId,
        List<Item> items,
        BigDecimal total,
        String shippingAddress,
        Instant occurredAt
) {
    public record Item(
            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
