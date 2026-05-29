package com.example.orderflow.fulfillment.model;

public sealed interface FulfillmentResult
        permits FulfillmentResult.Success, FulfillmentResult.Failure {

    String orderId();

    record Success(String orderId, String emailSentTo) implements FulfillmentResult {}

    // Failure: email sie nie udal - znamy powod niepowodzenia.
    record Failure(String orderId, String reason) implements FulfillmentResult {}
}
