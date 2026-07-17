package com.newideacase.platform.knowledge.infrastructure;

import com.newideacase.platform.knowledge.application.KnowledgeDocumentStatus;
import java.time.Instant;
import java.util.Set;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("knowledge_documents")
@CompoundIndex(
        name = "tenant_checksum_unique_idx",
        def = "{'tenantId': 1, 'checksum': 1}",
        unique = true)
public class KnowledgeDocumentEntity {

    @Id
    private String id;
    private String tenantId;
    private String title;
    private String mediaType;
    private String content;
    private String checksum;
    private long sizeBytes;
    @Indexed(name = "knowledge_status_idx")
    private KnowledgeDocumentStatus status;
    private int sourceVersion;
    private int chunkCount;
    private String failureReason;
    private Set<String> allowedRoles;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    protected KnowledgeDocumentEntity() {
    }

    public KnowledgeDocumentEntity(
            String tenantId,
            String title,
            String mediaType,
            String content,
            String checksum,
            long sizeBytes) {
        this(tenantId, title, mediaType, content, checksum, sizeBytes, Set.of());
    }

    public KnowledgeDocumentEntity(
            String tenantId,
            String title,
            String mediaType,
            String content,
            String checksum,
            long sizeBytes,
            Set<String> allowedRoles) {
        this.tenantId = tenantId;
        this.title = title;
        this.mediaType = mediaType;
        this.content = content;
        this.checksum = checksum;
        this.sizeBytes = sizeBytes;
        this.status = KnowledgeDocumentStatus.PENDING;
        this.sourceVersion = 1;
        this.allowedRoles = allowedRoles == null ? Set.of() : Set.copyOf(allowedRoles);
    }

    public void markProcessing() {
        this.status = KnowledgeDocumentStatus.PROCESSING;
        this.failureReason = null;
    }

    public void markPending() {
        this.status = KnowledgeDocumentStatus.PENDING;
    }

    public void markChunked(int chunkCount) {
        this.status = KnowledgeDocumentStatus.CHUNKED;
        this.chunkCount = chunkCount;
        this.failureReason = null;
    }

    public void markFailed(String failureReason) {
        this.status = KnowledgeDocumentStatus.FAILED;
        this.failureReason = failureReason;
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTitle() {
        return title;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getContent() {
        return content;
    }

    public String getChecksum() {
        return checksum;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public KnowledgeDocumentStatus getStatus() {
        return status;
    }

    public int getSourceVersion() {
        return sourceVersion == 0 ? 1 : sourceVersion;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Set<String> getAllowedRoles() {
        return allowedRoles == null ? Set.of() : Set.copyOf(allowedRoles);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
