package com.newideacase.platform.knowledge.application;

import com.newideacase.platform.knowledge.infrastructure.KnowledgeDocumentEntity;
import com.newideacase.platform.knowledge.infrastructure.MongoKnowledgeDocumentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class KnowledgeDocumentRegistrationWriter {

    private final MongoKnowledgeDocumentRepository repository;
    private final KnowledgeIngestionPort ingestionPort;

    public KnowledgeDocumentRegistrationWriter(
            MongoKnowledgeDocumentRepository repository, KnowledgeIngestionPort ingestionPort) {
        this.repository = repository;
        this.ingestionPort = ingestionPort;
    }

    @Transactional
    public KnowledgeDocumentEntity create(KnowledgeDocumentEntity entity) {
        KnowledgeDocumentEntity saved = repository.insert(entity);
        ingestionPort.submit(saved.getId());
        return saved;
    }
}
