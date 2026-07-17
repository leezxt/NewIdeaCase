package com.newideacase.platform.ordering.infrastructure;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MongoOrderRepository extends MongoRepository<OrderDocument, String> {

    Optional<OrderDocument> findByIdAndTenantIdAndSubject(
            String id, String tenantId, String subject);
}
