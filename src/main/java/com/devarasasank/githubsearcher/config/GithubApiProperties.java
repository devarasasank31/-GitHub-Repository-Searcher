package com.devarasasank.githubsearcher.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Externalized settings for the GitHub REST API client.
 *
 * <p>All values can be overridden through environment variables (see
 * {@code .env.example}). Credentials are never hard-coded in the source tree.</p>
 */
@ConfigurationProperties(prefix = "github.api")
@Validated
public record GithubApiProperties(
        @NotBlank String baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        String token,
        @Min(1) @Max(100) int maxResults,
        @Min(1) @Max(5) int retryMaxAttempts,
        @NotNull Duration retryDelay
) {

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
