package com.loomi.orders.service.events;

import java.time.OffsetDateTime;

public record LowStockAlertEvent(String eventId, String eventType, OffsetDateTime timestamp,
                                 String orderId, String productId, int remainingStock) {
    public LowStockAlertEvent {
        eventType = eventType == null ? "LOW_STOCK_ALERT" : eventType;
        timestamp = timestamp == null ? OffsetDateTime.now() : timestamp;
    }
}
