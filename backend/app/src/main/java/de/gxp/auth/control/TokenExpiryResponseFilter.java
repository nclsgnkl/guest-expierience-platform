package de.gxp.auth.control;

import java.time.Instant;

import de.gxp.api.model.LoginResponse;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Infrastructure adapter (REQ-327/REQ-331): exposes the refreshed idle expiry
 * as {@code GXP-Token-Expires} (ISO-8601 instant) on every authenticated
 * response — the identity carries the slid expiry — and on the login response
 * itself, taken from its body. Clients use it to warn before idle-out.
 *
 * <p>Device identities (REQ-412) are skipped BY CONSTRUCTION: device tokens
 * do not slide, so their identities never carry the expiry attribute and the
 * null-check below leaves the response header-free — verified by test.
 */
@Provider
class TokenExpiryResponseFilter implements ContainerResponseFilter {

    static final String HEADER = "GXP-Token-Expires";

    private final SecurityIdentity identity;

    TokenExpiryResponseFilter(SecurityIdentity identity) {
        this.identity = identity;
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (responseContext.getEntity() instanceof LoginResponse login && login.getExpiresAt() != null) {
            responseContext.getHeaders().putSingle(HEADER, login.getExpiresAt().toInstant().toString());
            return;
        }
        if (!identity.isAnonymous()) {
            Instant expiry = identity.getAttribute(TokenIdentityProvider.ATTRIBUTE_TOKEN_EXPIRY);
            if (expiry != null) {
                responseContext.getHeaders().putSingle(HEADER, expiry.toString());
            }
        }
    }
}
