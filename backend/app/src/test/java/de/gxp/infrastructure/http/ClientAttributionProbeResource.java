package de.gxp.infrastructure.http;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * TEST-ONLY Boundary (src/test, never shipped; precedent
 * {@code de.gxp.security.FailClosedResource}) that echoes what
 * {@link ClientAttribution} derived for the calling request, so the
 * proxy-attribution posture is observable over HTTP in both test profiles.
 */
@Path("/test/client-attribution")
public class ClientAttributionProbeResource {

    private final ClientAttribution clientAttribution;

    ClientAttributionProbeResource(ClientAttribution clientAttribution) {
        this.clientAttribution = clientAttribution;
    }

    @GET
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public ClientAttribution.Attribution current() {
        return clientAttribution.attribution();
    }
}
