package com.newideacase.platform.knowledge.infrastructure;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIngestionQueueMetrics {

    public KnowledgeIngestionQueueMetrics(
            MongoKnowledgeIngestionTaskRepository repository,
            MeterRegistry meterRegistry) {
        for (KnowledgeIngestionTaskStatus status : List.of(
                KnowledgeIngestionTaskStatus.PENDING,
                KnowledgeIngestionTaskStatus.PROCESSING,
                KnowledgeIngestionTaskStatus.FAILED)) {
            Gauge.builder(
                            "knowledge.ingestion.queue.size",
                            repository,
                            currentRepository -> currentRepository.countByStatus(status))
                    .description("Knowledge ingestion tasks by queue status")
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .register(meterRegistry);
        }
    }
}
