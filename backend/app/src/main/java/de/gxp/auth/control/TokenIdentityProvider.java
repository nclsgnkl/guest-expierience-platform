package de.gxp.auth.control;

import java.security.Principal;
import java.time.Instant;

import de.gxp.device.control.DeviceControl;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Infrastructure adapter: ONE bearer authentication path, two token kinds
 * (REQ-410 — "one authentication path to review, not two"). A presented
 * token is resolved first against the in-memory manager {@link TokenStore}
 * (a hit slides the idle expiry, REQ-327, role {@code admin}), then against
 * the device table by hash via {@link DeviceControl#verifyToken} (role
 * {@code device}, with the device/room identity attached — the ONLY source
 * a guest request's room is ever derived from, REQ-411). A miss on both —
 * unknown, idled-out or revoked — fails authentication into the mechanism's
 * existing 401 + Bearer challenge (REQ-412).
 *
 * <p>The device lookup is one indexed query per request — deliberate:
 * instant revocation beats caching a credential check. It runs via
 * {@code context.runBlocking(...)} because authentication may start on the
 * event loop and the lookup is blocking JDBC.
 *
 * <p>Manager identities carry the token hash (logout) and refreshed expiry
 * ({@code GXP-Token-Expires}); device identities carry deviceId/roomId and
 * deliberately NO expiry attribute — device tokens do not slide, and the
 * expiry response filter skips them by that absence. No token material is
 * logged and failures carry no message (constitution principle 3).
 */
@ApplicationScoped
class TokenIdentityProvider implements IdentityProvider<TokenAuthenticationRequest> {

    static final String ROLE_ADMIN = "admin";
    static final String ATTRIBUTE_TOKEN_HASH = "gxp.token.hash";
    static final String ATTRIBUTE_TOKEN_EXPIRY = "gxp.token.expiry";

    private final TokenStore tokenStore;
    private final DeviceControl deviceControl;

    TokenIdentityProvider(TokenStore tokenStore, DeviceControl deviceControl) {
        this.tokenStore = tokenStore;
        this.deviceControl = deviceControl;
    }

    @Override
    public Class<TokenAuthenticationRequest> getRequestType() {
        return TokenAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(TokenAuthenticationRequest request, AuthenticationRequestContext context) {
        String tokenHash = TokenStore.sha256Hex(request.getToken().getToken());
        Instant refreshedExpiry = tokenStore.slide(tokenHash);
        if (refreshedExpiry != null) {
            return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
                    .setPrincipal((Principal) () -> ROLE_ADMIN)
                    .addRole(ROLE_ADMIN)
                    .addAttribute(ATTRIBUTE_TOKEN_HASH, tokenHash)
                    .addAttribute(ATTRIBUTE_TOKEN_EXPIRY, refreshedExpiry)
                    .build());
        }
        return context.runBlocking(() -> {
            DeviceControl.DeviceIdentity device = deviceControl.verifyToken(tokenHash);
            if (device == null) {
                throw new AuthenticationFailedException();
            }
            return QuarkusSecurityIdentity.builder()
                    .setPrincipal((Principal) () -> AuthControl.ROLE_DEVICE)
                    .addRole(AuthControl.ROLE_DEVICE)
                    .addAttribute(AuthControl.DEVICE_ID_ATTRIBUTE, device.deviceId())
                    .addAttribute(AuthControl.DEVICE_ROOM_ATTRIBUTE, device.roomId())
                    .build();
        });
    }
}
