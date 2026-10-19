package com.devarasasank.githubsearcher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Persistent representation of a GitHub repository.
 *
 * <p>The GitHub repository id is the stable unique identifier; rows are
 * upserted (not duplicated) when the same repository is fetched again.</p>
 */
@Entity
@Table(name = "github_repositories")
public class GitHubRepositoryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "owner", nullable = false, length = 255)
    private String owner;

    @Column(name = "language", length = 100)
    private String language;

    @Column(name = "stars", nullable = false)
    private long stars;

    @Column(name = "forks", nullable = false)
    private long forks;

    @Column(name = "last_updated", nullable = false)
    private OffsetDateTime lastUpdated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected GitHubRepositoryEntity() {
    }

    public GitHubRepositoryEntity(Long id,
                                  String name,
                                  String description,
                                  String owner,
                                  String language,
                                  long stars,
                                  long forks,
                                  OffsetDateTime lastUpdated) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.language = language;
        this.stars = stars;
        this.forks = forks;
        this.lastUpdated = lastUpdated;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getOwner() {
        return owner;
    }

    public String getLanguage() {
        return language;
    }

    public long getStars() {
        return stars;
    }

    public long getForks() {
        return forks;
    }

    public OffsetDateTime getLastUpdated() {
        return lastUpdated;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
