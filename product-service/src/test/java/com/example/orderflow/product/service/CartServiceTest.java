package com.example.orderflow.product.service;

import com.example.orderflow.product.dto.CartItemRequest;
import com.example.orderflow.product.dto.CartResponse;
import com.example.orderflow.product.exception.ProductNotFoundException;
import com.example.orderflow.product.model.Product;
import com.example.orderflow.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService Unit Tests")
class CartServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ProductRepository productRepository;

    private CartService cartService;

    private static final String SESSION_ID = "session-test-123";
    private static final String CART_KEY = "cart::session-test-123";
    private static final String PRODUCT_ID = "product-laptop-001";

    private Product laptop;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        cartService = new CartService(redisTemplate, productRepository);

        laptop = Product.builder()
                .id(PRODUCT_ID)
                .name("Laptop Pro")
                .price(new BigDecimal("2500.00"))
                .category("electronics")
                .stock(10)
                .build();
    }

    // --- getCart ---

    @Test
    @DisplayName("should return empty cart when Redis has no items for session")
    void shouldReturnEmptyCartWhenNoItemsInRedis() {
        doReturn(Map.of()).when(hashOperations).entries(CART_KEY);

        CartResponse result = cartService.getCart(SESSION_ID);

        assertThat(result.sessionId()).isEqualTo(SESSION_ID);
        assertThat(result.items()).isEmpty();
        assertThat(result.itemCount()).isZero();
        assertThat(result.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("should return cart with items and correctly calculated totals")
    void shouldReturnCartWithCalculatedTotals() {
        doReturn(Map.of(PRODUCT_ID, "2")).when(hashOperations).entries(CART_KEY);
        when(productRepository.findAllById(anyIterable())).thenReturn(List.of(laptop));

        CartResponse result = cartService.getCart(SESSION_ID);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().productName()).isEqualTo("Laptop Pro");
        assertThat(result.items().getFirst().quantity()).isEqualTo(2);
        assertThat(result.items().getFirst().lineTotal())
                .isEqualByComparingTo(new BigDecimal("5000.00")); // 2500.00 * 2
        assertThat(result.total()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(result.itemCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("should auto-remove ghost product when it no longer exists in catalog")
    void shouldAutoRemoveGhostProductDeletedFromCatalog() {
        String ghostId = "deleted-product-999";
        doReturn(Map.of(ghostId, "1")).when(hashOperations).entries(CART_KEY);
        when(productRepository.findAllById(anyIterable())).thenReturn(List.of());

        CartResponse result = cartService.getCart(SESSION_ID);

        verify(hashOperations).delete(CART_KEY, ghostId);
        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- addItem ---

    @Test
    @DisplayName("should add new product to cart with correct quantity")
    void shouldAddNewProductToCart() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(laptop));
        doReturn(null).when(hashOperations).get(CART_KEY, PRODUCT_ID);

        cartService.addItem(SESSION_ID, new CartItemRequest(PRODUCT_ID, 2));

        verify(hashOperations).put(CART_KEY, PRODUCT_ID, "2");
        verify(redisTemplate).expire(eq(CART_KEY), any(Duration.class));
    }

    @Test
    @DisplayName("should increment quantity when product already exists in cart")
    void shouldIncrementQuantityForExistingCartItem() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(laptop));
        doReturn("3").when(hashOperations).get(CART_KEY, PRODUCT_ID);

        cartService.addItem(SESSION_ID, new CartItemRequest(PRODUCT_ID, 2));

        verify(hashOperations).put(CART_KEY, PRODUCT_ID, "5"); // 3 + 2 = 5
    }

    @Test
    @DisplayName("should throw ProductNotFoundException when adding product not in catalog")
    void shouldThrowWhenAddingNonExistentProduct() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(SESSION_ID, new CartItemRequest(PRODUCT_ID, 1)))
                .isInstanceOf(ProductNotFoundException.class);

        verify(hashOperations, never()).put(any(), any(), any());
        verify(redisTemplate, never()).expire(any(), any());
    }

    // --- updateItem ---

    @Test
    @DisplayName("should update item quantity when product exists in catalog")
    void shouldUpdateItemQuantity() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        doReturn(Map.of(PRODUCT_ID, "5")).when(hashOperations).entries(CART_KEY);
        when(productRepository.findAllById(anyIterable())).thenReturn(List.of(laptop));

        CartResponse result = cartService.updateItem(SESSION_ID, PRODUCT_ID, 5);

        verify(hashOperations).put(CART_KEY, PRODUCT_ID, "5");
        verify(redisTemplate).expire(eq(CART_KEY), any(Duration.class));
        assertThat(result.items()).hasSize(1);
    }

    @Test
    @DisplayName("should remove product from cart when quantity is set to zero")
    void shouldRemoveItemWhenQuantityIsZero() {
        doReturn(Map.of()).when(hashOperations).entries(CART_KEY);

        CartResponse result = cartService.updateItem(SESSION_ID, PRODUCT_ID, 0);

        verify(hashOperations).delete(CART_KEY, PRODUCT_ID);
        verify(hashOperations, never()).put(any(), any(), any());
        assertThat(result.items()).isEmpty();
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when quantity is negative")
    void shouldThrowWhenQuantityIsNegative() {
        assertThatThrownBy(() -> cartService.updateItem(SESSION_ID, PRODUCT_ID, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");

        verify(hashOperations, never()).put(any(), any(), any());
        verify(hashOperations, never()).delete(any(), any());
    }

    @Test
    @DisplayName("should throw ProductNotFoundException when updating quantity for non-existent product")
    void shouldThrowWhenUpdatingNonExistentProduct() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(false);

        assertThatThrownBy(() -> cartService.updateItem(SESSION_ID, PRODUCT_ID, 3))
                .isInstanceOf(ProductNotFoundException.class);

        verify(hashOperations, never()).put(any(), any(), any());
    }

    // --- removeItem ---

    @Test
    @DisplayName("should remove specific product from cart and refresh TTL")
    void shouldRemoveItemFromCart() {
        doReturn(Map.of()).when(hashOperations).entries(CART_KEY);

        cartService.removeItem(SESSION_ID, PRODUCT_ID);

        verify(hashOperations).delete(CART_KEY, PRODUCT_ID);
        verify(redisTemplate).expire(eq(CART_KEY), any(Duration.class));
    }

    // --- clearCart ---

    @Test
    @DisplayName("should delete entire cart key from Redis")
    void shouldClearEntireCart() {
        cartService.clearCart(SESSION_ID);

        verify(redisTemplate).delete(CART_KEY);
    }
}
