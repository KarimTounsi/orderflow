package com.example.orderflow.order.kafka;

import com.example.orderflow.order.event.FulfillmentFailedEvent;
import com.example.orderflow.order.exception.OrderNotFoundException;
import com.example.orderflow.order.model.OrderStatus;
import com.example.orderflow.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FulfillmentEventConsumer Unit Tests")
class FulfillmentEventConsumerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private ObjectMapper objectMapper;

    private FulfillmentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new FulfillmentEventConsumer(orderService, objectMapper);
    }

    @Test
    @DisplayName("should cancel order when fulfillment fails (compensating transaction)")
    void shouldCancelOrderOnFulfillmentFailed() throws Exception {
        FulfillmentFailedEvent event = new FulfillmentFailedEvent(
                "order-uuid-123", "session-abc", "SMTP failed", Instant.now());
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(event);

        consumer.handleFulfillmentFailed("{\"orderId\":\"order-uuid-123\"}");

        verify(orderService).updateStatus("order-uuid-123", OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("should not propagate exception when order not found during compensation")
    void shouldNotPropagateExceptionWhenOrderNotFound() throws Exception {
        FulfillmentFailedEvent event = new FulfillmentFailedEvent(
                "non-existent-order", "session-abc", "SMTP failed", Instant.now());
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(event);
        doThrow(new OrderNotFoundException("non-existent-order"))
                .when(orderService).updateStatus("non-existent-order", OrderStatus.CANCELLED);

        consumer.handleFulfillmentFailed("{\"orderId\":\"non-existent-order\"}");
    }
}
