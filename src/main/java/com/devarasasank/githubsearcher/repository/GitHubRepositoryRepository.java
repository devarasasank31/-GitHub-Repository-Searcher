package com.devarasasank.githubsearcher.repository;

import com.devarasasank.githubsearcher.entity.GitHubRepositoryEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepositoryEntity, Long> {

    /**
     * Finds persisted repositories using optional filters. A {@code null}
     * language or {@code minStars} disables that filter; ordering is applied
     * through the given {@link Sort} (column names are mapped internally and
     * never taken from user input directly). The language parameter must
     * already be lowercased by the caller so the comparison stays
     * case-insensitive without wrapping the bind parameter in a function
     * (which Postgres cannot type when the value is null).
     */
    @Query("""
            SELECT r FROM GitHubRepositoryEntity r
            WHERE (:language IS NULL OR LOWER(r.language) = :language)
              AND (:minStars IS NULL OR r.stars >= :minStars)
            """)
    List<GitHubRepositoryEntity> findByFilters(@Param("language") String language,
                                               @Param("minStars") Long minStars,
                                               Sort sort);
}
