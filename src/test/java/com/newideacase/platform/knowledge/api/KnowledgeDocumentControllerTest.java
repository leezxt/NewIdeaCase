package com.newideacase.platform.knowledge.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.newideacase.platform.knowledge.application.KnowledgeDocument;
import com.newideacase.platform.knowledge.application.KnowledgeDocumentService;
import com.newideacase.platform.knowledge.application.KnowledgeDocumentStatus;
import com.newideacase.platform.shared.error.ApiExceptionHandler;
import com.newideacase.platform.shared.web.CorrelationIdFilter;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeDocumentControllerTest {

    private KnowledgeDocumentService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(KnowledgeDocumentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new KnowledgeDocumentController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilter(new CorrelationIdFilter())
                .build();
    }

    @Test
    void registersDocumentWithoutReturningSourceContent() throws Exception {
        when(service.register(any())).thenReturn(document());

        mockMvc.perform(post("/api/v1/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Return policy",
                                  "mediaType":"text/plain",
                                  "content":"Products may be returned within 30 days."
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/knowledge/documents/document-id"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.checksum").value("checksum"))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void getsDocumentStatus() throws Exception {
        when(service.get(eq("document-id"))).thenReturn(document());

        mockMvc.perform(get("/api/v1/knowledge/documents/document-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("document-id"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void rejectsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Policy", "mediaType":"application/pdf", "content":"content"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value("https://api.newideacase.local/problems/validation-error"));
    }

    private static KnowledgeDocument document() {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        return new KnowledgeDocument(
                "document-id", "Return policy", "text/plain", "checksum", 39L,
                KnowledgeDocumentStatus.PENDING, 0, null, now, now, 0L);
    }
}
