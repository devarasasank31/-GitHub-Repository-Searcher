package com.devarasasank.githubsearcher.persistence;

import com.devarasasank.githubsearcher.entity.GitHubRepositoryEntity;
import com.devarasasank.githubsearcher.repository.GitHubRepositoryRepository;
import com.devarasasank.githubsearcher.repository.GitHubRepositoryUpserter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the application context against a real PostgreSQL container: verifies
 * Flyway migration, JPA mapping validation, batched upsert of new and
 * existing repositories, and database-level constraints.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class RepositoryPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private GitHubRepositoryRepository repositoryStore;

    @Autowired
    private GitHubRepositoryUpserter upserter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTable() {
        jdbcTemplate.update("DELETE FROM github_repositories");
    }

    private static GitHubRepositoryEntity sample(long id, long stars, String language, String description) {
        return new GitHubRepositoryEntity(
                id, "repo-" + id, description, "octocat", language, stars, 3,
                OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void flywayCreatesSchemaAndJpaMappingValidates() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(history).hasSize(1);
        assertThat(String.valueOf(history.get(0).get("version"))).isEqualTo("1");
        assertThat(String.valueOf(history.get(0).get("description")))
                .isEqualTo("create github repositories");
        assertThat(String.valueOf(history.get(0).get("success"))).isEqualTo("true");
    }

    @Test
    void insertsNewRepository() {
        upserter.upsertAll(List.of(sample(101L, 500, "Java", "Spring guide")));

        GitHubRepositoryEntity stored = repositoryStore.findById(101L).orElseThrow();
        assertThat(stored.getName()).isEqualTo("repo-101");
        assertThat(stored.getDescription()).isEqualTo("Spring guide");
        assertThat(stored.getOwner()).isEqualTo("octocat");
        assertThat(stored.getLanguage()).isEqualTo("Java");
        assertThat(stored.getStars()).isEqualTo(500);
        assertThat(stored.getForks()).isEqualTo(3);
        assertThat(stored.getLastUpdated().toInstant())
                .isEqualTo(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC).toInstant());
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
    }

    @Test
    void updatesExistingRepositoryInsteadOfDuplicating() throws InterruptedException {
        upserter.upsertAll(List.of(sample(102L, 100, "Java", "old description")));
        OffsetDateTime originalCreatedAt = repositoryStore.findById(102L).orElseThrow().getCreatedAt();
        Thread.sleep(10);

        GitHubRepositoryEntity changed = new GitHubRepositoryEntity(
                102L, "repo-102", "new description", "octocat", "Kotlin", 999, 42,
                OffsetDateTime.of(2025, 6, 15, 8, 30, 0, 0, ZoneOffset.UTC));
        upserter.upsertAll(List.of(changed));

        assertThat(repositoryStore.count()).isEqualTo(1);
        GitHubRepositoryEntity stored = repositoryStore.findById(102L).orElseThrow();
        assertThat(stored.getStars()).isEqualTo(999);
        assertThat(stored.getDescription()).isEqualTo("new description");
        assertThat(stored.getLanguage()).isEqualTo("Kotlin");
        assertThat(stored.getForks()).isEqualTo(42);
        assertThat(stored.getLastUpdated().toInstant()).isEqualTo(changed.getLastUpdated().toInstant());
        assertThat(stored.getCreatedAt().toInstant()).isEqualTo(originalCreatedAt.toInstant());
        assertThat(stored.getUpdatedAt()).isAfter(originalCreatedAt);
    }

    @Test
    void storesVeryLargeRepositoryIds() {
        long largeId = 9_000_000_000_000_000_000L;

        upserter.upsertAll(List.of(sample(largeId, 1, "Go", null)));

        GitHubRepositoryEntity stored = repositoryStore.findById(largeId).orElseThrow();
        assertThat(stored.getId()).isEqualTo(largeId);
    }

    @Test
    void allowsNullDescriptionAndLanguage() {
        upserter.upsertAll(List.of(sample(103L, 0, null, null)));

        GitHubRepositoryEntity stored = repositoryStore.findById(103L).orElseThrow();
        assertThat(stored.getDescription()).isNull();
        assertThat(stored.getLanguage()).isNull();
        assertThat(stored.getStars()).isZero();
    }

    @Test
    void storesDuplicateIdsFromBatchOnlyOnce() {
        upserter.upsertAll(List.of(
                sample(104L, 10, "Java", null),
                sample(104L, 20, "Java", null)));

        assertThat(repositoryStore.count()).isEqualTo(1);
        assertThat(repositoryStore.findById(104L).orElseThrow().getStars()).isEqualTo(10);
    }

    @Test
    void enforcesUniqueRepositoryIdAtDatabaseLevel() {
        upserter.upsertAll(List.of(sample(105L, 1, "Java", null)));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO github_repositories (id, name, owner, language, stars, forks, last_updated) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                105L, "duplicate", "someone", "Java", 5, 5, OffsetDateTime.now(ZoneOffset.UTC)))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(repositoryStore.count()).isEqualTo(1);
    }

    @Test
    void rejectsNegativeStarCountsAtDatabaseLevel() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO github_repositories (id, name, owner, language, stars, forks, last_updated) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                106L, "negative", "someone", "Java", -5, 0, OffsetDateTime.now(ZoneOffset.UTC)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void jpaSaveAppliesTimestampDefaults() {
        repositoryStore.save(sample(107L, 33, "Python", null));

        GitHubRepositoryEntity stored = repositoryStore.findById(107L).orElseThrow();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
    }

    @Test
    void repeatedSearchesUpdatingSameRepositoryKeepSingleRow() {
        for (int stars = 1; stars <= 3; stars++) {
            upserter.upsertAll(List.of(new GitHubRepositoryEntity(
                    108L, "repo-108", "iteration " + stars, "octocat", "Java", stars, 1,
                    OffsetDateTime.of(2024, 2, stars, 10, 0, 0, 0, ZoneOffset.UTC))));
        }

        assertThat(repositoryStore.count()).isEqualTo(1);
        GitHubRepositoryEntity stored = repositoryStore.findById(108L).orElseThrow();
        assertThat(stored.getStars()).isEqualTo(3);
        assertThat(stored.getDescription()).isEqualTo("iteration 3");
    }
}
