package com.example.orderflow.product.controller;

import com.example.orderflow.product.dto.ProductRequest;
import com.example.orderflow.product.dto.ProductResponse;
import com.example.orderflow.product.exception.ProductNotFoundException;
import com.example.orderflow.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@DisplayName("ProductController Web Layer Tests")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private ProductResponse testResponse;

    @BeforeEach
    void setUp() {
        testResponse = new ProductResponse(
                "test-id",
                "Test Laptop",
                "Great laptop",
                new BigDecimal("1999.99"),
                "electronics",
                null,
                10,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("GET /api/v1/products - should return paginated products")
    void shouldReturnPaginatedProducts() throws Exception {
        var page = new PageImpl<>(List.of(testResponse), PageRequest.of(0, 20), 1);
        when(productService.getAll(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("test-id"))
                .andExpect(jsonPath("$.content[0].name").value("Test Laptop"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} - should return product when found")
    void shouldReturnProductById() throws Exception {
        // Given
        when(productService.getById("test-id")).thenReturn(testResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/products/test-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-id"))
                .andExpect(jsonPath("$.name").value("Test Laptop"))
                .andExpect(jsonPath("$.price").value(1999.99));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} - should return 404 when product not found")
    void shouldReturn404WhenProductNotFound() throws Exception {
        when(productService.getById("non-existent"))
                .thenThrow(new ProductNotFoundException("non-existent"));

        // When & Then
        mockMvc.perform(get("/api/v1/products/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product with id 'non-existent' not found"));
    }

    @Test
    @DisplayName("POST /api/v1/products - should create product and return 201")
    void shouldCreateProduct() throws Exception {
        String requestBody = """
                {
                    "name": "Test Laptop",
                    "description": "Great laptop",
                    "price": 1999.99,
                    "category": "electronics",
                    "stock": 10
                }
                """;

        when(productService.create(any(ProductRequest.class))).thenReturn(testResponse);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("test-id"))
                .andExpect(jsonPath("$.name").value("Test Laptop"));
    }

    @Test
    @DisplayName("POST /api/v1/products - should return 400 when name is blank")
    void shouldReturn400WhenNameIsBlank() throws Exception {
        String invalidRequest = """
                {
                    "name": "",
                    "price": 1999.99,
                    "category": "electronics",
                    "stock": 10
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    @DisplayName("GET /api/v1/products/category/{category} - should return products by category")
    void shouldReturnProductsByCategory() throws Exception {
        // Given
        var page = new PageImpl<>(List.of(testResponse), PageRequest.of(0, 20), 1);
        when(productService.getByCategory(eq("electronics"), any())).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/products/category/electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].category").value("electronics"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/products/search?name=laptop - should return matching products")
    void shouldReturnProductsBySearch() throws Exception {
        // Given
        var page = new PageImpl<>(List.of(testResponse), PageRequest.of(0, 20), 1);
        when(productService.search(eq("laptop"), any())).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/products/search").param("name", "laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Test Laptop"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("DELETE /api/v1/products/{id} - should return 204")
    void shouldDeleteProduct() throws Exception {

        mockMvc.perform(delete("/api/v1/products/test-id"))
                .andExpect(status().isNoContent());
    }
}
