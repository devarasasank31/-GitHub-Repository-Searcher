package com.devarasasank.githubsearcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GitHubRepositorySearcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitHubRepositorySearcherApplication.class, args);
    }
}
