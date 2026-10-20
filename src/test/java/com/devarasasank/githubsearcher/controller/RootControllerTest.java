package com.devarasasank.githubsearcher.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RootController.class)
class RootControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUpStatusAndMessage() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.message").value("GitHub Repository Searcher API is running"));
    }

    @Test
    void rootEndpointReturns200WithLinks() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("GitHub Repository Searcher API"))
                .andExpect(jsonPath("$.links.health").value("/health"))
                .andExpect(jsonPath("$.links.swaggerUi").value("/swagger-ui.html"))
                .andExpect(jsonPath("$.links.apiDocs").value("/v3/api-docs"));
    }

    @Test
    void unknownRouteReturns404InsteadOf500() throws Exception {
        mockMvc.perform(get("/definitely-not-a-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No resource found for /definitely-not-a-route"));
    }

    @Test
    void wrongMethodReturns405InsteadOf500() throws Exception {
        mockMvc.perform(delete("/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
