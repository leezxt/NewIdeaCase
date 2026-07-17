package com.newideacase.platform.ordering.infrastructure;

import com.newideacase.platform.ordering.domain.OrderLine;
import java.math.BigDecimal;

public record OrderLineDocument(
        String productId,
        String sku,
        String name,
        BigDecimal unitAmount,
        int quantity,
        BigDecimal lineTotal) {

    OrderLine toDomain() {
        return new OrderLine(productId, sku, name, unitAmount, quantity, lineTotal);
    }
}
