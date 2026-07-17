package com.newideacase.platform.catalog.application;

import com.newideacase.platform.catalog.domain.ProductStatus;
import java.math.BigDecimal;

public record UpdateProductCommand(
        String name,
        String description,
        boolean clearDescription,
        BigDecimal amount,
        String currency,
        ProductStatus status,
        Long version) {
}
