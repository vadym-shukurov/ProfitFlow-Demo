package com.profitflow.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ClientIpResolver}.
 *
 * <p>Verifies the trusted-proxy model: {@code X-Forwarded-For} is only trusted
 * when the direct connection comes from a configured trusted proxy CIDR.
 */
class ClientIpResolverTest {

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        // Trust loopback and a private /24 subnet
        resolver = new ClientIpResolver("127.0.0.1,::1,10.0.1.0/24");
    }

    // ── Direct connections (no proxy) ────────────────────────────────────────

    @Test
    void directConnectionReturnsRemoteAddr() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.42");
        req.addHeader("X-Forwarded-For", "1.2.3.4"); // untrusted — should be ignored

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.42");
    }

    @Test
    void directConnectionIgnoresForwardedHeader() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("8.8.8.8"); // not in trusted list
        req.addHeader("X-Forwarded-For", "10.0.0.1, 172.16.0.1");

        assertThat(resolver.resolve(req)).isEqualTo("8.8.8.8");
    }

    // ── Trusted proxy — loopback ──────────────────────────────────────────────

    @Test
    void trustedProxyLoopbackHonorsForwardedFor() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Forwarded-For", "198.51.100.5, 10.0.0.2");

        // Leftmost IP is the genuine client; trailing entries are proxy hops
        assertThat(resolver.resolve(req)).isEqualTo("198.51.100.5");
    }

    @Test
    void trustedProxyWithNoForwardedHeaderReturnsRemoteAddr() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        // No X-Forwarded-For — internal health-check scenario

        assertThat(resolver.resolve(req)).isEqualTo("127.0.0.1");
    }

    @Test
    void trustedProxyIpv6LoopbackHonorsForwardedFor() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("::1");
        req.addHeader("X-Forwarded-For", "2001:db8::1");

        assertThat(resolver.resolve(req)).isEqualTo("2001:db8::1");
    }

    // ── Trusted proxy — CIDR range ────────────────────────────────────────────

    @Test
    void trustedCidrProxyHonorsForwardedFor() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.1.55"); // inside 10.0.1.0/24
        req.addHeader("X-Forwarded-For", "203.0.113.99");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.99");
    }

    @Test
    void ipOutsideCidrRangeIsNotTrusted() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.2.1"); // outside 10.0.1.0/24 trusted range
        req.addHeader("X-Forwarded-For", "203.0.113.99");

        assertThat(resolver.resolve(req)).isEqualTo("10.0.2.1");
    }

    // ── isTrustedProxy helpers ────────────────────────────────────────────────

    @Test
    void exactIpMatchIsTrusted() {
        assertThat(resolver.isTrustedProxy("127.0.0.1")).isTrue();
    }

    @Test
    void unknownIpIsNotTrusted() {
        assertThat(resolver.isTrustedProxy("192.168.1.1")).isFalse();
    }

    @Test
    void nullRemoteAddrIsNotTrusted() {
        assertThat(resolver.isTrustedProxy(null)).isFalse();
    }

    @Test
    void cidrBoundaryFirstAddressIsTrusted() {
        assertThat(resolver.isTrustedProxy("10.0.1.0")).isTrue();
    }

    @Test
    void cidrBoundaryLastAddressIsTrusted() {
        assertThat(resolver.isTrustedProxy("10.0.1.255")).isTrue();
    }

    @Test
    void cidrWithZeroPrefixMatchesAllIpv4() {
        ClientIpResolver wide = new ClientIpResolver("0.0.0.0/0");

        assertThat(wide.isTrustedProxy("192.168.0.1")).isTrue();
    }

    @Test
    void ipv6AddressDoesNotMatchIpv4CidrRule() {
        ClientIpResolver v4only = new ClientIpResolver("10.0.0.0/8");

        assertThat(v4only.isTrustedProxy("2001:db8::1")).isFalse();
    }

    @Test
    void trustedProxyWithEmptyLeftmostForwardedSegmentKeepsRemoteAddr() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Forwarded-For", " , 203.0.113.1");

        assertThat(resolver.resolve(req)).isEqualTo("127.0.0.1");
    }

    @Test
    void blankOnlyForwardedHeaderKeepsRemoteAddr() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Forwarded-For", "   ");

        assertThat(resolver.resolve(req)).isEqualTo("127.0.0.1");
    }
}
