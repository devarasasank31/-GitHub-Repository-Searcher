package com.devarasasank.githubsearcher.client;

import com.devarasasank.githubsearcher.dto.RepositorySort;

import java.util.List;

/**
 * Outbound access to the GitHub repository search API.
 */
public interface GitHubApiClient {

    /**
     * Searches GitHub repositories.
     *
     * @param query   free-text repository search term, must not be blank
     * @param language optional programming-language filter
     * @param sort    sort order, translated to GitHub's parameter values
     * @param perPage page size, GitHub's maximum is 100
     * @return matching repositories; never {@code null}, empty when nothing matches
     */
    List<GitHubRepositoryItem> searchRepositories(String query, String language, RepositorySort sort, int perPage);
}
