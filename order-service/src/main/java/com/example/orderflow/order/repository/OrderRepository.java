package com.example.orderflow.order.repository;

import com.example.orderflow.order.model.Order;
import com.example.orderflow.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {

    Page<Order> findAllBySessionId(String sessionId, Pageable pageable);

    List<Order> findAllBySessionIdAndStatus(String sessionId, OrderStatus status);
}
