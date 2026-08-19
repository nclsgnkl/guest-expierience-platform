package de.gxp.auth;

import java.security.Principal;
import java.time.Instant;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Infrastructure adapter: resolves a presented bearer token against the
 * package-private in-memory {@link TokenStore}. A hit slides the idle expiry
 * (REQ-327) and yields an identity with role {@code admin}; a miss (unknown
 * or idled-out) fails authentication — the mechanism's 401 challenge answers.
 *
 * <p>The identity carries two attributes for the auth package's own use:
 * the token's SHA-256 (so logout can drop exactly this token without ever
 * re-reading the raw value) and the refreshed expiry (so the response filter
 * can expose {@code GXP-Token-Expires}). No token material is logged and the
 * failure carries no message (constitution principle 3).
 */
@ApplicationScoped
class TokenIdentityProvider implements IdentityProvider<TokenAuthenticationRequest> {

    static final String ROLE_ADMIN = "admin";
    static final String ATTRIBUTE_TOKEN_HASH = "gxp.token.hash";
    static final String ATTRIBUTE_TOKEN_EXPIRY = "gxp.token.expiry";

    private final TokenStore tokenStore;

    TokenIdentityProvider(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public Class<TokenAuthenticationRequest> getRequestType() {
        return TokenAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(TokenAuthenticationRequest request, AuthenticationRequestContext context) {
        String tokenHash = TokenStore.sha256Hex(request.getToken().getToken());
        Instant refreshedExpiry = tokenStore.slide(tokenHash);
        if (refreshedExpiry == null) {
            return Uni.createFrom().failure(AuthenticationFailedException::new);
        }
        return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
                .setPrincipal((Principal) () -> ROLE_ADMIN)
                .addRole(ROLE_ADMIN)
                .addAttribute(ATTRIBUTE_TOKEN_HASH, tokenHash)
                .addAttribute(ATTRIBUTE_TOKEN_EXPIRY, refreshedExpiry)
                .build());
    }
}
