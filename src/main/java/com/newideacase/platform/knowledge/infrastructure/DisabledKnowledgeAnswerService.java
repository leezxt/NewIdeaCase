package com.newideacase.platform.knowledge.infrastructure;

import com.newideacase.platform.knowledge.application.KnowledgeAnswer;
import com.newideacase.platform.knowledge.application.KnowledgeAnswerQuery;
import com.newideacase.platform.knowledge.application.KnowledgeAnswerService;
import com.newideacase.platform.shared.error.ServiceUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledKnowledgeAnswerService implements KnowledgeAnswerService {

    @Override
    public KnowledgeAnswer answer(KnowledgeAnswerQuery query) {
        throw new ServiceUnavailableException(
                "RAG is not connected. Enable the rag profile after configuring Atlas and a model provider.");
    }
}
