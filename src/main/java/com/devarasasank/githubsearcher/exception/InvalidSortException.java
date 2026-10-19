package com.devarasasank.githubsearcher.exception;

/**
 * The caller supplied a sort value the API does not support.
 * Mapped to HTTP 400.
 */
public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String value) {
        super("Unsupported sort value '" + value + "'. Supported values: stars, forks, updated");
    }
}
