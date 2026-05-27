package com.example.orderflow.product.dto;

import com.example.orderflow.product.model.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;


public record ProductResponse(
        String id,
        String name,
        String description,
        BigDecimal price,
        String category,
        String imageUrl,
        int stock,
        Map<String, String> attributes,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProductResponse fromProduct(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.getImageUrl(),
                product.getStock(),
                product.getAttributes(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
