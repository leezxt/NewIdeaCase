package com.newideacase.platform.knowledge.application;

import com.newideacase.platform.knowledge.infrastructure.KnowledgeChunkEntity;
import com.newideacase.platform.knowledge.infrastructure.KnowledgeDocumentEntity;
import com.newideacase.platform.knowledge.infrastructure.KnowledgeIngestionTaskEntity;
import com.newideacase.platform.knowledge.infrastructure.MongoKnowledgeChunkRepository;
import com.newideacase.platform.knowledge.infrastructure.MongoKnowledgeDocumentRepository;
import com.newideacase.platform.knowledge.infrastructure.MongoKnowledgeIngestionOutbox;
import java.time.Instant;
import java.util.List;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIngestionWorker {

    private final MongoKnowledgeIngestionOutbox outbox;
    private final MongoKnowledgeDocumentRepository documentRepository;
    private final MongoKnowledgeChunkRepository chunkRepository;
    private final DeterministicTextChunker chunker;
    private final int maxAttempts;
    private final long retryDelayMillis;
    private final Counter completedTasks;
    private final Counter retriedTasks;
    private final Counter failedTasks;
    private final Counter orphanedTasks;

    public KnowledgeIngestionWorker(
            MongoKnowledgeIngestionOutbox outbox,
            MongoKnowledgeDocumentRepository documentRepository,
            MongoKnowledgeChunkRepository chunkRepository,
            DeterministicTextChunker chunker,
            MeterRegistry meterRegistry,
            @Value("${app.knowledge.worker.max-attempts:3}") int maxAttempts,
            @Value("${app.knowledge.worker.retry-delay-ms:1000}") long retryDelayMillis) {
        this.outbox = outbox;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.chunker = chunker;
        this.maxAttempts = maxAttempts;
        this.retryDelayMillis = retryDelayMillis;
        this.completedTasks = taskCounter(meterRegistry, "completed");
        this.retriedTasks = taskCounter(meterRegistry, "retried");
        this.failedTasks = taskCounter(meterRegistry, "failed");
        this.orphanedTasks = taskCounter(meterRegistry, "orphaned");
        if (maxAttempts < 1 || retryDelayMillis < 0) {
            throw new IllegalArgumentException("Invalid knowledge ingestion retry configuration");
        }
    }

    public void reconcilePendingDocuments() {
        documentRepository.findTop100ByStatus(KnowledgeDocumentStatus.PENDING)
                .forEach(document -> outbox.submit(document.getId()));
    }

    public boolean processNext() {
        KnowledgeIngestionTaskEntity task = outbox.claimNext(Instant.now()).orElse(null);
        if (task == null) {
            return false;
        }

        KnowledgeDocumentEntity document = documentRepository.findById(task.getDocumentId()).orElse(null);
        if (document == null) {
            outbox.complete(task.getId());
            orphanedTasks.increment();
            return true;
        }

        try {
            document.markProcessing();
            document = documentRepository.save(document);
            List<TextChunk> chunks = chunker.split(document.getContent());
            if (chunks.isEmpty()) {
                throw new IllegalStateException("No indexable text remained after normalization");
            }
            KnowledgeDocumentEntity source = document;
            chunkRepository.saveAll(chunks.stream()
                    .map(chunk -> new KnowledgeChunkEntity(
                            source.getTenantId(), source.getId(), source.getSourceVersion(), chunk.index(),
                            source.getTitle(), chunk.content(), chunk.checksum(), source.getAllowedRoles()))
                    .toList());
            chunkRepository.deleteByDocumentIdAndDocumentVersionAndChunkIndexGreaterThanEqual(
                    document.getId(), document.getSourceVersion(), chunks.size());
            document.markChunked(chunks.size());
            documentRepository.save(document);
            outbox.complete(task.getId());
            completedTasks.increment();
        } catch (RuntimeException exception) {
            String failure = exception.getClass().getSimpleName();
            if (task.getAttempts() >= maxAttempts) {
                document.markFailed("Ingestion failed after " + task.getAttempts() + " attempts");
                documentRepository.save(document);
                outbox.fail(task.getId(), failure);
                failedTasks.increment();
            } else {
                document.markPending();
                documentRepository.save(document);
                long multiplier = 1L << Math.max(0, task.getAttempts() - 1);
                outbox.retry(task.getId(), Instant.now().plusMillis(retryDelayMillis * multiplier), failure);
                retriedTasks.increment();
            }
        }
        return true;
    }

    private static Counter taskCounter(MeterRegistry meterRegistry, String outcome) {
        return Counter.builder("knowledge.ingestion.tasks")
                .description("Knowledge ingestion task outcomes")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }
}
