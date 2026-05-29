package com.example.orderflow.order.integration;

import com.example.orderflow.order.model.Order;
import com.example.orderflow.order.model.OrderItem;
import com.example.orderflow.order.model.OrderStatus;
import com.example.orderflow.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"order-service.order.placed"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DisplayName("Order Integration Tests with Testcontainers")
class OrderIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderRepository orderRepository;

    private Order laptopOrder;

    @BeforeEach
    void setUp() {
        OrderItem laptopItem = OrderItem.builder()
                .productId("product-laptop-1")
                .productName("Test Laptop Pro")
                .unitPrice(new BigDecimal("2499.99"))
                .quantity(1)
                .subtotal(new BigDecimal("2499.99"))
                .build();

        laptopOrder = Order.builder()
                .sessionId("session-test-123")
                .items(new ArrayList<>(List.of(laptopItem)))
                .status(OrderStatus.PENDING)
                .total(new BigDecimal("2499.99"))
                .shippingAddress("ul. Integracyjna 1, Krakow")
                .build();
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("should save and retrieve order by id")
    void shouldSaveAndRetrieveOrder() {
        Order saved = orderRepository.save(laptopOrder);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Order found = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getSessionId()).isEqualTo("session-test-123");
        assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(found.getTotal()).isEqualByComparingTo(new BigDecimal("2499.99"));
        assertThat(found.getItems()).hasSize(1);
        assertThat(found.getItems().getFirst().getProductName()).isEqualTo("Test Laptop Pro");
    }

    @Test
    @DisplayName("should find orders by sessionId with pagination")
    void shouldFindOrdersBySessionId() {
        Order order2 = Order.builder()
                .sessionId("session-test-123")
                .items(List.of())
                .status(OrderStatus.CONFIRMED)
                .total(new BigDecimal("99.99"))
                .shippingAddress("ul. Inna 2, Gdansk")
                .build();

        Order orderOtherSession = Order.builder()
                .sessionId("session-other-456")
                .items(List.of())
                .status(OrderStatus.PENDING)
                .total(new BigDecimal("49.99"))
                .shippingAddress("ul. Testowa 3")
                .build();

        orderRepository.save(laptopOrder);
        orderRepository.save(order2);
        orderRepository.save(orderOtherSession);

        Page<Order> result = orderRepository.findAllBySessionId(
                "session-test-123", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(Order::getSessionId)
                .containsOnly("session-test-123");
    }

    @Test
    @DisplayName("should find orders by sessionId and status")
    void shouldFindOrdersBySessionIdAndStatus() {
        Order confirmedOrder = Order.builder()
                .sessionId("session-test-123")
                .items(List.of())
                .status(OrderStatus.CONFIRMED)
                .total(new BigDecimal("150.00"))
                .shippingAddress("ul. Testowa 1")
                .build();

        orderRepository.save(laptopOrder);
        orderRepository.save(confirmedOrder);

        var pending = orderRepository.findAllBySessionIdAndStatus(
                "session-test-123", OrderStatus.PENDING);
        var confirmed = orderRepository.findAllBySessionIdAndStatus(
                "session-test-123", OrderStatus.CONFIRMED);

        assertThat(pending).hasSize(1);
        assertThat(confirmed).hasSize(1);
        assertThat(pending.getFirst().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("should persist order items correctly")
    void shouldPersistOrderItemsCorrectly() {
        OrderItem mouseItem = OrderItem.builder()
                .productId("product-mouse-2")
                .productName("Gaming Mouse")
                .unitPrice(new BigDecimal("149.99"))
                .quantity(3)
                .subtotal(new BigDecimal("449.97"))
                .build();

        laptopOrder.getItems().add(mouseItem);
        Order saved = orderRepository.save(laptopOrder);

        Order found = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getItems()).hasSize(2);
        assertThat(found.getItems())
                .extracting(OrderItem::getProductName)
                .containsExactlyInAnyOrder("Test Laptop Pro", "Gaming Mouse");
    }

    @Test
    @DisplayName("should update order status")
    void shouldUpdateOrderStatus() {
        Order saved = orderRepository.save(laptopOrder);
        saved.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(saved);

        Order updated = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
