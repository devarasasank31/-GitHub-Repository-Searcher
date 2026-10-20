package com.devarasasank.githubsearcher.client;

import com.devarasasank.githubsearcher.config.GithubApiProperties;
import com.devarasasank.githubsearcher.dto.RepositorySort;
import com.devarasasank.githubsearcher.exception.GitHubAuthenticationException;
import com.devarasasank.githubsearcher.exception.GitHubRateLimitException;
import com.devarasasank.githubsearcher.exception.GitHubUpstreamException;
import com.devarasasank.githubsearcher.exception.GitHubUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * {@link GitHubApiClient} implementation backed by Spring's {@link RestClient}.
 *
 * <p>Transient failures (network errors, timeouts and upstream 5xx) are
 * retried a bounded number of times. Client errors and rate-limit
 * responses are never retried.</p>
 */
@Component
public class GitHubRestClient implements GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubRestClient.class);

    private final RestClient restClient;
    private final GithubApiProperties properties;

    public GitHubRestClient(RestClient githubRestClient, GithubApiProperties properties) {
        this.restClient = githubRestClient;
        this.properties = properties;
    }

    @Override
    public List<GitHubRepositoryItem> searchRepositories(String query, String language, RepositorySort sort, int perPage) {
        GitHubSearchResponse response = executeWithRetry(query, language, sort, perPage);
        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items();
    }

    private GitHubSearchResponse executeWithRetry(String query, String language, RepositorySort sort, int perPage) {
        for (int attempt = 1; ; attempt++) {
            try {
                return executeOnce(query, language, sort, perPage);
            } catch (GitHubUnavailableException | GitHubUpstreamException e) {
                if (!isRetryable(e) || attempt >= properties.retryMaxAttempts()) {
                    throw e;
                }
                log.warn("GitHub API call failed (attempt {}/{}): {}",
                        attempt, properties.retryMaxAttempts(), e.getMessage());
                pauseBeforeRetry();
            }
        }
    }

    private static boolean isRetryable(RuntimeException exception) {
        if (exception instanceof GitHubUnavailableException) {
            return true;
        }
        if (exception instanceof GitHubUpstreamException upstream) {
            return upstream.statusCode() >= 500;
        }
        return false;
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(properties.retryDelay().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubUnavailableException("Interrupted while waiting to retry GitHub API call", e);
        }
    }

    private GitHubSearchResponse executeOnce(String query, String language, RepositorySort sort, int perPage) {
        String searchQuery = buildSearchQuery(query, language);
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search/repositories")
                            .queryParam("q", searchQuery)
                            .queryParam("sort", sort.gitHubParameter())
                            .queryParam("order", "desc")
                            .queryParam("per_page", perPage)
                            .build())
                    .retrieve()
                    .body(GitHubSearchResponse.class);
        } catch (HttpClientErrorException e) {
            throw translateClientError(e);
        } catch (HttpServerErrorException e) {
            throw new GitHubUpstreamException(
                    "GitHub API returned a server error (HTTP " + e.getStatusCode().value() + ")",
                    e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new GitHubUnavailableException("GitHub API could not be reached");
        } catch (CancellationException e) {
            // A read timeout can surface as a cancelled request instead of a
            // socket timeout (race inside the JDK HTTP client's timeout
            // handler). Translate it like any other timeout so it is retried
            // and reported as 503 rather than leaking out as a 500.
            throw new GitHubUnavailableException("GitHub API could not be reached");
        } catch (RestClientException e) {
            throw new GitHubUpstreamException("GitHub API returned an unexpected or malformed response", -1);
        }
    }

    private static String buildSearchQuery(String query, String language) {
        if (language == null || language.isBlank()) {
            return query;
        }
        return query + " language:" + language.trim();
    }

    private static RuntimeException translateClientError(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        HttpHeaders headers = e.getResponseHeaders() != null ? e.getResponseHeaders() : HttpHeaders.EMPTY;
        if (status == 429 || (status == 403 && "0".equals(headers.getFirst("X-RateLimit-Remaining")))) {
            return new GitHubRateLimitException(parseRetryAfterSeconds(headers));
        }
        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return new GitHubAuthenticationException("GitHub rejected the configured credentials (HTTP 401)");
        }
        return new GitHubUpstreamException("GitHub API request was rejected (HTTP " + status + ")", status);
    }

    private static Long parseRetryAfterSeconds(HttpHeaders headers) {
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null) {
            return null;
        }
        try {
            return Long.parseLong(retryAfter.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
