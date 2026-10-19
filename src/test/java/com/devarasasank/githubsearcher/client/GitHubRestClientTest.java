package com.devarasasank.githubsearcher.client;

import com.devarasasank.githubsearcher.config.GitHubRestClientConfig;
import com.devarasasank.githubsearcher.config.GithubApiProperties;
import com.devarasasank.githubsearcher.dto.RepositorySort;
import com.devarasasank.githubsearcher.exception.GitHubAuthenticationException;
import com.devarasasank.githubsearcher.exception.GitHubRateLimitException;
import com.devarasasank.githubsearcher.exception.GitHubUpstreamException;
import com.devarasasank.githubsearcher.exception.GitHubUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubRestClientTest {

    private static final String SEARCH_PATH = "/search/repositories";

    private static final String REPOSITORIES_JSON = """
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

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    private GithubApiProperties properties(int retryMaxAttempts) {
        return properties(retryMaxAttempts, Duration.ofSeconds(5), Duration.ofSeconds(5), null);
    }

    private GithubApiProperties properties(int retryMaxAttempts, Duration connectTimeout,
                                           Duration readTimeout, String token) {
        return new GithubApiProperties(
                wireMock.baseUrl(), connectTimeout, readTimeout, token, 30, retryMaxAttempts, Duration.ofMillis(10));
    }

    private GitHubRestClient client(GithubApiProperties properties) {
        return new GitHubRestClient(GitHubRestClientConfig.buildRestClient(properties), properties);
    }

    @Test
    void mapsRepositoryItemsFromGitHubResponse() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .withQueryParam("q", equalTo("spring boot"))
                .withQueryParam("sort", equalTo("stars"))
                .withQueryParam("order", equalTo("desc"))
                .withQueryParam("per_page", equalTo("30"))
                .willReturn(okJson(REPOSITORIES_JSON)));

        List<GitHubRepositoryItem> items =
                client(properties(1)).searchRepositories("spring boot", null, RepositorySort.STARS, 30);

        assertThat(items).hasSize(1);
        GitHubRepositoryItem item = items.get(0);
        assertThat(item.id()).isEqualTo(1296269L);
        assertThat(item.name()).isEqualTo("Hello-World");
        assertThat(item.description()).isEqualTo("My first repository on GitHub!");
        assertThat(item.owner().login()).isEqualTo("octocat");
        assertThat(item.language()).isEqualTo("C");
        assertThat(item.stargazersCount()).isEqualTo(2543);
        assertThat(item.forksCount()).isEqualTo(1251);
        assertThat(item.updatedAt().toInstant()).isEqualTo(Instant.parse("2024-01-01T12:00:00Z"));
    }

    @ParameterizedTest
    @CsvSource({"STARS,stars", "FORKS,forks", "UPDATED,updated"})
    void mapsApplicationSortToGitHubSortParameter(RepositorySort sort, String expectedParameter) {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(okJson(REPOSITORIES_JSON)));

        client(properties(1)).searchRepositories("spring", null, sort, 10);

        wireMock.verify(getRequestedFor(urlPathEqualTo(SEARCH_PATH))
                .withQueryParam("sort", equalTo(expectedParameter))
                .withQueryParam("order", equalTo("desc")));
    }

    @Test
    void appendsLanguageQualifierToQuery() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .withQueryParam("q", equalTo("spring boot language:Java"))
                .willReturn(okJson(REPOSITORIES_JSON)));

        client(properties(1)).searchRepositories("spring boot", "Java", RepositorySort.STARS, 10);

        wireMock.verify(getRequestedFor(urlPathEqualTo(SEARCH_PATH)));
    }

    @Test
    void sendsAuthorizationHeaderWhenTokenConfigured() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .willReturn(okJson(REPOSITORIES_JSON)));

        client(properties(1, Duration.ofSeconds(2), Duration.ofSeconds(2), "test-token"))
                .searchRepositories("spring", null, RepositorySort.STARS, 10);

        wireMock.verify(getRequestedFor(urlPathEqualTo(SEARCH_PATH))
                .withHeader("Authorization", equalTo("Bearer test-token")));
    }

    @Test
    void omitsAuthorizationHeaderWhenNoTokenConfigured() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(okJson(REPOSITORIES_JSON)));

        client(properties(1)).searchRepositories("spring", null, RepositorySort.STARS, 10);

        wireMock.verify(getRequestedFor(urlPathEqualTo(SEARCH_PATH))
                .withoutHeader("Authorization"));
    }

    @Test
    void emptyResultListReturnsEmpty() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(okJson("{\"total_count\": 0, \"incomplete_results\": false, \"items\": []}")));

        List<GitHubRepositoryItem> items =
                client(properties(1)).searchRepositories("no-such-repo-xyz", null, RepositorySort.STARS, 10);

        assertThat(items).isEmpty();
    }

    @Test
    void nullItemsFieldReturnsEmptyList() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(okJson("{\"total_count\": 0, \"incomplete_results\": true, \"items\": null}")));

        List<GitHubRepositoryItem> items =
                client(properties(1)).searchRepositories("spring", null, RepositorySort.STARS, 10);

        assertThat(items).isEmpty();
    }

    @Test
    void malformedResponseThrowsUpstreamErrorWithoutRetry() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html>oops</html>")));

        assertThatThrownBy(() -> client(properties(2)).searchRepositories("spring", null, RepositorySort.STARS, 10))
                .isInstanceOf(GitHubUpstreamException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(SEARCH_PATH)));
    }

    @Test
    void serverErrorRetriesBoundedTimesThenThrows() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(500).withBody("{}")));

        assertThatThrownBy(() -> client(properties(2)).searchRepositories("spring", null, RepositorySort.STARS, 10))
                .isInstanceOf(GitHubUpstreamException.class)
                .hasMessageContaining("500");

        wireMock.verify(2, getRequestedFor(urlPathEqualTo(SEARCH_PATH)));
    }

    @Test
    void serverErrorRecoversOnRetry() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(500).withBody("{}"))
                .willSetStateTo("second attempt"));
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .inScenario("retry")
                .whenScenarioStateIs("second attempt")
                .willReturn(okJson(REPOSITORIES_JSON)));

        List<GitHubRepositoryItem> items =
                client(properties(2)).searchRepositories("spring", null, RepositorySort.STARS, 10);

        assertThat(items).hasSize(1);
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(SEARCH_PATH)));
    }

    @Test
    void http429IsMappedToRateLimitWithRetryAfter() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "30")
                        .withBody("{}")));

        assertThatThrownBy(() -> client(properties(2)).searchRepositories("spring", null, RepositorySort.STARS, 10))
                .isInstanceOf(GitHubRateLimitException.class)
                .hasMessageContaining("rate limit")
                .hasMessageContaining("30");

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(SEARCH_PATH)));
    }

    @Test
    void forbiddenWithExhaustedQuotaIsMappedToRateLimit() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("X-RateLimit-Remaining", "0")
                        .withBody("{}")));

        assertThatThrownBy(() -> client(properties(2)).searchRepositories("spring", null, RepositorySort.STARS, 10))
                .isInstanceOf(GitHubRateLimitException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(SEARCH_PATH)));
    }

    @Test
    void forbiddenWithoutRateLimitIsUpstreamErrorAndNotRetried() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(403).withBody("{}")));

        assertThatThrownBy(() -> client(properties(2)).searchRepositories("spring", null, RepositorySort.STARS, 10))
                .isInstanceOf(GitHubUpstreamException.class)
                .satisfies(e -> assertThat(((GitHubUpstreamException) e).statusCode()).isEqualTo(403));

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(SEARCH_PATH)));
    }

    @Test
    void unauthorizedIsMappedToAuthenticationError() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(401).withBody("{}")));

        assertThatThrownBy(() -> client(properties(2)).searchRepositories("spring", null, RepositorySort.STARS, 10))
                .isInstanceOf(GitHubAuthenticationException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(SEARCH_PATH)));
    }

    @Test
    void notFoundIsMappedToUpstreamError() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(404).withBody("{}")));

        assertThatThrownBy(() -> client(properties(2)).searchRepositories("spring", null, RepositorySort.STARS, 10))
                .isInstanceOf(GitHubUpstreamException.class)
                .satisfies(e -> assertThat(((GitHubUpstreamException) e).statusCode()).isEqualTo(404));

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(SEARCH_PATH)));
    }

    @Test
    void networkFailureRetriesThenThrowsUnavailable() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client(properties(2)).searchRepositories("spring", null, RepositorySort.STARS, 10))
                .isInstanceOf(GitHubUnavailableException.class);

        wireMock.verify(2, getRequestedFor(urlPathEqualTo(SEARCH_PATH)));
    }

    @Test
    void readTimeoutThrowsUnavailable() {
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(200).withFixedDelay(3000).withBody("{}")));

        GitHubRestClient slowClient = client(properties(2, Duration.ofMillis(500), Duration.ofMillis(300), null));

        assertThatThrownBy(() -> slowClient.searchRepositories("spring", null, RepositorySort.STARS, 10))
                .isInstanceOf(GitHubUnavailableException.class);
    }

    @Test
    void duplicateSearchResultsArePreservedForPersistenceLayer() {
        String duplicateItemsJson = """
                {
                  "total_count": 2,
                  "items": [
                    { "id": 1, "name": "a", "owner": { "login": "x" }, "stargazers_count": 1, "forks_count": 0,
                      "updated_at": "2024-01-01T00:00:00Z" },
                    { "id": 1, "name": "a", "owner": { "login": "x" }, "stargazers_count": 2, "forks_count": 0,
                      "updated_at": "2024-02-01T00:00:00Z" }
                  ]
                }
                """;
        wireMock.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(okJson(duplicateItemsJson)));

        List<GitHubRepositoryItem> items =
                client(properties(1)).searchRepositories("a", null, RepositorySort.STARS, 10);

        assertThat(items).hasSize(2);
        assertThat(items).allSatisfy(item -> assertThat(item.id()).isEqualTo(1L));
    }
}
