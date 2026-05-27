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

import java.math.BigDecimal;
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
}
