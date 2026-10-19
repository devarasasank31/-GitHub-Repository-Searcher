package com.devarasasank.githubsearcher.controller;

import com.devarasasank.githubsearcher.dto.SearchRepositoriesRequest;
import com.devarasasank.githubsearcher.dto.SearchRepositoriesResponse;
import com.devarasasank.githubsearcher.service.RepositorySearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
