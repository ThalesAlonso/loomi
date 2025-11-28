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
            // Produtos físicos
            Map.entry("BOOK-CC-001", new ProductRecord("BOOK-CC-001", "Clean Code", ProductType.PHYSICAL, new BigDecimal("89.90"), 150, true, null, null, null)),
            Map.entry("LAPTOP-PRO-2024", new ProductRecord("LAPTOP-PRO-2024", "Laptop Pro", ProductType.PHYSICAL, new BigDecimal("5499.00"), 8, true, null, null, null)),
            Map.entry("LAPTOP-MBP-M3-001", new ProductRecord("LAPTOP-MBP-M3-001", "MacBook Pro M3", ProductType.PHYSICAL, new BigDecimal("12999.00"), 25, true, null, null, null)),

            // Assinaturas
            Map.entry("SUB-PREMIUM-001", new ProductRecord("SUB-PREMIUM-001", "Premium Monthly", ProductType.SUBSCRIPTION, new BigDecimal("49.90"), null, true, null, null, null)),
            Map.entry("SUB-BASIC-001", new ProductRecord("SUB-BASIC-001", "Basic Monthly", ProductType.SUBSCRIPTION, new BigDecimal("19.90"), null, true, null, null, null)),
            Map.entry("SUB-ENTERPRISE-001", new ProductRecord("SUB-ENTERPRISE-001", "Enterprise Plan", ProductType.SUBSCRIPTION, new BigDecimal("299.00"), null, true, null, null, null)),
            Map.entry("SUB-ADOBE-CC-001", new ProductRecord("SUB-ADOBE-CC-001", "Adobe Creative Cloud", ProductType.SUBSCRIPTION, new BigDecimal("159.00"), null, true, null, null, null)),

            // Digitais
            Map.entry("EBOOK-JAVA-001", new ProductRecord("EBOOK-JAVA-001", "Effective Java", ProductType.DIGITAL, new BigDecimal("39.90"), null, true, null, null, 1000)),
            Map.entry("EBOOK-DDD-001", new ProductRecord("EBOOK-DDD-001", "Domain-Driven Design", ProductType.DIGITAL, new BigDecimal("59.90"), null, true, null, null, 500)),
            Map.entry("EBOOK-SWIFT-001", new ProductRecord("EBOOK-SWIFT-001", "Swift Programming", ProductType.DIGITAL, new BigDecimal("49.90"), null, true, null, null, 800)),
            Map.entry("COURSE-KAFKA-001", new ProductRecord("COURSE-KAFKA-001", "Kafka Mastery", ProductType.DIGITAL, new BigDecimal("299.00"), null, true, null, null, 500)),

            // Pré-venda
            Map.entry("GAME-2025-001", new ProductRecord("GAME-2025-001", "Epic Game 2025", ProductType.PRE_ORDER, new BigDecimal("249.90"), 1000, true, LocalDate.parse("2025-06-01"), 1000, null)),
            Map.entry("PRE-PS6-001", new ProductRecord("PRE-PS6-001", "PlayStation 6", ProductType.PRE_ORDER, new BigDecimal("4999.00"), 500, true, LocalDate.parse("2025-11-15"), 500, null)),
            Map.entry("PRE-IPHONE16-001", new ProductRecord("PRE-IPHONE16-001", "iPhone 16 Pro", ProductType.PRE_ORDER, new BigDecimal("7999.00"), 2000, true, LocalDate.parse("2025-09-20"), 2000, null)),

            // Corporativo
            Map.entry("CORP-LICENSE-ENT", new ProductRecord("CORP-LICENSE-ENT", "Enterprise License", ProductType.CORPORATE, new BigDecimal("15000.00"), null, true, null, null, null)),
            Map.entry("CORP-CHAIR-ERG-001", new ProductRecord("CORP-CHAIR-ERG-001", "Ergonomic Chair Bulk", ProductType.CORPORATE, new BigDecimal("899.00"), 500, true, null, null, null))
    );

    public Optional<ProductRecord> findById(String productId) {
        return Optional.ofNullable(products.get(productId));
    }

    public record ProductRecord(String productId, String name, ProductType productType, BigDecimal price, Integer stock,
                                boolean active, LocalDate releaseDate, Integer preOrderSlots, Integer licenses) {
    }
}
