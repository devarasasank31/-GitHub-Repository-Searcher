package com.devarasasank.githubsearcher.exception;

public abstract class GitHubApiException extends RuntimeException {

    protected GitHubApiException(String message) {
        super(message);
    }

    protected GitHubApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
