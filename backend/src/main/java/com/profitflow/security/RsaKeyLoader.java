package com.profitflow.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Loads the RSA key pair used to sign and verify JWTs.
 *
 * <h2>Key source priority (highest first)</h2>
 * <ol>
 *   <li><strong>Environment variables</strong> — {@code RSA_PRIVATE_KEY_PEM} and
 *       {@code RSA_PUBLIC_KEY_PEM}. Each must contain the full PEM text including
 *       header/footer lines. <em>This is the required production path.</em></li>
 *   <li><strong>Classpath files</strong> — {@code certs/private.pem} and
 *       {@code certs/public.pem}. Used only in local development and tests.
 *       A startup warning is emitted whenever classpath keys are used.</li>
 * </ol>
 *
 * <h2>Production setup</h2>
 * <pre>
 * RSA_PRIVATE_KEY_PEM="(PEM omitted — do not paste keys into source control)"
 * RSA_PUBLIC_KEY_PEM="(PEM omitted — do not paste keys into source control)"
 * </pre>
 *
 * The keys must be injected via a secrets manager (Vault, AWS Secrets Manager,
 * Kubernetes Secret) — never committed to source control.
 *
 * <h2>Key rotation (JWT {@code kid})</h2>
 * <ul>
 *   <li>{@code JWT_SIGNING_KEY_ID} — {@code kid} for newly issued tokens (default
 *       {@code profitflow-1}).</li>
 *   <li>{@code RSA_PREVIOUS_PUBLIC_KEY_PEM} — optional second public key still trusted
 *       for verification until old tokens expire.</li>
 *   <li>{@code RSA_PREVIOUS_KEY_ID} — {@code kid} for the previous key (default
 *       {@code profitflow-0}).</li>
 * </ul>
 */
@Component
public class RsaKeyLoader {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyLoader.class);

    private static final String LOCAL_DEV_ONLY_NEVER_PROD =
            "This is acceptable for local development only — NEVER in production.";

    private static final String ENV_SIGNING_KEY_ID = "JWT_SIGNING_KEY_ID";
    private static final String ENV_PREV_PUBLIC    = "RSA_PREVIOUS_PUBLIC_KEY_PEM";
    private static final String ENV_PREV_KEY_ID    = "RSA_PREVIOUS_KEY_ID";

    // Classpath fallback — active only in local dev / CI with local profile
    private final RsaKeyProperties classpathKeys;

    // Ephemeral dev fallback (generated once per JVM when no key material is configured)
    private final AtomicReference<KeyPair> generatedDevKeyPair = new AtomicReference<>();

    // Active profile — used to determine whether to block classpath keys
    private final String activeProfiles;

    /** {@code kid} claim for access tokens signed with the current private key. */
    private final String configuredSigningKeyId;

    private final Function<String, String> getenv;

    @Autowired
    public RsaKeyLoader(RsaKeyProperties classpathKeys,
                        @Value("${spring.profiles.active:default}") String activeProfiles,
                        @Value("${profitflow.security.jwt.signing-key-id:profitflow-1}")
                                String configuredSigningKeyId) {
        this(classpathKeys, activeProfiles, configuredSigningKeyId, System::getenv);
    }

    RsaKeyLoader(RsaKeyProperties classpathKeys,
                 String activeProfiles,
                 String configuredSigningKeyId,
                 Function<String, String> getenv) {
        this.classpathKeys           = classpathKeys;
        this.activeProfiles          = activeProfiles;
        this.configuredSigningKeyId  = configuredSigningKeyId;
        this.getenv                  = getenv != null ? getenv : System::getenv;
    }

    /**
     * Key ID embedded in the JWT JWS header and matched against verification keys.
     */
    public String signingKeyId() {
        String fromEnv = getenv.apply(ENV_SIGNING_KEY_ID);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return configuredSigningKeyId;
    }

    /**
     * Nimbus JWK for the encoder — includes private key + {@code kid}.
     */
    public RSAKey signingJwk() {
        return new RSAKey.Builder(publicKey())
                .privateKey(privateKey())
                .keyID(signingKeyId())
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    /**
     * Optional previous public key still trusted during key rotation.
     *
     * @see FallbackJwtDecoder
     */
    public Optional<RSAPublicKey> previousVerificationPublicKey() {
        String prevPem = getenv.apply(ENV_PREV_PUBLIC);
        if (prevPem == null || prevPem.isBlank()) {
            return Optional.empty();
        }
        RSAPublicKey prevPub = parsePublicKey(prevPem);
        String prevKid = getenv.apply(ENV_PREV_KEY_ID);
        if (prevKid == null || prevKid.isBlank()) {
            prevKid = "profitflow-0";
        }
        if (log.isInfoEnabled()) {
            log.info("JWT verification will fall back to previous key kid={}", prevKid.trim());
        }
        return Optional.of(prevPub);
    }

    /**
     * Returns the RSA public key used to verify JWT signatures.
     *
     * @throws IllegalStateException in production if no env-var key is configured
     */
    public RSAPublicKey publicKey() {
        String pem = getenv.apply("RSA_PUBLIC_KEY_PEM");
        if (pem != null && !pem.isBlank()) {
            return parsePublicKey(pem);
        }
        if (isProductionProfile()) {
            guardClasspathUsage("RSA_PUBLIC_KEY_PEM");
        }
        RSAPublicKey classpathPublic = classpathKeys != null ? classpathKeys.rsaPublicKey() : null;
        RSAPrivateKey classpathPrivate = classpathKeys != null ? classpathKeys.rsaPrivateKey() : null;

        // IMPORTANT: Signing requires a matched keypair. If only the public key is present,
        // we must NOT use it together with a generated private key (would break login with 500).
        if (classpathPublic != null && classpathPrivate != null) {
            if (log.isWarnEnabled()) {
                log.warn("SECURITY WARNING: RSA_PUBLIC_KEY_PEM is not set. Using bundled classpath keypair. {}",
                        LOCAL_DEV_ONLY_NEVER_PROD);
            }
            return classpathPublic;
        }
        if (classpathPublic != null && classpathPrivate == null) {
            if (log.isWarnEnabled()) {
                log.warn("SECURITY WARNING: Only a classpath public key is configured (no private key). "
                        + "Generating an ephemeral dev keypair so JWT signing works. "
                        + "Tokens will be invalid after restart.");
            }
        }
        return (RSAPublicKey) devKeyPair().getPublic();
    }

    /**
     * Returns the RSA private key used to sign JWTs.
     *
     * @throws IllegalStateException in production if no env-var key is configured
     */
    public RSAPrivateKey privateKey() {
        String pem = getenv.apply("RSA_PRIVATE_KEY_PEM");
        if (pem != null && !pem.isBlank()) {
            return parsePrivateKey(pem);
        }
        if (isProductionProfile()) {
            guardClasspathUsage("RSA_PRIVATE_KEY_PEM");
        }
        RSAPublicKey classpathPublic = classpathKeys != null ? classpathKeys.rsaPublicKey() : null;
        RSAPrivateKey classpathPrivate = classpathKeys != null ? classpathKeys.rsaPrivateKey() : null;
        if (classpathPublic != null && classpathPrivate != null) {
            if (log.isWarnEnabled()) {
                log.warn("SECURITY WARNING: RSA_PRIVATE_KEY_PEM is not set. Using bundled classpath keypair. {}",
                        LOCAL_DEV_ONLY_NEVER_PROD);
            }
            return classpathPrivate;
        }
        if (log.isWarnEnabled()) {
            log.warn("SECURITY WARNING: RSA_PRIVATE_KEY_PEM is not set and no classpath private key is configured. "
                    + "Generating an ephemeral dev keypair for this JVM. Tokens will be invalid after restart.");
        }
        return (RSAPrivateKey) devKeyPair().getPrivate();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private KeyPair devKeyPair() {
        KeyPair current = generatedDevKeyPair.get();
        if (current != null) {
            return current;
        }
        synchronized (this) {
            KeyPair existing = generatedDevKeyPair.get();
            if (existing != null) {
                return existing;
            }
            try {
                KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
                g.initialize(2048);
                KeyPair pair = g.generateKeyPair();
                generatedDevKeyPair.set(pair);
                return pair;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to generate ephemeral RSA keypair: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Rejects startup in the {@code prod} profile if the named env var is absent.
     * In other profiles, logs a prominent security warning instead.
     */
    private void guardClasspathUsage(String envVarName) {
        if (isProductionProfile()) {
            throw new IllegalStateException(
                    "SECURITY VIOLATION: JWT signing key '" + envVarName + "' must be supplied "
                    + "as an environment variable in the 'prod' profile. "
                    + "Classpath/bundled keys are forbidden in production — they allow anyone "
                    + "with image access to forge tokens with any role.");
        }
        if (log.isWarnEnabled()) {
            log.warn("SECURITY WARNING: {} is not set. Using bundled classpath key. {}",
                    envVarName, LOCAL_DEV_ONLY_NEVER_PROD);
        }
    }

    private boolean isProductionProfile() {
        return activeProfiles != null && activeProfiles.contains("prod");
    }

    /**
     * Parses a PEM-encoded RSA public key (PKCS#8 / X.509 SubjectPublicKeyInfo format).
     *
     * @param pem full PEM text including {@code -----BEGIN PUBLIC KEY-----} header
     */
    private static RSAPublicKey parsePublicKey(String pem) {
        try {
            String base64 = stripPemHeaders(pem);
            byte[] der = Base64.getDecoder().decode(base64);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse RSA public key from RSA_PUBLIC_KEY_PEM: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a PEM-encoded RSA private key (PKCS#8 format).
     *
     * @param pem full PEM text including {@code -----BEGIN PRIVATE KEY-----} header
     */
    private static RSAPrivateKey parsePrivateKey(String pem) {
        try {
            String base64 = stripPemHeaders(pem);
            byte[] der = Base64.getDecoder().decode(base64);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse RSA private key from RSA_PRIVATE_KEY_PEM: " + e.getMessage(), e);
        }
    }

    /**
     * Removes PEM header/footer lines and all whitespace to produce a raw base64 string.
     */
    private static String stripPemHeaders(String pem) {
        return pem
                .replaceAll("-----BEGIN[^-]*-----", "")
                .replaceAll("-----END[^-]*-----", "")
                .replaceAll("\\s", "");
    }
}
