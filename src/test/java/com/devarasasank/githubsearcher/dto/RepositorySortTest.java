package com.devarasasank.githubsearcher.dto;

import com.devarasasank.githubsearcher.exception.InvalidSortException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositorySortTest {

    @ParameterizedTest
    @CsvSource({"stars,STARS", "STARS,STARS", "Stars,STARS", "forks,FORKS", "Forks,FORKS",
            "updated,UPDATED", "UPDATED,UPDATED", "Updated,UPDATED"})
    void parsesSupportedValuesCaseInsensitively(String input, RepositorySort expected) {
        assertThat(RepositorySort.fromParam(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankOrMissingValuesFallBackToStars(String input) {
        assertThat(RepositorySort.fromParam(input)).isEqualTo(RepositorySort.STARS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"watchers", "best-match", "stars,forks", "1"})
    void rejectsUnsupportedValues(String input) {
        assertThatThrownBy(() -> RepositorySort.fromParam(input))
                .isInstanceOf(InvalidSortException.class)
                .hasMessageContaining("Unsupported sort value")
                .hasMessageContaining("stars, forks, updated");
    }

    @Test
    void mapsToGitHubSortParameters() {
        assertThat(RepositorySort.STARS.gitHubParameter()).isEqualTo("stars");
        assertThat(RepositorySort.FORKS.gitHubParameter()).isEqualTo("forks");
        assertThat(RepositorySort.UPDATED.gitHubParameter()).isEqualTo("updated");
    }
}
