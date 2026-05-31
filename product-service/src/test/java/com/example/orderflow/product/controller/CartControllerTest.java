package com.example.orderflow.product.controller;

import com.example.orderflow.product.dto.CartItemRequest;
import com.example.orderflow.product.dto.CartItemResponse;
import com.example.orderflow.product.dto.CartResponse;
import com.example.orderflow.product.exception.ProductNotFoundException;
import com.example.orderflow.product.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
@DisplayName("CartController Web Layer Tests")
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    private static final String SESSION_ID = "test-session-abc123";
    private CartResponse emptyCart;
    private CartResponse cartWithItem;

    @BeforeEach
    void setUp() {
        emptyCart = new CartResponse(SESSION_ID, List.of(), 0, BigDecimal.ZERO);

        CartItemResponse item = new CartItemResponse(
                "product-1", "Test Laptop", "/products/test-laptop.jpg", new BigDecimal("1999.99"), 2, new BigDecimal("3999.98")
        );
        cartWithItem = new CartResponse(SESSION_ID, List.of(item), 1, new BigDecimal("3999.98"));
    }

    @Test
    @DisplayName("GET /api/v1/cart - should return empty cart for new session")
    void shouldReturnEmptyCart() throws Exception {
        when(cartService.getCart(SESSION_ID)).thenReturn(emptyCart);

        mockMvc.perform(get("/api/v1/cart")
                        .header("X-Session-Id", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/cart - should return cart with items")
    void shouldReturnCartWithItems() throws Exception {
        when(cartService.getCart(SESSION_ID)).thenReturn(cartWithItem);

        mockMvc.perform(get("/api/v1/cart")
                        .header("X-Session-Id", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.items[0].productId").value("product-1"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(3999.98));
    }

    @Test
    @DisplayName("POST /api/v1/cart/items - should add item and return updated cart")
    void shouldAddItemToCart() throws Exception {
        String requestBody = """
                {
                    "productId": "product-1",
                    "quantity": 2
                }
                """;
        when(cartService.addItem(eq(SESSION_ID), any(CartItemRequest.class))).thenReturn(cartWithItem);

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("X-Session-Id", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.items[0].productId").value("product-1"));
    }

    @Test
    @DisplayName("POST /api/v1/cart/items - should return 404 when product does not exist")
    void shouldReturn404WhenProductNotFound() throws Exception {
        String requestBody = """
                {
                    "productId": "non-existent",
                    "quantity": 1
                }
                """;
        when(cartService.addItem(eq(SESSION_ID), any(CartItemRequest.class)))
                .thenThrow(new ProductNotFoundException("non-existent"));

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("X-Session-Id", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/v1/cart/items - should return 400 when productId is blank")
    void shouldReturn400WhenProductIdIsBlank() throws Exception {
        String requestBody = """
                {
                    "productId": "",
                    "quantity": 1
                }
                """;

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("X-Session-Id", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("productId"));
    }

    @Test
    @DisplayName("POST /api/v1/cart/items - should return 400 when quantity is less than 1")
    void shouldReturn400WhenQuantityIsZero() throws Exception {
        String requestBody = """
                {
                    "productId": "product-1",
                    "quantity": 0
                }
                """;

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("X-Session-Id", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));
    }

    @Test
    @DisplayName("PUT /api/v1/cart/items/{productId} - should update item quantity")
    void shouldUpdateItemQuantity() throws Exception {
        when(cartService.updateItem(SESSION_ID, "product-1", 3)).thenReturn(cartWithItem);

        mockMvc.perform(put("/api/v1/cart/items/product-1")
                        .header("X-Session-Id", SESSION_ID)
                        .param("quantity", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID));
    }

    @Test
    @DisplayName("PUT /api/v1/cart/items/{productId} - should return 400 when quantity is negative")
    void shouldReturn400WhenQuantityIsNegative() throws Exception {
        when(cartService.updateItem(eq(SESSION_ID), eq("product-1"), eq(-1)))
                .thenThrow(new IllegalArgumentException("Quantity cannot be negative, got: -1"));

        mockMvc.perform(put("/api/v1/cart/items/product-1")
                        .header("X-Session-Id", SESSION_ID)
                        .param("quantity", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Quantity cannot be negative, got: -1"));
    }

    @Test
    @DisplayName("DELETE /api/v1/cart/items/{productId} - should remove item and return updated cart")
    void shouldRemoveItemFromCart() throws Exception {
        when(cartService.removeItem(SESSION_ID, "product-1")).thenReturn(emptyCart);

        mockMvc.perform(delete("/api/v1/cart/items/product-1")
                        .header("X-Session-Id", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    @DisplayName("DELETE /api/v1/cart - should clear cart and return 204")
    void shouldClearCart() throws Exception {
        doNothing().when(cartService).clearCart(SESSION_ID);

        mockMvc.perform(delete("/api/v1/cart")
                        .header("X-Session-Id", SESSION_ID))
                .andExpect(status().isNoContent());
    }
}
