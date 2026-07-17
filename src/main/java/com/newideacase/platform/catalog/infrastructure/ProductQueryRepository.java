package com.newideacase.platform.catalog.infrastructure;

import com.newideacase.platform.catalog.domain.ProductCursor;
import java.util.List;

public interface ProductQueryRepository {

    List<ProductDocument> findPage(ProductCursor cursor, int size);
}
