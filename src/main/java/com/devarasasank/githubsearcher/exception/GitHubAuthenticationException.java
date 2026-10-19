package com.devarasasank.githubsearcher.exception;

/**
 * GitHub answered HTTP 401, meaning the optional configured token was
 * rejected. The token value itself is never included in the message.
 */
public class GitHubAuthenticationException extends GitHubApiException {

    public GitHubAuthenticationException(String message) {
        super(message);
    }
}
