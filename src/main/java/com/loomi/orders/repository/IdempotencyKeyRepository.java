package com.loomi.orders.repository;

import com.loomi.orders.domain.model.IdempotencyKeyEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, String> {
    Optional<IdempotencyKeyEntity> findByIdempotencyKey(String key);
}
