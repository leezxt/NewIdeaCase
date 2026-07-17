package com.newideacase.platform.knowledge.infrastructure;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MongoKnowledgeChunkRepository extends MongoRepository<KnowledgeChunkEntity, String> {

    List<KnowledgeChunkEntity> findByDocumentIdAndDocumentVersionOrderByChunkIndex(
            String documentId, int documentVersion);

    List<KnowledgeChunkEntity> findTop500ByTenantIdOrderByDocumentIdAscChunkIndexAsc(String tenantId);

    void deleteByDocumentIdAndDocumentVersionAndChunkIndexGreaterThanEqual(
            String documentId, int documentVersion, int chunkIndex);
}
