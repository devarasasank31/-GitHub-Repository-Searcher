package com.devarasasank.githubsearcher.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test: HTTP request -> controller -> service ->
 * GitHub client (WireMock) -> PostgreSQL. The database runs in a
 * Testcontainers PostgreSQL container with Flyway migrations applied on
 * startup; the GitHub API is stubbed with WireMock on a dynamic port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class RepositorySearchIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @DynamicPropertySource
    static void githubApiProperties(DynamicPropertyRegistry registry) {
        registry.add("github.api.base-url", () -> wireMock.baseUrl());
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void reset() {
        wireMock.resetAll();
        jdbcTemplate.update("DELETE FROM github_repositories");
    }

    private static final String GITHUB_RESPONSE = """
            {
              "total_count": 1,
              "incomplete_results": false,
              "items": [
                {
                  "id": 1296269,
                  "name": "Hello-World",
                  "description": "My first repository on GitHub!",
                  "language": "C",
                  "stargazers_count": 2543,
                  "forks_count": 1251,
                  "updated_at": "2024-01-01T12:00:00Z",
                  "owner": { "login": "octocat", "id": 1 }
                }
              ]
            }
            """;

    private ResponseEntity<String> post(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/api/github/search", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return MAPPER.readTree(response.getBody());
    }

    @Test
    void searchReturnsResultsAndPersistsToDatabase() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/search/repositories"))
                .withQueryParam("q", equalTo("spring boot"))
                .willReturn(okJson(GITHUB_RESPONSE)));

        ResponseEntity<String> response = post(
                "{\"query\": \"spring boot\", \"language\": null, \"sort\": \"stars\", \"maxResults\": 30}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parse(response);
        assertThat(json.get("totalCount").asInt()).isEqualTo(1);
        assertThat(json.get("repositories").get(0).get("name").asText()).isEqualTo("Hello-World");
        assertThat(json.get("repositories").get(0).get("owner").asText()).isEqualTo("octocat");

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM github_repositories WHERE id = ?", Long.class, 1296269L);
        assertThat(count).isEqualTo(1L);

        String owner = jdbcTemplate.queryForObject(
                "SELECT owner FROM github_repositories WHERE id = ?", String.class, 1296269L);
        assertThat(owner).isEqualTo("octocat");
    }

    @Test
    void repeatedSearchUpsertsRatherThanDuplicates() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/search/repositories"))
                .willReturn(okJson(GITHUB_RESPONSE)));

        post("{\"query\": \"spring boot\", \"maxResults\": 10}");
        post("{\"query\": \"spring boot\", \"maxResults\": 10}");

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM github_repositories WHERE id = ?", Long.class, 1296269L);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void invalidSortReturns400WithoutCallingGitHub() {
        ResponseEntity<String> response = post("{\"query\": \"spring\", \"sort\": \"bogus\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        wireMock.verify(0, getRequestedForAnySearch());
    }

    @Test
    void rateLimitedResponseReturns429() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/search/repositories"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30").withBody("{}")));

        ResponseEntity<String> response = post("{\"query\": \"spring\", \"maxResults\": 10}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        JsonNode json = parse(response);
        assertThat(json.get("message").asText()).contains("rate limit");
    }

    @Test
    void upstreamServerErrorReturns502AfterRetries() {
        wireMock.stubFor(get(urlPathEqualTo("/search/repositories"))
                .willReturn(aResponse().withStatus(500).withBody("{}")));

        ResponseEntity<String> response = post("{\"query\": \"spring\", \"maxResults\": 10}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        wireMock.verify(2, getRequestedForAnySearch());
    }

    @Test
    void networkFaultReturns503() {
        wireMock.stubFor(get(urlPathEqualTo("/search/repositories"))
                .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        ResponseEntity<String> response = post("{\"query\": \"spring\", \"maxResults\": 10}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder getRequestedForAnySearch() {
        return getRequestedFor(urlPathEqualTo("/search/repositories"));
    }
}
