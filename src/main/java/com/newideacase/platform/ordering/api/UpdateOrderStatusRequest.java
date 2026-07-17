package com.newideacase.platform.ordering.api;

import com.newideacase.platform.ordering.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status,
        @NotNull Long version) {
}
