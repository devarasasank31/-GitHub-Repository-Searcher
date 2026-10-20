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
import com.devarasasank.githubsearcher.repository.GitHubRepositoryRepository;
import com.devarasasank.githubsearcher.repository.GitHubRepositoryUpserter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositorySearchServiceTest {

    @Mock
    private GitHubApiClient gitHubApiClient;

    @Mock
    private GitHubRepositoryUpserter upserter;

    @Mock
    private GitHubRepositoryRepository repository;

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

    private static GitHubRepositoryEntity entity(long id, String name, long stars) {
        return new GitHubRepositoryEntity(
                id, name, "desc", "octocat", "Java", stars, 5L,
                OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void findStoredDefaultsToStarsDescendingWithDeterministicTieBreak() {
        when(repository.findByFilters(isNull(), isNull(), any(Sort.class)))
                .thenReturn(List.of(entity(1L, "a", 10)));

        SearchRepositoriesResponse response = service.findStored(null, null, null);

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(repository).findByFilters(isNull(), isNull(), sortCaptor.capture());
        assertThat(orderSequence(sortCaptor.getValue())).containsExactly("stars DESC", "id ASC");
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.repositories().getFirst().name()).isEqualTo("a");
    }

    @Test
    void findStoredBuildsDeterministicOrderForEverySortOption() {
        when(repository.findByFilters(any(), any(), any(Sort.class))).thenReturn(List.of());

        service.findStored(null, null, "stars");
        service.findStored(null, null, "forks");
        service.findStored(null, null, "updated");

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(repository, times(3)).findByFilters(any(), any(), sortCaptor.capture());
        assertThat(orderSequence(sortCaptor.getAllValues().get(0)))
                .containsExactly("stars DESC", "id ASC");
        assertThat(orderSequence(sortCaptor.getAllValues().get(1)))
                .containsExactly("forks DESC", "stars DESC", "id ASC");
        assertThat(orderSequence(sortCaptor.getAllValues().get(2)))
                .containsExactly("lastUpdated DESC", "stars DESC", "id ASC");
    }

    private static List<String> orderSequence(Sort sort) {
        return sort.stream()
                .map(order -> order.getProperty() + " " + order.getDirection())
                .toList();
    }

    @Test
    void findStoredPassesFiltersAndLowercasesLanguage() {
        when(repository.findByFilters(any(), any(), any(Sort.class))).thenReturn(List.of());

        service.findStored("  Java  ", 500L, "stars");

        verify(repository).findByFilters(eq("java"), eq(500L), any(Sort.class));
    }

    @Test
    void findStoredConvertsBlankLanguageToNull() {
        when(repository.findByFilters(any(), any(), any(Sort.class))).thenReturn(List.of());

        service.findStored("   ", null, "stars");

        verify(repository).findByFilters(isNull(), isNull(), any(Sort.class));
    }

    @Test
    void findStoredRejectsUnknownSortWithoutQuerying() {
        assertThatThrownBy(() -> service.findStored(null, null, "popularity"))
                .isInstanceOf(InvalidSortException.class);

        verify(repository, never()).findByFilters(any(), any(), any());
    }

    @Test
    void findStoredReturnsEmptyResponseWhenNothingMatches() {
        when(repository.findByFilters(any(), any(), any(Sort.class))).thenReturn(List.of());

        SearchRepositoriesResponse response = service.findStored("Java", 10_000L, null);

        assertThat(response.totalCount()).isZero();
        assertThat(response.repositories()).isEmpty();
    }

    @Test
    void findStoredMapsEntitiesToPublicResponses() {
        when(repository.findByFilters(any(), any(), any(Sort.class)))
                .thenReturn(List.of(entity(42L, "spring-boot", 80_000L)));

        SearchRepositoriesResponse response = service.findStored(null, null, "stars");

        RepositoryResponse dto = response.repositories().getFirst();
        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.name()).isEqualTo("spring-boot");
        assertThat(dto.owner()).isEqualTo("octocat");
        assertThat(dto.stars()).isEqualTo(80_000L);
        assertThat(dto.lastUpdated()).isEqualTo(
                OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void findStoredNeverCallsGitHubApi() {
        when(repository.findByFilters(any(), any(), any(Sort.class))).thenReturn(List.of());

        service.findStored("Java", null, null);

        verify(gitHubApiClient, never()).searchRepositories(anyString(), any(), any(), anyInt());
    }
}
