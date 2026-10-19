package com.devarasasank.githubsearcher.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@code POST /api/github/search}.
 *
 * <p>Validation runs before the controller method is entered; any violation
 * is surfaced as HTTP 400 by the global exception handler.</p>
 */
public record SearchRepositoriesRequest(
        @NotBlank(message = "query must not be blank")
        @Size(max = 256, message = "query must be at most 256 characters")
        String query,

        @Size(max = 100, message = "language must be at most 100 characters")
        String language,

        String sort,

        @Min(value = 1, message = "maxResults must be between 1 and 100")
        @Max(value = 100, message = "maxResults must be between 1 and 100")
        Integer maxResults
) {

    /**
     * Effective page size: the caller's value when present, otherwise the
     * configured default of 30.
     */
    public int effectiveMaxResults() {
        return maxResults == null ? 30 : maxResults;
    }
}
