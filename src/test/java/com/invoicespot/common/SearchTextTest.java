package com.invoicespot.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchTextTest {

    @Test
    @DisplayName("a search matches anywhere, ignoring case and surrounding spaces")
    void containing() {
        assertThat(SearchText.containing("  Acme Ltd ")).isEqualTo("%acme ltd%");
        assertThat(SearchText.containing(null)).isEqualTo("%%");
        assertThat(SearchText.containing(" ")).isEqualTo("%%");
    }

    @Test
    @DisplayName("wildcards and the escape character in a search are matched as typed")
    void escapesWildcards() {
        assertThat(SearchText.containing("50%_off!")).isEqualTo("%50!%!_off!!%");
    }
}
