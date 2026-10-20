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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    private ResponseEntity<String> getFrom(String path) {
        return restTemplate.getForEntity(path, String.class);
    }

    private void insertRepository(long id, String name, String language, long stars, long forks,
                                  OffsetDateTime lastUpdated) {
        jdbcTemplate.update("""
                        INSERT INTO github_repositories
                            (id, name, description, owner, language, stars, forks, last_updated, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                        """,
                id, name, "description of " + name, "owner" + id, language, stars, forks, lastUpdated);
    }

    private void seedThreeRepositories() {
        insertRepository(1L, "alpha", "Java", 100, 10,
                OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        insertRepository(2L, "beta", "Java", 500, 50,
                OffsetDateTime.of(2024, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        insertRepository(3L, "gamma", "Python", 50, 70,
                OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return MAPPER.readTree(response.getBody());
    }

    private static List<String> names(JsonNode json) {
        List<String> names = new ArrayList<>();
        json.get("repositories").forEach(repo -> names.add(repo.get("name").asText()));
        return names;
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

    @Test
    void getRepositoriesReturnsStoredRowsSortedByStarsDescending() throws Exception {
        seedThreeRepositories();

        ResponseEntity<String> response = getFrom("/api/github/repositories");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parse(response);
        assertThat(json.get("totalCount").asInt()).isEqualTo(3);
        assertThat(names(json)).containsExactly("beta", "alpha", "gamma");
        assertThat(json.get("repositories").get(0).get("owner").asText()).isEqualTo("owner2");
        wireMock.verify(0, getRequestedForAnySearch());
    }

    @Test
    void getRepositoriesFiltersByLanguageCaseInsensitivelyAndMinStars() throws Exception {
        seedThreeRepositories();

        JsonNode byLanguage = parse(getFrom("/api/github/repositories?language=java"));
        assertThat(names(byLanguage)).containsExactly("beta", "alpha");

        JsonNode filtered = parse(getFrom("/api/github/repositories?language=java&minStars=200"));
        assertThat(filtered.get("totalCount").asInt()).isEqualTo(1);
        assertThat(names(filtered)).containsExactly("beta");

        JsonNode noneMatch = parse(getFrom("/api/github/repositories?language=Rust&minStars=1000"));
        assertThat(noneMatch.get("totalCount").asInt()).isZero();
        assertThat(noneMatch.get("repositories").isEmpty()).isTrue();
        wireMock.verify(0, getRequestedForAnySearch());
    }

    @Test
    void getRepositoriesSortsByForksAndUpdated() throws Exception {
        seedThreeRepositories();

        JsonNode byForks = parse(getFrom("/api/github/repositories?sort=forks"));
        assertThat(names(byForks)).containsExactly("gamma", "beta", "alpha");

        JsonNode byUpdated = parse(getFrom("/api/github/repositories?sort=updated"));
        assertThat(names(byUpdated)).containsExactly("gamma", "beta", "alpha");
    }

    @Test
    void getRepositoriesOrdersEntireSequenceDeterministicallyForEverySort() throws Exception {
        // Ties on the primary and secondary sort keys: order must fall through
        // to stars DESC and then id ASC, never shuffle between requests.
        insertRepository(1L, "alpha", "Java", 5L, 10L,
                OffsetDateTime.of(2024, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC));
        insertRepository(2L, "beta", "Java", 9L, 10L,
                OffsetDateTime.of(2024, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        insertRepository(3L, "gamma", "Java", 9L, 10L,
                OffsetDateTime.of(2024, 2, 10, 0, 0, 0, 0, ZoneOffset.UTC));
        insertRepository(4L, "delta", "Python", 100L, 7L,
                OffsetDateTime.of(2023, 12, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        insertRepository(5L, "epsilon", "Python", 1L, 12L,
                OffsetDateTime.of(2023, 11, 1, 0, 0, 0, 0, ZoneOffset.UTC));

        JsonNode byStars = parse(getFrom("/api/github/repositories?sort=stars"));
        assertThat(names(byStars))
                .containsExactly("delta", "beta", "gamma", "alpha", "epsilon");

        JsonNode byForks = parse(getFrom("/api/github/repositories?sort=forks"));
        assertThat(names(byForks))
                .containsExactly("epsilon", "beta", "gamma", "alpha", "delta");

        JsonNode byUpdated = parse(getFrom("/api/github/repositories?sort=updated"));
        assertThat(names(byUpdated))
                .containsExactly("gamma", "beta", "alpha", "delta", "epsilon");

        JsonNode defaultSort = parse(getFrom("/api/github/repositories"));
        assertThat(names(defaultSort))
                .containsExactly("delta", "beta", "gamma", "alpha", "epsilon");

        JsonNode filtered = parse(getFrom("/api/github/repositories?language=Java&sort=forks"));
        assertThat(names(filtered)).containsExactly("beta", "gamma", "alpha");

        JsonNode filteredStars = parse(getFrom("/api/github/repositories?language=java&minStars=9&sort=stars"));
        assertThat(names(filteredStars)).containsExactly("beta", "gamma");

        JsonNode minStarsOnly = parse(getFrom("/api/github/repositories?minStars=9&sort=stars"));
        assertThat(names(minStarsOnly)).containsExactly("delta", "beta", "gamma");
    }

    @Test
    void getRepositoriesSortUpdatedOrdersByParsedTimestampsNotStarsOrForks() throws Exception {
        // Three rows whose stars order, forks order and timestamp order are
        // three different permutations: aaa leads stars, bbb leads forks,
        // ccc has the newest timestamp but the fewest stars and forks.
        insertRepository(1L, "aaa-most-stars", "Java", 900L, 1L,
                OffsetDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        insertRepository(2L, "bbb-most-forks", "Java", 1L, 900L,
                OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        insertRepository(3L, "ccc-newest", "Java", 50L, 50L,
                OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC));

        JsonNode byUpdated = parse(getFrom("/api/github/repositories?sort=updated"));
        assertThat(names(byUpdated)).containsExactly("ccc-newest", "aaa-most-stars", "bbb-most-forks");

        // Assert on the parsed timestamps themselves, descending.
        List<Instant> timestamps = new ArrayList<>();
        byUpdated.get("repositories").forEach(repo ->
                timestamps.add(Instant.parse(repo.get("lastUpdated").asText())));
        assertThat(timestamps).hasSize(3);
        assertThat(timestamps).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(timestamps.get(0)).isAfter(timestamps.get(1));
        assertThat(timestamps.get(1)).isAfter(timestamps.get(2));

        // The other sort options must each produce their own different order,
        // proving sort=updated does not accidentally sort by stars or forks.
        JsonNode byStars = parse(getFrom("/api/github/repositories?sort=stars"));
        assertThat(names(byStars)).containsExactly("aaa-most-stars", "ccc-newest", "bbb-most-forks");

        JsonNode byForks = parse(getFrom("/api/github/repositories?sort=forks"));
        assertThat(names(byForks)).containsExactly("bbb-most-forks", "ccc-newest", "aaa-most-stars");

        // Filters still apply together with sort=updated.
        JsonNode filtered = parse(getFrom("/api/github/repositories?language=java&minStars=40&sort=updated"));
        assertThat(names(filtered)).containsExactly("ccc-newest", "aaa-most-stars");
    }

    @Test
    void searchThenGetUpdatedSortOrdersByTimestampsWrittenThroughFullPipeline() throws Exception {
        // Production write path: GitHub JSON -> Jackson -> mapper -> batched
        // upsert -> PostgreSQL -> GET. The previous ordering tests seeded the
        // database directly; this one goes through POST /search like Render.
        String firstPage = """
                {
                  "total_count": 3,
                  "incomplete_results": false,
                  "items": [
                    {"id": 101, "name": "geopulse", "description": "d", "language": "Java",
                     "stargazers_count": 100, "forks_count": 10, "updated_at": "2026-10-20T09:46:25Z",
                     "owner": {"login": "octocat", "id": 1}},
                    {"id": 102, "name": "nightwatch", "description": "d", "language": "Java",
                     "stargazers_count": 500, "forks_count": 50, "updated_at": "2026-10-19T23:59:59Z",
                     "owner": {"login": "octocat", "id": 1}},
                    {"id": 103, "name": "solstice", "description": "d", "language": "Java",
                     "stargazers_count": 300, "forks_count": 30, "updated_at": "2026-10-18T12:00:00Z",
                     "owner": {"login": "octocat", "id": 1}}
                  ]
                }
                """;
        wireMock.stubFor(get(urlPathEqualTo("/search/repositories")).willReturn(okJson(firstPage)));
        post("{\"query\": \"observability\", \"maxResults\": 30}");

        // Lifecycle: geopulse receives a newer commit and is re-indexed.
        String secondPage = """
                {
                  "total_count": 1,
                  "incomplete_results": false,
                  "items": [
                    {"id": 101, "name": "geopulse", "description": "d", "language": "Java",
                     "stargazers_count": 100, "forks_count": 10, "updated_at": "2026-10-21T10:00:00Z",
                     "owner": {"login": "octocat", "id": 1}}
                  ]
                }
                """;
        wireMock.resetAll();
        wireMock.stubFor(get(urlPathEqualTo("/search/repositories")).willReturn(okJson(secondPage)));
        post("{\"query\": \"observability\", \"maxResults\": 30}");

        JsonNode byUpdated = parse(getFrom("/api/github/repositories?sort=updated"));
        assertThat(names(byUpdated)).containsExactly("geopulse", "nightwatch", "solstice");

        // Every adjacent pair in the complete returned list must have
        // strictly descending parsed timestamps.
        List<Instant> timestamps = new ArrayList<>();
        byUpdated.get("repositories").forEach(repo ->
                timestamps.add(Instant.parse(repo.get("lastUpdated").asText())));
        assertThat(timestamps).hasSize(3);
        for (int i = 0; i < timestamps.size() - 1; i++) {
            assertThat(timestamps.get(i))
                    .as("adjacent pair %d and %d", i, i + 1)
                    .isAfter(timestamps.get(i + 1));
        }

        // The refreshed geopulse timestamp reached the database via the
        // upsert, not just the initial insert.
        OffsetDateTime storedGeopulse = jdbcTemplate.queryForObject(
                "SELECT last_updated FROM github_repositories WHERE id = ?", OffsetDateTime.class, 101L);
        assertThat(storedGeopulse.toInstant()).isEqualTo(Instant.parse("2026-10-21T10:00:00Z"));

        // Other sort options and filters still correct through the same pipeline.
        assertThat(names(parse(getFrom("/api/github/repositories?sort=stars"))))
                .containsExactly("nightwatch", "solstice", "geopulse");
        assertThat(names(parse(getFrom("/api/github/repositories?sort=forks"))))
                .containsExactly("nightwatch", "solstice", "geopulse");
        assertThat(names(parse(getFrom("/api/github/repositories?language=java&minStars=200&sort=updated"))))
                .containsExactly("nightwatch", "solstice");
    }

    @Test
    void getRepositoriesReturnsSameOrderAcrossRepeatedRequests() throws Exception {
        insertRepository(1L, "alpha", "Java", 5L, 10L,
                OffsetDateTime.of(2024, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC));
        insertRepository(2L, "beta", "Java", 9L, 10L,
                OffsetDateTime.of(2024, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        insertRepository(3L, "gamma", "Java", 9L, 10L,
                OffsetDateTime.of(2024, 2, 10, 0, 0, 0, 0, ZoneOffset.UTC));

        List<String> first = names(parse(getFrom("/api/github/repositories?sort=forks")));
        List<String> second = names(parse(getFrom("/api/github/repositories?sort=forks")));
        List<String> third = names(parse(getFrom("/api/github/repositories?sort=forks")));

        assertThat(first).containsExactly("beta", "gamma", "alpha");
        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
    }

    @Test
    void getRepositoriesReturnsEmptyListWhenDatabaseIsEmpty() throws Exception {
        ResponseEntity<String> response = getFrom("/api/github/repositories");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parse(response);
        assertThat(json.get("totalCount").asInt()).isZero();
        assertThat(json.get("repositories").isEmpty()).isTrue();
    }

    @Test
    void getRepositoriesInvalidSortReturns400() throws Exception {
        ResponseEntity<String> response = getFrom("/api/github/repositories?sort=popularity");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode json = parse(response);
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("message").asText())
                .isEqualTo("Unsupported sort value 'popularity'. Supported values: stars, forks, updated");
        wireMock.verify(0, getRequestedForAnySearch());
    }

    @Test
    void getRepositoriesInvalidMinStarsReturns400() throws Exception {
        ResponseEntity<String> response = getFrom("/api/github/repositories?minStars=abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode json = parse(response);
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("message").asText())
                .isEqualTo("Invalid value 'abc' for parameter 'minStars'");
    }

    @Test
    void searchResultsAreReadableThroughGetEndpoint() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/search/repositories"))
                .willReturn(okJson(GITHUB_RESPONSE)));

        post("{\"query\": \"spring boot\", \"maxResults\": 10}");

        ResponseEntity<String> response = getFrom("/api/github/repositories");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parse(response);
        assertThat(json.get("totalCount").asInt()).isEqualTo(1);
        assertThat(json.get("repositories").get(0).get("name").asText()).isEqualTo("Hello-World");
        assertThat(json.get("repositories").get(0).get("stars").asLong()).isEqualTo(2543L);
    }

    @Test
    void apiDocsDocumentGetRepositoriesEndpoint() throws Exception {
        ResponseEntity<String> response = getFrom("/v3/api-docs");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parse(response);
        JsonNode operations = json.get("paths").get("/api/github/repositories");
        assertThat(operations).isNotNull();
        assertThat(operations.has("get")).isTrue();
        assertThat(operations.get("get").get("summary").asText()).isEqualTo("List stored repositories");
    }

    private static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder getRequestedForAnySearch() {
        return getRequestedFor(urlPathEqualTo("/search/repositories"));
    }
}
