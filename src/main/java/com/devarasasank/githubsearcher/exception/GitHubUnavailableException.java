package com.devarasasank.githubsearcher.exception;

/**
 * The GitHub API could not be reached (connection failure or timeout).
 * Retried a bounded number of times, then surfaced as HTTP 503.
 */
public class GitHubUnavailableException extends GitHubApiException {

    public GitHubUnavailableException(String message) {
        super(message);
    }

    public GitHubUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
