package com.loomi.orders.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomi.orders.api.dto.OrderRequest;
import com.loomi.orders.api.dto.OrderResponse;
import com.loomi.orders.domain.model.OrderEntity;
import com.loomi.orders.domain.model.OrderItemEntity;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {
    private final ObjectMapper objectMapper;

    public OrderMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OrderResponse toResponse(OrderEntity order) {
        List<OrderResponse.OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderResponse.OrderItemResponse(item.getItemId(), item.getProductId(), item.getQuantity(), item.getPriceSnapshot()))
                .collect(Collectors.toList());
        return new OrderResponse(order.getOrderId(), order.getCustomerId(), order.getStatus(), order.getTotalAmount(),
                order.getCreatedAt(), order.getUpdatedAt(), items);
    }

    public String metadataToString(Object metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid metadata", e);
        }
    }
}
