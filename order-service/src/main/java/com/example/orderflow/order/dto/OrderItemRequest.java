package com.example.orderflow.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull; // potrzebny dla BigDecimal unitPrice

import java.math.BigDecimal;

public record OrderItemRequest(

        @NotBlank(message = "Product ID is required")
        String productId,

        @NotBlank(message = "Product name is required")
        String productName,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.01", message = "Unit price must be greater than 0")
        BigDecimal unitPrice,

        @Min(value = 1, message = "Quantity must be at least 1")
        int quantity
) {}
