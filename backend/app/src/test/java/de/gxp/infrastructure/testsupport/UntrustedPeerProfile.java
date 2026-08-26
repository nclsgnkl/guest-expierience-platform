package de.gxp.infrastructure.testsupport;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * The feature-ON negative (review of T-01…T-04, finding #3): the operator
 * switch is on exactly as in {@link TrustedProxyProfile}, but the trusted
 * proxy is {@code 192.0.2.1/32} — an address rest-assured's loopback origin
 * is NOT. Every {@code Forwarded}/{@code X-Forwarded-*} header the test
 * sends therefore arrives from an untrusted peer and must be ignored by
 * Quarkus' own trust check. One more profile costs one more boot; it is paid
 * because this is the A1.4 defect the spec names as most likely.
 */
public class UntrustedPeerProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.http.proxy.proxy-address-forwarding", "true",
                "quarkus.http.proxy.allow-forwarded", "true",
                "quarkus.http.proxy.enable-trusted-proxy-header", "true",
                "quarkus.http.proxy.trusted-proxies", "192.0.2.1/32");
    }
}
