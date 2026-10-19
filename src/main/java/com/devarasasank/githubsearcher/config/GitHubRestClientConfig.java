package com.devarasasank.githubsearcher.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Builds the {@link RestClient} used for GitHub API calls with explicit
 * connect/read timeouts, standard headers and optional authentication.
 * The static factory is shared with the client tests so the same
 * configuration is exercised against a mock server.
 */
@Configuration
public class GitHubRestClientConfig {

    public static final String GITHUB_API_VERSION_HEADER = "X-GitHub-Api-Version";
    public static final String GITHUB_API_VERSION = "2022-11-28";

    @Bean
    RestClient githubRestClient(GithubApiProperties properties) {
        return buildRestClient(properties);
    }

    public static RestClient buildRestClient(GithubApiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(GITHUB_API_VERSION_HEADER, GITHUB_API_VERSION)
                .defaultHeader(HttpHeaders.USER_AGENT, "github-repository-searcher");
        if (properties.hasToken()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token());
        }
        return builder.build();
    }
}
