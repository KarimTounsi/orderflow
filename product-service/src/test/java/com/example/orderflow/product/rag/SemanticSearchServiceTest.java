package com.example.orderflow.product.rag;

import com.example.orderflow.product.model.Product;
import com.example.orderflow.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticSearchService Unit Tests")
class SemanticSearchServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private SemanticSearchService semanticSearchService;

    @Captor
    private ArgumentCaptor<SearchRequest> searchRequestCaptor;

    private Document hit(String productId, double score) {
        return Document.builder()
                .id(ProductIndexService.vectorId(productId))
                .text("irrelevant for this test")
                .metadata(Map.of("productId", productId))
                .score(score)
                .build();
    }

    private Product product(String id, String name) {
        return Product.builder()
                .id(id)
                .name(name)
                .price(new BigDecimal("99.99"))
                .category("Sports")
                .stock(5)
                .build();
    }

    @Test
    @DisplayName("search() maps vector hits to fresh products from Mongo, keeping similarity order")
    void searchMapsHitsInSimilarityOrder() {
        // Vector store zwraca trafienia posortowane od najbardziej podobnego
        when(vectorStore.similaritySearch(searchRequestCaptor.capture()))
                .thenReturn(List.of(hit("p1", 0.91), hit("p2", 0.74)));
        // Mongo zwraca produkty w DOWOLNEJ kolejnosci - serwis musi zachowac kolejnosc similarity
        when(productRepository.findAllById(anyList()))
                .thenReturn(List.of(product("p2", "Yoga Mat"), product("p1", "Running Shoes")));

        List<SemanticSearchResult> results = semanticSearchService.search("something for running", 5, null);

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().product().name()).isEqualTo("Running Shoes");
        assertThat(results.getFirst().score()).isEqualTo(0.91);
        assertThat(results.get(1).product().name()).isEqualTo("Yoga Mat");

        // Zapytanie i topK przekazane do vector store
        SearchRequest request = searchRequestCaptor.getValue();
        assertThat(request.getQuery()).isEqualTo("something for running");
        assertThat(request.getTopK()).isEqualTo(5);
    }

    @Test
    @DisplayName("search() with maxPrice adds a metadata filter expression")
    void searchWithMaxPriceAddsFilter() {
        when(vectorStore.similaritySearch(searchRequestCaptor.capture())).thenReturn(List.of());

        semanticSearchService.search("running", 5, new BigDecimal("200"));

        assertThat(searchRequestCaptor.getValue().getFilterExpression()).isNotNull();
    }

    @Test
    @DisplayName("search() skips stale index entries (product deleted from Mongo)")
    void searchSkipsOrphanedHits() {
        when(vectorStore.similaritySearch(searchRequestCaptor.capture()))
                .thenReturn(List.of(hit("p1", 0.9), hit("ghost", 0.8)));
        when(productRepository.findAllById(anyList()))
                .thenReturn(List.of(product("p1", "Running Shoes")));

        List<SemanticSearchResult> results = semanticSearchService.search("running", 5, null);

        // "ghost" wisi w indeksie, ale nie ma go w Mongo -> pomijamy zamiast zwracac pusty produkt
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().product().id()).isEqualTo("p1");
    }

    @Test
    @DisplayName("search() returns empty list when vector store has no hits")
    void searchNoHits() {
        when(vectorStore.similaritySearch(searchRequestCaptor.capture())).thenReturn(List.of());

        assertThat(semanticSearchService.search("anything", 5, null)).isEmpty();
        verify(vectorStore).similaritySearch(searchRequestCaptor.getValue());
    }
}
