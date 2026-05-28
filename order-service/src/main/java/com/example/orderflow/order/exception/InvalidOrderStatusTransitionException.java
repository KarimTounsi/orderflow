package com.example.orderflow.order.exception;

import com.example.orderflow.order.model.OrderStatus;

public class InvalidOrderStatusTransitionException extends RuntimeException {

    public InvalidOrderStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot transition order status from " + from + " to " + to);
    }
}
