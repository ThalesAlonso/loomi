package com.loomi.orders.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomi.orders.catalog.ProductCatalog;
import com.loomi.orders.catalog.ProductCatalog.ProductRecord;
import com.loomi.orders.domain.FailureReason;
import com.loomi.orders.domain.OrderStatus;
import com.loomi.orders.domain.ProductType;
import com.loomi.orders.domain.model.OrderEntity;
import com.loomi.orders.repository.OrderRepository;
import com.loomi.orders.service.events.OrderCreatedEvent;
import com.loomi.orders.service.events.FraudAlertEvent;
import com.loomi.orders.service.events.LowStockAlertEvent;
import com.loomi.orders.service.events.OrderResultEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.MDC;

@Component
public class OrderProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(OrderProcessor.class);
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal FRAUD_CHECK_THRESHOLD = new BigDecimal("20000");
    private static final BigDecimal CORPORATE_CREDIT_LIMIT = new BigDecimal("100000");
    private static final BigDecimal CORPORATE_APPROVAL_THRESHOLD = new BigDecimal("50000");
    private static final BigDecimal CORPORATE_VOLUME_DISCOUNT = new BigDecimal("0.85");

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderResultEvent> resultKafkaTemplate;
    private final KafkaTemplate<String, LowStockAlertEvent> lowStockKafkaTemplate;
    private final KafkaTemplate<String, FraudAlertEvent> fraudKafkaTemplate;
    private final ProductCatalog productCatalog;
    private final ObjectMapper objectMapper;

    public OrderProcessor(OrderRepository orderRepository,
                          KafkaTemplate<String, OrderResultEvent> resultKafkaTemplate,
                          KafkaTemplate<String, LowStockAlertEvent> lowStockKafkaTemplate,
                          KafkaTemplate<String, FraudAlertEvent> fraudKafkaTemplate,
                          ProductCatalog productCatalog,
                          ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.resultKafkaTemplate = resultKafkaTemplate;
        this.lowStockKafkaTemplate = lowStockKafkaTemplate;
        this.fraudKafkaTemplate = fraudKafkaTemplate;
        this.productCatalog = productCatalog;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order-events", groupId = "order-processor", containerFactory = "orderCreatedListenerContainerFactory")
    @Transactional
    public void consume(OrderCreatedEvent event) {
        LOG.info("Processing order {}", event.orderId());
        OrderEntity order = orderRepository.findByOrderId(event.orderId()).orElse(null);
        if (order == null) {
            LOG.warn("Order {} not found, ignoring message", event.orderId());
            return;
        }
        MDC.put("orderId", order.getOrderId());
        MDC.put("customerId", order.getCustomerId());
        if (order.getStatus() != OrderStatus.PENDING) {
            LOG.info("Order {} already processed with status {}, skipping", order.getOrderId(), order.getStatus());
            return;
        }
        try {
            runGlobalChecks(event);
            ProcessContext context = processItems(event);
            order.setTotalAmount(context.totalAmount);
            if (context.requiresApproval) {
                order.updateStatus(OrderStatus.PENDING_APPROVAL);
                orderRepository.save(order);
                publishPending(order.getOrderId(), context.pendingReason);
                publishAlerts(order.getOrderId(), context);
                return;
            }
            order.updateStatus(OrderStatus.PROCESSED);
            orderRepository.save(order);
            publishProcessed(order.getOrderId());
            publishAlerts(order.getOrderId(), context);
        } catch (IllegalStateException ex) {
            if (FailureReason.PENDING_MANUAL_APPROVAL.name().equals(ex.getMessage())) {
                order.updateStatus(OrderStatus.PENDING_APPROVAL);
                orderRepository.save(order);
                publishPending(order.getOrderId(), ex.getMessage());
            } else {
                order.updateStatus(OrderStatus.FAILED);
                orderRepository.save(order);
                publishFailed(order.getOrderId(), ex.getMessage());
                if (FailureReason.FRAUD_ALERT.name().equals(ex.getMessage())) {
                    publishFraudAlert(order.getOrderId());
                }
                LOG.error("Order {} failed: {}", order.getOrderId(), ex.getMessage());
            }
        }
    }

    private void runGlobalChecks(OrderCreatedEvent event) {
        BigDecimal total = event.totalAmount();
        if (total.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            LOG.info("High value order {}, running additional validations", event.orderId());
        }
        ensurePaymentAuthorized(event.orderId(), total);
        if (isFraudSuspected(event.orderId(), total)) {
            throw new IllegalStateException(FailureReason.FRAUD_ALERT.name());
        }
    }

    private ProcessContext processItems(OrderCreatedEvent event) {
        ProcessContext context = new ProcessContext(event.totalAmount());
        Set<String> subscriptionTypes = new HashSet<>();
        boolean hasPhysical = false;
        boolean hasPreOrder = false;

        for (OrderCreatedEvent.OrderItemPayload item : event.items()) {
            ProductRecord product = productCatalog.findById(item.productId())
                    .orElseThrow(() -> new IllegalStateException(FailureReason.WAREHOUSE_UNAVAILABLE.name()));

            ProductType type = ProductType.valueOf(item.productType());
            switch (type) {
                case PHYSICAL -> handlePhysical(item, product, context);
                case SUBSCRIPTION -> handleSubscription(event.customerId(), item, subscriptionTypes);
                case DIGITAL -> handleDigital(item, product);
                case PRE_ORDER -> handlePreOrder(item, product, context);
                case CORPORATE -> handleCorporate(item, product, context);
            }
            hasPhysical |= type == ProductType.PHYSICAL;
            hasPreOrder |= type == ProductType.PRE_ORDER;
        }

        if (hasPhysical && hasPreOrder) {
            LOG.info("Mixed order with physical and pre-order items will ship separately");
        }

        return context;
    }

    private void handlePhysical(OrderCreatedEvent.OrderItemPayload item, ProductRecord product, ProcessContext context) {
        int available = Optional.ofNullable(product.stock()).orElse(0);
        if (available < item.quantity()) {
            throw new IllegalStateException(FailureReason.OUT_OF_STOCK.name());
        }
        int remaining = available - item.quantity();
        if (remaining < 5) {
            context.addLowStock(item.productId(), remaining);
        }
        // Mock de reserva e cálculo de prazo (não altera estoque global)
        int deliveryDays = estimateDeliveryDays(item.metadata());
        LOG.info("Reserved {} units of {} for order {}, ETA {} days", item.quantity(), item.productId(), item.productId(), deliveryDays);
    }

    private void handleSubscription(String customerId, OrderCreatedEvent.OrderItemPayload item, Set<String> subscriptions) {
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
        if (hasActiveSubscription(customerId, item.productId())) {
            throw new IllegalStateException(FailureReason.DUPLICATE_ACTIVE_SUBSCRIPTION.name());
        }
        subscriptions.add(item.productId());
        scheduleFirstBilling();
    }

    private void handleDigital(OrderCreatedEvent.OrderItemPayload item, ProductRecord product) {
        if (item.quantity() > 1) {
            throw new IllegalStateException(FailureReason.ALREADY_OWNED.name());
        }
        Integer licenses = product.licenses();
        if (licenses != null && licenses < item.quantity()) {
            throw new IllegalStateException(FailureReason.LICENSE_UNAVAILABLE.name());
        }
        generateLicense(item.productId());
        sendDigitalDelivery(item.productId());
    }

    private void handlePreOrder(OrderCreatedEvent.OrderItemPayload item, ProductRecord product, ProcessContext context) {
        LocalDate releaseDate = product.releaseDate();
        if (releaseDate == null) {
            throw new IllegalStateException(FailureReason.INVALID_RELEASE_DATE.name());
        }
        if (!releaseDate.isAfter(LocalDate.now())) {
            throw new IllegalStateException(FailureReason.RELEASE_DATE_PASSED.name());
        }
        Integer slots = product.preOrderSlots();
        if (slots != null && item.quantity() > slots) {
            throw new IllegalStateException(FailureReason.PRE_ORDER_SOLD_OUT.name());
        }
        BigDecimal discount = resolvePreOrderDiscount(item.metadata(), item.priceSnapshot(), item.quantity());
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            LOG.info("Pre-order discount applied for {}", item.productId());
            context.applyDiscount(discount);
        }
    }

    private void handleCorporate(OrderCreatedEvent.OrderItemPayload item, ProductRecord product, ProcessContext context) {
        Map<String, Object> metadata = parseMetadata(item.metadata());
        String cnpj = Optional.ofNullable(metadata.get("cnpj")).map(Object::toString).orElse("");
        if (cnpj.isBlank() || !isValidCnpj(cnpj)) {
            throw new IllegalStateException(FailureReason.INVALID_CORPORATE_DATA.name());
        }
        BigDecimal lineTotal = item.priceSnapshot().multiply(BigDecimal.valueOf(item.quantity()));
        if (lineTotal.compareTo(CORPORATE_CREDIT_LIMIT) > 0) {
            throw new IllegalStateException(FailureReason.CREDIT_LIMIT_EXCEEDED.name());
        }
        if (item.quantity() > 100) {
            BigDecimal discount = lineTotal.subtract(lineTotal.multiply(CORPORATE_VOLUME_DISCOUNT));
            context.applyDiscount(discount);
            LOG.info("Volume discount applied for {}", item.productId());
        }
        if (lineTotal.compareTo(CORPORATE_APPROVAL_THRESHOLD) > 0) {
            context.requiresApproval(FailureReason.PENDING_MANUAL_APPROVAL.name());
        }
        metadata.getOrDefault("paymentTerms", "NET_30");
    }

    private void publishProcessed(String orderId) {
        OrderResultEvent event = new OrderResultEvent(
                UUID.randomUUID().toString(),
                "ORDER_PROCESSED",
                OffsetDateTime.now(),
                new OrderResultEvent.ProcessedPayload(orderId, OffsetDateTime.now())
        );
        resultKafkaTemplate.send("order-results", orderId, event);
    }

    private void publishFailed(String orderId, String reason) {
        OrderResultEvent event = new OrderResultEvent(
                UUID.randomUUID().toString(),
                "ORDER_FAILED",
                OffsetDateTime.now(),
                new OrderResultEvent.FailedPayload(orderId, reason, OffsetDateTime.now())
        );
        resultKafkaTemplate.send("order-results", orderId, event);
    }

    private void publishPending(String orderId, String reason) {
        OrderResultEvent event = new OrderResultEvent(
                UUID.randomUUID().toString(),
                "ORDER_PENDING_APPROVAL",
                OffsetDateTime.now(),
                new OrderResultEvent.PendingApprovalPayload(orderId, reason, OffsetDateTime.now())
        );
        resultKafkaTemplate.send("order-results", orderId, event);
    }

    private void publishAlerts(String orderId, ProcessContext context) {
        for (LowStockAlert alert : context.lowStockAlerts) {
            LowStockAlertEvent event = new LowStockAlertEvent(
                    UUID.randomUUID().toString(),
                    "LOW_STOCK_ALERT",
                    OffsetDateTime.now(),
                    orderId,
                    alert.productId(),
                    alert.remainingStock()
            );
            lowStockKafkaTemplate.send("order-alerts", orderId, event);
        }
    }

    private void publishFraudAlert(String orderId) {
        FraudAlertEvent event = new FraudAlertEvent(
                UUID.randomUUID().toString(),
                "FRAUD_ALERT",
                OffsetDateTime.now(),
                orderId
        );
        fraudKafkaTemplate.send("order-alerts", orderId, event);
    }

    private boolean isValidCnpj(String cnpj) {
        return cnpj.matches("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}") || cnpj.matches("\\d{14}");
    }

    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            LOG.warn("Invalid metadata, ignoring: {}", e.getMessage());
            return Map.of();
        }
    }

    private void ensurePaymentAuthorized(String orderId, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(FailureReason.PAYMENT_FAILED.name());
        }
        int hash = Math.abs(orderId.hashCode());
        if (hash % 31 == 0) {
            throw new IllegalStateException(FailureReason.PAYMENT_FAILED.name());
        }
    }

    private boolean isFraudSuspected(String orderId, BigDecimal total) {
        if (total.compareTo(FRAUD_CHECK_THRESHOLD) <= 0) {
            return false;
        }
        int hash = Math.abs(orderId.hashCode());
        return hash % 20 == 0;
    }

    private void scheduleFirstBilling() {
        LOG.info("First billing scheduled for subscription");
    }

    private boolean hasActiveSubscription(String customerId, String productId) {
        if (customerId == null || customerId.isBlank()) {
            return false;
        }
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .filter(order -> order.getStatus() == OrderStatus.PROCESSED || order.getStatus() == OrderStatus.PENDING_APPROVAL)
                .flatMap(order -> order.getItems().stream())
                .anyMatch(item -> item.getProductId().equals(productId));
    }

    private void generateLicense(String productId) {
        String license = UUID.randomUUID().toString();
        LOG.info("Generated license {} for product {}", license, productId);
    }

    private void sendDigitalDelivery(String productId) {
        LOG.info("Sent digital delivery email for {}", productId);
    }

    private int estimateDeliveryDays(String metadata) {
        Map<String, Object> meta = parseMetadata(metadata);
        String location = Optional.ofNullable(meta.get("warehouseLocation")).map(Object::toString).orElse("DEFAULT");
        int hash = Math.abs(location.hashCode());
        return 5 + (hash % 6); // 5-10 dias
    }

    private BigDecimal resolvePreOrderDiscount(String metadata, BigDecimal price, int qty) {
        Map<String, Object> meta = parseMetadata(metadata);
        Object discountObj = meta.get("preOrderDiscount");
        if (discountObj == null) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal discount = new BigDecimal(discountObj.toString());
            if (discount.compareTo(BigDecimal.ONE) < 0) {
                discount = price.multiply(BigDecimal.valueOf(qty)).multiply(discount); // percentual
            }
            return discount.max(BigDecimal.ZERO);
        } catch (NumberFormatException ex) {
            LOG.warn("Invalid preOrderDiscount format: {}", discountObj);
            return BigDecimal.ZERO;
        }
    }

    private static final class ProcessContext {
        private BigDecimal totalAmount;
        private boolean requiresApproval;
        private String pendingReason;
        private final List<LowStockAlert> lowStockAlerts = new ArrayList<>();

        ProcessContext(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        void applyDiscount(BigDecimal discountAmount) {
            if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            this.totalAmount = this.totalAmount.subtract(discountAmount);
        }

        void addLowStock(String productId, int remaining) {
            lowStockAlerts.add(new LowStockAlert(productId, remaining));
        }

        void requiresApproval(String reason) {
            this.requiresApproval = true;
            this.pendingReason = reason;
        }
    }

    private record LowStockAlert(String productId, int remainingStock) {
    }
}
