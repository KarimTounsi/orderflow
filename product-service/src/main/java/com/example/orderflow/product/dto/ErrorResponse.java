package com.example.orderflow.product.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,

        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }
}
