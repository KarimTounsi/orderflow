package com.example.orderflow.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrderRequest(

        @NotBlank(message = "Session ID is required")
        String sessionId,

        @NotEmpty(message = "Order must contain at least one item")
        @Valid
        List<OrderItemRequest> items,

        @NotBlank(message = "Shipping address is required")
        String shippingAddress
) {}
