package com.profitflow.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringCurrentUserPortTest {

    private final SpringCurrentUserPort port = new SpringCurrentUserPort();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsSystemWhenNotAuthenticated() {
        SecurityContextHolder.clearContext();
        assertThat(port.currentUsernameOrSystem()).isEqualTo("system");
    }

    @Test
    void returnsPrincipalNameWhenAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "finance-user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ANALYST"))));

        assertThat(port.currentUsernameOrSystem()).isEqualTo("finance-user");
    }
}
