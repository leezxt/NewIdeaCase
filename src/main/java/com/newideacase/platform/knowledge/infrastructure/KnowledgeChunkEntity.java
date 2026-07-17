package com.newideacase.platform.knowledge.infrastructure;

import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("knowledge_chunks")
@CompoundIndex(
        name = "document_version_chunk_unique_idx",
        def = "{'documentId': 1, 'documentVersion': 1, 'chunkIndex': 1}",
        unique = true)
public class KnowledgeChunkEntity {

    @Id
    private String id;
    private String tenantId;
    private String documentId;
    private int documentVersion;
    private int chunkIndex;
    private String title;
    private String content;
    private String checksum;
    private Set<String> allowedRoles;

    protected KnowledgeChunkEntity() {
    }

    public KnowledgeChunkEntity(
            String tenantId,
            String documentId,
            int documentVersion,
            int chunkIndex,
            String title,
            String content,
            String checksum) {
        this(tenantId, documentId, documentVersion, chunkIndex, title, content, checksum, Set.of());
    }

    public KnowledgeChunkEntity(
            String tenantId,
            String documentId,
            int documentVersion,
            int chunkIndex,
            String title,
            String content,
            String checksum,
            Set<String> allowedRoles) {
        this.id = documentId + ":" + documentVersion + ":" + chunkIndex;
        this.tenantId = tenantId;
        this.documentId = documentId;
        this.documentVersion = documentVersion;
        this.chunkIndex = chunkIndex;
        this.title = title;
        this.content = content;
        this.checksum = checksum;
        this.allowedRoles = allowedRoles == null ? Set.of() : Set.copyOf(allowedRoles);
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public int getDocumentVersion() {
        return documentVersion;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getChecksum() {
        return checksum;
    }

    public Set<String> getAllowedRoles() {
        return allowedRoles == null ? Set.of() : Set.copyOf(allowedRoles);
    }
}
