package com.devarasasank.githubsearcher.service;

import com.devarasasank.githubsearcher.client.GitHubApiClient;
import com.devarasasank.githubsearcher.client.GitHubOwner;
import com.devarasasank.githubsearcher.client.GitHubRepositoryItem;
import com.devarasasank.githubsearcher.dto.RepositoryResponse;
import com.devarasasank.githubsearcher.dto.RepositorySort;
import com.devarasasank.githubsearcher.dto.SearchRepositoriesRequest;
import com.devarasasank.githubsearcher.dto.SearchRepositoriesResponse;
import com.devarasasank.githubsearcher.entity.GitHubRepositoryEntity;
import com.devarasasank.githubsearcher.exception.InvalidSortException;
import com.devarasasank.githubsearcher.mapper.RepositoryMapper;
import com.devarasasank.githubsearcher.repository.GitHubRepositoryUpserter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositorySearchServiceTest {

    @Mock
    private GitHubApiClient gitHubApiClient;

    @Mock
    private GitHubRepositoryUpserter upserter;

    @Spy
    private RepositoryMapper mapper = new RepositoryMapper();

    @InjectMocks
    private RepositorySearchService service;

    private static GitHubRepositoryItem item(long id, String name) {
        return new GitHubRepositoryItem(
                id, name, "desc", new GitHubOwner("octocat"), "Java", 10, 5,
                OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void searchDelegatesWithParsedSortAndDefaultPageSize() {
        when(gitHubApiClient.searchRepositories(anyString(), any(), any(), anyInt()))
                .thenReturn(List.of(item(1L, "a")));

        SearchRepositoriesResponse response = service.search(
                new SearchRepositoriesRequest("spring", null, "forks", null));

        verify(gitHubApiClient).searchRepositories("spring", null, RepositorySort.FORKS, 30);
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.repositories()).hasSize(1);
        assertThat(response.repositories().getFirst().name()).isEqualTo("a");
    }

    @Test
    void searchPassesLanguageAndExplicitPageSize() {
        when(gitHubApiClient.searchRepositories(anyString(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.search(new SearchRepositoriesRequest("spring", "Java", null, 50));

        verify(gitHubApiClient).searchRepositories("spring", "Java", RepositorySort.STARS, 50);
    }

    @Test
    void searchPersistsFetchedRepositories() {
        when(gitHubApiClient.searchRepositories(anyString(), any(), any(), anyInt()))
                .thenReturn(List.of(item(1L, "a"), item(2L, "b")));

        service.search(new SearchRepositoriesRequest("spring", null, null, null));

        ArgumentCaptor<List<GitHubRepositoryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(upserter).upsertAll(captor.capture());
        assertThat(captor.getValue()).extracting(GitHubRepositoryEntity::getId).containsExactly(1L, 2L);
    }

    @Test
    void searchSkipsPersistenceWhenNoResults() {
        when(gitHubApiClient.searchRepositories(anyString(), any(), any(), anyInt()))
                .thenReturn(List.of());

        SearchRepositoriesResponse response =
                service.search(new SearchRepositoriesRequest("nothing", null, null, null));

        verify(upserter, never()).upsertAll(any());
        assertThat(response.totalCount()).isZero();
        assertThat(response.repositories()).isEmpty();
    }

    @Test
    void searchToleratesPersistenceFailure() {
        when(gitHubApiClient.searchRepositories(anyString(), any(), any(), anyInt()))
                .thenReturn(List.of(item(1L, "a")));
        when(upserter.upsertAll(any())).thenThrow(new RuntimeException("db down"));

        SearchRepositoriesResponse response =
                service.search(new SearchRepositoriesRequest("spring", null, null, null));

        assertThat(response.totalCount()).isEqualTo(1);
    }

    @Test
    void searchRejectsUnknownSort() {
        assertThatThrownBy(() -> service.search(
                new SearchRepositoriesRequest("spring", null, "popularity", null)))
                .isInstanceOf(InvalidSortException.class);

        verify(gitHubApiClient, never()).searchRepositories(anyString(), isNull(), any(), anyInt());
    }

    @Test
    void searchPropagatesClientFailure() {
        when(gitHubApiClient.searchRepositories(anyString(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.search(
                new SearchRepositoriesRequest("spring", null, null, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");
    }
}
