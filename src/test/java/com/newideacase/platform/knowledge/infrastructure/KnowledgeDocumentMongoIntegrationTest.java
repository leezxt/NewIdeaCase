package com.newideacase.platform.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.newideacase.platform.knowledge.application.KnowledgeDocument;
import com.newideacase.platform.knowledge.application.KnowledgeDocumentService;
import com.newideacase.platform.knowledge.application.KnowledgeDocumentStatus;
import com.newideacase.platform.knowledge.application.KnowledgeIngestionPort;
import com.newideacase.platform.knowledge.application.KnowledgeIngestionWorker;
import com.newideacase.platform.knowledge.application.RegisterKnowledgeDocumentCommand;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class KnowledgeDocumentMongoIntegrationTest {

    @Container
    static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0").withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGODB.getReplicaSetUrl("new_idea_case_knowledge_test"));
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
    }

    @Autowired
    private KnowledgeDocumentService service;

    @Autowired
    private MongoKnowledgeDocumentRepository repository;

    @Autowired
    private MongoKnowledgeChunkRepository chunkRepository;

    @Autowired
    private MongoKnowledgeIngestionTaskRepository taskRepository;

    @Autowired
    private KnowledgeIngestionPort ingestionPort;

    @Autowired
    private KnowledgeIngestionWorker worker;

    @Autowired
    private MongoKnowledgeIngestionOutbox outbox;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clearDocuments() {
        taskRepository.deleteAll();
        chunkRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void persistsOutboxChunksAndDeduplicatesEveryRetry() {
        RegisterKnowledgeDocumentCommand command = new RegisterKnowledgeDocumentCommand(
                "Return policy", "text/plain", "Products may be returned within 30 days.");

        KnowledgeDocument first = service.register(command);
        KnowledgeDocument retry = service.register(command);

        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(taskRepository.count()).isEqualTo(1);
        assertThat(worker.processNext()).isTrue();

        KnowledgeDocument chunked = service.get(first.id());
        assertThat(chunked.status()).isEqualTo(KnowledgeDocumentStatus.CHUNKED);
        assertThat(chunked.chunkCount()).isEqualTo(1);
        assertThat(chunkRepository.findByDocumentIdAndDocumentVersionOrderByChunkIndex(first.id(), 1))
                .singleElement()
                .satisfies(chunk -> {
                    assertThat(chunk.getTenantId()).isEqualTo("test");
                    assertThat(chunk.getContent()).isEqualTo(command.content());
                    assertThat(chunk.getChecksum()).hasSize(64);
                });

        taskRepository.deleteAll();
        ingestionPort.submit(first.id());
        assertThat(worker.processNext()).isTrue();
        assertThat(chunkRepository.count()).isEqualTo(1);

        KnowledgeDocumentEntity stored = repository.findById(first.id()).orElseThrow();
        assertThat(stored.getTenantId()).isEqualTo("test");
        assertThat(stored.getContent()).isEqualTo(command.content());
        assertThat(stored.getStatus()).isEqualTo(KnowledgeDocumentStatus.CHUNKED);
        assertThat(service.get(first.id()).checksum()).hasSize(64);
    }

    @Test
    void preventsConcurrentClaimsAndRecoversExpiredLease() {
        KnowledgeDocument document = service.register(new RegisterKnowledgeDocumentCommand(
                "Lease test", "text/plain", "Lease recovery content"));

        KnowledgeIngestionTaskEntity firstClaim = outbox.claimNext(Instant.now()).orElseThrow();
        assertThat(firstClaim.getDocumentId()).isEqualTo(document.id());
        assertThat(outbox.claimNext(Instant.now())).isEmpty();

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(firstClaim.getId())),
                new Update().set("lockedUntil", Instant.now().minusSeconds(1)),
                KnowledgeIngestionTaskEntity.class);

        KnowledgeIngestionTaskEntity recovered = outbox.claimNext(Instant.now()).orElseThrow();
        assertThat(recovered.getId()).isEqualTo(firstClaim.getId());
        assertThat(recovered.getAttempts()).isEqualTo(2);
    }

    @Test
    void persistsBackendAclOnDocumentAndChunks() {
        Set<String> allowedRoles = Set.of("support", "legal");
        KnowledgeDocumentEntity source = repository.save(new KnowledgeDocumentEntity(
                "tenant-a", "Restricted policy", "text/plain", "Restricted content",
                "restricted-checksum", 18, allowedRoles));
        ingestionPort.submit(source.getId());

        assertThat(worker.processNext()).isTrue();

        assertThat(repository.findById(source.getId()).orElseThrow().getAllowedRoles())
                .containsExactlyInAnyOrderElementsOf(allowedRoles);
        assertThat(chunkRepository.findByDocumentIdAndDocumentVersionOrderByChunkIndex(source.getId(), 1))
                .singleElement()
                .satisfies(chunk -> {
                    assertThat(chunk.getTenantId()).isEqualTo("tenant-a");
                    assertThat(chunk.getAllowedRoles()).containsExactlyInAnyOrderElementsOf(allowedRoles);
                });
    }
}
