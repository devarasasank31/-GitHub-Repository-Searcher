package com.devarasasank.githubsearcher.dto;

import java.time.Instant;

/**
 * Standard error body returned for every failed request. The shape is stable
 * across all error types so clients can parse it uniformly.
 *
 * @param timestamp  when the error occurred
 * @param status     HTTP status code
 * @param error      HTTP reason phrase
 * @param message    human-readable description safe to show to callers
 * @param path       request path that produced the error
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
