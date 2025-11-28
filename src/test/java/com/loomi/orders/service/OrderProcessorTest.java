package com.loomi.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomi.orders.catalog.ProductCatalog;
import com.loomi.orders.catalog.ProductCatalog.ProductRecord;
import com.loomi.orders.domain.OrderStatus;
import com.loomi.orders.domain.ProductType;
import com.loomi.orders.domain.model.OrderEntity;
import com.loomi.orders.repository.OrderRepository;
import com.loomi.orders.service.events.FraudAlertEvent;
import com.loomi.orders.service.events.LowStockAlertEvent;
import com.loomi.orders.service.events.OrderCreatedEvent;
import com.loomi.orders.service.events.OrderResultEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;

class OrderProcessorTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private KafkaTemplate<String, OrderResultEvent> kafkaTemplate;
    @Mock
    private KafkaTemplate<String, LowStockAlertEvent> lowStockKafkaTemplate;
    @Mock
    private KafkaTemplate<String, FraudAlertEvent> fraudKafkaTemplate;
    @Mock
    private ProductCatalog productCatalog;

    private OrderProcessor processor;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        processor = new OrderProcessor(orderRepository, kafkaTemplate, lowStockKafkaTemplate, fraudKafkaTemplate, productCatalog, new ObjectMapper());
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(null);
        when(lowStockKafkaTemplate.send(any(), any(), any())).thenReturn(null);
        when(fraudKafkaTemplate.send(any(), any(), any())).thenReturn(null);
        when(orderRepository.findByCustomerIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
    }

    @Test
    void shouldMarkCorporateOrderAsPendingApproval() {
        OrderEntity entity = OrderEntity.create("customer");
        entity.setTotalAmount(new BigDecimal("60000"));
        String orderId = setOrderId(entity, "corp-approval-123");
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(entity));

        when(productCatalog.findById("CORP-LICENSE-ENT"))
                .thenReturn(Optional.of(new ProductRecord("CORP-LICENSE-ENT", "Enterprise License", ProductType.CORPORATE,
                        new BigDecimal("15000.00"), null, true, null, null, null)));

        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-1",
                orderId,
                "customer",
                new BigDecimal("60000"),
                OffsetDateTime.now(),
                List.of(new OrderCreatedEvent.OrderItemPayload("CORP-LICENSE-ENT", "CORPORATE", 4,
                        new BigDecimal("15000.00"), "{\"cnpj\":\"12345678000199\"}"))
        );

        processor.consume(event);

        assertThat(entity.getStatus()).isEqualTo(OrderStatus.PENDING_APPROVAL);
        verify(kafkaTemplate).send(eq("order-results"), eq(orderId), argWithType("ORDER_PENDING_APPROVAL"));
    }

    @Test
    void shouldProcessPhysicalOrderAndPublishLowStockAlert() {
        OrderEntity entity = OrderEntity.create("customer");
        entity.setTotalAmount(new BigDecimal("1200"));
        String orderId = setOrderId(entity, "physical-low-stock-1");
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(entity));

        when(productCatalog.findById("LAPTOP-PRO-2024"))
                .thenReturn(Optional.of(new ProductRecord("LAPTOP-PRO-2024", "Laptop Pro", ProductType.PHYSICAL,
                        new BigDecimal("300.00"), 6, true, LocalDate.now().plusDays(5), null, null)));

        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-2",
                orderId,
                "customer",
                new BigDecimal("1200"),
                OffsetDateTime.now(),
                List.of(new OrderCreatedEvent.OrderItemPayload("LAPTOP-PRO-2024", "PHYSICAL", 4,
                        new BigDecimal("300.00"), "{\"warehouseLocation\":\"SP\"}"))
        );

        processor.consume(event);

        assertThat(entity.getStatus()).isEqualTo(OrderStatus.PROCESSED);
        verify(kafkaTemplate).send(eq("order-results"), eq(orderId), argWithType("ORDER_PROCESSED"));
        verify(lowStockKafkaTemplate).send(eq("order-alerts"), eq(orderId), any(LowStockAlertEvent.class));
    }

    @Test
    void shouldFailForIncompatibleSubscriptions() {
        OrderEntity entity = OrderEntity.create("customer");
        entity.setTotalAmount(new BigDecimal("500"));
        String orderId = setOrderId(entity, "sub-incompat");
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(entity));

        when(productCatalog.findById("SUB-ENTERPRISE-001"))
                .thenReturn(Optional.of(new ProductRecord("SUB-ENTERPRISE-001", "Enterprise", ProductType.SUBSCRIPTION,
                        new BigDecimal("299.00"), null, true, LocalDate.now().plusDays(1), null, null)));
        when(productCatalog.findById("SUB-BASIC-001"))
                .thenReturn(Optional.of(new ProductRecord("SUB-BASIC-001", "Basic", ProductType.SUBSCRIPTION,
                        new BigDecimal("19.90"), null, true, LocalDate.now().plusDays(1), null, null)));

        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-3",
                orderId,
                "customer",
                new BigDecimal("318.90"),
                OffsetDateTime.now(),
                List.of(
                        new OrderCreatedEvent.OrderItemPayload("SUB-ENTERPRISE-001", "SUBSCRIPTION", 1, new BigDecimal("299.00"), "{}"),
                        new OrderCreatedEvent.OrderItemPayload("SUB-BASIC-001", "SUBSCRIPTION", 1, new BigDecimal("19.90"), "{}")
                )
        );

        processor.consume(event);

        assertThat(entity.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(kafkaTemplate).send(eq("order-results"), eq(orderId), argWithType("ORDER_FAILED"));
    }

    @Test
    void shouldFailForDigitalWithoutLicense() {
        OrderEntity entity = OrderEntity.create("customer");
        entity.setTotalAmount(new BigDecimal("59.90"));
        String orderId = setOrderId(entity, "digital-license");
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(entity));

        when(productCatalog.findById("EBOOK-DDD-001"))
                .thenReturn(Optional.of(new ProductRecord("EBOOK-DDD-001", "DDD", ProductType.DIGITAL,
                        new BigDecimal("59.90"), null, true, LocalDate.now().plusDays(1), null, 0)));

        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-4",
                orderId,
                "customer",
                new BigDecimal("59.90"),
                OffsetDateTime.now(),
                List.of(new OrderCreatedEvent.OrderItemPayload("EBOOK-DDD-001", "DIGITAL", 1, new BigDecimal("59.90"), "{}"))
        );

        processor.consume(event);

        assertThat(entity.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(kafkaTemplate).send(eq("order-results"), eq(orderId), argWithType("ORDER_FAILED"));
    }

    @Test
    void shouldFailForPreOrderPastReleaseDate() {
        OrderEntity entity = OrderEntity.create("customer");
        entity.setTotalAmount(new BigDecimal("100"));
        String orderId = setOrderId(entity, "preorder-invalid");
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(entity));

        when(productCatalog.findById("GAME-2020-001"))
                .thenReturn(Optional.of(new ProductRecord("GAME-2020-001", "Old Game", ProductType.PRE_ORDER,
                        new BigDecimal("100.00"), 100, true, LocalDate.now().minusDays(1), 100, null)));

        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-5",
                orderId,
                "customer",
                new BigDecimal("100.00"),
                OffsetDateTime.now(),
                List.of(new OrderCreatedEvent.OrderItemPayload("GAME-2020-001", "PRE_ORDER", 1, new BigDecimal("100.00"), "{}"))
        );

        processor.consume(event);

        assertThat(entity.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(kafkaTemplate).send(eq("order-results"), eq(orderId), argWithType("ORDER_FAILED"));
    }

    private OrderResultEvent argWithType(String eventType) {
        return org.mockito.ArgumentMatchers.argThat((ArgumentMatcher<OrderResultEvent>) event ->
                event != null && event.eventType().equals(eventType));
    }

    private String setOrderId(OrderEntity entity, String orderId) {
        // keep hash values predictable for fraud/payment checks inside processor
        int mod20 = Math.abs(orderId.hashCode()) % 20;
        int mod31 = Math.abs(orderId.hashCode()) % 31;
        if (mod20 == 0 || mod31 == 0) {
            orderId = orderId + "-safe";
        }
        try {
            var field = OrderEntity.class.getDeclaredField("orderId");
            field.setAccessible(true);
            field.set(entity, orderId);
            return orderId;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
