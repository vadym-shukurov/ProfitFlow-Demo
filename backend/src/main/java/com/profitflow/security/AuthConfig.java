package com.profitflow.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
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
            AppUserDetailsService userDetailsService,
            PasswordEncoder encoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
