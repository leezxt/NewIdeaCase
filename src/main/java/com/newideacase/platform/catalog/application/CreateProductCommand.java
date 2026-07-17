package com.newideacase.platform.catalog.application;

import java.math.BigDecimal;

public record CreateProductCommand(
        String sku,
        String name,
        String description,
        BigDecimal amount,
        String currency) {
}
