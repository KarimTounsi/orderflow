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
import org.springframework.ai.vectorstore.VectorStore;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductIndexService Unit Tests")
class ProductIndexServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductIndexService productIndexService;

    @Captor
    private ArgumentCaptor<List<Document>> documentsCaptor;

    private Product runningShoes() {
        return Product.builder()
                .id("prod-1")
                .name("Trail Running Shoes")
                .description("Lightweight shoes for jogging and trail running")
                .price(new BigDecimal("179.99"))
                .category("Sports")
                .stock(10)
                .attributes(Map.of("color", "blue"))
                .build();
    }

    @Test
    @DisplayName("index() builds a Document with semantic text and filterable metadata")
    void indexBuildsDocument() {
        productIndexService.index(runningShoes());

        verify(vectorStore).add(documentsCaptor.capture());
        Document doc = documentsCaptor.getValue().getFirst();

        // Tekst dokumentu: semantyka (nazwa, kategoria, opis, atrybuty) + fakty dla LLM (cena) -
        // w pelnym RAG ten tekst trafia do prompta, wiec LLM musi widziec cene
        assertThat(doc.getText())
                .contains("Trail Running Shoes")
                .contains("Sports")
                .contains("jogging")
                .contains("color: blue")
                .contains("Price: 179.99");

        // Metadata: identyfikacja + filtry (cena jako double, nie BigDecimal - JSON w Postgresie)
        assertThat(doc.getMetadata())
                .containsEntry("productId", "prod-1")
                .containsEntry("category", "Sports")
                .containsEntry("price", 179.99);
    }

    @Test
    @DisplayName("vectorId() is deterministic - same product always maps to the same UUID")
    void vectorIdIsDeterministic() {
        assertThat(ProductIndexService.vectorId("prod-1"))
                .isEqualTo(ProductIndexService.vectorId("prod-1"))
                .isNotEqualTo(ProductIndexService.vectorId("prod-2"));
    }

    @Test
    @DisplayName("removeFromIndex() deletes the deterministic vector id")
    void removeDeletesVectorId() {
        productIndexService.removeFromIndex("prod-1");

        verify(vectorStore).delete(List.of(ProductIndexService.vectorId("prod-1")));
    }

    @Test
    @DisplayName("reindexAll() embeds the whole catalog and returns the count")
    void reindexAllReturnsCount() {
        when(productRepository.findAll()).thenReturn(List.of(runningShoes()));

        int indexed = productIndexService.reindexAll();

        assertThat(indexed).isEqualTo(1);
        verify(vectorStore).add(documentsCaptor.capture());
        assertThat(documentsCaptor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("reindexAll() on empty catalog does not call the vector store")
    void reindexAllEmptyCatalog() {
        when(productRepository.findAll()).thenReturn(List.of());

        assertThat(productIndexService.reindexAll()).isZero();
    }
}
