package de.gxp.infrastructure.http;

import java.util.List;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import de.gxp.api.model.ApiError;
import de.gxp.api.model.ApiErrorDetail;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Infrastructure adapters (REQ-325): every non-2xx carries the shared
 * {@link ApiError} shape — including contract/bean-validation failures and
 * malformed JSON, which would otherwise use framework-default bodies. This
 * mirrors the foundation's pinned precedent that the JAX-RS runtime, not the
 * Boundary, maps exceptions.
 *
 * <p>Messages name the violated constraint's path, never a submitted value —
 * a request body can contain guest-sensitive data (principle 3).
 */
public final class ApiErrorMappers {

    private ApiErrorMappers() {
    }

    /** Bean-validation failures on generated DTOs → 400 {@link ApiError}. */
    @Provider
    public static class ConstraintViolationMapper implements ExceptionMapper<ConstraintViolationException> {
        @Override
        public Response toResponse(ConstraintViolationException exception) {
            List<ApiErrorDetail> details = exception.getConstraintViolations().stream()
                    .map(violation -> new ApiErrorDetail(
                            violation.getPropertyPath() + ": " + violation.getMessage())
                            .reference(String.valueOf(violation.getPropertyPath())))
                    .toList();
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ApiError()
                            .code("validation-failed")
                            .message("The request does not satisfy the contract's constraints")
                            .details(details))
                    .build();
        }
    }

    /**
     * Structurally wrong bodies (e.g. a missing REQUIRED creator property —
     * the generated models bind required fields through a
     * {@code @JsonCreator} constructor, so Jackson rejects those before bean
     * validation ever runs) → 400 {@link ApiError}. Registered for the exact
     * exception type so it takes precedence over Quarkus' built-in mapper,
     * which would answer with a framework-default body. Only property PATHS
     * are echoed, never submitted values.
     */
    @Provider
    public static class MismatchedInputMapper implements ExceptionMapper<MismatchedInputException> {
        @Override
        public Response toResponse(MismatchedInputException exception) {
            List<ApiErrorDetail> details = exception.getPath().stream()
                    .map(reference -> reference.getFieldName())
                    .filter(java.util.Objects::nonNull)
                    .map(field -> new ApiErrorDetail("Property '" + field + "' is missing or has the wrong type")
                            .reference(field))
                    .toList();
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ApiError()
                            .code("validation-failed")
                            .message("The request body does not match the contract's required shape")
                            .details(details.isEmpty()
                                    ? List.of(new ApiErrorDetail("The request body does not match the schema"))
                                    : details))
                    .build();
        }
    }

    /**
     * Malformed/unparseable JSON bodies → 400 {@link ApiError}. The parser's
     * own message is deliberately NOT forwarded: it quotes the raw input.
     */
    @Provider
    public static class JsonParseMapper implements ExceptionMapper<JacksonException> {
        @Override
        public Response toResponse(JacksonException exception) {
            return malformedJsonResponse();
        }
    }

    /**
     * Quarkus REST's Jackson body reader wraps parse failures
     * ({@code StreamReadException} & friends — everything except
     * MismatchedInput/InvalidDefinition, which it rethrows as-is) in an
     * ENTITY-LESS {@code WebApplicationException(cause, 400)} before any
     * mapper runs, so {@link JsonParseMapper} never sees them and the client
     * would get a bare 400 without body or content type (verified against
     * 3.33.3.1: {@code ServerJacksonMessageBodyReader#readFrom}). This mapper
     * catches the wrapped case and answers the shared shape. Every other
     * WebApplicationException keeps its original response untouched — and
     * entity-carrying ones (all {@link ApiErrors} outcomes) never reach
     * mappers at all, the runtime uses their response directly.
     */
    @Provider
    public static class WrappedJacksonExceptionMapper implements ExceptionMapper<WebApplicationException> {
        @Override
        public Response toResponse(WebApplicationException exception) {
            if (exception.getCause() instanceof JacksonException) {
                return malformedJsonResponse();
            }
            return exception.getResponse();
        }
    }

    private static Response malformedJsonResponse() {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiError()
                        .code("malformed-request-body")
                        .message("The request body is not parseable JSON for this operation"))
                .build();
    }
}
