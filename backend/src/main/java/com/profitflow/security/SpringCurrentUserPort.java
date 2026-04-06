package com.profitflow.security;

import com.profitflow.application.port.out.CurrentUserPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Bridges Spring Security to {@link CurrentUserPort} so application services stay
 * framework-agnostic.
 */
@Component
public class SpringCurrentUserPort implements CurrentUserPort {

    @Override
    public String currentUsernameOrSystem() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "system";
        }
        return auth.getName();
    }
}
