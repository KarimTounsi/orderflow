package com.example.orderflow.product.repository;

import com.example.orderflow.product.model.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@DisplayName("ProductRepository Integration Tests with Testcontainers")
class ProductRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final GenericContainer<?> redisContainer =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private ProductRepository productRepository;

    private Product laptop;
    private Product phone;
    private Product shirt;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();

        laptop = productRepository.save(Product.builder()
                .name("Gaming Laptop")
                .description("High performance laptop")
                .price(new BigDecimal("3999.99"))
                .category("electronics")
                .stock(5)
                .build());

        phone = productRepository.save(Product.builder()
                .name("Smartphone Pro")
                .description("Latest smartphone")
                .price(new BigDecimal("1299.99"))
                .category("electronics")
                .stock(20)
                .build());

        shirt = productRepository.save(Product.builder()
                .name("Blue T-Shirt")
                .description("Cotton shirt")
                .price(new BigDecimal("49.99"))
                .category("clothing")
                .stock(100)
                .build());
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("findByCategory - should return only products in given category")
    void shouldFindProductsByCategory() {
        Page<Product> result = productRepository.findByCategory("electronics", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(Product::getName)
                .containsExactlyInAnyOrder("Gaming Laptop", "Smartphone Pro");
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByCategory - should return empty page for non-existent category")
    void shouldReturnEmptyPageForNonExistentCategory() {
        Page<Product> result = productRepository.findByCategory("furniture", PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("findByNameContainingIgnoreCase - should find products with partial name match")
    void shouldFindProductsByNameContaining() {
        Page<Product> result = productRepository.findByNameContainingIgnoreCase(
                "laptop", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Gaming Laptop");
    }

    @Test
    @DisplayName("findByNameContainingIgnoreCase - should be case insensitive")
    void shouldSearchCaseInsensitively() {
        Page<Product> result = productRepository.findByNameContainingIgnoreCase(
                "SMART", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Smartphone Pro");
    }

    @Test
    @DisplayName("save - should persist product with generated id")
    void shouldPersistProductWithGeneratedId() {
        Product newProduct = Product.builder()
                .name("Wireless Keyboard")
                .price(new BigDecimal("199.99"))
                .category("electronics")
                .stock(50)
                .build();

        Product saved = productRepository.save(newProduct);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Wireless Keyboard");
        assertThat(productRepository.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("deleteById - should remove product from database")
    void shouldDeleteProduct() {
        String laptopId = laptop.getId();
        assertThat(productRepository.findById(laptopId)).isPresent();

        productRepository.deleteById(laptopId);

        assertThat(productRepository.findById(laptopId)).isEmpty();
        assertThat(productRepository.count()).isEqualTo(2);
    }
}
