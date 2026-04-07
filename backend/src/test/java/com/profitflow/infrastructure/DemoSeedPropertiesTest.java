package com.profitflow.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSeedPropertiesTest {

    @Test
    void isCompleteWhenAllPasswordsNonBlank() {
        DemoSeedProperties p = new DemoSeedProperties();
        p.setAdminPassword("a");
        p.setManagerPassword("b");
        p.setAnalystPassword("c");
        assertThat(p.isComplete()).isTrue();
    }

    @Test
    void isCompleteFalseWhenAdminBlank() {
        DemoSeedProperties p = new DemoSeedProperties();
        p.setAdminPassword("");
        p.setManagerPassword("b");
        p.setAnalystPassword("c");
        assertThat(p.isComplete()).isFalse();
    }

    @Test
    void isCompleteFalseWhenManagerWhitespaceOnly() {
        DemoSeedProperties p = new DemoSeedProperties();
        p.setAdminPassword("a");
        p.setManagerPassword("   ");
        p.setAnalystPassword("c");
        assertThat(p.isComplete()).isFalse();
    }

    @Test
    void isCompleteFalseWhenAnalystBlank() {
        DemoSeedProperties p = new DemoSeedProperties();
        p.setAdminPassword("a");
        p.setManagerPassword("b");
        p.setAnalystPassword("");
        assertThat(p.isComplete()).isFalse();
    }

    @Test
    void settersCoalesceNullToEmptyString() {
        DemoSeedProperties p = new DemoSeedProperties();
        p.setAdminPassword(null);
        p.setManagerPassword(null);
        p.setAnalystPassword(null);
        assertThat(p.getAdminPassword()).isEmpty();
        assertThat(p.getManagerPassword()).isEmpty();
        assertThat(p.getAnalystPassword()).isEmpty();
        assertThat(p.isComplete()).isFalse();
    }
}
