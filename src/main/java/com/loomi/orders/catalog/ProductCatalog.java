package com.loomi.orders.catalog;

import com.loomi.orders.domain.ProductType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProductCatalog {
    private final Map<String, ProductRecord> products = Map.ofEntries(
            Map.entry("BOOK-CC-001", new ProductRecord("BOOK-CC-001", "Clean Code", ProductType.PHYSICAL, new BigDecimal("89.90"), 150, true, null)),
            Map.entry("LAPTOP-PRO-2024", new ProductRecord("LAPTOP-PRO-2024", "Laptop Pro", ProductType.PHYSICAL, new BigDecimal("5499.00"), 8, true, null)),
            Map.entry("SUB-PREMIUM-001", new ProductRecord("SUB-PREMIUM-001", "Premium Monthly", ProductType.SUBSCRIPTION, new BigDecimal("49.90"), null, true, null)),
            Map.entry("EBOOK-DDD-001", new ProductRecord("EBOOK-DDD-001", "Domain-Driven Design", ProductType.DIGITAL, new BigDecimal("59.90"), null, true, null)),
            Map.entry("PRE-PS6-001", new ProductRecord("PRE-PS6-001", "PlayStation 6", ProductType.PRE_ORDER, new BigDecimal("4999.00"), 500, true, LocalDate.parse("2025-11-15"))),
            Map.entry("CORP-CHAIR-ERG-001", new ProductRecord("CORP-CHAIR-ERG-001", "Ergonomic Chair Bulk", ProductType.CORPORATE, new BigDecimal("899.00"), 500, true, null))
    );

    public Optional<ProductRecord> findById(String productId) {
        return Optional.ofNullable(products.get(productId));
    }

    public record ProductRecord(String productId, String name, ProductType productType, BigDecimal price, Integer stock,
                                boolean active, LocalDate releaseDate) {
    }
}
