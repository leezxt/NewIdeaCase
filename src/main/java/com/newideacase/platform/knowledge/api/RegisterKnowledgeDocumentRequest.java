package com.newideacase.platform.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterKnowledgeDocumentRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Pattern(regexp = "(?i)text/plain") String mediaType,
        @NotBlank @Size(max = 100000) String content) {
}
