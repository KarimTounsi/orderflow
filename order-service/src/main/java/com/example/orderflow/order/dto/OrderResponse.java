package com.example.orderflow.order.dto;

import com.example.orderflow.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String sessionId,
        List<OrderItemResponse> items,
        OrderStatus status,
        String statusLabel,
        BigDecimal total,
        String shippingAddress,
        Instant createdAt,
        Instant updatedAt
) {}
