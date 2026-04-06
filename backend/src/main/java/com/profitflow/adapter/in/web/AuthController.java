package com.profitflow.adapter.in.web;

import com.profitflow.adapter.in.web.dto.ApiErrorResponse;
import com.profitflow.application.port.out.BusinessMetricsPort;
import com.profitflow.application.port.out.UserLockoutPort;
import com.profitflow.security.AppUserDetailsService;
import com.profitflow.security.JwtTokenService;
import com.profitflow.security.RefreshTokenService;
import com.profitflow.security.TokenRevocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Public and protected authentication endpoints.
 *
 * <h2>Token lifecycle</h2>
 * <ol>
 *   <li>{@code POST /login} — credential check → issues access token (15 min) +
 *       opaque refresh token (7 days).</li>
 *   <li>{@code POST /refresh} — rotates the refresh token and issues a new access
 *       token without requiring credentials again.</li>
 *   <li>{@code POST /logout} — revokes the refresh token server-side; the access
 *       token remains valid until its natural expiry (at most 15 min).</li>
 * </ol>
 *
 * <h2>Account lockout</h2>
 * After {@code AppUserEntity.MAX_FAILED_ATTEMPTS} consecutive failures the account is
 * locked for {@code AppUserEntity.LOCKOUT_MINUTES} minutes.
 * A locked account returns HTTP 401 (same as bad credentials) — this prevents account
 * enumeration via differential responses.
 *
 * <h2>Error messages</h2>
 * All authentication failures return a generic {@code "Authentication failed."} message
 * with no detail about whether the user exists, the password was wrong, or the account
 * is locked. This prevents both username enumeration and lock-state probing.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager  authManager;
    private final JwtTokenService        jwtTokenService;
    private final RefreshTokenService    refreshTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final AppUserDetailsService  userDetailsService;
    private final UserLockoutPort        userLockout;
    private final BusinessMetricsPort    metrics;

    public AuthController(AuthenticationManager authManager,
                          JwtTokenService jwtTokenService,
                          RefreshTokenService refreshTokenService,
                          TokenRevocationService tokenRevocationService,
                          AppUserDetailsService userDetailsService,
                          UserLockoutPort userLockout,
                          BusinessMetricsPort metrics) {
        this.authManager            = authManager;
        this.jwtTokenService        = jwtTokenService;
        this.refreshTokenService    = refreshTokenService;
        this.tokenRevocationService = tokenRevocationService;
        this.userDetailsService     = userDetailsService;
        this.userLockout            = userLockout;
        this.metrics                = metrics;
    }

    /**
     * Authenticates a user and returns an access token + refresh token.
     *
     * @param request login credentials
     * @return 200 with token pair, or 401 on any failure (including lockout)
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(), request.password()));

            userLockout.recordSuccessfulLogin(request.username());

            String accessToken   = jwtTokenService.generateToken(auth);
            String refreshToken  = refreshTokenService.issue(auth.getName());
            String roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","));

            metrics.recordLoginSuccess();
            return ResponseEntity.ok(new AuthResponse(
                    accessToken,
                    refreshToken,
                    "Bearer",
                    JwtTokenService.ACCESS_TOKEN_TTL_MINUTES * 60,
                    auth.getName(),
                    roles));

        } catch (LockedException ex) {
            metrics.recordLoginLocked();
            // Return 401 — same as bad credentials — to prevent lock-state enumeration
            return ResponseEntity.status(401)
                    .body(new ApiErrorResponse("Authentication failed."));

        } catch (BadCredentialsException ex) {
            userLockout.recordFailedLogin(request.username());
            metrics.recordLoginFailure();
            return ResponseEntity.status(401)
                    .body(new ApiErrorResponse("Authentication failed."));

        } catch (AuthenticationException ex) {
            metrics.recordLoginFailure();
            return ResponseEntity.status(401)
                    .body(new ApiErrorResponse("Authentication failed."));
        }
    }

    /**
     * Rotates a refresh token and issues a new access token.
     *
     * <p>The old refresh token is immediately revoked. If the same token is
     * presented again, it will be rejected — protecting against token theft.
     *
     * @param request the current refresh token
     * @return 200 with new token pair, or 401 if the refresh token is invalid
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshTokenService.RotationResult result =
                refreshTokenService.rotate(request.refreshToken());

        if (!result.valid()) {
            return ResponseEntity.status(401)
                    .body(new ApiErrorResponse("Authentication failed."));
        }

        // Re-load the user's current authorities (role may have changed since last login)
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(result.username());
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    userDetails.getUsername(), null,
                    userDetails.getAuthorities());
            String accessToken = jwtTokenService.generateToken(auth);
            String roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","));
            return ResponseEntity.ok(new AuthResponse(
                    accessToken,
                    result.newRawToken(),
                    "Bearer",
                    JwtTokenService.ACCESS_TOKEN_TTL_MINUTES * 60,
                    userDetails.getUsername(),
                    roles));
        } catch (Exception ex) {
            return ResponseEntity.status(401)
                    .body(new ApiErrorResponse("Authentication failed."));
        }
    }

    /**
     * Revokes the provided refresh token (single-device logout) and immediately
     * invalidates the current access token via the JTI denylist.
     *
     * <p>After this call:
     * <ul>
     *   <li>The provided refresh token is revoked — future {@code /refresh} calls
     *       using it will return 401.</li>
     *   <li>The current access token is added to the JTI denylist — subsequent
     *       requests using it return 401 immediately (no need to wait for the
     *       15-minute TTL to expire).</li>
     * </ul>
     *
     * @param request body containing the refresh token to revoke
     * @return 204 No Content on success
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        refreshTokenService.revokeByRawToken(request.refreshToken());
        revokeCurrentAccessToken("LOGOUT");
        return ResponseEntity.noContent().build();
    }

    /**
     * Revokes <em>all</em> refresh tokens for the authenticated user (logout-all-devices)
     * and immediately invalidates the current access token.
     *
     * <p>Use this when the user suspects their account is compromised or wants to
     * sign out from all browsers/devices at once.
     *
     * @return 204 No Content on success
     */
    @PostMapping("/logout/all")
    public ResponseEntity<Void> logoutAll() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        refreshTokenService.revokeAll(username);
        revokeCurrentAccessToken("LOGOUT_ALL");
        return ResponseEntity.noContent().build();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Adds the current request's access token {@code jti} to the revocation denylist
     * so it is rejected immediately rather than waiting for its 15-minute natural expiry.
     */
    private void revokeCurrentAccessToken(String reason) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String  jti       = jwtAuth.getToken().getId();
            Instant expiresAt = jwtAuth.getToken().getExpiresAt();
            String  username  = jwtAuth.getName();
            if (jti != null && expiresAt != null) {
                tokenRevocationService.revoke(jti, expiresAt, username, reason);
            }
        }
    }

    // ── Inner record types ───────────────────────────────────────────────────

    record LoginRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 128) String password) {
    }

    record RefreshRequest(
            @NotBlank @Size(max = 128) String refreshToken) {
    }

    record LogoutRequest(
            @NotBlank @Size(max = 128) String refreshToken) {
    }

    record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long   expiresIn,
            String username,
            String roles) {
    }
}
