package com.loomi.orders.service.events;

import java.time.OffsetDateTime;

public record OrderResultEvent(String eventId, String eventType, OffsetDateTime timestamp, Object payload) {

    public record ProcessedPayload(String orderId, OffsetDateTime processedAt) {
    }

    public record FailedPayload(String orderId, String reason, OffsetDateTime failedAt) {
    }

    public record PendingApprovalPayload(String orderId, String reason, OffsetDateTime pendingAt) {
    }

    public record LowStockPayload(String orderId, String productId, int remainingStock, OffsetDateTime occurredAt) {
    }

    public record FraudAlertPayload(String orderId, OffsetDateTime occurredAt) {
    }
}
