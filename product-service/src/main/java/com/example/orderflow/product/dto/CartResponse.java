package com.example.orderflow.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        String sessionId,
        List<CartItemResponse> items,
        int itemCount,
        BigDecimal total
) {
}
