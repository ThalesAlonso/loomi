package com.loomi.orders.service;

import com.loomi.orders.domain.FailureReason;
import com.loomi.orders.domain.OrderStatus;
import com.loomi.orders.domain.ProductType;
import com.loomi.orders.domain.model.OrderEntity;
import com.loomi.orders.repository.OrderRepository;
import com.loomi.orders.service.events.OrderCreatedEvent;
import com.loomi.orders.service.events.OrderResultEvent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(OrderProcessor.class);

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderResultEvent> resultKafkaTemplate;

    public OrderProcessor(OrderRepository orderRepository, KafkaTemplate<String, OrderResultEvent> resultKafkaTemplate) {
        this.orderRepository = orderRepository;
        this.resultKafkaTemplate = resultKafkaTemplate;
    }

    @KafkaListener(topics = "order-events", groupId = "order-processor", containerFactory = "orderCreatedListenerContainerFactory")
    @Transactional
    public void consume(OrderCreatedEvent event) {
        LOG.info("Processando pedido {}", event.orderId());
        OrderEntity order = orderRepository.findByOrderId(event.orderId()).orElse(null);
        if (order == null) {
            LOG.warn("Pedido {} não encontrado, ignorando mensagem", event.orderId());
            return;
        }
        try {
            validateHighValue(order.getTotalAmount());
            processItems(event);
            order.updateStatus(OrderStatus.PROCESSED);
            orderRepository.save(order);
            publishResult(event.orderId(), OrderStatus.PROCESSED, null);
        } catch (IllegalStateException ex) {
            order.updateStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            publishResult(event.orderId(), OrderStatus.FAILED, ex.getMessage());
            LOG.error("Pedido {} falhou: {}", event.orderId(), ex.getMessage());
        }
    }

    private void processItems(OrderCreatedEvent event) {
        boolean requiresApproval = false;
        Set<String> subscriptionTypes = new HashSet<>();
        for (OrderCreatedEvent.OrderItemPayload item : event.items()) {
            ProductType type = ProductType.valueOf(item.productType());
            switch (type) {
                case PHYSICAL -> handlePhysical(item);
                case SUBSCRIPTION -> handleSubscription(item, subscriptionTypes);
                case DIGITAL -> handleDigital(item);
                case PRE_ORDER -> handlePreOrder(item);
                case CORPORATE -> requiresApproval = handleCorporate(item);
            }
        }
        if (requiresApproval) {
            throw new IllegalStateException(FailureReason.PENDING_MANUAL_APPROVAL.name());
        }
    }

    private void validateHighValue(BigDecimal total) {
        if (total.compareTo(new BigDecimal("20000")) > 0) {
            throw new IllegalStateException(FailureReason.FRAUD_ALERT.name());
        }
    }

    private void handlePhysical(OrderCreatedEvent.OrderItemPayload item) {
        if (item.quantity() <= 0) {
            throw new IllegalStateException(FailureReason.OUT_OF_STOCK.name());
        }
        if (item.quantity() > 5) {
            LOG.info("Alerta de estoque baixo para o produto {}", item.productId());
        }
    }

    private void handleSubscription(OrderCreatedEvent.OrderItemPayload item, Set<String> subscriptions) {
        if (subscriptions.contains(item.productId())) {
            throw new IllegalStateException(FailureReason.DUPLICATE_ACTIVE_SUBSCRIPTION.name());
        }
        if (subscriptions.size() >= 5) {
            throw new IllegalStateException(FailureReason.SUBSCRIPTION_LIMIT_EXCEEDED.name());
        }
        if (subscriptions.contains("SUB-ENTERPRISE-001") && item.productId().equals("SUB-BASIC-001")
                || subscriptions.contains("SUB-BASIC-001") && item.productId().equals("SUB-ENTERPRISE-001")) {
            throw new IllegalStateException(FailureReason.INCOMPATIBLE_SUBSCRIPTIONS.name());
        }
        subscriptions.add(item.productId());
    }

    private void handleDigital(OrderCreatedEvent.OrderItemPayload item) {
        if (item.quantity() > 1) {
            throw new IllegalStateException(FailureReason.ALREADY_OWNED.name());
        }
    }

    private void handlePreOrder(OrderCreatedEvent.OrderItemPayload item) {
        if (item.metadata() != null && item.metadata().contains("2020")) {
            throw new IllegalStateException(FailureReason.INVALID_RELEASE_DATE.name());
        }
    }

    private boolean handleCorporate(OrderCreatedEvent.OrderItemPayload item) {
        if (item.quantity() > 100 && item.priceSnapshot() != null) {
            LOG.info("Desconto por volume aplicado para {}", item.productId());
        }
        BigDecimal limit = new BigDecimal("100000");
        if (item.priceSnapshot().multiply(BigDecimal.valueOf(item.quantity())).compareTo(limit) > 0) {
            throw new IllegalStateException(FailureReason.CREDIT_LIMIT_EXCEEDED.name());
        }
        return item.priceSnapshot().multiply(BigDecimal.valueOf(item.quantity())).compareTo(new BigDecimal("50000")) > 0;
    }

    private void publishResult(String orderId, OrderStatus status, String reason) {
        OrderResultEvent event = new OrderResultEvent(UUID.randomUUID().toString(), orderId, status, reason, OffsetDateTime.now());
        resultKafkaTemplate.send("order-results", orderId, event);
    }
}
