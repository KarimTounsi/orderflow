package com.example.orderflow.product.controller;

import com.example.orderflow.product.dto.CartItemRequest;
import com.example.orderflow.product.dto.CartResponse;
import com.example.orderflow.product.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart", description = "Shopping cart management (backed by Redis)")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Get cart contents", description = "Returns all items in cart for given session")
    public CartResponse getCart(@RequestHeader("X-Session-Id") String sessionId) {
        return cartService.getCart(sessionId);
    }

    @PostMapping("/items")
    @Operation(summary = "Add item to cart")
    public CartResponse addItem(
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestBody @Valid CartItemRequest request) {
        return cartService.addItem(sessionId, request);
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Update item quantity in cart")
    public CartResponse updateItem(
            @RequestHeader("X-Session-Id") String sessionId,
            @PathVariable String productId,
            @RequestParam int quantity) {
        return cartService.updateItem(sessionId, productId, quantity);
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove item from cart")
    public CartResponse removeItem(
            @RequestHeader("X-Session-Id") String sessionId,
            @PathVariable String productId) {
        return cartService.removeItem(sessionId, productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear entire cart")
    public void clearCart(@RequestHeader("X-Session-Id") String sessionId) {
        cartService.clearCart(sessionId);
    }
}
