package com.loomi.orders.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {
    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected IdempotencyKeyEntity() {
        // JPA
    }

    public static IdempotencyKeyEntity of(String key, String orderId) {
        IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
        entity.idempotencyKey = key;
        entity.orderId = orderId;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getOrderId() {
        return orderId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
