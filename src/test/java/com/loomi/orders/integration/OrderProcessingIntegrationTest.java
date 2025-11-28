package com.loomi.orders.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.loomi.orders.api.dto.OrderRequest;
import com.loomi.orders.api.dto.OrderResponse;
import com.loomi.orders.domain.OrderStatus;
import com.loomi.orders.domain.model.OrderEntity;
import com.loomi.orders.repository.OrderRepository;
import com.loomi.orders.service.events.OrderCreatedEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderProcessingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3"));

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KafkaTemplate<String, OrderCreatedEvent> orderCreatedKafkaTemplate;

    @Test
    void shouldProcessPhysicalOrderAndIgnoreDuplicateEvent() {
        OrderRequest request = new OrderRequest();
        request.setCustomerId("customer-int-1");
        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest();
        item.setProductId("BOOK-CC-001");
        item.setQuantity(1);
        item.setMetadata(Map.of("warehouseLocation", "SP"));
        request.setItems(List.of(item));

        ResponseEntity<OrderResponse> response = restTemplate.postForEntity("/api/orders", request, OrderResponse.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String orderId = response.getBody().getOrderId();

        awaitStatus(orderId, OrderStatus.PROCESSED);

        // reenviar o mesmo evento duas vezes para testar idempotência
        OrderEntity entity = orderRepository.findByOrderId(orderId).orElseThrow();
        OrderCreatedEvent event = toEvent(entity);
        orderCreatedKafkaTemplate.send("order-events", orderId, event);
        orderCreatedKafkaTemplate.send("order-events", orderId, event);

        awaitStatus(orderId, OrderStatus.PROCESSED);
    }

    @Test
    void shouldFailIncompatibleSubscriptionsEndToEnd() {
        OrderRequest request = new OrderRequest();
        request.setCustomerId("customer-int-2");
        OrderRequest.OrderItemRequest enterprise = new OrderRequest.OrderItemRequest();
        enterprise.setProductId("SUB-ENTERPRISE-001");
        enterprise.setQuantity(1);
        enterprise.setMetadata(Map.of("billingCycle", "ANNUAL"));

        OrderRequest.OrderItemRequest basic = new OrderRequest.OrderItemRequest();
        basic.setProductId("SUB-BASIC-001");
        basic.setQuantity(1);
        basic.setMetadata(Map.of("billingCycle", "MONTHLY"));

        request.setItems(List.of(enterprise, basic));

        ResponseEntity<OrderResponse> response = restTemplate.postForEntity("/api/orders", request, OrderResponse.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String orderId = response.getBody().getOrderId();

        awaitStatus(orderId, OrderStatus.FAILED);
    }

    private void awaitStatus(String orderId, OrderStatus expected) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() ->
                orderRepository.findByOrderId(orderId)
                        .map(OrderEntity::getStatus)
                        .filter(status -> status == expected)
                        .isPresent());
    }

    private OrderCreatedEvent toEvent(OrderEntity entity) {
        List<OrderCreatedEvent.OrderItemPayload> items = entity.getItems().stream()
                .map(item -> new OrderCreatedEvent.OrderItemPayload(
                        item.getProductId(),
                        item.getProductType().name(),
                        item.getQuantity(),
                        item.getPriceSnapshot(),
                        item.getMetadata()
                ))
                .collect(Collectors.toList());
        return new OrderCreatedEvent(
                "itest-" + entity.getOrderId(),
                entity.getOrderId(),
                entity.getCustomerId(),
                entity.getTotalAmount() != null ? entity.getTotalAmount() : BigDecimal.ZERO,
                entity.getCreatedAt() != null ? entity.getCreatedAt() : OffsetDateTime.now(),
                items
        );
    }
}
