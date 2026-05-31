package com.example.orderflow.order.service;

import com.example.orderflow.order.dto.OrderItemResponse;
import com.example.orderflow.order.dto.OrderRequest;
import com.example.orderflow.order.dto.OrderResponse;
import com.example.orderflow.order.event.OrderPlacedEvent;
import com.example.orderflow.order.exception.InvalidOrderStatusTransitionException;
import com.example.orderflow.order.exception.OrderNotFoundException;
import com.example.orderflow.order.model.Order;
import com.example.orderflow.order.model.OrderItem;
import com.example.orderflow.order.model.OrderStatus;
import com.example.orderflow.order.model.OutboxEvent;
import com.example.orderflow.order.repository.OrderRepository;
import com.example.orderflow.order.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    private final OutboxRepository outboxRepository;

    // ObjectMapper serializuje zdarzenie do JSON JUZ w momencie zapisu do outbox - relay wysyla
    // gotowe bajty i nie musi znac typu zdarzenia.
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.order-placed:order-service.order.placed}")
    private String orderPlacedTopic;

    @Transactional
    public OrderResponse create(OrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(itemReq -> {
                    BigDecimal subtotal = itemReq.unitPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
                    return OrderItem.builder()
                            .productId(itemReq.productId())
                            .productName(itemReq.productName())
                            .unitPrice(itemReq.unitPrice())
                            .quantity(itemReq.quantity())
                            .subtotal(subtotal)
                            .build();
                })
                .toList();

        BigDecimal total = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .sessionId(request.sessionId())
                .items(items)
                .total(total)
                .shippingAddress(request.shippingAddress())
                .status(OrderStatus.PENDING)
                .build();

        Order saved = orderRepository.save(order);
        log.info("Order created: id={}, sessionId={}, total={}", saved.getId(), saved.getSessionId(), saved.getTotal());

        OrderPlacedEvent event = toEvent(saved);
        outboxRepository.save(OutboxEvent.builder()
                .aggregateId(saved.getId())
                .topic(orderPlacedTopic)
                .messageKey(saved.getId())
                .payload(objectMapper.writeValueAsString(event))
                .build());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(String id) {
        return orderRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getBySessionId(String sessionId, Pageable pageable) {
        return orderRepository.findAllBySessionId(sessionId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public OrderResponse updateStatus(String id, OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        OrderStatus previousStatus = order.getStatus();
        if (!previousStatus.canTransitionTo(newStatus)) {
            throw new InvalidOrderStatusTransitionException(previousStatus, newStatus);
        }

        order.setStatus(newStatus);
        log.info("Order status updated: id={}, from={}, to={}", id, previousStatus, newStatus);

        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getProductId(),
                        item.getProductName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getSubtotal()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getSessionId(),
                itemResponses,
                order.getStatus(),
                order.getStatus().label(),
                order.getTotal(),
                order.getShippingAddress(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderPlacedEvent toEvent(Order order) {
        List<OrderPlacedEvent.Item> eventItems = order.getItems().stream()
                .map(item -> new OrderPlacedEvent.Item(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .toList();

        return new OrderPlacedEvent(
                order.getId(),
                order.getSessionId(),
                eventItems,
                order.getTotal(),
                order.getShippingAddress(),
                Instant.now()
        );
    }
}
