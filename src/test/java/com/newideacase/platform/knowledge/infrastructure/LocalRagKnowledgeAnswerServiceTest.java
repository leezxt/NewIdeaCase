package com.newideacase.platform.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.newideacase.platform.knowledge.application.KnowledgeAnswer;
import com.newideacase.platform.knowledge.application.KnowledgeAnswerQuery;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LocalRagKnowledgeAnswerServiceTest {

    @Test
    void doesNotSendRoleProtectedChunksToModelsForUnauthorizedUser() {
        MongoKnowledgeChunkRepository repository = repositoryReturning(List.of(new KnowledgeChunkEntity(
                "tenant-a", "doc-1", 1, 0, "Policy", "private content", "checksum",
                Set.of("legal"))));
        LocalRagKnowledgeAnswerService service = new LocalRagKnowledgeAnswerService(
                repository, null, null, "qwen2.5", 6, 0.45);

        KnowledgeAnswer answer = service.answer(
                new KnowledgeAnswerQuery("What is the policy?", "tenant-a", Set.of("support")));

        assertThat(answer.citations()).isEmpty();
        assertThat(answer.answer()).contains("insufficient");
    }

    private static MongoKnowledgeChunkRepository repositoryReturning(List<KnowledgeChunkEntity> chunks) {
        return (MongoKnowledgeChunkRepository) Proxy.newProxyInstance(
                MongoKnowledgeChunkRepository.class.getClassLoader(),
                new Class<?>[] {MongoKnowledgeChunkRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("findTop500ByTenantIdOrderByDocumentIdAscChunkIndexAsc")) {
                        return chunks;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
