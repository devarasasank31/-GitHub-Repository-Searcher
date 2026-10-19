package com.devarasasank.githubsearcher.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Internal view of the GitHub repository search response envelope.
 */
public record GitHubSearchResponse(
        @JsonProperty("total_count") Integer totalCount,
        @JsonProperty("incomplete_results") Boolean incompleteResults,
        List<GitHubRepositoryItem> items
) {
}
