package com.devarasasank.githubsearcher.repository;

import com.devarasasank.githubsearcher.entity.GitHubRepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepositoryEntity, Long> {
}
