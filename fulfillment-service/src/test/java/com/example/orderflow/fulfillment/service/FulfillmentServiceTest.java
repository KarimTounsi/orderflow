package com.example.orderflow.fulfillment.service;

import com.example.orderflow.fulfillment.event.FulfillmentCompletedEvent;
import com.example.orderflow.fulfillment.event.FulfillmentFailedEvent;
import com.example.orderflow.fulfillment.event.OrderPlacedEvent;
import com.example.orderflow.fulfillment.exception.FulfillmentException;
import com.example.orderflow.fulfillment.kafka.FulfillmentEventPublisher;
import com.example.orderflow.fulfillment.model.FulfillmentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FulfillmentService Unit Tests")
class FulfillmentServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private FulfillmentEventPublisher eventPublisher;

    private FulfillmentService fulfillmentService;

    private OrderPlacedEvent event;

    @BeforeEach
    void setUp() {
        fulfillmentService = new FulfillmentService(emailService, eventPublisher);

        event = new OrderPlacedEvent(
                "order-uuid-123",
                "session-abc",
                List.of(new OrderPlacedEvent.Item("prod-1", "Laptop", 1, new BigDecimal("2500.00"))),
                new BigDecimal("2500.00"),
                "ul. Testowa 1, Warszawa",
                Instant.now()
        );
    }

    @Test
    @DisplayName("should publish FulfillmentCompletedEvent when email succeeds")
    void shouldPublishCompletedWhenEmailSucceeds() {
        when(emailService.sendOrderConfirmation(event))
                .thenReturn(new FulfillmentResult.Success("order-uuid-123", "test@orderflow.demo"));

        fulfillmentService.process(event);

        ArgumentCaptor<FulfillmentCompletedEvent> captor =
                ArgumentCaptor.forClass(FulfillmentCompletedEvent.class);
        verify(eventPublisher).publishCompleted(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo("order-uuid-123");
        assertThat(captor.getValue().emailSentTo()).isEqualTo("test@orderflow.demo");
        assertThat(captor.getValue().sessionId()).isEqualTo("session-abc");
        verify(eventPublisher, never()).publishFailed(any());
    }

    @Test
    @DisplayName("should publish FulfillmentFailedEvent and throw when email fails")
    void shouldPublishFailedAndThrowWhenEmailFails() {
        when(emailService.sendOrderConfirmation(event))
                .thenReturn(new FulfillmentResult.Failure("order-uuid-123", "SMTP connection refused"));

        assertThatThrownBy(() -> fulfillmentService.process(event))
                .isInstanceOf(FulfillmentException.class)
                .hasMessageContaining("SMTP connection refused");

        ArgumentCaptor<FulfillmentFailedEvent> captor =
                ArgumentCaptor.forClass(FulfillmentFailedEvent.class);
        verify(eventPublisher).publishFailed(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo("order-uuid-123");
        assertThat(captor.getValue().reason()).contains("SMTP connection refused");
        verify(eventPublisher, never()).publishCompleted(any());
    }
}
