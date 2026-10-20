package com.devarasasank.githubsearcher.controller;

import com.devarasasank.githubsearcher.dto.RepositoryResponse;
import com.devarasasank.githubsearcher.dto.SearchRepositoriesRequest;
import com.devarasasank.githubsearcher.dto.SearchRepositoriesResponse;
import com.devarasasank.githubsearcher.exception.GitHubAuthenticationException;
import com.devarasasank.githubsearcher.exception.GitHubRateLimitException;
import com.devarasasank.githubsearcher.exception.GitHubUnavailableException;
import com.devarasasank.githubsearcher.exception.GitHubUpstreamException;
import com.devarasasank.githubsearcher.exception.InvalidSortException;
import com.devarasasank.githubsearcher.service.RepositorySearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RepositorySearchController.class)
class RepositorySearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RepositorySearchService searchService;

    private static final String BODY = """
            {"query": "spring boot", "language": "Java", "sort": "stars", "maxResults": 30}
            """;

    private static SearchRepositoriesResponse sampleResponse() {
        RepositoryResponse repository = new RepositoryResponse(
                1L, "Hello-World", "My first repository", "octocat", "C",
                2543L, 1251L, OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC));
        return new SearchRepositoriesResponse(1, List.of(repository));
    }

    @Test
    void searchReturns200WithRepositories() throws Exception {
        when(searchService.search(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.repositories[0].id").value(1))
                .andExpect(jsonPath("$.repositories[0].name").value("Hello-World"))
                .andExpect(jsonPath("$.repositories[0].owner").value("octocat"))
                .andExpect(jsonPath("$.repositories[0].stars").value(2543));
    }

    @Test
    void blankQueryReturns400() throws Exception {
        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void invalidSortReturns400() throws Exception {
        when(searchService.search(any()))
                .thenThrow(new InvalidSortException("popularity"));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"spring\", \"sort\": \"popularity\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Unsupported sort value 'popularity'. Supported values: stars, forks, updated"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void outOfRangeMaxResultsReturns400() throws Exception {
        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"spring\", \"maxResults\": 500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rateLimitReturns429() throws Exception {
        doThrow(new GitHubRateLimitException(30L)).when(searchService).search(any());

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"spring\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(
                        "GitHub API rate limit exceeded; retry after 30 seconds"));
    }

    @Test
    void upstreamErrorReturns502() throws Exception {
        doThrow(new GitHubUpstreamException("upstream boom", 500)).when(searchService).search(any());

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"spring\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void authErrorReturns502() throws Exception {
        doThrow(new GitHubAuthenticationException("bad token")).when(searchService).search(any());

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"spring\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("bad token"));
    }

    @Test
    void unavailableReturns503() throws Exception {
        doThrow(new GitHubUnavailableException("GitHub API could not be reached"))
                .when(searchService).search(any());

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"spring\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void unexpectedErrorReturns500WithoutLeakingDetails() throws Exception {
        doThrow(new IllegalStateException("secret internals")).when(searchService).search(any());

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"spring\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void errorBodyIncludesTimestampAndPath() throws Exception {
        doThrow(new GitHubUnavailableException("down")).when(searchService).search(any());

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"spring\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.path").value("/api/github/search"));
    }

    @Test
    void searchForwardsParsedRequest() throws Exception {
        when(searchService.search(any())).thenReturn(new SearchRepositoriesResponse(0, List.of()));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<SearchRepositoriesRequest> captor =
                ArgumentCaptor.forClass(SearchRepositoriesRequest.class);
        verify(searchService).search(captor.capture());
        SearchRepositoriesRequest forwarded = captor.getValue();
        assertThat(forwarded.query()).isEqualTo("spring boot");
        assertThat(forwarded.language()).isEqualTo("Java");
        assertThat(forwarded.sort()).isEqualTo("stars");
        assertThat(forwarded.maxResults()).isEqualTo(30);
    }

    @Test
    void listReturns200WithDefaultsWhenNoParams() throws Exception {
        when(searchService.findStored(any(), any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/github/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.repositories[0].id").value(1))
                .andExpect(jsonPath("$.repositories[0].name").value("Hello-World"));

        verify(searchService).findStored(null, null, "stars");
    }

    @Test
    void listForwardsQueryParameters() throws Exception {
        when(searchService.findStored(any(), any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/github/repositories")
                        .param("language", "Java")
                        .param("minStars", "100")
                        .param("sort", "forks"))
                .andExpect(status().isOk());

        verify(searchService).findStored("Java", 100L, "forks");
    }

    @Test
    void listInvalidSortReturns400() throws Exception {
        when(searchService.findStored(any(), any(), any()))
                .thenThrow(new InvalidSortException("popularity"));

        mockMvc.perform(get("/api/github/repositories")
                        .param("sort", "popularity"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Unsupported sort value 'popularity'. Supported values: stars, forks, updated"));
    }

    @Test
    void listNonNumericMinStarsReturns400() throws Exception {
        mockMvc.perform(get("/api/github/repositories")
                        .param("minStars", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Invalid value 'abc' for parameter 'minStars'"));
    }

    @Test
    void listEmptyResultReturns200() throws Exception {
        when(searchService.findStored(any(), any(), any()))
                .thenReturn(new SearchRepositoriesResponse(0, List.of()));

        mockMvc.perform(get("/api/github/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.repositories").isEmpty());
    }

    @Test
    void listUnexpectedErrorReturns500WithoutLeakingDetails() throws Exception {
        when(searchService.findStored(any(), any(), any()))
                .thenThrow(new IllegalStateException("secret internals"));

        mockMvc.perform(get("/api/github/repositories"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void listErrorBodyIncludesPath() throws Exception {
        when(searchService.findStored(any(), any(), any()))
                .thenThrow(new InvalidSortException("nope"));

        mockMvc.perform(get("/api/github/repositories"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/api/github/repositories"));
    }
}
