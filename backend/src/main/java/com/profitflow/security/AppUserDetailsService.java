package com.profitflow.security;

import com.profitflow.adapter.out.persistence.jpa.AppUserEntityRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security {@link UserDetailsService} backed by the {@code app_user} table.
 *
 * <p>Account locking is handled here: if the user's {@code lockedUntil} timestamp
 * is in the future the returned {@link UserDetails} marks the account as locked
 * ({@code isAccountNonLocked() == false}), causing Spring Security to reject
 * authentication with {@code LockedException} before even checking the password.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserEntityRepository users;

    public AppUserDetailsService(AppUserEntityRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var entity = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Authentication failed"));  // deliberate: don't reveal whether user exists

        return User.builder()
                .username(entity.getUsername())
                .password(entity.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + entity.getRole().name())))
                .disabled(!entity.isEnabled())
                .accountLocked(entity.isLocked())
                .build();
    }
}
