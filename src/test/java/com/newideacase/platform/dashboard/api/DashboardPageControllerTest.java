package com.newideacase.platform.dashboard.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DashboardPageControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new DashboardPageController())
            .build();

    @Test
    void forwardsDashboardRouteToStaticPage() throws Exception {
        mockMvc.perform(get("/dashboard/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/dashboard/index.html"));
    }
}
