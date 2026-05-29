package com.example.orderflow.fulfillment.service;

import com.example.orderflow.fulfillment.event.OrderPlacedEvent;
import com.example.orderflow.fulfillment.model.FulfillmentResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService Unit Tests")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    private OrderPlacedEvent event;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "demoEmail", "test@orderflow.demo");
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@orderflow.com");

        event = new OrderPlacedEvent(
                "order-uuid-123",
                "session-abc",
                List.of(
                        new OrderPlacedEvent.Item("prod-1", "Laptop Pro", 1, new BigDecimal("2500.00")),
                        new OrderPlacedEvent.Item("prod-2", "Mouse", 2, new BigDecimal("150.00"))
                ),
                new BigDecimal("2800.00"),
                "ul. Testowa 1, Warszawa",
                Instant.now()
        );
    }

    @Test
    @DisplayName("should return Success when email is sent successfully")
    void shouldReturnSuccessWhenEmailSent() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        FulfillmentResult result = emailService.sendOrderConfirmation(event);

        assertThat(result).isInstanceOf(FulfillmentResult.Success.class);
        FulfillmentResult.Success success = (FulfillmentResult.Success) result;
        assertThat(success.orderId()).isEqualTo("order-uuid-123");
        assertThat(success.emailSentTo()).isEqualTo("test@orderflow.demo");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("should return Failure when mail sender throws exception")
    void shouldReturnFailureWhenMailThrows() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP connection refused")).when(mailSender).send(any(MimeMessage.class));

        FulfillmentResult result = emailService.sendOrderConfirmation(event);

        assertThat(result).isInstanceOf(FulfillmentResult.Failure.class);
        FulfillmentResult.Failure failure = (FulfillmentResult.Failure) result;
        assertThat(failure.orderId()).isEqualTo("order-uuid-123");
        assertThat(failure.reason()).contains("SMTP connection refused");
    }

    @Test
    @DisplayName("should return Failure when MimeMessage creation fails")
    void shouldReturnFailureWhenMimeMessageFails() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("Mail server unavailable"));

        FulfillmentResult result = emailService.sendOrderConfirmation(event);

        assertThat(result).isInstanceOf(FulfillmentResult.Failure.class);
    }
}
