package com.newideacase.platform.knowledge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.newideacase.platform.knowledge.infrastructure.DisabledKnowledgeAnswerService;
import com.newideacase.platform.shared.error.ApiExceptionHandler;
import com.newideacase.platform.shared.security.RequestIdentity;
import com.newideacase.platform.shared.security.RequestIdentityProvider;
import com.newideacase.platform.shared.web.CorrelationIdFilter;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RagControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RequestIdentityProvider identityProvider = org.mockito.Mockito.mock(RequestIdentityProvider.class);
        org.mockito.Mockito.when(identityProvider.current())
                .thenReturn(new RequestIdentity("test", Set.of("support")));
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RagController(new DisabledKnowledgeAnswerService(), identityProvider))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilter(new CorrelationIdFilter())
                .build();
    }

    @Test
    void returnsServiceUnavailableUntilRagAdapterIsConnected() throws Exception {
        mockMvc.perform(post("/api/v1/rag/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"What is the product policy?"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type")
                        .value("https://api.newideacase.local/problems/service-unavailable"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("not connected")));
    }
}
