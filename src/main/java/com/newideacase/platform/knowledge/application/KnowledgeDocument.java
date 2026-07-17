package com.newideacase.platform.knowledge.application;

import java.time.Instant;

public record KnowledgeDocument(
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
}
