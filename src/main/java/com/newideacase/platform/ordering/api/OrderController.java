package com.newideacase.platform.ordering.api;

import com.newideacase.platform.ordering.application.CreateOrderItem;
import com.newideacase.platform.ordering.application.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = OrderResponse.from(service.create(request.items().stream()
                .map(item -> new CreateOrderItem(item.productId(), item.quantity()))
                .toList()));
        return ResponseEntity.created(URI.create("/api/v1/orders/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable String id) {
        return OrderResponse.from(service.get(id));
    }

    @PatchMapping("/{id}/status")
    OrderResponse updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return OrderResponse.from(service.updateStatus(id, request.status(), request.version()));
    }
}
