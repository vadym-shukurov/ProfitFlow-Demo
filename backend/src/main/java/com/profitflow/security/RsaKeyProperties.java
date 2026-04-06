package com.profitflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Typed configuration holder for the RSA key pair used to sign and verify JWTs.
 *
 * <p>Both keys are read from PEM files whose paths are configured in
 * {@code application.yml} under the {@code profitflow.security} prefix:
 * <pre>
 * profitflow:
 *   security:
 *     rsa-public-key:  classpath:certs/public.pem
 *     rsa-private-key: classpath:certs/private.pem
 * </pre>
 *
 * <p>In production, mount the private key via a Kubernetes secret or AWS Secrets
 * Manager, never bake it into the container image.
 */
@ConfigurationProperties(prefix = "profitflow.security")
public record RsaKeyProperties(RSAPublicKey rsaPublicKey, RSAPrivateKey rsaPrivateKey) {
}
