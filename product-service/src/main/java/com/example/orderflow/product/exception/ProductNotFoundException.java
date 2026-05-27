package com.example.orderflow.product.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String id) {
        super("Product with id '%s' not found".formatted(id));
    }
}
