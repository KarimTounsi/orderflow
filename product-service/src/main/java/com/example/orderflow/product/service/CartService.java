package com.example.orderflow.product.service;

import com.example.orderflow.product.dto.CartItemRequest;
import com.example.orderflow.product.dto.CartItemResponse;
import com.example.orderflow.product.dto.CartResponse;
import com.example.orderflow.product.exception.ProductNotFoundException;
import com.example.orderflow.product.model.Product;
import com.example.orderflow.product.repository.ProductRepository;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CartService {

    private static final String CART_PREFIX = "cart::";

    private static final Duration CART_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    private final HashOperations<String, String, String> hashOperations;

    private final ProductRepository productRepository;

    public CartService(StringRedisTemplate redisTemplate, ProductRepository productRepository) {
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
        this.productRepository = productRepository;
    }

    public CartResponse addItem(String sessionId, CartItemRequest request) {
        String cartKey = CART_PREFIX + sessionId;

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        hashOperations.increment(cartKey, request.productId(), request.quantity());

        redisTemplate.expire(cartKey, CART_TTL);

        return buildCartResponse(sessionId, cartKey);
    }

    public CartResponse getCart(String sessionId) {
        String cartKey = CART_PREFIX + sessionId;
        return buildCartResponse(sessionId, cartKey);
    }

    public CartResponse updateItem(String sessionId, String productId, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative, got: " + quantity);
        }

        String cartKey = CART_PREFIX + sessionId;

        if (quantity == 0) {
            hashOperations.delete(cartKey, productId);
        } else {
            if (!productRepository.existsById(productId)) {
                throw new ProductNotFoundException(productId);
            }
            hashOperations.put(cartKey, productId, String.valueOf(quantity));
        }

        redisTemplate.expire(cartKey, CART_TTL);
        return buildCartResponse(sessionId, cartKey);
    }

    public CartResponse removeItem(String sessionId, String productId) {
        String cartKey = CART_PREFIX + sessionId;
        hashOperations.delete(cartKey, productId);
        redisTemplate.expire(cartKey, CART_TTL);
        return buildCartResponse(sessionId, cartKey);
    }

    public void clearCart(String sessionId) {
        redisTemplate.delete(CART_PREFIX + sessionId);
    }

    private CartResponse buildCartResponse(String sessionId, String cartKey) {
        Map<String, String> cartItems = hashOperations.entries(cartKey);

        if (cartItems == null || cartItems.isEmpty()) {
            return new CartResponse(sessionId, List.of(), 0, BigDecimal.ZERO);
        }

        List<String> productIds = new ArrayList<>(cartItems.keySet());
        Map<String, Product> productsById = productRepository.findAllById(productIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));

        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<String, String> entry : cartItems.entrySet()) {
            String productId = entry.getKey();
            int quantity = Integer.parseInt(entry.getValue());

            Product product = productsById.get(productId);
            if (product == null) {
                // Produkt zostal usuniety z katalogu - czyścimy go z koszyka automatycznie.
                // Self-healing: koszyk nie trzyma ghost-productow po ich usunieciu z katalogu.
                hashOperations.delete(cartKey, productId);
                continue;
            }

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            total = total.add(lineTotal);

            items.add(new CartItemResponse(productId, product.getName(), product.getImageUrl(), product.getPrice(), quantity, lineTotal));
        }

        return new CartResponse(sessionId, items, items.size(), total);
    }
}
