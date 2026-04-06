package com.profitflow.security;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RsaKeyLoader}.
 *
 * <p>Uses the bundled test-classpath PEM files ({@code certs/public.pem} and
 * {@code certs/private.pem}) so the tests work offline and without a mock for
 * {@link RsaKeyProperties}, which is a final Java record and cannot be
 * instrumented by Mockito on Java 21+.
 */
class RsaKeyLoaderTest {

    /** Generates a fresh RSA key pair for tests (no PEMs in repo). */
    private static RsaKeyProperties generatedKeys() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair pair = g.generateKeyPair();
            return new RsaKeyProperties((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate RSA test keypair: " + e.getMessage(), e);
        }
    }

    // ── Non-production (classpath fallback) ────────────────────────────────────

    @Test
    void publicKeyFallsBackInDevProfile() {
        RsaKeyLoader loader = new RsaKeyLoader(generatedKeys(), "default", "profitflow-1");

        RSAPublicKey key = loader.publicKey();

        assertThat(key).isNotNull();
        assertThat(key.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void privateKeyFallsBackInDevProfile() {
        RsaKeyLoader loader = new RsaKeyLoader(generatedKeys(), "default", "profitflow-1");

        RSAPrivateKey key = loader.privateKey();

        assertThat(key).isNotNull();
        assertThat(key.getAlgorithm()).isEqualTo("RSA");
    }

    // ── Production profile guard ──────────────────────────────────────────────

    @Test
    void publicKeyThrowsInProdWhenEnvVarAbsent() {
        // Prod guard throws before touching classpathKeys, so nulls are safe here
        RsaKeyLoader loader = new RsaKeyLoader(new RsaKeyProperties(null, null), "prod", "profitflow-1");

        assertThatThrownBy(loader::publicKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RSA_PUBLIC_KEY_PEM")
                .hasMessageContaining("SECURITY VIOLATION");
    }

    @Test
    void privateKeyThrowsInProdWhenEnvVarAbsent() {
        RsaKeyLoader loader = new RsaKeyLoader(new RsaKeyProperties(null, null), "prod", "profitflow-1");

        assertThatThrownBy(loader::privateKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RSA_PRIVATE_KEY_PEM")
                .hasMessageContaining("SECURITY VIOLATION");
    }

    // ── Profile detection ─────────────────────────────────────────────────────

    @Test
    void activeProfilesContainingProdTriggersGuard() {
        // "prod,metrics" contains "prod" — guard should trigger
        RsaKeyLoader loader = new RsaKeyLoader(new RsaKeyProperties(null, null), "prod,metrics", "profitflow-1");

        assertThatThrownBy(loader::publicKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SECURITY VIOLATION");
    }

    @Test
    void nonproductionProfileDoesNotTriggerGuard() {
        RsaKeyLoader loader = new RsaKeyLoader(generatedKeys(), "staging", "profitflow-1");

        // "staging" does not contain "prod" — should fall back to classpath without throwing
        assertThat(loader.publicKey()).isNotNull();
    }

    @Test
    void signingKeyIdUsesConfiguredPropertyWhenEnvUnset() {
        RsaKeyLoader loader = new RsaKeyLoader(generatedKeys(), "default", "custom-kid");

        assertThat(loader.signingKeyId()).isEqualTo("custom-kid");
    }

    @Test
    void signingJwkCarriesSameKid() {
        RsaKeyLoader loader = new RsaKeyLoader(generatedKeys(), "default", "kid-99");

        assertThat(loader.signingJwk().getKeyID()).isEqualTo("kid-99");
    }

    @Test
    void previousVerificationKeyAbsentWithoutEnv() {
        RsaKeyLoader loader = new RsaKeyLoader(generatedKeys(), "default", "profitflow-1");

        assertThat(loader.previousVerificationPublicKey()).isEmpty();
    }
}
