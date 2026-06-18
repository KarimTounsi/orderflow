package com.example.orderflow.product.rag;

import com.example.orderflow.product.model.Product;
import com.example.orderflow.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductIndexService {

    private final VectorStore vectorStore;
    private final ProductRepository productRepository;

    static String vectorId(String productId) {
        return UUID.nameUUIDFromBytes(productId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public void index(Product product) {
        vectorStore.add(List.of(toDocument(product)));
        log.info("Indexed product {} into vector store", product.getId());
    }

    public void removeFromIndex(String productId) {
        vectorStore.delete(List.of(vectorId(productId)));
        log.info("Removed product {} from vector store", productId);
    }

    public int reindexAll() {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return 0;
        }
        List<Document> documents = products.stream().map(this::toDocument).toList();
        vectorStore.add(documents);
        log.info("Reindexed {} products into vector store", documents.size());
        return documents.size();
    }

    private Document toDocument(Product product) {
        StringBuilder text = new StringBuilder();
        text.append(product.getName());
        if (product.getCategory() != null) {
            text.append(". Category: ").append(product.getCategory());
        }
        if (product.getDescription() != null) {
            text.append(". ").append(product.getDescription());
        }
        if (product.getAttributes() != null && !product.getAttributes().isEmpty()) {
            text.append(". ").append(product.getAttributes().entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(", ")));
        }
        if (product.getPrice() != null) {
            text.append(". Price: ").append(product.getPrice());
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("productId", product.getId());
        metadata.put("name", product.getName());
        if (product.getCategory() != null) {
            metadata.put("category", product.getCategory());
        }
        if (product.getPrice() != null) {
            metadata.put("price", product.getPrice().doubleValue());
        }

        return Document.builder()
                .id(vectorId(product.getId()))
                .text(text.toString())
                .metadata(metadata)
                .build();
    }
}
