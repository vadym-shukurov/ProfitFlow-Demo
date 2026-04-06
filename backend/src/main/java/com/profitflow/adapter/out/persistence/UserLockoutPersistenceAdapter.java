package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.jpa.AppUserEntityRepository;
import com.profitflow.application.port.out.UserLockoutPort;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter for {@link UserLockoutPort}.
 *
 * <p>Translates the port's domain-neutral method calls into JPA entity updates,
 * keeping the web layer ({@code AuthController}) free of direct JPA dependencies.
 */
@Component
public class UserLockoutPersistenceAdapter implements UserLockoutPort {

    private final AppUserEntityRepository users;

    public UserLockoutPersistenceAdapter(AppUserEntityRepository users) {
        this.users = users;
    }

    @Override
    public void recordSuccessfulLogin(String username) {
        users.findByUsername(username).ifPresent(u -> {
            u.recordSuccessfulLogin();
            users.save(u);
        });
    }

    @Override
    public void recordFailedLogin(String username) {
        users.findByUsername(username).ifPresent(u -> {
            u.recordFailedLogin();
            users.save(u);
        });
    }
}
