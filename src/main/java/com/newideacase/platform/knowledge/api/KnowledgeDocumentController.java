package com.newideacase.platform.knowledge.api;

import com.newideacase.platform.knowledge.application.KnowledgeDocumentService;
import com.newideacase.platform.knowledge.application.RegisterKnowledgeDocumentCommand;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge/documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService service;

    public KnowledgeDocumentController(KnowledgeDocumentService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<KnowledgeDocumentResponse> register(
            @Valid @RequestBody RegisterKnowledgeDocumentRequest request) {
        KnowledgeDocumentResponse response = KnowledgeDocumentResponse.from(service.register(
                new RegisterKnowledgeDocumentCommand(request.title(), request.mediaType(), request.content())));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/knowledge/documents/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    KnowledgeDocumentResponse get(@PathVariable String id) {
        return KnowledgeDocumentResponse.from(service.get(id));
    }
}
