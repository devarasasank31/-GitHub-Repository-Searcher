package com.devarasasank.githubsearcher.exception;

/**
 * GitHub rejected the call because the API rate limit was exhausted.
 * Mapped to HTTP 429 for the API caller.
 */
public class GitHubRateLimitException extends GitHubApiException {

    private final Long retryAfterSeconds;

    public GitHubRateLimitException(Long retryAfterSeconds) {
        super(buildMessage(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private static String buildMessage(Long retryAfterSeconds) {
        if (retryAfterSeconds != null) {
            return "GitHub API rate limit exceeded; retry after " + retryAfterSeconds + " seconds";
        }
        return "GitHub API rate limit exceeded";
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
