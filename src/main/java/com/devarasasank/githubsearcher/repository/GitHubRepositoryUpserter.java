package com.devarasasank.githubsearcher.repository;

import com.devarasasank.githubsearcher.entity.GitHubRepositoryEntity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes repositories to PostgreSQL with a single batched
 * {@code INSERT ... ON CONFLICT (id) DO UPDATE} statement, so rows are
 * inserted once and updated on subsequent fetches.
 */
@Repository
public class GitHubRepositoryUpserter {

    static final String UPSERT_SQL = """
            INSERT INTO github_repositories
                (id, name, description, owner, language, stars, forks, last_updated, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                owner = EXCLUDED.owner,
                language = EXCLUDED.language,
                stars = EXCLUDED.stars,
                forks = EXCLUDED.forks,
                last_updated = EXCLUDED.last_updated,
                updated_at = now()
            """;

    private final JdbcTemplate jdbcTemplate;

    public GitHubRepositoryUpserter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Upserts all given repositories in one batch inside a single transaction.
     * Duplicate ids within the batch are collapsed so the conflict target is
     * never matched twice by the same statement.
     *
     * @return update counts, one per distinct repository
     */
    @Transactional
    public int[] upsertAll(List<GitHubRepositoryEntity> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            return new int[0];
        }
        Map<Long, GitHubRepositoryEntity> distinct = new LinkedHashMap<>();
        for (GitHubRepositoryEntity repository : repositories) {
            distinct.putIfAbsent(repository.getId(), repository);
        }
        List<GitHubRepositoryEntity> rows = List.copyOf(distinct.values());
        return jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                GitHubRepositoryEntity entity = rows.get(i);
                ps.setLong(1, entity.getId());
                ps.setString(2, entity.getName());
                ps.setString(3, entity.getDescription());
                ps.setString(4, entity.getOwner());
                ps.setString(5, entity.getLanguage());
                ps.setLong(6, entity.getStars());
                ps.setLong(7, entity.getForks());
                ps.setObject(8, entity.getLastUpdated());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }
}
