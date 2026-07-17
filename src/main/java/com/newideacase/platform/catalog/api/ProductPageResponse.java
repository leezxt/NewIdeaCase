package com.newideacase.platform.catalog.api;

import com.newideacase.platform.catalog.application.ProductPage;
import java.util.List;

public record ProductPageResponse(List<ProductResponse> items, String nextCursor) {

    static ProductPageResponse from(ProductPage page) {
        return new ProductPageResponse(
                page.items().stream().map(ProductResponse::from).toList(),
                page.nextCursor());
    }
}
