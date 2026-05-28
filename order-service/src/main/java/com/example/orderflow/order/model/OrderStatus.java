package com.example.orderflow.order.model;

public enum OrderStatus {

    PENDING("Oczekuje na potwierdzenie"),
    CONFIRMED("Potwierdzone"),
    SHIPPED("Wysłane"),
    DELIVERED("Dostarczone"),
    CANCELLED("Anulowane");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isFinal() {
        return switch (this) {
            case DELIVERED, CANCELLED -> true;
            case PENDING, CONFIRMED, SHIPPED -> false;
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING   -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == SHIPPED   || next == CANCELLED;
            case SHIPPED   -> next == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
