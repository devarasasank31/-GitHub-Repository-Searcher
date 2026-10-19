package com.devarasasank.githubsearcher.dto;

import com.devarasasank.githubsearcher.exception.InvalidSortException;

import java.util.Locale;

/**
 * Sort options accepted by the API, mapped to the parameter values the
 * GitHub repository search API understands.
 */
public enum RepositorySort {

    STARS("stars"),
    FORKS("forks"),
    UPDATED("updated");

    private final String gitHubParameter;

    RepositorySort(String gitHubParameter) {
        this.gitHubParameter = gitHubParameter;
    }

    public String gitHubParameter() {
        return gitHubParameter;
    }

    /**
     * Parses a caller-supplied sort value, case-insensitively.
     * {@code null} or blank falls back to the default ({@link #STARS}).
     *
     * @throws InvalidSortException for any other unsupported value
     */
    public static RepositorySort fromParam(String value) {
        if (value == null || value.isBlank()) {
            return STARS;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidSortException(value);
        }
    }
}
