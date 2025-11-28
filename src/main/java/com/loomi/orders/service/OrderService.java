package com.loomi.orders.service;

import com.loomi.orders.api.dto.OrderRequest;
import com.loomi.orders.api.dto.OrderResponse;
import com.loomi.orders.catalog.ProductCatalog;
import com.loomi.orders.catalog.ProductCatalog.ProductRecord;
import com.loomi.orders.domain.OrderStatus;
import com.loomi.orders.domain.ProductType;
import com.loomi.orders.domain.model.OrderEntity;
import com.loomi.orders.domain.model.OrderItemEntity;
import com.loomi.orders.repository.OrderRepository;
import com.loomi.orders.service.events.OrderCreatedEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);
    private final ProductCatalog productCatalog;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public OrderService(ProductCatalog productCatalog, OrderRepository orderRepository, OrderMapper orderMapper,
                        KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.productCatalog = productCatalog;
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        OrderEntity order = OrderEntity.create(request.getCustomerId());
        BigDecimal total = BigDecimal.ZERO;

        for (OrderRequest.OrderItemRequest item : request.getItems()) {
            ProductRecord product = productCatalog.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product %s not found".formatted(item.getProductId())));
            if (!product.active()) {
                throw new IllegalArgumentException("Product %s is not available".formatted(item.getProductId()));
            }
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive for product %s".formatted(product.productId()));
            }
            ProductType productType = product.productType();
            BigDecimal itemTotal = product.price().multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(itemTotal);
            OrderItemEntity entity = OrderItemEntity.from(product.productId(), productType, item.getQuantity(), product.price(),
                    orderMapper.metadataToString(item.getMetadata()));
            order.addItem(entity);
        }
        order.setTotalAmount(total);
        orderRepository.save(order);

        publishCreatedEvent(order);
        LOG.info("Order {} created for customer {}", order.getOrderId(), order.getCustomerId());
        return orderMapper.toResponse(order);
    }

    public OrderResponse findById(String orderId) {
        OrderEntity entity = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order %s not found".formatted(orderId)));
        return orderMapper.toResponse(entity);
    }

    public List<OrderResponse> findByCustomer(String customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(orderMapper::toResponse)
                .collect(Collectors.toList());
    }

    private void publishCreatedEvent(OrderEntity order) {
        List<OrderCreatedEvent.OrderItemPayload> items = order.getItems().stream()
                .map(item -> new OrderCreatedEvent.OrderItemPayload(item.getProductId(), item.getProductType().name(), item.getQuantity(), item.getPriceSnapshot(), item.getMetadata()))
                .toList();
        OrderCreatedEvent event = new OrderCreatedEvent(UUID.randomUUID().toString(), order.getOrderId(), order.getCustomerId(),
                order.getTotalAmount(), order.getCreatedAt(), items);
        kafkaTemplate.send("order-events", order.getOrderId(), event);
    }

    @Transactional
    public void updateStatus(String orderId, OrderStatus status) {
        orderRepository.findByOrderId(orderId).ifPresent(order -> {
            order.updateStatus(status);
            orderRepository.save(order);
        });
    }

    public List<OrderResponse> findAll() {
        return orderRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(orderMapper::toResponse)
                .collect(Collectors.toList());
    }
}
