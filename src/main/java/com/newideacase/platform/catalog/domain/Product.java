package com.newideacase.platform.catalog.domain;

import java.math.BigDecimal;
import java.io.Serializable;
import java.time.Instant;

public record Product(
        String id,
        String sku,
        String name,
        String description,
        BigDecimal amount,
        String currency,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version) implements Serializable {
}
