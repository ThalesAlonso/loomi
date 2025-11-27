package com.loomi.orders.api.dto;

import com.loomi.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public class OrderResponse {
    private String orderId;
    private String customerId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<OrderItemResponse> items;

    public OrderResponse(String orderId, String customerId, OrderStatus status, BigDecimal totalAmount,
                         OffsetDateTime createdAt, OffsetDateTime updatedAt, List<OrderItemResponse> items) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.items = items;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItemResponse> getItems() {
        return items;
    }

    public record OrderItemResponse(String itemId, String productId, int quantity, BigDecimal priceSnapshot) { }
}
