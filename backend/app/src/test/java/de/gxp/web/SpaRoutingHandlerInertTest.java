package de.gxp.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

/**
 * REQ-526 — the fallback's inert half: without a bundled
 * {@code META-INF/resources/index.html} the handler registers NO route at
 * all, so a bundle-less instance ({@code %dev} backend-only) cannot reroute
 * into a 404 loop. A plain unit test (no Quarkus boot): the test classpath
 * carries the fixture, so absence is simulated by overriding the one
 * package-visible probe.
 */
class SpaRoutingHandlerInertTest {

    @Test
    void withoutABundleNoRouteIsRegistered() {
        Vertx vertx = Vertx.vertx();
        try {
            Router router = Router.router(vertx);
            SpaRoutingHandler handler = new SpaRoutingHandler() {
                @Override
                boolean bundlePresent() {
                    return false;
                }
            };

            handler.install(router);

            assertTrue(router.getRoutes().isEmpty(), "no bundle -> the handler must stay inert");
        } finally {
            vertx.close();
        }
    }

    @Test
    void withABundleTheRouteIsRegistered() {
        Vertx vertx = Vertx.vertx();
        try {
            Router router = Router.router(vertx);
            new SpaRoutingHandler().install(router); // fixture on the test classpath

            assertFalse(router.getRoutes().isEmpty(), "bundle present -> exactly the fallback route");
        } finally {
            vertx.close();
        }
    }
}
