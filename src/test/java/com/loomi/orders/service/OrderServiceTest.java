package com.loomi.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loomi.orders.api.dto.OrderRequest;
import com.loomi.orders.catalog.ProductCatalog;
import com.loomi.orders.catalog.ProductCatalog.ProductRecord;
import com.loomi.orders.domain.ProductType;
import com.loomi.orders.domain.model.OrderEntity;
import com.loomi.orders.repository.OrderRepository;
import com.loomi.orders.service.events.OrderCreatedEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;

class OrderServiceTest {

    @Mock
    private ProductCatalog productCatalog;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    private OrderService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new OrderService(productCatalog, orderRepository, new OrderMapper(new com.fasterxml.jackson.databind.ObjectMapper()), kafkaTemplate);
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(null);
    }

    @Test
    void shouldCreateOrderAndPublishEvent() {
        OrderRequest request = new OrderRequest();
        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest();
        item.setProductId("BOOK-CC-001");
        item.setQuantity(2);
        request.setCustomerId("customer-1");
        request.setItems(List.of(item));

        when(productCatalog.findById("BOOK-CC-001"))
                .thenReturn(Optional.of(new ProductRecord("BOOK-CC-001", "Clean Code", ProductType.PHYSICAL, new BigDecimal("10.00"), 10, true, null)));

        var response = service.create(request);

        assertThat(response.getTotalAmount()).isEqualByComparingTo("20.00");
        ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(kafkaTemplate).send(any(), any(), eventCaptor.capture());
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(response.getOrderId());
    }

    @Test
    void shouldFailWhenProductMissing() {
        OrderRequest request = new OrderRequest();
        request.setCustomerId("customer");
        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest();
        item.setProductId("MISSING");
        item.setQuantity(1);
        request.setItems(List.of(item));

        when(productCatalog.findById("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Produto MISSING não encontrado");
    }
}
