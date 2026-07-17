package com.newideacase.platform.knowledge.api;

import com.newideacase.platform.knowledge.application.KnowledgeAnswerService;
import com.newideacase.platform.knowledge.application.KnowledgeAnswerQuery;
import com.newideacase.platform.shared.security.RequestIdentity;
import com.newideacase.platform.shared.security.RequestIdentityProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private final KnowledgeAnswerService answerService;
    private final RequestIdentityProvider identityProvider;

    public RagController(KnowledgeAnswerService answerService, RequestIdentityProvider identityProvider) {
        this.answerService = answerService;
        this.identityProvider = identityProvider;
    }

    @PostMapping("/answers")
    AnswerResponse answer(@Valid @RequestBody AnswerRequest request) {
        RequestIdentity identity = identityProvider.current();
        return AnswerResponse.from(answerService.answer(
                new KnowledgeAnswerQuery(request.question(), identity.tenantId(), identity.roles())));
    }
}
