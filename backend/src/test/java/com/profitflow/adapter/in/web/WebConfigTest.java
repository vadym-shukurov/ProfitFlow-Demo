package com.profitflow.adapter.in.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebConfigTest {

    @Test
    void buildOriginList_alwaysIncludesLocalDevOrigins() {
        String[] origins = WebConfig.buildOriginList("");
        assertThat(origins).containsExactly(
                "http://localhost:4200",
                "http://127.0.0.1:4200");
    }

    @Test
    void buildOriginList_appendsTrimmedHttpsOrigins() {
        String[] origins = WebConfig.buildOriginList(" https://app.example.com ,https://other.dev ");
        assertThat(origins).containsExactly(
                "http://localhost:4200",
                "http://127.0.0.1:4200",
                "https://app.example.com",
                "https://other.dev");
    }

    @Test
    void buildOriginList_skipsEmptySegmentsBetweenCommas() {
        String[] origins = WebConfig.buildOriginList("https://a.test,, https://b.test");
        assertThat(origins).contains(
                "http://localhost:4200",
                "https://a.test",
                "https://b.test");
        assertThat(origins).doesNotContain("");
    }

    @Test
    void buildOriginList_rejectsWildcard() {
        assertThatThrownBy(() -> WebConfig.buildOriginList("https://ok.com,*"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALLOWED_ORIGINS must not contain '*'");
    }

    @Test
    void buildOriginList_nullTreatedLikeUnset() {
        String[] origins = WebConfig.buildOriginList(null);
        assertThat(origins).containsExactly(
                "http://localhost:4200",
                "http://127.0.0.1:4200");
    }
}
