package com.profitflow.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provides the {@link AuthenticationManager} used by the login endpoint.
 *
 * <p>Separated from {@link SecurityConfig} to allow {@code @WebMvcTest} slices
 * to load the JWT filter chain (RBAC rules) without requiring the JPA-backed
 * {@link AppUserDetailsService} — which is only needed for the username/password
 * login flow, not for JWT validation.
 */
@Configuration
public class AuthConfig {

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration,
            AppUserDetailsService userDetailsService,
            PasswordEncoder encoder) throws Exception {
        // Ensure these beans are present so the default AuthenticationManager can wire a DAO provider.
        // (They are deliberately injected here to keep @WebMvcTest slices honest.)
        if (userDetailsService == null || encoder == null) {
            throw new IllegalStateException("Auth configuration is incomplete");
        }
        return configuration.getAuthenticationManager();
    }
}
