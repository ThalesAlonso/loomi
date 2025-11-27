package com.loomi.orders.service.events;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderCreatedEvent(String eventId, String orderId, String customerId, BigDecimal totalAmount,
                                OffsetDateTime createdAt, List<OrderItemPayload> items) {
    public record OrderItemPayload(String productId, String productType, int quantity, BigDecimal priceSnapshot, String metadata) {
    }
}
