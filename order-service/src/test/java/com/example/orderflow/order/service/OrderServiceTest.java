package com.example.orderflow.order.service;

import com.example.orderflow.order.dto.OrderItemRequest;
import com.example.orderflow.order.dto.OrderRequest;
import com.example.orderflow.order.dto.OrderResponse;
import com.example.orderflow.order.exception.InvalidOrderStatusTransitionException;
import com.example.orderflow.order.exception.OrderNotFoundException;
import com.example.orderflow.order.model.Order;
import com.example.orderflow.order.model.OrderItem;
import com.example.orderflow.order.model.OrderStatus;
import com.example.orderflow.order.model.OutboxEvent;
import com.example.orderflow.order.repository.OrderRepository;
import com.example.orderflow.order.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;
    private OrderRequest testRequest;

    @BeforeEach
    void setUp() {
        OrderItem item = OrderItem.builder()
                .productId("product-1")
                .productName("Test Laptop")
                .unitPrice(new BigDecimal("1999.99"))
                .quantity(2)
                .subtotal(new BigDecimal("3999.98"))
                .build();

        testOrder = Order.builder()
                .id("order-uuid-123")
                .sessionId("session-abc")
                .items(List.of(item))
                .status(OrderStatus.PENDING)
                .total(new BigDecimal("3999.98"))
                .shippingAddress("ul. Testowa 1, Warszawa")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        testRequest = new OrderRequest(
                "session-abc",
                List.of(new OrderItemRequest("product-1", "Test Laptop", new BigDecimal("1999.99"), 2)),
                "ul. Testowa 1, Warszawa"
        );
    }

    @Test
    @DisplayName("create - should save order, write outbox event, and return response")
    void shouldCreateOrderAndWriteOutbox() {
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"orderId\":\"order-uuid-123\"}");

        OrderResponse result = orderService.create(testRequest);

        assertThat(result).isNotNull();
        assertThat(result.sessionId()).isEqualTo("session-abc");
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.total()).isEqualByComparingTo(new BigDecimal("3999.98"));
        assertThat(result.items()).hasSize(1);

        verify(orderRepository, times(1)).save(any(Order.class));
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getAggregateId()).isEqualTo("order-uuid-123");
        assertThat(captor.getValue().getMessageKey()).isEqualTo("order-uuid-123");
        assertThat(captor.getValue().getPayload()).contains("order-uuid-123");
    }

    @Test
    @DisplayName("create - should calculate total correctly from items")
    void shouldCalculateTotalCorrectly() {
        OrderRequest multiItemRequest = new OrderRequest(
                "session-abc",
                List.of(
                        new OrderItemRequest("product-1", "Laptop", new BigDecimal("1999.99"), 2),
                        new OrderItemRequest("product-2", "Mouse", new BigDecimal("49.99"), 3)
                ),
                "ul. Testowa 1"
        );

        Order capturedOrder = Order.builder()
                .id("new-id")
                .sessionId("session-abc")
                .items(List.of())
                .status(OrderStatus.PENDING)
                .total(new BigDecimal("4149.95"))
                .shippingAddress("ul. Testowa 1")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.save(any(Order.class))).thenReturn(capturedOrder);

        OrderResponse result = orderService.create(multiItemRequest);

        // 2 * 1999.99 + 3 * 49.99 = 3999.98 + 149.97 = 4149.95
        assertThat(result.total()).isEqualByComparingTo(new BigDecimal("4149.95"));
    }

    @Test
    @DisplayName("getById - should return order when found")
    void shouldReturnOrderWhenFound() {
        when(orderRepository.findById("order-uuid-123")).thenReturn(Optional.of(testOrder));

        OrderResponse result = orderService.getById("order-uuid-123");

        assertThat(result.id()).isEqualTo("order-uuid-123");
        assertThat(result.statusLabel()).isEqualTo("Oczekuje na potwierdzenie");
    }

    @Test
    @DisplayName("getById - should throw OrderNotFoundException when not found")
    void shouldThrowWhenOrderNotFound() {
        when(orderRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById("non-existent"))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("non-existent");
    }

    @Test
    @DisplayName("updateStatus - should transition PENDING to CONFIRMED")
    void shouldUpdateOrderStatus() {
        when(orderRepository.findById("order-uuid-123")).thenReturn(Optional.of(testOrder));

        OrderResponse result = orderService.updateStatus("order-uuid-123", OrderStatus.CONFIRMED);

        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("updateStatus - should throw when transition is invalid")
    void shouldThrowOnInvalidStatusTransition() {
        // DELIVERED jest finalnym statusem - nie mozna z niego przejsc do PENDING
        testOrder.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById("order-uuid-123")).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() -> orderService.updateStatus("order-uuid-123", OrderStatus.PENDING))
                .isInstanceOf(InvalidOrderStatusTransitionException.class)
                .hasMessageContaining("DELIVERED")
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("updateStatus - should throw OrderNotFoundException when order does not exist")
    void shouldThrowWhenUpdatingNonExistentOrder() {
        when(orderRepository.findById("ghost-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateStatus("ghost-id", OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
