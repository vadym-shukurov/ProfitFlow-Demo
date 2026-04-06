package com.profitflow.application.port.out;

/**
 * Outbound port for managing user account lockout state.
 *
 * <p>This port allows the authentication adapter ({@code AuthController}) to record
 * login successes and failures without depending on JPA entity types directly,
 * preserving the hexagonal architecture boundary between the web adapter and the
 * persistence adapter.
 *
 * <p>Implemented by the persistence adapter; decouples the web layer from
 * {@code AppUserEntityRepository} and {@code AppUserEntity}.
 */
public interface UserLockoutPort {

    /**
     * Records a successful login for the given user, resetting the failed-attempt counter.
     *
     * @param username the authenticated username
     */
    void recordSuccessfulLogin(String username);

    /**
     * Records a failed login attempt for the given user, incrementing the failed-attempt counter.
     * If the threshold is reached, the account is locked for the configured lockout period.
     *
     * @param username the username that was attempted
     */
    void recordFailedLogin(String username);
}
