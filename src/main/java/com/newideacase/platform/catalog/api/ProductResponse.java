package com.newideacase.platform.catalog.api;

import com.newideacase.platform.catalog.domain.Product;
import com.newideacase.platform.catalog.domain.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        String id,
        String sku,
        String name,
        String description,
        BigDecimal amount,
        String currency,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.id(), product.sku(), product.name(), product.description(), product.amount(),
                product.currency(), product.status(), product.createdAt(), product.updatedAt(), product.version());
    }
}
