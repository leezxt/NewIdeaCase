package com.newideacase.platform.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.newideacase.platform.knowledge.infrastructure.KnowledgeDocumentEntity;
import com.newideacase.platform.knowledge.infrastructure.KnowledgeIngestionTaskEntity;
import com.newideacase.platform.knowledge.infrastructure.KnowledgeIngestionTaskStatus;
import com.newideacase.platform.knowledge.infrastructure.MongoKnowledgeChunkRepository;
import com.newideacase.platform.knowledge.infrastructure.MongoKnowledgeDocumentRepository;
import com.newideacase.platform.knowledge.infrastructure.MongoKnowledgeIngestionOutbox;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionWorkerTest {

    @Mock
    private MongoKnowledgeIngestionOutbox outbox;
    @Mock
    private MongoKnowledgeDocumentRepository documentRepository;
    @Mock
    private MongoKnowledgeChunkRepository chunkRepository;
    @Mock
    private DeterministicTextChunker chunker;

    private KnowledgeIngestionWorker worker;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        worker = new KnowledgeIngestionWorker(
                outbox, documentRepository, chunkRepository, chunker, meterRegistry, 3, 1);
    }

    @Test
    void retriesTransientFailureAndReturnsDocumentToPending() {
        KnowledgeIngestionTaskEntity task = claimedTask(1);
        KnowledgeDocumentEntity document = document();
        when(outbox.claimNext(any())).thenReturn(Optional.of(task));
        when(documentRepository.findById("document-id")).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunker.split(any())).thenThrow(new IllegalStateException("failure"));

        assertThat(worker.processNext()).isTrue();

        assertThat(document.getStatus()).isEqualTo(KnowledgeDocumentStatus.PENDING);
        verify(outbox).retry(eq("task-id"), any(), eq("IllegalStateException"));
        verify(outbox, never()).fail(any(), any());
        assertThat(taskCount("retried")).isEqualTo(1);
    }

    @Test
    void marksDocumentAndTaskFailedAfterMaximumAttempts() {
        KnowledgeIngestionTaskEntity task = claimedTask(3);
        KnowledgeDocumentEntity document = document();
        when(outbox.claimNext(any())).thenReturn(Optional.of(task));
        when(documentRepository.findById("document-id")).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunker.split(any())).thenThrow(new IllegalStateException("failure"));

        assertThat(worker.processNext()).isTrue();

        assertThat(document.getStatus()).isEqualTo(KnowledgeDocumentStatus.FAILED);
        assertThat(document.getFailureReason()).contains("3 attempts");
        verify(outbox).fail("task-id", "IllegalStateException");
        verify(outbox, never()).retry(any(), any(), any());
        assertThat(taskCount("failed")).isEqualTo(1);
    }

    @Test
    void reconcilesPendingDocumentsThatHaveNoOutboxTask() {
        KnowledgeDocumentEntity document = document();
        when(documentRepository.findTop100ByStatus(KnowledgeDocumentStatus.PENDING))
                .thenReturn(List.of(document));

        worker.reconcilePendingDocuments();

        verify(outbox).submit("document-id");
    }

    @Test
    void copiesTenantAndAclMetadataToEveryChunk() {
        KnowledgeIngestionTaskEntity task = claimedTask(1);
        KnowledgeDocumentEntity document = persistedDocument(Set.of("support", "legal"));
        when(outbox.claimNext(any())).thenReturn(Optional.of(task));
        when(documentRepository.findById("document-id")).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunker.split("content")).thenReturn(List.of(new TextChunk(0, "content", "chunk-checksum")));

        assertThat(worker.processNext()).isTrue();

        verify(chunkRepository).saveAll(org.mockito.ArgumentMatchers.<Iterable<
                        com.newideacase.platform.knowledge.infrastructure.KnowledgeChunkEntity>>argThat(chunks -> {
                    var chunk = chunks.iterator().next();
                    return chunk.getTenantId().equals("test")
                            && chunk.getAllowedRoles().equals(Set.of("support", "legal"));
                }));
        verify(outbox).complete("task-id");
        assertThat(taskCount("completed")).isEqualTo(1);
    }

    private static KnowledgeIngestionTaskEntity claimedTask(int attempts) {
        KnowledgeIngestionTaskEntity task = new KnowledgeIngestionTaskEntity("document-id", Instant.now());
        ReflectionTestUtils.setField(task, "id", "task-id");
        ReflectionTestUtils.setField(task, "status", KnowledgeIngestionTaskStatus.PROCESSING);
        ReflectionTestUtils.setField(task, "attempts", attempts);
        return task;
    }

    private double taskCount(String outcome) {
        return meterRegistry.get("knowledge.ingestion.tasks")
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private static KnowledgeDocumentEntity document() {
        return persistedDocument(Set.of());
    }

    private static KnowledgeDocumentEntity persistedDocument(Set<String> allowedRoles) {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity(
                "test", "Title", "text/plain", "content", "checksum", 7, allowedRoles);
        ReflectionTestUtils.setField(document, "id", "document-id");
        ReflectionTestUtils.setField(document, "version", 0L);
        return document;
    }
}
