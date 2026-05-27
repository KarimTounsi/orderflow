package com.example.orderflow.product.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        String productId,
        String productName,
        BigDecimal price,
        int quantity,
        BigDecimal lineTotal
) {
}
