package com.example.orderflow.product.rag;

import java.util.List;

public record RagAnswerResponse(
        String answer,
        List<Source> sources
) {
    public record Source(
            String productId,
            String name,
            double score
    ) {}
}
