package com.newideacase.platform.knowledge.api;

import com.newideacase.platform.knowledge.application.KnowledgeDocument;
import com.newideacase.platform.knowledge.application.KnowledgeDocumentStatus;
import java.time.Instant;

public record KnowledgeDocumentResponse(
        String id,
        String title,
        String mediaType,
        String checksum,
        long sizeBytes,
        KnowledgeDocumentStatus status,
        int chunkCount,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    static KnowledgeDocumentResponse from(KnowledgeDocument document) {
        return new KnowledgeDocumentResponse(
                document.id(), document.title(), document.mediaType(), document.checksum(),
                document.sizeBytes(), document.status(), document.chunkCount(), document.failureReason(),
                document.createdAt(), document.updatedAt(),
                document.version());
    }
}
