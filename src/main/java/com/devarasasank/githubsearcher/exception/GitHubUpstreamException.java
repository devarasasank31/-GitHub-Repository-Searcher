package com.devarasasank.githubsearcher.exception;

/**
 * GitHub answered with an HTTP error status or a malformed payload.
 * Server errors (5xx) are retried a bounded number of times; client
 * errors are not.
 */
public class GitHubUpstreamException extends GitHubApiException {

    private final int statusCode;

    public GitHubUpstreamException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public GitHubUpstreamException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * @return upstream HTTP status, or {@code -1} when the payload could not
     * be interpreted at all
     */
    public int statusCode() {
        return statusCode;
    }
}
