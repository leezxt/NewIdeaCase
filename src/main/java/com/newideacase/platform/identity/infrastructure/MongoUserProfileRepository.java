package com.newideacase.platform.identity.infrastructure;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MongoUserProfileRepository extends MongoRepository<UserProfileDocument, String> {

    Optional<UserProfileDocument> findByTenantIdAndSubject(String tenantId, String subject);
}
