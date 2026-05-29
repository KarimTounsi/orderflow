package com.example.orderflow.fulfillment.exception;

public class FulfillmentException extends RuntimeException {

    public FulfillmentException(String message) {
        super(message);
    }

    public FulfillmentException(String message, Throwable cause) {
        super(message, cause);
    }
}
