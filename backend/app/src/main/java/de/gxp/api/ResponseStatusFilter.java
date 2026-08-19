package de.gxp.api;

import java.lang.reflect.Method;

import org.jboss.resteasy.reactive.ResponseStatus;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;

/**
 * Infrastructure adapter: honors {@link ResponseStatus} on Boundary overrides
 * at runtime.
 *
 * <p>Quarkus REST resolves {@code @ResponseStatus} at build time from the
 * method that carries the JAX-RS annotations — for this project's
 * contract-first Boundaries that is the GENERATED interface method, so the
 * annotation on the implementing override is invisible to the build-time
 * scanner and creates would answer 200 instead of the contract's 201
 * (verified against 3.33.3.1: {@code ServerEndpointIndexer} hands its method
 * scanners the interface's {@code MethodInfo}). {@code ResourceInfo}, by
 * contrast, resolves the method against the ACTUAL resource class, so the
 * override — and its runtime-retained annotation — is visible here.
 *
 * <p>Only the default 200 is rewritten; explicitly built responses (thrown
 * {@code Response}s such as the asset binary 200/304, and every error path)
 * keep their status untouched.
 */
@Provider
class ResponseStatusFilter implements ContainerResponseFilter {

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (responseContext.getStatus() != 200) {
            return;
        }
        Method method = resourceInfo.getResourceMethod();
        if (method == null) {
            return; // no matched endpoint (e.g. a 404 route)
        }
        ResponseStatus responseStatus = method.getAnnotation(ResponseStatus.class);
        if (responseStatus != null) {
            responseContext.setStatus(responseStatus.value());
        }
    }
}
