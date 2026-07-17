package com.newideacase.platform.knowledge.application;

import com.newideacase.platform.knowledge.infrastructure.KnowledgeDocumentEntity;
import com.newideacase.platform.knowledge.infrastructure.MongoKnowledgeDocumentRepository;
import com.newideacase.platform.shared.error.BadRequestException;
import com.newideacase.platform.shared.error.ResourceNotFoundException;
import com.newideacase.platform.shared.security.RequestIdentity;
import com.newideacase.platform.shared.security.RequestIdentityProvider;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeDocumentService {

    private static final int MAX_CONTENT_BYTES = 100_000;
    private static final String SUPPORTED_MEDIA_TYPE = "text/plain";

    private final MongoKnowledgeDocumentRepository repository;
    private final KnowledgeIngestionPort ingestionPort;
    private final KnowledgeDocumentRegistrationWriter registrationWriter;
    private final RequestIdentityProvider identityProvider;

    public KnowledgeDocumentService(
            MongoKnowledgeDocumentRepository repository,
            KnowledgeIngestionPort ingestionPort,
            KnowledgeDocumentRegistrationWriter registrationWriter,
            RequestIdentityProvider identityProvider) {
        this.repository = repository;
        this.ingestionPort = ingestionPort;
        this.registrationWriter = registrationWriter;
        this.identityProvider = identityProvider;
    }

    public KnowledgeDocument register(RegisterKnowledgeDocumentCommand command) {
        RequestIdentity identity = identityProvider.current();
        String title = command.title() == null ? "" : command.title().trim();
        String mediaType = command.mediaType() == null
                ? ""
                : command.mediaType().trim().toLowerCase(Locale.ROOT);
        String content = command.content();
        if (title.isBlank() || title.length() > 200) {
            throw new BadRequestException("Knowledge document title must contain 1 to 200 characters");
        }
        if (!SUPPORTED_MEDIA_TYPE.equals(mediaType)) {
            throw new BadRequestException("Only text/plain knowledge documents are supported");
        }
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Knowledge document content is required");
        }

        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        if (contentBytes.length > MAX_CONTENT_BYTES) {
            throw new BadRequestException("Knowledge document content must not exceed 100000 UTF-8 bytes");
        }
        String checksum = ContentChecksum.sha256(contentBytes);
        KnowledgeDocumentEntity existing = repository.findByTenantIdAndChecksum(identity.tenantId(), checksum).orElse(null);
        if (existing != null) {
            requireAccess(existing, identity);
            if (existing.getStatus() == KnowledgeDocumentStatus.PENDING) {
                ingestionPort.submit(existing.getId());
            }
            return toDomain(existing);
        }

        KnowledgeDocumentEntity saved;
        try {
            saved = registrationWriter.create(new KnowledgeDocumentEntity(
                    identity.tenantId(), title, mediaType, content, checksum, contentBytes.length, identity.roles()));
        } catch (DuplicateKeyException exception) {
            saved = repository.findByTenantIdAndChecksum(identity.tenantId(), checksum).orElseThrow(() -> exception);
            requireAccess(saved, identity);
            if (saved.getStatus() == KnowledgeDocumentStatus.PENDING) {
                ingestionPort.submit(saved.getId());
            }
            return toDomain(saved);
        }
        return toDomain(saved);
    }

    public KnowledgeDocument get(String id) {
        RequestIdentity identity = identityProvider.current();
        return repository.findByIdAndTenantId(id, identity.tenantId())
                .filter(entity -> identity.canAccess(entity.getAllowedRoles()))
                .map(KnowledgeDocumentService::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found: " + id));
    }

    private static void requireAccess(KnowledgeDocumentEntity entity, RequestIdentity identity) {
        if (!identity.canAccess(entity.getAllowedRoles())) {
            throw new ResourceNotFoundException("Knowledge document not found");
        }
    }

    private static KnowledgeDocument toDomain(KnowledgeDocumentEntity entity) {
        return new KnowledgeDocument(
                entity.getId(), entity.getTitle(), entity.getMediaType(), entity.getChecksum(),
                entity.getSizeBytes(), entity.getStatus(), entity.getChunkCount(), entity.getFailureReason(),
                entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getVersion());
    }
}
