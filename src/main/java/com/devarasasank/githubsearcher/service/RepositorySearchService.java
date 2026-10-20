package com.devarasasank.githubsearcher.service;

import com.devarasasank.githubsearcher.client.GitHubApiClient;
import com.devarasasank.githubsearcher.client.GitHubRepositoryItem;
import com.devarasasank.githubsearcher.dto.RepositoryResponse;
import com.devarasasank.githubsearcher.dto.RepositorySort;
import com.devarasasank.githubsearcher.dto.SearchRepositoriesRequest;
import com.devarasasank.githubsearcher.dto.SearchRepositoriesResponse;
import com.devarasasank.githubsearcher.entity.GitHubRepositoryEntity;
import com.devarasasank.githubsearcher.exception.InvalidSortException;
import com.devarasasank.githubsearcher.mapper.RepositoryMapper;
import com.devarasasank.githubsearcher.repository.GitHubRepositoryRepository;
import com.devarasasank.githubsearcher.repository.GitHubRepositoryUpserter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

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
    private final GitHubRepositoryRepository repository;
    private final RepositoryMapper mapper;

    public RepositorySearchService(GitHubApiClient gitHubApiClient,
                                   GitHubRepositoryUpserter upserter,
                                   GitHubRepositoryRepository repository,
                                   RepositoryMapper mapper) {
        this.gitHubApiClient = gitHubApiClient;
        this.upserter = upserter;
        this.repository = repository;
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

    /**
     * Returns repositories already persisted in PostgreSQL, optionally
     * filtered by language and minimum star count. Never calls the GitHub
     * API. Results are ordered descending by the requested sort field
     * (stars by default) with deterministic tie-breaking — stars descending,
     * then id ascending — so rows sharing the same primary value always come
     * back in the same order.
     *
     * @param language  optional language filter, case-insensitive
     * @param minStars  optional minimum star count
     * @param sortParam one of {@code stars}, {@code forks}, {@code updated}
     * @return the stored repositories matching the filters
     * @throws InvalidSortException for an unsupported sort value
     */
    public SearchRepositoriesResponse findStored(String language, Long minStars, String sortParam) {
        RepositorySort sort = RepositorySort.fromParam(sortParam);
        String languageFilter = (language == null || language.isBlank())
                ? null
                : language.trim().toLowerCase(Locale.ROOT);

        List<GitHubRepositoryEntity> entities = repository.findByFilters(
                languageFilter, minStars, sortOrder(sort));

        List<RepositoryResponse> repositories = entities.stream()
                .map(mapper::toResponse)
                .toList();
        return new SearchRepositoriesResponse(repositories.size(), repositories);
    }

    private static String sortProperty(RepositorySort sort) {
        return switch (sort) {
            case STARS -> "stars";
            case FORKS -> "forks";
            case UPDATED -> "lastUpdated";
        };
    }

    /**
     * Builds a deterministic total order: the requested field descending,
     * then stars descending, then id ascending as the final tie-breaker so
     * equal primary (and equal secondary) values never shuffle between
     * requests.
     */
    private static Sort sortOrder(RepositorySort sort) {
        Sort.Order primary = Sort.Order.desc(sortProperty(sort));
        if (primary.getProperty().equals("stars")) {
            return Sort.by(primary, Sort.Order.asc("id"));
        }
        return Sort.by(primary, Sort.Order.desc("stars"), Sort.Order.asc("id"));
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
