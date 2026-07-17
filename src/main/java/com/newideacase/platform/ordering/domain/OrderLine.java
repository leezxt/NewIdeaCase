package com.newideacase.platform.ordering.domain;

import java.math.BigDecimal;

public record OrderLine(
        String productId,
        String sku,
        String name,
        BigDecimal unitAmount,
        int quantity,
        BigDecimal lineTotal) {
}
