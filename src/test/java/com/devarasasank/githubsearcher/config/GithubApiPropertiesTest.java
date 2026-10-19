package com.devarasasank.githubsearcher.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class GithubApiPropertiesTest {

    private static final String[] VALID_PROPERTIES = {
            "github.api.base-url=https://api.github.com",
            "github.api.connect-timeout=5s",
            "github.api.read-timeout=10s",
            "github.api.max-results=30",
            "github.api.retry-max-attempts=2",
            "github.api.retry-delay=200ms"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GithubApiProperties.class)
    static class TestConfiguration {
    }

    @Test
    void bindsValidConfiguration() {
        runner.withPropertyValues(VALID_PROPERTIES).run(context -> {
            assertThat(context).hasNotFailed();
            GithubApiProperties properties = context.getBean(GithubApiProperties.class);
            assertThat(properties.baseUrl()).isEqualTo("https://api.github.com");
            assertThat(properties.connectTimeout()).hasSeconds(5);
            assertThat(properties.readTimeout()).hasSeconds(10);
            assertThat(properties.maxResults()).isEqualTo(30);
            assertThat(properties.retryMaxAttempts()).isEqualTo(2);
            assertThat(properties.retryDelay()).hasMillis(200);
            assertThat(properties.hasToken()).isFalse();
        });
    }

    @Test
    void detectsConfiguredToken() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("github.api.token=some-token")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GithubApiProperties.class).hasToken()).isTrue();
                });
    }

    @Test
    void blankTokenIsTreatedAsAbsent() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("github.api.token=   ")
                .run(context -> assertThat(context.getBean(GithubApiProperties.class).hasToken()).isFalse());
    }

    @Test
    void failsWhenBaseUrlMissing() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenBaseUrlBlank() {
        runner.withPropertyValues("github.api.base-url=   ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenMaxResultsExceedsGitHubPageSize() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("github.api.max-results=101")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenRetryAttemptsAreNotPositive() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("github.api.retry-max-attempts=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenRetryDelayMissing() {
        runner.withPropertyValues(
                "github.api.base-url=https://api.github.com",
                "github.api.connect-timeout=5s",
                "github.api.read-timeout=10s",
                "github.api.max-results=30",
                "github.api.retry-max-attempts=2"
        ).run(context -> assertThat(context).hasFailed());
    }
}
