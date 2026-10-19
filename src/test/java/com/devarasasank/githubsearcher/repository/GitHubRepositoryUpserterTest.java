package com.devarasasank.githubsearcher.repository;

import com.devarasasank.githubsearcher.entity.GitHubRepositoryEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GitHubRepositoryUpserterTest {

    private static final OffsetDateTime LAST_UPDATED =
            OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final GitHubRepositoryUpserter upserter = new GitHubRepositoryUpserter(jdbcTemplate);

    private static GitHubRepositoryEntity sample(long id, String name) {
        return new GitHubRepositoryEntity(id, name, "description", "octocat", "Java", 10, 5, LAST_UPDATED);
    }

    @Test
    void emptyListDoesNotTouchDatabase() {
        assertThat(upserter.upsertAll(List.of())).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void nullListDoesNotTouchDatabase() {
        assertThat(upserter.upsertAll(null)).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void usesBatchedUpsertWithConflictTargetAndMapsColumns() throws Exception {
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1, 1});

        int[] counts = upserter.upsertAll(List.of(sample(1L, "one"), sample(2L, "two")));

        assertThat(counts).containsExactly(1, 1);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), setterCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains("ON CONFLICT (id) DO UPDATE")
                .contains("EXCLUDED.stars")
                .contains("updated_at = now()");

        BatchPreparedStatementSetter setter = setterCaptor.getValue();
        assertThat(setter.getBatchSize()).isEqualTo(2);

        PreparedStatement statement = mock(PreparedStatement.class);
        setter.setValues(statement, 0);
        verify(statement).setLong(1, 1L);
        verify(statement).setString(2, "one");
        verify(statement).setString(3, "description");
        verify(statement).setString(4, "octocat");
        verify(statement).setString(5, "Java");
        verify(statement).setLong(6, 10L);
        verify(statement).setLong(7, 5L);
        verify(statement).setObject(eq(8), any(OffsetDateTime.class));
    }

    @Test
    void collapsesDuplicateIdsWithinBatch() throws Exception {
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1});

        upserter.upsertAll(List.of(sample(7L, "first"), sample(7L, "second")));

        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(anyString(), setterCaptor.capture());
        assertThat(setterCaptor.getValue().getBatchSize()).isEqualTo(1);

        PreparedStatement statement = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(statement, 0);
        verify(statement).setString(2, "first");
    }

    @Test
    void mapsNullDescriptionAndLanguage() throws Exception {
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1});

        upserter.upsertAll(List.of(
                new GitHubRepositoryEntity(3L, "repo", null, "owner", null, 0, 0, LAST_UPDATED)));

        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(anyString(), setterCaptor.capture());

        PreparedStatement statement = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(statement, 0);
        verify(statement).setString(3, null);
        verify(statement).setString(5, null);
        verify(statement).setLong(6, 0L);
    }
}
