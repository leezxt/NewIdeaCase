package com.newideacase.platform.knowledge.infrastructure;

import java.util.Optional;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.newideacase.platform.knowledge.application.KnowledgeDocumentStatus;

public interface MongoKnowledgeDocumentRepository extends MongoRepository<KnowledgeDocumentEntity, String> {

    Optional<KnowledgeDocumentEntity> findByTenantIdAndChecksum(String tenantId, String checksum);

    Optional<KnowledgeDocumentEntity> findByIdAndTenantId(String id, String tenantId);

    List<KnowledgeDocumentEntity> findTop100ByStatus(KnowledgeDocumentStatus status);
}
