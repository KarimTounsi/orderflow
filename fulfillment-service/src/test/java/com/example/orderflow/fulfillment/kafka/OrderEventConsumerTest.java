package com.example.orderflow.fulfillment.kafka;

import com.example.orderflow.fulfillment.event.FulfillmentFailedEvent;
import com.example.orderflow.fulfillment.event.OrderPlacedEvent;
import com.example.orderflow.fulfillment.exception.FulfillmentException;
import com.example.orderflow.fulfillment.service.FulfillmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventConsumer Unit Tests")
class OrderEventConsumerTest {

    @Mock
    private FulfillmentService fulfillmentService;

    @Mock
    private FulfillmentEventPublisher eventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ProcessedOrderStore processedOrderStore;

    private OrderEventConsumer consumer;

    private OrderPlacedEvent event;

    @BeforeEach
    void setUp() {
        consumer = new OrderEventConsumer(fulfillmentService, eventPublisher, objectMapper, processedOrderStore);

        event = new OrderPlacedEvent(
                "order-uuid-123",
                "session-abc",
                List.of(new OrderPlacedEvent.Item("prod-1", "Laptop", 1, new BigDecimal("2500.00"))),
                new BigDecimal("2500.00"),
                "ul. Testowa 1",
                Instant.now()
        );
    }

    @Test
    @DisplayName("should delegate to FulfillmentService on consume (first time)")
    void shouldDelegateToFulfillmentService() throws Exception {
        when(objectMapper.readValue(any(String.class), eq(OrderPlacedEvent.class))).thenReturn(event);
        when(processedOrderStore.claim("order-uuid-123")).thenReturn(true);

        consumer.consume("{\"orderId\":\"order-uuid-123\"}");

        verify(fulfillmentService).process(event);
        // sukces -> rezerwacja NIE jest zwalniana (zostaje, zeby blokowac duplikaty)
        verify(processedOrderStore, never()).release(any());
    }

    @Test
    @DisplayName("should skip processing for a duplicate event (idempotency)")
    void shouldSkipDuplicate() throws Exception {
        when(objectMapper.readValue(any(String.class), eq(OrderPlacedEvent.class))).thenReturn(event);
        when(processedOrderStore.claim("order-uuid-123")).thenReturn(false);

        consumer.consume("{\"orderId\":\"order-uuid-123\"}");

        // duplikat -> nie przetwarzamy ponownie, nie wysylamy drugiego maila
        verifyNoInteractions(fulfillmentService);
    }

    @Test
    @DisplayName("should release claim and propagate exception from FulfillmentService for retry")
    void shouldPropagateExceptionForRetry() throws Exception {
        when(objectMapper.readValue(any(String.class), eq(OrderPlacedEvent.class))).thenReturn(event);
        when(processedOrderStore.claim("order-uuid-123")).thenReturn(true);
        doThrow(new FulfillmentException("Email failed"))
                .when(fulfillmentService).process(event);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> consumer.consume("{\"orderId\":\"order-uuid-123\"}"))
                .isInstanceOf(FulfillmentException.class)
                .hasMessage("Email failed");

        // blad -> rezerwacja zwolniona, zeby @RetryableTopic mogl ponowic
        verify(processedOrderStore).release("order-uuid-123");
    }

    @Test
    @DisplayName("should publish FulfillmentFailedEvent on DLT handling")
    void shouldPublishFailedEventOnDlt() throws Exception {
        when(objectMapper.readValue(any(String.class), eq(OrderPlacedEvent.class))).thenReturn(event);

        consumer.handleDlt("{\"orderId\":\"order-uuid-123\"}", "order.placed.dlt", "All retries exhausted");

        ArgumentCaptor<FulfillmentFailedEvent> captor =
                ArgumentCaptor.forClass(FulfillmentFailedEvent.class);
        verify(eventPublisher).publishFailed(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo("order-uuid-123");
        assertThat(captor.getValue().sessionId()).isEqualTo("session-abc");
        assertThat(captor.getValue().reason()).contains("All retries exhausted");
        verifyNoInteractions(fulfillmentService);
    }
}
