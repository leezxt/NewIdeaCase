package com.newideacase.platform.knowledge.infrastructure;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MongoKnowledgeIngestionTaskRepository
        extends MongoRepository<KnowledgeIngestionTaskEntity, String> {

    Optional<KnowledgeIngestionTaskEntity> findByDocumentId(String documentId);

    long countByStatus(KnowledgeIngestionTaskStatus status);
}
