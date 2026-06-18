package com.example.orderflow.product.rag;

import com.example.orderflow.product.dto.ProductResponse;
import com.example.orderflow.product.model.Product;
import com.example.orderflow.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchService {

    private final VectorStore vectorStore;
    private final ProductRepository productRepository;

    public List<SemanticSearchResult>
    search(String query, int topK, BigDecimal maxPrice) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(query)
                .topK(topK);

        if (maxPrice != null) {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            requestBuilder.filterExpression(b.lte("price", maxPrice.doubleValue()).build());
        }

        List<Document> documents = vectorStore.similaritySearch(requestBuilder.build());
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        // Jedno zapytanie do Mongo po wszystkie trafione produkty (zamiast N x findById)
        List<String> productIds = documents.stream()
                .map(doc -> (String) doc.getMetadata().get("productId"))
                .filter(Objects::nonNull)
                .toList();
        Map<String, Product> productsById = new HashMap<>();
        productRepository.findAllById(productIds)
                .forEach(product -> productsById.put(product.getId(), product));

        List<SemanticSearchResult> results = new ArrayList<>();
        for (Document doc : documents) {
            String productId = (String) doc.getMetadata().get("productId");
            Product product = productsById.get(productId);
            if (product == null) {
                log.warn("Vector hit for product {} not found in MongoDB - stale index entry", productId);
                continue;
            }

            double score = doc.getScore() != null ? doc.getScore() : 0.0;
            results.add(new SemanticSearchResult(ProductResponse.fromProduct(product), score));
        }
        return results;
    }
}
