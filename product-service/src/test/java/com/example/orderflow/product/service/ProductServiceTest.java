package com.example.orderflow.product.service;

import com.example.orderflow.product.dto.ProductRequest;
import com.example.orderflow.product.dto.ProductResponse;
import com.example.orderflow.product.exception.ProductNotFoundException;
import com.example.orderflow.product.model.Product;
import com.example.orderflow.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Unit Tests")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    // Testowy produkt uzywany w wielu testach
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .id("test-product-id")
                .name("Test Laptop")
                .description("Great laptop for testing")
                .price(new BigDecimal("1999.99"))
                .category("electronics")
                .stock(10)
                .build();
    }


    @Test
    @DisplayName("getById - should return product when found")
    void shouldReturnProductWhenFound() {

        when(productRepository.findById("test-product-id"))
                .thenReturn(Optional.of(testProduct));

        // When
        ProductResponse result = productService.getById("test-product-id");

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("test-product-id");
        assertThat(result.name()).isEqualTo("Test Laptop");
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("1999.99"));

        verify(productRepository, times(1)).findById("test-product-id");
    }

    @Test
    @DisplayName("getById - should throw ProductNotFoundException when not found")
    void shouldThrowProductNotFoundExceptionWhenNotFound() {
        when(productRepository.findById("non-existent-id"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById("non-existent-id"))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("non-existent-id");
    }

    @Test
    @DisplayName("create - should save product and return response")
    void shouldSaveProductAndReturnResponse() {
        // Given
        ProductRequest request = new ProductRequest(
                "New Laptop",
                "Brand new laptop",
                new BigDecimal("2499.99"),
                "electronics",
                null,
                5,
                null
        );

        when(productRepository.save(any(Product.class)))
                .thenReturn(testProduct);

        // When
        ProductResponse result = productService.create(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Test Laptop"); // testProduct ma name "Test Laptop"

        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("delete - should delete product when found")
    void shouldDeleteProductWhenFound() {
        // Given
        when(productRepository.existsById("test-product-id")).thenReturn(true);

        doNothing().when(productRepository).deleteById("test-product-id");

        // When
        productService.delete("test-product-id");

        verify(productRepository, times(1)).deleteById("test-product-id");
    }

    @Test
    @DisplayName("delete - should throw ProductNotFoundException when product does not exist")
    void shouldThrowWhenDeletingNonExistentProduct() {
        // Given
        when(productRepository.existsById("non-existent-id")).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> productService.delete("non-existent-id"))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("getAll - no filters - should query findAll")
    void shouldGetAllWithoutFilters() {
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(testProduct)));

        Page<ProductResponse> result = productService.getAll(null, "   ", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(productRepository, times(1)).findAll(pageable);
    }

    @Test
    @DisplayName("getAll - category only - should query findByCategory")
    void shouldGetAllByCategoryOnly() {
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.findByCategory("electronics", pageable))
                .thenReturn(new PageImpl<>(List.of(testProduct)));

        Page<ProductResponse> result = productService.getAll("electronics", null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(productRepository, times(1)).findByCategory("electronics", pageable);
    }

    @Test
    @DisplayName("getAll - search only - should query findByNameContainingIgnoreCase")
    void shouldGetAllBySearchOnly() {
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.findByNameContainingIgnoreCase("lap", pageable))
                .thenReturn(new PageImpl<>(List.of(testProduct)));

        Page<ProductResponse> result = productService.getAll("", "lap", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(productRepository, times(1)).findByNameContainingIgnoreCase("lap", pageable);
    }

    @Test
    @DisplayName("getAll - category and search - should query combined finder")
    void shouldGetAllByCategoryAndSearch() {
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.findByCategoryAndNameContainingIgnoreCase("electronics", "lap", pageable))
                .thenReturn(new PageImpl<>(List.of(testProduct)));

        Page<ProductResponse> result = productService.getAll("electronics", "lap", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(productRepository, times(1))
                .findByCategoryAndNameContainingIgnoreCase("electronics", "lap", pageable);
    }

    @Test
    @DisplayName("getByCategory - should delegate to repository")
    void shouldGetByCategory() {
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.findByCategory("electronics", pageable))
                .thenReturn(new PageImpl<>(List.of(testProduct)));

        assertThat(productService.getByCategory("electronics", pageable).getContent()).hasSize(1);
    }

    @Test
    @DisplayName("search - should delegate to repository")
    void shouldSearchByName() {
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.findByNameContainingIgnoreCase("lap", pageable))
                .thenReturn(new PageImpl<>(List.of(testProduct)));

        assertThat(productService.search("lap", pageable).getContent()).hasSize(1);
    }

    @Test
    @DisplayName("update - should update existing product")
    void shouldUpdateExistingProduct() {
        ProductRequest request = new ProductRequest(
                "Updated Laptop", "desc", new BigDecimal("2999.99"), "electronics", null, 7, null);
        when(productRepository.findById("test-product-id")).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        ProductResponse result = productService.update("test-product-id", request);

        assertThat(result).isNotNull();
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("update - should throw when product does not exist")
    void shouldThrowWhenUpdatingNonExistentProduct() {
        ProductRequest request = new ProductRequest(
                "X", "y", new BigDecimal("1.00"), "books", null, 1, null);
        when(productRepository.findById("ghost-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update("ghost-id", request))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("updateStock - should update stock when value is valid")
    void shouldUpdateStockWhenValid() {
        when(productRepository.findById("test-product-id")).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        ProductResponse result = productService.updateStock("test-product-id", 50);

        assertThat(result).isNotNull();
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("updateStock - should throw IllegalArgumentException on negative stock")
    void shouldThrowOnNegativeStock() {
        assertThatThrownBy(() -> productService.updateStock("test-product-id", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");

        verify(productRepository, never()).save(any());
    }
}
