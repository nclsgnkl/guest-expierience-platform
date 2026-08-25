package de.gxp.guest.control;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

/**
 * Infrastructure adapter (REQ-423, the {@code ResponseStatusFilter}
 * category — no business logic): strong ETags for {@code /api/guest/**} JSON
 * so steady-state kiosk polling costs {@code 304}s.
 *
 * <p>Interception point (the design's verify-at-implementation item): a
 * {@link ContainerResponseFilter} that serializes the entity through the
 * CDI {@link ObjectMapper} — the SAME mapper Quarkus REST Jackson writes the
 * response with, so the hashed bytes are the wire bytes. A
 * {@code WriterInterceptor} would see the truly-serialized stream but cannot
 * rewrite an already-started response into a 304; the filter can. The double
 * serialization per hit is the design's accepted trade-off (recompute over
 * cache invalidation). Deterministic: generated DTOs serialize with fixed
 * property order and localized maps are {@code LinkedHashMap}s in offered-
 * language order.
 *
 * <p>Asset responses pass through untouched: they are thrown {@code Response}s
 * with image media types and their own stored-SHA ETag.
 */
@Provider
class GuestEtagFilter implements ContainerResponseFilter {

    private final ObjectMapper objectMapper;

    GuestEtagFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        String path = requestContext.getUriInfo().getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.startsWith("/api/guest/") || responseContext.getStatus() != 200
                || responseContext.getEntity() == null) {
            return;
        }
        MediaType mediaType = responseContext.getMediaType();
        if (mediaType == null || !MediaType.APPLICATION_JSON_TYPE.isCompatible(mediaType)) {
            return;
        }
        String etag = sha256Hex(objectMapper.writeValueAsBytes(responseContext.getEntity()));
        responseContext.getHeaders().putSingle(HttpHeaders.ETAG, '"' + etag + '"');
        responseContext.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, "no-cache");
        String ifNoneMatch = requestContext.getHeaderString(HttpHeaders.IF_NONE_MATCH);
        if (ifNoneMatch != null && etag.equals(ifNoneMatch.replace("\"", ""))) {
            responseContext.setStatus(304);
            responseContext.setEntity(null);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
