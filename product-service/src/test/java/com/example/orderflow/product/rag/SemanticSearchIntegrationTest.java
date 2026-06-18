package com.example.orderflow.product.rag;

import com.example.orderflow.product.AbstractIntegrationTest;
import com.example.orderflow.product.model.Product;
import com.example.orderflow.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Semantic Search Integration Tests (pgvector + real ONNX embeddings)")
class SemanticSearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductIndexService productIndexService;

    @Autowired
    private SemanticSearchService semanticSearchService;

    private Product runningShoes;
    private Product coffeeMug;
    private Product gardenHose;

    @BeforeEach
    void setUp() {
        // Czysty stan w OBU magazynach: Mongo (produkty) i pgvector (wektory).
        // Wektory czyscimy przez removeFromIndex (deterministyczne UUID), bo VectorStore
        // nie ma deleteAll - a zostawione wpisy psulyby kolejne testy.
        productRepository.findAll().forEach(p -> productIndexService.removeFromIndex(p.getId()));
        productRepository.deleteAll();

        runningShoes = productRepository.save(Product.builder()
                .name("Trail Running Shoes")
                .description("Lightweight breathable shoes for jogging and marathon training")
                .price(new BigDecimal("179.99"))
                .category("Sports")
                .stock(10)
                .build());

        coffeeMug = productRepository.save(Product.builder()
                .name("Ceramic Coffee Mug")
                .description("Handmade ceramic mug for hot drinks")
                .price(new BigDecimal("19.99"))
                .category("Home")
                .stock(50)
                .build());

        gardenHose = productRepository.save(Product.builder()
                .name("Garden Hose 20m")
                .description("Flexible hose for watering plants")
                .price(new BigDecimal("89.99"))
                .category("Home")
                .stock(30)
                .build());

        productIndexService.reindexAll();
    }

    @Test
    @DisplayName("semantic query about jogging ranks running shoes first - real cosine similarity")
    void semanticQueryRanksByMeaning() {
        // "footwear for jogging" NIE zawiera slow "running" ani "shoes" w formie z opisu -
        // substring match by tu polegl. Embeddingi maja zrozumiec ZNACZENIE.
        List<SemanticSearchResult> results =
                semanticSearchService.search("footwear for jogging and marathons", 3, null);

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().product().id()).isEqualTo(runningShoes.getId());
        // Score pierwszego wyniku musi byc wyraznie wyzszy niz reszty (rozdzielczosc semantyki)
        if (results.size() > 1) {
            assertThat(results.getFirst().score()).isGreaterThan(results.get(1).score());
        }
    }

    @Test
    @DisplayName("maxPrice metadata filter excludes products above the limit in SQL, not in Java")
    void maxPriceFilterWorksInVectorStore() {
        // Limit 100: buty (179.99) musza wypasc juz na poziomie zapytania do pgvector
        List<SemanticSearchResult> results =
                semanticSearchService.search("footwear for jogging and marathons", 3, new BigDecimal("100"));

        assertThat(results)
                .extracting(r -> r.product().id())
                .doesNotContain(runningShoes.getId());
    }

    @Test
    @DisplayName("update + reindex is an upsert - no duplicate vectors, fresh content wins")
    void updateIsUpsertNotDuplicate() {
        // Zmieniamy kubek w produkt do biegania i reindeksujemy TEN SAM produkt
        coffeeMug.setName("Marathon Energy Gel");
        coffeeMug.setDescription("Energy gel for long distance running and jogging");
        Product updated = productRepository.save(coffeeMug);
        productIndexService.index(updated);

        List<SemanticSearchResult> results =
                semanticSearchService.search("energy gel for marathon runners", 3, null);

        // Deterministyczny UUID -> stary wektor kubka zostal NADPISANY, nie zduplikowany:
        // produkt pojawia sie raz, z nowa trescia, wysoko w wynikach
        long occurrences = results.stream()
                .filter(r -> r.product().id().equals(coffeeMug.getId()))
                .count();
        assertThat(occurrences).isEqualTo(1);
        assertThat(results.getFirst().product().id()).isEqualTo(coffeeMug.getId());
    }

    @Test
    @DisplayName("removeFromIndex deletes the vector - product disappears from results")
    void removedProductDisappearsFromSearch() {
        productIndexService.removeFromIndex(gardenHose.getId());

        List<SemanticSearchResult> results =
                semanticSearchService.search("hose for watering the garden", 3, null);

        assertThat(results)
                .extracting(r -> r.product().id())
                .doesNotContain(gardenHose.getId());
    }
}
