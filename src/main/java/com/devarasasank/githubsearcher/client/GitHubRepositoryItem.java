package com.devarasasank.githubsearcher.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * Internal view of a repository returned by the GitHub search API.
 * Only the fields this application needs are mapped; raw GitHub payloads
 * never leak past the client layer.
 */
public record GitHubRepositoryItem(
        Long id,
        String name,
        String description,
        GitHubOwner owner,
        String language,
        @JsonProperty("stargazers_count") Integer stargazersCount,
        @JsonProperty("forks_count") Integer forksCount,
        @JsonProperty("updated_at") OffsetDateTime updatedAt
) {
}
