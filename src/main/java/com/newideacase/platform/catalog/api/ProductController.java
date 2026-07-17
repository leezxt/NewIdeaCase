package com.newideacase.platform.catalog.api;

import com.newideacase.platform.catalog.application.CreateProductCommand;
import com.newideacase.platform.catalog.application.ProductService;
import com.newideacase.platform.catalog.application.UpdateProductCommand;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = ProductResponse.from(service.create(new CreateProductCommand(
                request.sku(), request.name(), request.description(), request.amount(), request.currency())));
        return ResponseEntity.created(URI.create("/api/v1/products/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    ProductResponse get(@PathVariable String id) {
        return ProductResponse.from(service.get(id));
    }

    @GetMapping
    ProductPageResponse list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ProductPageResponse.from(service.list(cursor, limit));
    }

    @PatchMapping("/{id}")
    ProductResponse update(@PathVariable String id, @Valid @RequestBody UpdateProductRequest request) {
        return ProductResponse.from(service.update(id, new UpdateProductCommand(
                request.name(), request.description(), Boolean.TRUE.equals(request.clearDescription()),
                request.amount(), request.currency(),
                request.status(), request.version())));
    }
}
