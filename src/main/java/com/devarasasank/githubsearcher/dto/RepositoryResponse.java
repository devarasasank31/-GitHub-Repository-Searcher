package com.devarasasank.githubsearcher.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * A single repository as exposed by the public API. Field names mirror the
 * persisted entity so callers see a stable contract independent of GitHub's
 * payload shape.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepositoryResponse(
        Long id,
        String name,
        String description,
        String owner,
        String language,
        long stars,
        long forks,
        OffsetDateTime lastUpdated
) {
}
