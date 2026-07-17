package com.newideacase.platform.knowledge.api;

import com.newideacase.platform.knowledge.application.KnowledgeAnswer;
import java.util.List;

public record AnswerResponse(String answer, List<CitationResponse> citations, String model) {

    static AnswerResponse from(KnowledgeAnswer answer) {
        return new AnswerResponse(
                answer.answer(),
                answer.citations().stream().map(CitationResponse::from).toList(),
                answer.model());
    }

    public record CitationResponse(String documentId, String title, Integer page, String chunkId) {

        static CitationResponse from(KnowledgeAnswer.Citation citation) {
            return new CitationResponse(
                    citation.documentId(), citation.title(), citation.page(), citation.chunkId());
        }
    }
}
