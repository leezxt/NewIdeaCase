package com.newideacase.platform.knowledge.application;

import java.util.List;

public record KnowledgeAnswer(String answer, List<Citation> citations, String model) {

    public record Citation(String documentId, String title, Integer page, String chunkId) {
    }
}
