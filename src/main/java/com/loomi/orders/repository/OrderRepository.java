package com.loomi.orders.repository;

import com.loomi.orders.domain.model.OrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {
    Optional<OrderEntity> findByOrderId(String orderId);
    List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
