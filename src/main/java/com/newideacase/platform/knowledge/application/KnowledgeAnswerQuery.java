package com.newideacase.platform.knowledge.application;

import java.util.Set;

public record KnowledgeAnswerQuery(String question, String tenantId, Set<String> roles) {

    public KnowledgeAnswerQuery {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
