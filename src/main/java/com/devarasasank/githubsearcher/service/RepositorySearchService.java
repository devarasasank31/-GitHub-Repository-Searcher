package com.devarasasank.githubsearcher.service;

import com.devarasasank.githubsearcher.client.GitHubApiClient;
import com.devarasasank.githubsearcher.client.GitHubRepositoryItem;
import com.devarasasank.githubsearcher.dto.RepositoryResponse;
import com.devarasasank.githubsearcher.dto.RepositorySort;
import com.devarasasank.githubsearcher.dto.SearchRepositoriesRequest;
import com.devarasasank.githubsearcher.dto.SearchRepositoriesResponse;
import com.devarasasank.githubsearcher.entity.GitHubRepositoryEntity;
import com.devarasasank.githubsearcher.mapper.RepositoryMapper;
import com.devarasasank.githubsearcher.repository.GitHubRepositoryUpserter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates a repository search: validates and normalises the request,
 * delegates to the GitHub client, persists the results, and shapes the
 * public response.
 *
 * <p>Persistence is best-effort: a storage failure is logged but does not
 * fail an otherwise successful search, so callers still receive results when
 * the database is briefly unavailable.</p>
 */
@Service
public class RepositorySearchService {

    private static final Logger log = LoggerFactory.getLogger(RepositorySearchService.class);

    private final GitHubApiClient gitHubApiClient;
    private final GitHubRepositoryUpserter upserter;
    private final RepositoryMapper mapper;

    public RepositorySearchService(GitHubApiClient gitHubApiClient,
                                   GitHubRepositoryUpserter upserter,
                                   RepositoryMapper mapper) {
        this.gitHubApiClient = gitHubApiClient;
        this.upserter = upserter;
        this.mapper = mapper;
    }

    /**
     * Executes a repository search and persists the results.
     *
     * @param request validated search request
     * @return the matched repositories
     */
    public SearchRepositoriesResponse search(SearchRepositoriesRequest request) {
        RepositorySort sort = RepositorySort.fromParam(request.sort());
        int maxResults = request.effectiveMaxResults();

        List<GitHubRepositoryItem> items =
                gitHubApiClient.searchRepositories(request.query(), request.language(), sort, maxResults);

        persistQuietly(items);

        List<RepositoryResponse> repositories = mapper.toResponseList(items);
        return new SearchRepositoriesResponse(repositories.size(), repositories);
    }

    private void persistQuietly(List<GitHubRepositoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        try {
            List<GitHubRepositoryEntity> entities = items.stream()
                    .filter(item -> item != null && item.id() != null)
                    .map(mapper::toEntity)
                    .toList();
            upserter.upsertAll(entities);
        } catch (RuntimeException e) {
            log.warn("Failed to persist search results, continuing without storage: {}", e.getMessage());
        }
    }
}
