package de.gxp.infrastructure.testsupport;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Test profile that turns the operator switch of auth-hardening REQ-603
 * (design D4/D5) ON with the loopback as the trusted proxy: rest-assured's
 * origin ({@code 127.0.0.1}) becomes the relay, so a test that sends
 * {@code Forwarded: for=<address>} is attributed to that address and flagged
 * as via-proxy. Exactly the four {@code quarkus.http.proxy.*} lines from
 * {@code application.properties}, flipped. One profile class on purpose — a
 * profile costs one extra Quarkus boot (~60 s on the Pi).
 */
public class TrustedProxyProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.http.proxy.proxy-address-forwarding", "true",
                "quarkus.http.proxy.allow-forwarded", "true",
                "quarkus.http.proxy.enable-trusted-proxy-header", "true",
                "quarkus.http.proxy.trusted-proxies", "127.0.0.1/32");
    }
}
