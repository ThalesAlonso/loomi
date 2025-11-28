package com.loomi.orders.service.events;

import java.time.OffsetDateTime;

public record FraudAlertEvent(String eventId, String eventType, OffsetDateTime timestamp, String orderId) {
    public FraudAlertEvent {
        eventType = eventType == null ? "FRAUD_ALERT" : eventType;
        timestamp = timestamp == null ? OffsetDateTime.now() : timestamp;
    }
}
