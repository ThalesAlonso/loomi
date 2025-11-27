package com.loomi.orders.service.events;

import com.loomi.orders.domain.OrderStatus;
import java.time.OffsetDateTime;

public record OrderResultEvent(String eventId, String orderId, OrderStatus status, String reason, OffsetDateTime occurredAt) {
}
