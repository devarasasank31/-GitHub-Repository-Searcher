package com.devarasasank.githubsearcher.mapper;

import com.devarasasank.githubsearcher.client.GitHubOwner;
import com.devarasasank.githubsearcher.client.GitHubRepositoryItem;
import com.devarasasank.githubsearcher.dto.RepositoryResponse;
import com.devarasasank.githubsearcher.entity.GitHubRepositoryEntity;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Converts between the GitHub client's internal view, the persisted entity
 * and the public API response. Centralises the field mappings so a change to
 * one representation does not ripple through the whole application.
 */
@Component
public class RepositoryMapper {

    /**
     * Maps a client item to its persistent form. The GitHub item's
     * {@code updatedAt} is the repository's last activity timestamp, which is
     * what the {@code last_updated} column stores.
     */
    public GitHubRepositoryEntity toEntity(GitHubRepositoryItem item) {
        OffsetDateTime updatedAt = item.updatedAt() == null
                ? OffsetDateTime.now(ZoneOffset.UTC)
                : item.updatedAt();
        return new GitHubRepositoryEntity(
                item.id(),
                item.name(),
                item.description(),
                ownerLogin(item.owner()),
                item.language(),
                longValue(item.stargazersCount()),
                longValue(item.forksCount()),
                updatedAt);
    }

    /**
     * Maps a client item straight to its public response form.
     */
    public RepositoryResponse toResponse(GitHubRepositoryItem item) {
        return new RepositoryResponse(
                item.id(),
                item.name(),
                item.description(),
                ownerLogin(item.owner()),
                item.language(),
                longValue(item.stargazersCount()),
                longValue(item.forksCount()),
                item.updatedAt());
    }

    /**
     * Maps a persisted entity to its public response form.
     */
    public RepositoryResponse toResponse(GitHubRepositoryEntity entity) {
        return new RepositoryResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getOwner(),
                entity.getLanguage(),
                entity.getStars(),
                entity.getForks(),
                entity.getLastUpdated());
    }

    /**
     * Maps a list of client items to their public response form, preserving
     * order and skipping {@code null} entries defensively.
     */
    public List<RepositoryResponse> toResponseList(List<GitHubRepositoryItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item != null)
                .map(this::toResponse)
                .toList();
    }

    private static String ownerLogin(GitHubOwner owner) {
        return owner == null ? null : owner.login();
    }

    private static long longValue(Integer value) {
        return value == null ? 0L : value.longValue();
    }
}
