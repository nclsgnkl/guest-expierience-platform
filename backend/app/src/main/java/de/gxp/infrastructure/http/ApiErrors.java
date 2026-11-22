package de.gxp.infrastructure.http;

import java.util.List;

import de.gxp.api.model.ApiError;
import de.gxp.api.model.ApiErrorDetail;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Infrastructure helper: builds the thrown non-2xx outcomes every Control
 * uses (the foundation's pinned pattern — non-200 is thrown from the Control,
 * as a {@code jakarta.ws.rs} exception carrying the shared {@link ApiError}).
 * Messages must never contain guest-sensitive values (principle 3) — callers
 * pass codes, field names and identifiers, not payload content.
 */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static WebApplicationException badRequest(String code, String message) {
        return withStatus(400, code, message, null);
    }

    public static WebApplicationException badRequest(String code, String message, List<ApiErrorDetail> details) {
        return withStatus(400, code, message, details);
    }

    public static WebApplicationException unauthorized(String code, String message) {
        return withStatus(401, code, message, null);
    }

    public static WebApplicationException notFound(String code, String message) {
        return withStatus(404, code, message, null);
    }

    public static WebApplicationException conflict(String code, String message) {
        return withStatus(409, code, message, null);
    }

    public static WebApplicationException conflict(String code, String message, List<ApiErrorDetail> details) {
        return withStatus(409, code, message, details);
    }

    public static WebApplicationException payloadTooLarge(String code, String message) {
        return withStatus(413, code, message, null);
    }

    /**
     * {@code 429} with a {@code Retry-After} header (seconds) — the one
     * header-carrying builder; used by the per-address login throttle
     * (auth-hardening REQ-602, D3). Deliberately a whole number of seconds
     * and nothing else: no address, no count, no bucket detail.
     */
    public static WebApplicationException tooManyRequests(String code, String message, long retryAfterSeconds) {
        return new WebApplicationException(responseWith(429, code, message, null)
                .header("Retry-After", Long.toString(retryAfterSeconds))
                .build());
    }

    public static WebApplicationException serviceUnavailable(String code, String message) {
        return withStatus(503, code, message, null);
    }

    private static WebApplicationException withStatus(int status, String code, String message,
            List<ApiErrorDetail> details) {
        return new WebApplicationException(responseWith(status, code, message, details).build());
    }

    private static Response.ResponseBuilder responseWith(int status, String code, String message,
            List<ApiErrorDetail> details) {
        ApiError error = new ApiError().code(code).message(message);
        if (details != null && !details.isEmpty()) {
            error.details(details);
        }
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(error);
    }
}
