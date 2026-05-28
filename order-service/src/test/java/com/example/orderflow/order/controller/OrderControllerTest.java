package com.example.orderflow.order.controller;

import com.example.orderflow.order.dto.OrderItemResponse;
import com.example.orderflow.order.dto.OrderResponse;
import com.example.orderflow.order.exception.OrderNotFoundException;
import com.example.orderflow.order.model.OrderStatus;
import com.example.orderflow.order.service.OrderService;
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

@WebMvcTest(OrderController.class)
@DisplayName("OrderController Web Layer Tests")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private OrderResponse testResponse;

    @BeforeEach
    void setUp() {
        OrderItemResponse item = new OrderItemResponse(
                "product-1", "Test Laptop", new BigDecimal("1999.99"), 2, new BigDecimal("3999.98")
        );
        testResponse = new OrderResponse(
                "order-uuid-123",
                "session-abc",
                List.of(item),
                OrderStatus.PENDING,
                "Oczekuje na potwierdzenie",
                new BigDecimal("3999.98"),
                "ul. Testowa 1, Warszawa",
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("POST /api/v1/orders - should create order and return 201")
    void shouldCreateOrder() throws Exception {
        String requestBody = """
                {
                    "sessionId": "session-abc",
                    "items": [
                        {
                            "productId": "product-1",
                            "productName": "Test Laptop",
                            "unitPrice": 1999.99,
                            "quantity": 2
                        }
                    ],
                    "shippingAddress": "ul. Testowa 1, Warszawa"
                }
                """;

        when(orderService.create(any())).thenReturn(testResponse);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value("order-uuid-123"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.statusLabel").value("Oczekuje na potwierdzenie"))
                .andExpect(jsonPath("$.total").value(3999.98))
                .andExpect(jsonPath("$.items[0].productId").value("product-1"));
    }

    @Test
    @DisplayName("POST /api/v1/orders - should return 400 when sessionId is blank")
    void shouldReturn400WhenSessionIdIsBlank() throws Exception {
        String invalidRequest = """
                {
                    "sessionId": "",
                    "items": [
                        {
                            "productId": "product-1",
                            "productName": "Laptop",
                            "unitPrice": 1999.99,
                            "quantity": 1
                        }
                    ],
                    "shippingAddress": "ul. Testowa 1"
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sessionId"));
    }

    @Test
    @DisplayName("POST /api/v1/orders - should return 400 when items list is empty")
    void shouldReturn400WhenItemsIsEmpty() throws Exception {
        String invalidRequest = """
                {
                    "sessionId": "session-abc",
                    "items": [],
                    "shippingAddress": "ul. Testowa 1"
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items"));
    }

    @Test
    @DisplayName("POST /api/v1/orders - should return 400 when item quantity is zero")
    void shouldReturn400WhenQuantityIsZero() throws Exception {
        String invalidRequest = """
                {
                    "sessionId": "session-abc",
                    "items": [
                        {
                            "productId": "product-1",
                            "productName": "Laptop",
                            "unitPrice": 1999.99,
                            "quantity": 0
                        }
                    ],
                    "shippingAddress": "ul. Testowa 1"
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items[0].quantity"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} - should return order when found")
    void shouldReturnOrderById() throws Exception {
        when(orderService.getById("order-uuid-123")).thenReturn(testResponse);

        mockMvc.perform(get("/api/v1/orders/order-uuid-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-uuid-123"))
                .andExpect(jsonPath("$.sessionId").value("session-abc"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} - should return 404 when order not found")
    void shouldReturn404WhenOrderNotFound() throws Exception {
        when(orderService.getById("non-existent"))
                .thenThrow(new OrderNotFoundException("non-existent"));

        mockMvc.perform(get("/api/v1/orders/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Order with id 'non-existent' not found"));
    }

    @Test
    @DisplayName("GET /api/v1/orders?sessionId=abc - should return paginated orders")
    void shouldReturnOrdersBySessionId() throws Exception {
        var page = new PageImpl<>(List.of(testResponse), PageRequest.of(0, 20), 1);
        when(orderService.getBySessionId(eq("session-abc"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders").param("sessionId", "session-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("order-uuid-123"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{id}/status - should update order status")
    void shouldUpdateOrderStatus() throws Exception {
        OrderResponse confirmed = new OrderResponse(
                "order-uuid-123", "session-abc", List.of(),
                OrderStatus.CONFIRMED, "Potwierdzone",
                new BigDecimal("3999.98"), "ul. Testowa 1", Instant.now(), Instant.now()
        );
        when(orderService.updateStatus("order-uuid-123", OrderStatus.CONFIRMED)).thenReturn(confirmed);

        mockMvc.perform(patch("/api/v1/orders/order-uuid-123/status")
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.statusLabel").value("Potwierdzone"));
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{id}/status - should return 409 on invalid transition")
    void shouldReturn409OnInvalidStatusTransition() throws Exception {
        when(orderService.updateStatus(eq("order-uuid-123"), eq(OrderStatus.PENDING)))
                .thenThrow(new com.example.orderflow.order.exception.InvalidOrderStatusTransitionException(
                        OrderStatus.DELIVERED, OrderStatus.PENDING));

        mockMvc.perform(patch("/api/v1/orders/order-uuid-123/status")
                        .param("status", "PENDING"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
