package de.gxp.auth.control;

import java.util.Set;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Infrastructure adapter (not a Boundary/Control): extracts the
 * {@code Authorization: Bearer} token and hands it to the security layer —
 * the SPI of the approved {@code quarkus-security}/{@code quarkus-vertx-http}
 * extensions, no new dependency (REQ-332). Zero business logic here: token
 * lookup lives in the package-private {@link TokenStore} via
 * {@link TokenIdentityProvider}.
 *
 * <p>Requests without a bearer token proceed anonymously and fail closed at
 * {@code @RolesAllowed} with the 401 challenge below ({@code WWW-Authenticate:
 * Bearer}). Nothing about the token is ever logged.
 */
@ApplicationScoped
class BearerAuthMechanism implements HttpAuthenticationMechanism {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String header = context.request().getHeader(HttpHeaderNames.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Uni.createFrom().nullItem(); // anonymous — deny-unannotated / @RolesAllowed fail closed
        }
        String rawToken = header.substring(BEARER_PREFIX.length());
        return identityProviderManager.authenticate(new TokenAuthenticationRequest(new TokenCredential(rawToken, "bearer")));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401, "WWW-Authenticate", "Bearer"));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(TokenAuthenticationRequest.class);
    }
}
