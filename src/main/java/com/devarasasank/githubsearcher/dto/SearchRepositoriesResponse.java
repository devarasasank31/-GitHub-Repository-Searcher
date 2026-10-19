package com.devarasasank.githubsearcher.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response payload for {@code POST /api/github/search}.
 *
 * @param totalCount   number of repositories matched by the query
 * @param repositories matching repositories in the order GitHub returned them
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchRepositoriesResponse(
        int totalCount,
        List<RepositoryResponse> repositories
) {
}
