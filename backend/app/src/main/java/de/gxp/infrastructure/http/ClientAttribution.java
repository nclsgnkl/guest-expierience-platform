package de.gxp.infrastructure.http;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.RequestScoped;

/**
 * Infrastructure helper — the ONE transport fact the application consumes
 * (constitution v1.8.0, principle 4; auth-hardening REQ-603, D4/D5): the
 * attributed client address and whether the request came through the
 * configured trusted proxy. It drives the login throttle bucket and the
 * second-factor requirement, and nothing else.
 *
 * <p>Attribution is Quarkus' own {@code quarkus.http.proxy.*} support, never
 * a hand-parsed header: with {@code proxy-address-forwarding} on and the
 * socket peer inside {@code trusted-proxies}, {@code remoteAddress()} is
 * already the forwarded address and Quarkus has stamped
 * {@code X-Forwarded-Trusted-Proxy: true} (stripping a forged one). From any
 * other peer the headers are ignored by Quarkus itself. Because that handler
 * is only installed when forwarding is on, the {@code enable-trusted-proxy-header}
 * config value is additionally consulted here — it is what makes a forged
 * header inert on a LAN-only box where nothing strips it.
 *
 * <p>Quarkus honours the FIRST {@code for=} element of a multi-hop
 * {@code Forwarded} header (verified against 3.33.3.1, pinned in
 * {@code TrustedProxyLoginTest}), so the relay MUST replace the header with
 * exactly one element, never append ({@code docs/backend/remote-operations.md}
 * §7, "Relay obligation"). An address the relay let through that is not an IP
 * literal (e.g. RFC 7239's obfuscated {@code for=_hidden}) has no
 * {@code hostAddress()}; it is answered {@code 400 unattributable-address}
 * rather than bucketed under a shared token or silently downgraded to
 * "not via proxy" (which would drop the second-factor requirement).
 *
 * <p>Read once per request; logs nothing.
 */
@RequestScoped
public class ClientAttribution {

    /** The header Quarkus stamps for a request forwarded by a trusted proxy. */
    static final String TRUSTED_PROXY_HEADER = "X-Forwarded-Trusted-Proxy";

    /** Attributed address (socket peer or the trusted proxy's forwarded address). */
    public record Attribution(String address, boolean viaTrustedProxy) {
    }

    private final CurrentVertxRequest currentRequest;
    private final boolean trustedProxyHeaderEnabled;
    private Attribution attribution;

    ClientAttribution(CurrentVertxRequest currentRequest,
            @ConfigProperty(name = "quarkus.http.proxy.enable-trusted-proxy-header",
                    defaultValue = "false") boolean trustedProxyHeaderEnabled) {
        this.currentRequest = currentRequest;
        this.trustedProxyHeaderEnabled = trustedProxyHeaderEnabled;
    }

    public Attribution attribution() {
        if (attribution == null) {
            HttpServerRequest request = currentRequest.getCurrent().request();
            boolean viaProxy = trustedProxyHeaderEnabled
                    && "true".equalsIgnoreCase(request.getHeader(TRUSTED_PROXY_HEADER));
            String address = request.remoteAddress() == null ? null : request.remoteAddress().hostAddress();
            if (address == null || address.isBlank()) {
                throw ApiErrors.badRequest("unattributable-address",
                        "The forwarded client address is not an IP address");
            }
            attribution = new Attribution(address, viaProxy);
        }
        return attribution;
    }
}
