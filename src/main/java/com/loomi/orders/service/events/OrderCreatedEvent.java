package com.loomi.orders.service.events;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderCreatedEvent(String eventId, String orderId, String customerId, BigDecimal totalAmount,
                                OffsetDateTime createdAt, List<OrderItemPayload> items, String eventType,
                                OffsetDateTime timestamp) {

    public OrderCreatedEvent(String eventId, String orderId, String customerId, BigDecimal totalAmount,
                             OffsetDateTime createdAt, List<OrderItemPayload> items) {
        this(eventId, orderId, customerId, totalAmount, createdAt, items, "ORDER_CREATED", OffsetDateTime.now());
    }

    public OrderCreatedEvent {
        eventType = eventType == null ? "ORDER_CREATED" : eventType;
        timestamp = timestamp == null ? OffsetDateTime.now() : timestamp;
    }

    public record OrderItemPayload(String productId, String productType, int quantity, BigDecimal priceSnapshot, String metadata) {
    }
}
