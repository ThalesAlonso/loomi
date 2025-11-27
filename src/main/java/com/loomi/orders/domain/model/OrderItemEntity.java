package com.loomi.orders.domain.model;

import com.loomi.orders.domain.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItemEntity {
    @Id
    @Column(name = "item_id", nullable = false, updatable = false)
    private String itemId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "price_snapshot", nullable = false)
    private BigDecimal priceSnapshot;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private OrderEntity order;

    public static OrderItemEntity from(String productId, ProductType productType, int quantity, BigDecimal priceSnapshot, String metadata) {
        OrderItemEntity entity = new OrderItemEntity();
        entity.itemId = UUID.randomUUID().toString();
        entity.productId = productId;
        entity.productType = productType;
        entity.quantity = quantity;
        entity.priceSnapshot = priceSnapshot;
        entity.metadata = metadata;
        return entity;
    }

    public String getItemId() {
        return itemId;
    }

    public String getProductId() {
        return productId;
    }

    public ProductType getProductType() {
        return productType;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPriceSnapshot() {
        return priceSnapshot;
    }

    public String getMetadata() {
        return metadata;
    }

    public OrderEntity getOrder() {
        return order;
    }

    public void setOrder(OrderEntity order) {
        this.order = order;
    }
}
