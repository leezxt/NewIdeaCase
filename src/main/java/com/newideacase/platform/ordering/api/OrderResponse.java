package com.newideacase.platform.ordering.api;

import com.newideacase.platform.ordering.domain.Order;
import com.newideacase.platform.ordering.domain.OrderLine;
import com.newideacase.platform.ordering.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        List<OrderLine> items,
        BigDecimal totalAmount,
        String currency,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.items(),
                order.totalAmount(),
                order.currency(),
                order.status(),
                order.createdAt(),
                order.updatedAt(),
                order.version());
    }
}
