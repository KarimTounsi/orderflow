package com.example.orderflow.fulfillment.service;

import com.example.orderflow.fulfillment.event.OrderPlacedEvent;
import com.example.orderflow.fulfillment.model.FulfillmentResult;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${fulfillment.demo-email:customer@orderflow.demo}")
    private String demoEmail;

    @Value("${mail.from:noreply@orderflow.com}")
    private String fromAddress;

    public FulfillmentResult sendOrderConfirmation(OrderPlacedEvent event) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(demoEmail);
            helper.setSubject("Order Confirmation #" + event.orderId());
            helper.setText(buildEmailBody(event), true);

            mailSender.send(message);
            log.info("Confirmation email sent for order={} to={}", event.orderId(), demoEmail);

            return new FulfillmentResult.Success(event.orderId(), demoEmail);

        } catch (Exception e) {
            log.error("Failed to send email for order={}: {}", event.orderId(), e.getMessage());
            return new FulfillmentResult.Failure(event.orderId(), e.getMessage());
        }
    }

    private String buildEmailBody(OrderPlacedEvent event) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h2>Thank you for your order!</h2>");
        html.append("<p><strong>Order ID:</strong> ").append(event.orderId()).append("</p>");
        html.append("<p><strong>Shipping to:</strong> ").append(event.shippingAddress()).append("</p>");
        html.append("<h3>Items:</h3><ul>");

        for (OrderPlacedEvent.Item item : event.items()) {
            BigDecimal lineTotal = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
            html.append("<li>")
                .append(item.productName())
                .append(" x").append(item.quantity())
                .append(" = ").append(lineTotal).append(" PLN")
                .append("</li>");
        }

        html.append("</ul>");
        html.append("<p><strong>Total: ").append(event.total()).append(" PLN</strong></p>");
        html.append("<p>We will notify you when your order is shipped.</p>");
        html.append("</body></html>");

        return html.toString();
    }
}
