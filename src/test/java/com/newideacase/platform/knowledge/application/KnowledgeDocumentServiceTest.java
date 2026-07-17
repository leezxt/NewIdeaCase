package com.newideacase.platform.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.newideacase.platform.knowledge.infrastructure.KnowledgeDocumentEntity;
import com.newideacase.platform.knowledge.infrastructure.MongoKnowledgeDocumentRepository;
import com.newideacase.platform.shared.error.BadRequestException;
import com.newideacase.platform.shared.error.ResourceNotFoundException;
import com.newideacase.platform.shared.security.RequestIdentity;
import com.newideacase.platform.shared.security.RequestIdentityProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceTest {

    @Mock
    private MongoKnowledgeDocumentRepository repository;

    @Mock
    private KnowledgeIngestionPort ingestionPort;

    @Mock
    private KnowledgeDocumentRegistrationWriter registrationWriter;

    @Mock
    private RequestIdentityProvider identityProvider;

    private KnowledgeDocumentService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeDocumentService(repository, ingestionPort, registrationWriter, identityProvider);
        when(identityProvider.current()).thenReturn(new RequestIdentity("test", Set.of("support")));
    }

    @Test
    void registersPendingDocumentAndSubmitsItOnce() {
        when(repository.findByTenantIdAndChecksum(org.mockito.ArgumentMatchers.eq("test"), any()))
                .thenReturn(Optional.empty());
        when(registrationWriter.create(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        KnowledgeDocument result = service.register(new RegisterKnowledgeDocumentCommand(
                " Return policy ", "TEXT/PLAIN", "Products may be returned within 30 days."));

        assertThat(result.id()).isEqualTo("document-id");
        assertThat(result.title()).isEqualTo("Return policy");
        assertThat(result.checksum()).hasSize(64);
        assertThat(result.status()).isEqualTo(KnowledgeDocumentStatus.PENDING);
        org.mockito.ArgumentCaptor<KnowledgeDocumentEntity> captor =
                org.mockito.ArgumentCaptor.forClass(KnowledgeDocumentEntity.class);
        verify(registrationWriter).create(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("test");
        assertThat(captor.getValue().getAllowedRoles()).containsExactly("support");
    }

    @Test
    void returnsExistingDocumentAndRepairsPendingOutboxSubmission() {
        KnowledgeDocumentEntity existing = persisted(entity("Existing", "same content"));
        when(repository.findByTenantIdAndChecksum(org.mockito.ArgumentMatchers.eq("test"), any()))
                .thenReturn(Optional.of(existing));

        KnowledgeDocument result = service.register(new RegisterKnowledgeDocumentCommand(
                "Retry title", "text/plain", "same content"));

        assertThat(result.id()).isEqualTo("document-id");
        verify(registrationWriter, never()).create(any());
        verify(ingestionPort).submit("document-id");
    }

    @Test
    void rejectsContentThatExceedsUtf8ByteLimit() {
        String multibyteContent = "\u4e2d".repeat(40_000);

        assertThatThrownBy(() -> service.register(new RegisterKnowledgeDocumentCommand(
                "Large", "text/plain", multibyteContent)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("UTF-8 bytes");
    }

    @Test
    void concealsDocumentsFromOtherTenants() {
        when(identityProvider.current()).thenReturn(new RequestIdentity("tenant-a", Set.of("support")));
        when(repository.findByIdAndTenantId("document-id", "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("document-id"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository).findByIdAndTenantId("document-id", "tenant-a");
    }

    @Test
    void concealsDocumentsWhenRolesDoNotMatchAcl() {
        KnowledgeDocumentEntity restricted = new KnowledgeDocumentEntity(
                "test", "Restricted", "text/plain", "content", "checksum", 7, Set.of("admin"));
        when(repository.findByIdAndTenantId("document-id", "test")).thenReturn(Optional.of(restricted));

        assertThatThrownBy(() -> service.get("document-id"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static KnowledgeDocumentEntity entity(String title, String content) {
        return new KnowledgeDocumentEntity("test", title, "text/plain", content, "checksum", content.length());
    }

    private static KnowledgeDocumentEntity persisted(KnowledgeDocumentEntity entity) {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        ReflectionTestUtils.setField(entity, "id", "document-id");
        ReflectionTestUtils.setField(entity, "createdAt", now);
        ReflectionTestUtils.setField(entity, "updatedAt", now);
        ReflectionTestUtils.setField(entity, "version", 0L);
        return entity;
    }
}
