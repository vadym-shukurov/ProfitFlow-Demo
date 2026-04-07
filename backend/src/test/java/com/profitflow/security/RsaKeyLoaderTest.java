package com.profitflow.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;

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

    private static String readClasspathPem(String classpathLocation) throws IOException {
        ClassLoader cl = RsaKeyLoaderTest.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + classpathLocation);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static RsaKeyLoader loaderWithEnv(RsaKeyProperties keys,
                                             String activeProfiles,
                                             String configuredSigningKeyId,
                                             Map<String, String> env) {
        return new RsaKeyLoader(keys, activeProfiles, configuredSigningKeyId, env::get);
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
    void signingKeyIdTrimsJwtSigningKeyIdFromEnv() throws Exception {
        RsaKeyProperties keys = generatedKeys();
        Map<String, String> env = new HashMap<>();
        env.put("JWT_SIGNING_KEY_ID", "  env-kid  ");
        RsaKeyLoader loader = loaderWithEnv(keys, "default", "ignored-property-kid", env);
        assertThat(loader.signingKeyId()).isEqualTo("env-kid");
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

    @Test
    void previousVerificationPublicKeyPresentWhenEnvPemSet() throws Exception {
        String pem = readClasspathPem("certs/public.pem");
        RsaKeyProperties keys = generatedKeys();
        Map<String, String> env = new HashMap<>();
        env.put("RSA_PREVIOUS_PUBLIC_KEY_PEM", pem);
        env.put("RSA_PREVIOUS_KEY_ID", "custom-rotation-kid");
        RsaKeyLoader loader = loaderWithEnv(keys, "default", "profitflow-1", env);
        assertThat(loader.previousVerificationPublicKey()).isPresent();
    }

    @Test
    void previousVerificationPublicKeyUsesDefaultKidWhenKeyIdBlank() throws Exception {
        String pem = readClasspathPem("certs/public.pem");
        RsaKeyProperties keys = generatedKeys();
        Map<String, String> env = new HashMap<>();
        env.put("RSA_PREVIOUS_PUBLIC_KEY_PEM", pem);
        env.put("RSA_PREVIOUS_KEY_ID", "   ");
        RsaKeyLoader loader = loaderWithEnv(keys, "default", "profitflow-1", env);
        assertThat(loader.previousVerificationPublicKey()).isPresent();
    }

    @Test
    void ephemeralKeyPairReusedWhenClasspathHasPublicOnly() {
        RsaKeyProperties full = generatedKeys();
        RsaKeyProperties pubOnly = new RsaKeyProperties(full.rsaPublicKey(), null);
        RsaKeyLoader loader = new RsaKeyLoader(pubOnly, "default", "k");
        RSAPrivateKey a = loader.privateKey();
        RSAPrivateKey b = loader.privateKey();
        assertThat(a).isSameAs(b);
    }

    @Test
    void publicKeyFromEphemeralDevPairMatchesAcrossCalls() {
        RsaKeyProperties full = generatedKeys();
        RsaKeyProperties pubOnly = new RsaKeyProperties(full.rsaPublicKey(), null);
        RsaKeyLoader loader = new RsaKeyLoader(pubOnly, "default", "k");
        RSAPublicKey p1 = loader.publicKey();
        RSAPublicKey p2 = loader.publicKey();
        assertThat(p1).isSameAs(p2);
    }
}
