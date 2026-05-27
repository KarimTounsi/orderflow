package com.example.orderflow.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CartItemRequest(

        @NotBlank(message = "Product ID is required")
        String productId,

        @Min(value = 1, message = "Quantity must be at least 1")
        int quantity
) {
}
