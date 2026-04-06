package com.profitflow.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitflow.adapter.out.persistence.jpa.AppUserEntityRepository;
import com.profitflow.adapter.out.persistence.jpa.RevokedAccessTokenRepository;
import com.profitflow.application.port.out.UserLockoutPort;
import com.profitflow.application.port.out.RefreshTokenRepositoryPort;
import com.profitflow.application.port.out.RefreshTokenRepositoryPort.RefreshTokenView;
import com.profitflow.application.port.out.BusinessMetricsPort;
import com.profitflow.security.AppUserDetailsService;
import com.profitflow.security.JwtTokenService;
import com.profitflow.security.RefreshTokenService;
import com.profitflow.config.SecurityMvcSliceImports;
import com.profitflow.security.SecurityConfig;
import com.profitflow.security.TokenRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link AuthController}.
 *
 * <p>Concrete service classes ({@link JwtTokenService}, {@link AppUserDetailsService},
 * {@link RefreshTokenService}) are imported as real beans to avoid Java 25 / Byte Buddy
 * inline-mocking incompatibility with concrete classes. Only their interface
 * dependencies are {@code @MockBean}.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, SecurityMvcSliceImports.class, JwtTokenService.class,
         AppUserDetailsService.class, RefreshTokenService.class,
         TokenRevocationService.class})
class AuthControllerTest {

    @Autowired private MockMvc        mockMvc;
    @Autowired private ObjectMapper   objectMapper;

    @MockBean private AuthenticationManager         authenticationManager;
    @MockBean private AppUserEntityRepository      userRepository;
    @MockBean private RefreshTokenRepositoryPort   refreshTokenRepository;
    @MockBean private RevokedAccessTokenRepository revokedAccessTokenRepository;
    @MockBean private UserLockoutPort              userLockout;
    @MockBean private BusinessMetricsPort          businessMetrics;

    // JwtDecoder / JwtEncoder are interfaces — safe to mock on all JVM versions
    @MockBean JwtDecoder jwtDecoder;
    @MockBean JwtEncoder jwtEncoder;

    private String loginJson(String user, String pass) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", user, "password", pass));
    }

    private void stubJwtEncoder() {
        Jwt stubJwt = Jwt.withTokenValue("jwt-token-value")
                .header("alg", "RS256")
                .claim("sub", "admin")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(stubJwt);
    }

    @Test
    void successfulLoginReturns200WithTokenPair() throws Exception {
        stubJwtEncoder();
        // RefreshTokenService.issue() calls port.save() — void, no stub needed (mock default)
        doNothing().when(refreshTokenRepository).save(any(), anyString(), anyString(), any(), any());

        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        doNothing().when(userLockout).recordSuccessfulLogin(anyString());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        verify(businessMetrics).recordLoginSuccess();
    }

    @Test
    void badCredentialsReturns401() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));
        doNothing().when(userLockout).recordFailedLogin(anyString());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "wrongpass")))
                .andExpect(status().isUnauthorized());

        verify(businessMetrics).recordLoginFailure();
    }

    @Test
    void lockedAccountReturns401NotLockStatus() throws Exception {
        // Locked accounts return 401 — same as bad credentials — to prevent lock-state enumeration
        when(authenticationManager.authenticate(any()))
                .thenThrow(new LockedException("locked"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "secret123")))
                .andExpect(status().isUnauthorized());

        verify(businessMetrics).recordLoginLocked();
    }

    @Test
    void missingUsernameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "secret"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshWithValidTokenReturns200() throws Exception {
        stubJwtEncoder();

        // Stub the repository to return a valid (non-expired) token view
        String oldRawToken = "old-refresh-token";
        String hash        = sha256Hex(oldRawToken);
        var view = new RefreshTokenView(UUID.randomUUID(), "admin",
                Instant.now().plusSeconds(604800));

        when(refreshTokenRepository.findActiveByHash(hash)).thenReturn(Optional.of(view));
        doNothing().when(refreshTokenRepository).revoke(any(), anyString());
        doNothing().when(refreshTokenRepository).save(any(), anyString(), anyString(), any(), any());

        // Stub user lookup for the post-rotation token generation step
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(
                new com.profitflow.adapter.out.persistence.entity.AppUserEntity(
                        UUID.randomUUID(), "admin", "admin@example.com",
                        "hashed", com.profitflow.domain.security.UserRole.ADMIN)));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", oldRawToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token-value"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void refreshWithInvalidTokenReturns401() throws Exception {
        when(refreshTokenRepository.findActiveByHash(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", "expired-or-unknown"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesSpecificRefreshTokenAndReturns204() throws Exception {
        when(refreshTokenRepository.revokeByHash(anyString(), eq("LOGOUT"))).thenReturn(1);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", "some-refresh-token"))))
                .andExpect(status().isNoContent());

        verify(refreshTokenRepository).revokeByHash(anyString(), eq("LOGOUT"));
    }

    /** SHA-256 hex — mirrors {@code RefreshTokenService.sha256Hex}. */
    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
