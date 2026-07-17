package com.newideacase.platform.knowledge.infrastructure;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("knowledge_ingestion_tasks")
@CompoundIndex(
        name = "ingestion_claim_idx",
        def = "{'status': 1, 'availableAt': 1, 'lockedUntil': 1}")
public class KnowledgeIngestionTaskEntity {

    @Id
    private String id;

    @Indexed(unique = true)
    private String documentId;

    private KnowledgeIngestionTaskStatus status;
    private int attempts;
    private Instant availableAt;
    private Instant lockedUntil;
    private String lastError;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    protected KnowledgeIngestionTaskEntity() {
    }

    public KnowledgeIngestionTaskEntity(String documentId, Instant availableAt) {
        this.documentId = documentId;
        this.status = KnowledgeIngestionTaskStatus.PENDING;
        this.availableAt = availableAt;
    }

    public String getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public KnowledgeIngestionTaskStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public String getLastError() {
        return lastError;
    }
}
