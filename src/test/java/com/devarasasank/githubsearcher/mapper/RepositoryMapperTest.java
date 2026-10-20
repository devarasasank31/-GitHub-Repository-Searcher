package com.devarasasank.githubsearcher.mapper;

import com.devarasasank.githubsearcher.client.GitHubOwner;
import com.devarasasank.githubsearcher.client.GitHubRepositoryItem;
import com.devarasasank.githubsearcher.dto.RepositoryResponse;
import com.devarasasank.githubsearcher.entity.GitHubRepositoryEntity;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryMapperTest {

    private final RepositoryMapper mapper = new RepositoryMapper();

    private static GitHubRepositoryItem item(Long id, String name) {
        return new GitHubRepositoryItem(
                id,
                name,
                "description of " + name,
                new GitHubOwner("octocat"),
                "Java",
                100,
                42,
                OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void toEntityMapsAllFields() {
        GitHubRepositoryEntity entity = mapper.toEntity(item(1L, "demo"));

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getName()).isEqualTo("demo");
        assertThat(entity.getDescription()).isEqualTo("description of demo");
        assertThat(entity.getOwner()).isEqualTo("octocat");
        assertThat(entity.getLanguage()).isEqualTo("Java");
        assertThat(entity.getStars()).isEqualTo(100L);
        assertThat(entity.getForks()).isEqualTo(42L);
        assertThat(entity.getLastUpdated()).isEqualTo(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void toEntityUsesNowWhenUpdatedAtAbsent() {
        GitHubRepositoryItem noTimestamp = new GitHubRepositoryItem(
                2L, "no-ts", null, null, null, null, null, null);

        GitHubRepositoryEntity entity = mapper.toEntity(noTimestamp);

        assertThat(entity.getOwner()).isNull();
        assertThat(entity.getLanguage()).isNull();
        assertThat(entity.getStars()).isZero();
        assertThat(entity.getForks()).isZero();
        assertThat(entity.getLastUpdated()).isNotNull();
    }

    @Test
    void toResponseMapsAllFields() {
        RepositoryResponse response = mapper.toResponse(item(7L, "repo"));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("repo");
        assertThat(response.owner()).isEqualTo("octocat");
        assertThat(response.stars()).isEqualTo(100L);
        assertThat(response.forks()).isEqualTo(42L);
    }

    @Test
    void toResponseListPreservesOrder() {
        List<RepositoryResponse> responses =
                mapper.toResponseList(List.of(item(1L, "a"), item(2L, "b")));

        assertThat(responses).extracting(RepositoryResponse::id).containsExactly(1L, 2L);
    }

    @Test
    void toResponseListHandlesNullAndEmpty() {
        assertThat(mapper.toResponseList(null)).isEmpty();
        assertThat(mapper.toResponseList(List.of())).isEmpty();
    }

    @Test
    void toResponseListSkipsNullEntries() {
        List<GitHubRepositoryItem> withNull = Arrays.asList(item(1L, "a"), null);

        assertThat(mapper.toResponseList(withNull)).hasSize(1);
    }

    @Test
    void toResponseMapsPersistedEntity() {
        GitHubRepositoryEntity entity = new GitHubRepositoryEntity(
                7L, "repo", "stored description", "octocat", "Java", 100L, 42L,
                OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC));

        RepositoryResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("repo");
        assertThat(response.description()).isEqualTo("stored description");
        assertThat(response.owner()).isEqualTo("octocat");
        assertThat(response.language()).isEqualTo("Java");
        assertThat(response.stars()).isEqualTo(100L);
        assertThat(response.forks()).isEqualTo(42L);
        assertThat(response.lastUpdated())
                .isEqualTo(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void toResponseMapsEntityWithNullOptionalFields() {
        GitHubRepositoryEntity entity = new GitHubRepositoryEntity(
                8L, "no-desc", null, "octocat", null, 0L, 0L,
                OffsetDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC));

        RepositoryResponse response = mapper.toResponse(entity);

        assertThat(response.description()).isNull();
        assertThat(response.language()).isNull();
        assertThat(response.stars()).isZero();
        assertThat(response.forks()).isZero();
    }
}
