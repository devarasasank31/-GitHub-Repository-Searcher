package com.devarasasank.githubsearcher.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight informational endpoints served from the application root.
 */
@RestController
public class RootController {

    @Operation(summary = "API root",
            description = "Basic service information with links to the health endpoint and Swagger UI.")
    @GetMapping("/")
    public RootResponse root() {
        return new RootResponse(
                "GitHub Repository Searcher API",
                Map.of(
                        "health", "/health",
                        "swaggerUi", "/swagger-ui.html",
                        "apiDocs", "/v3/api-docs"));
    }

    @Operation(summary = "Health check",
            description = "Simple liveness payload for the API process.")
    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "GitHub Repository Searcher API is running");
    }

    public record RootResponse(String message, Map<String, String> links) {
    }

    public record HealthResponse(String status, String message) {
    }
}
