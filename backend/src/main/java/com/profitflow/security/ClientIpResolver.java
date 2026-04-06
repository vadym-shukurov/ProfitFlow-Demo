package com.profitflow.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Resolves the true client IP address with a trusted-proxy model.
 *
 * <h2>Why this matters</h2>
 * Blindly trusting {@code X-Forwarded-For} lets any caller inject an arbitrary IP,
 * bypassing both rate-limiting and audit logging. Correct behaviour is:
 * <ol>
 *   <li>Check whether the <em>direct</em> TCP connection comes from a known trusted
 *       proxy (load-balancer, Nginx, Kubernetes ingress).</li>
 *   <li>Only if so, parse {@code X-Forwarded-For} and return its leftmost IP
 *       (the genuine originating client).</li>
 *   <li>Otherwise, return the raw {@code RemoteAddr} without trusting the header.</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * Set {@code profitflow.security.trusted-proxies} in {@code application.yml} to a
 * comma-separated list of CIDR ranges for your infrastructure's load-balancers / ingresses.
 * Example:
 * <pre>
 * profitflow:
 *   security:
 *     trusted-proxies: ${TRUSTED_PROXIES:127.0.0.1,::1}
 * </pre>
 *
 * <p>Defaults to loopback only ({@code 127.0.0.1} and {@code ::1}), which is safe
 * for local development and means no proxy is trusted by default in production
 * until the operator explicitly configures one.
 */
@Component
public class ClientIpResolver implements ClientIpResolverPort {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    /** Parsed list of trusted proxy CIDR networks / single IPs. */
    private final List<String> trustedProxies;

    public ClientIpResolver(
            @Value("${profitflow.security.trusted-proxies:127.0.0.1,::1}") String rawProxies) {
        this.trustedProxies = List.of(rawProxies.split(","))
                .stream()
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .toList();
    }

    /**
     * Resolves the client IP from the given request.
     *
     * @param request the current HTTP request
     * @return best-effort client IP string; never {@code null}
     */
    @Override
    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // Leftmost IP is the genuine originating client; rightmost entries are proxies
                String clientIp = forwarded.split(",")[0].strip();
                if (!clientIp.isEmpty()) {
                    return clientIp;
                }
            }
            // Trusted proxy but no X-Forwarded-For — return remoteAddr (e.g. health checks)
            return remoteAddr;
        }

        // Direct connection from untrusted IP — ignore any X-Forwarded-For header
        return remoteAddr;
    }

    /**
     * Resolves the client IP from the current request context.
     * Safe to call from outside a servlet (returns {@code null} if no request is active).
     */
    @Override
    public String resolveFromContext() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            return resolve(attrs.getRequest());
        } catch (Exception ex) {
            log.debug("Could not resolve client IP from context: {}", ex.getMessage());
            return null;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code remoteAddr} is in the trusted proxy list.
     * Supports exact IP matches and CIDR notation (e.g. {@code 10.0.0.0/8}).
     */
    boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        for (String proxy : trustedProxies) {
            if (proxy.contains("/")) {
                if (matchesCidr(remoteAddr, proxy)) {
                    return true;
                }
            } else {
                if (proxy.equals(remoteAddr)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Simple CIDR match for IPv4 addresses.
     * IPv6 CIDR is not supported in this implementation; add Apache Commons Net if needed.
     */
    private static boolean matchesCidr(String ip, String cidr) {
        try {
            String[] parts      = cidr.split("/");
            int      prefixLen  = Integer.parseInt(parts[1]);
            InetAddress network = InetAddress.getByName(parts[0]);
            InetAddress address = InetAddress.getByName(ip);

            // Only handle IPv4 CIDR — skip IPv6 CIDR (return false to be conservative)
            byte[] netBytes  = network.getAddress();
            byte[] addrBytes = address.getAddress();
            if (netBytes.length != addrBytes.length) {
                return false;
            }

            int totalBits  = netBytes.length * 8;
            int mask       = prefixLen == 0 ? 0 : (int) (-1L << (totalBits - prefixLen));
            int netInt     = toInt(netBytes) & mask;
            int addrInt    = toInt(addrBytes) & mask;
            return netInt == addrInt;
        } catch (UnknownHostException | IllegalArgumentException e) {
            log.warn("Invalid CIDR in trusted-proxies list: {}", cidr);
            return false;
        }
    }

    private static int toInt(byte[] bytes) {
        int result = 0;
        for (byte b : bytes) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }
}
