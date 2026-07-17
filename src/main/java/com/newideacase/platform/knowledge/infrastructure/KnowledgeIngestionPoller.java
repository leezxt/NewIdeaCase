package com.newideacase.platform.knowledge.infrastructure;

import com.newideacase.platform.knowledge.application.KnowledgeIngestionWorker;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class KnowledgeIngestionPoller {

    private static final int MAX_BATCH_SIZE = 10;

    private final KnowledgeIngestionWorker worker;

    public KnowledgeIngestionPoller(KnowledgeIngestionWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${app.knowledge.worker.poll-interval-ms:1000}")
    public void poll() {
        worker.reconcilePendingDocuments();
        for (int processed = 0; processed < MAX_BATCH_SIZE && worker.processNext(); processed++) {
            // Drain a bounded batch so one poll cannot monopolize the scheduler thread.
        }
    }
}
