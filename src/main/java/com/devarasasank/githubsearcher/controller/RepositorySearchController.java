package com.devarasasank.githubsearcher.controller;

import com.devarasasank.githubsearcher.dto.SearchRepositoriesRequest;
import com.devarasasank.githubsearcher.dto.SearchRepositoriesResponse;
import com.devarasasank.githubsearcher.service.RepositorySearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public API for searching GitHub repositories.
 */
@RestController
@RequestMapping("/api/github")
public class RepositorySearchController {

    private final RepositorySearchService searchService;

    public RepositorySearchController(RepositorySearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Searches GitHub repositories and persists the results.
     *
     * @return HTTP 200 with the matching repositories
     */
    @PostMapping("/search")
    public ResponseEntity<SearchRepositoriesResponse> search(
            @Valid @RequestBody SearchRepositoriesRequest request) {
        SearchRepositoriesResponse response = searchService.search(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns repositories previously saved to PostgreSQL by
     * {@code POST /api/github/search}. Does not call the GitHub API.
     *
     * @return HTTP 200 with the stored repositories
     */
    @Operation(summary = "List stored repositories",
            description = "Returns repositories persisted in PostgreSQL by previous searches. "
                    + "Does not call the GitHub API.")
    @GetMapping("/repositories")
    public ResponseEntity<SearchRepositoriesResponse> listRepositories(
            @Parameter(description = "Filter by programming language (case-insensitive)")
            @RequestParam(name = "language", required = false) String language,
            @Parameter(description = "Minimum number of stars")
            @RequestParam(name = "minStars", required = false) Long minStars,
            @Parameter(description = "Sort by stars, forks or updated (default: stars), descending")
            @RequestParam(name = "sort", required = false, defaultValue = "stars") String sort) {
        SearchRepositoriesResponse response = searchService.findStored(language, minStars, sort);
        return ResponseEntity.ok(response);
    }
}
