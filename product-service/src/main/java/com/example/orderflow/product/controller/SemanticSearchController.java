package com.example.orderflow.product.controller;

import com.example.orderflow.product.rag.ProductIndexService;
import com.example.orderflow.product.rag.RagAnswerResponse;
import com.example.orderflow.product.rag.RagAnswerService;
import com.example.orderflow.product.rag.SemanticSearchResult;
import com.example.orderflow.product.rag.SemanticSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Semantic Search", description = "RAG retrieval - vector similarity search over the product catalog")
public class SemanticSearchController {

    private final SemanticSearchService semanticSearchService;
    private final ProductIndexService productIndexService;

    private final ObjectProvider<RagAnswerService> ragAnswerService;

    public SemanticSearchController(SemanticSearchService semanticSearchService,
                                    ProductIndexService productIndexService,
                                    ObjectProvider<RagAnswerService> ragAnswerService) {
        this.semanticSearchService = semanticSearchService;
        this.productIndexService = productIndexService;
        this.ragAnswerService = ragAnswerService;
    }

    @GetMapping("/semantic")
    @Operation(summary = "Semantic product search",
            description = "Embeds the natural-language query and returns the most similar products (cosine similarity, pgvector). Optional maxPrice metadata filter.")
    public List<SemanticSearchResult> semanticSearch(
            @RequestParam @NotBlank String query,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int topK,
            @RequestParam(required = false) BigDecimal maxPrice) {
        return semanticSearchService.search(query, topK, maxPrice);
    }

    @GetMapping("/ask")
    @Operation(summary = "Ask the shopping assistant (full RAG)",
            description = "Retrieves the most similar products (pgvector) and lets the LLM answer based on them. Requires OPENROUTER_API_KEY - returns 503 when not configured.")
    public RagAnswerResponse ask(@RequestParam @NotBlank String question) {
        RagAnswerService service = ragAnswerService.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RAG answering is disabled - set RAG_CHAT_MODEL=openai and OPENROUTER_API_KEY to enable it");
        }
        return service.ask(question);
    }

    @PostMapping("/reindex")
    @Operation(summary = "Reindex all products",
            description = "Embeds the whole catalog into the vector store. Idempotent (deterministic ids = upsert).")
    public Map<String, Integer> reindex() {
        int indexed = productIndexService.reindexAll();
        return Map.of("indexed", indexed);
    }
}
