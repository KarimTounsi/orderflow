package com.example.orderflow.product.rag;

import com.example.orderflow.product.dto.ProductResponse;

public record SemanticSearchResult(
        ProductResponse product,
        double score
) {}
