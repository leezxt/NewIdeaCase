package com.newideacase.platform.catalog.application;

import com.newideacase.platform.catalog.domain.Product;
import java.util.List;

public record ProductPage(List<Product> items, String nextCursor) {
}
