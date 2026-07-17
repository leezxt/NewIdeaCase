package com.newideacase.platform.knowledge.infrastructure;

import com.newideacase.platform.knowledge.application.KnowledgeIngestionPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class MongoKnowledgeIngestionOutbox implements KnowledgeIngestionPort {

    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    private final MongoKnowledgeIngestionTaskRepository repository;
    private final MongoTemplate mongoTemplate;

    public MongoKnowledgeIngestionOutbox(
            MongoKnowledgeIngestionTaskRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void submit(String documentId) {
        if (repository.findByDocumentId(documentId).isPresent()) {
            return;
        }
        try {
            repository.insert(new KnowledgeIngestionTaskEntity(documentId, Instant.now()));
        } catch (DuplicateKeyException ignored) {
            // A concurrent idempotent submission already created the task.
        }
    }

    public Optional<KnowledgeIngestionTaskEntity> claimNext(Instant now) {
        Criteria pending = new Criteria().andOperator(
                Criteria.where("status").is(KnowledgeIngestionTaskStatus.PENDING),
                Criteria.where("availableAt").lte(now));
        Criteria expiredLease = new Criteria().andOperator(
                Criteria.where("status").is(KnowledgeIngestionTaskStatus.PROCESSING),
                Criteria.where("lockedUntil").lte(now));
        Query query = new Query(new Criteria().orOperator(pending, expiredLease))
                .with(Sort.by(Sort.Order.asc("availableAt"), Sort.Order.asc("_id")));
        Update update = new Update()
                .set("status", KnowledgeIngestionTaskStatus.PROCESSING)
                .set("lockedUntil", now.plus(LEASE_DURATION))
                .set("updatedAt", now)
                .inc("attempts", 1);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                KnowledgeIngestionTaskEntity.class));
    }

    public void complete(String taskId) {
        updateProcessing(taskId, new Update()
                .set("status", KnowledgeIngestionTaskStatus.COMPLETED)
                .unset("lockedUntil")
                .unset("lastError"));
    }

    public void retry(String taskId, Instant availableAt, String lastError) {
        updateProcessing(taskId, new Update()
                .set("status", KnowledgeIngestionTaskStatus.PENDING)
                .set("availableAt", availableAt)
                .set("lastError", lastError)
                .unset("lockedUntil"));
    }

    public void fail(String taskId, String lastError) {
        updateProcessing(taskId, new Update()
                .set("status", KnowledgeIngestionTaskStatus.FAILED)
                .set("lastError", lastError)
                .unset("lockedUntil"));
    }

    private void updateProcessing(String taskId, Update update) {
        update.set("updatedAt", Instant.now());
        Query query = Query.query(Criteria.where("_id").is(taskId)
                .and("status").is(KnowledgeIngestionTaskStatus.PROCESSING));
        mongoTemplate.updateFirst(query, update, KnowledgeIngestionTaskEntity.class);
    }
}
