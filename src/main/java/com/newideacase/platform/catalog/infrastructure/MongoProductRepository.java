package com.newideacase.platform.catalog.infrastructure;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MongoProductRepository extends MongoRepository<ProductDocument, String> {

    boolean existsBySku(String sku);

    Optional<ProductDocument> findBySku(String sku);
}
