package de.gxp.infrastructure.web;

import io.quarkus.vertx.http.runtime.RouteConstants;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * SPA deep-link fallback for the bundled guest app (guest-frontend-integration
 * REQ-526, 02-design.md §11 — decided with D7, 2026-08-20). An infrastructure
 * adapter beside the {@code ApiErrorMappers}/filter precedent: no business
 * logic, not a Boundary — a single late-priority Vert.x route that reroutes a
 * guest deep link (e.g. a kiosk reload on {@code /einstellungen}) to
 * {@code /index.html}, which the static-resources handler then serves.
 *
 * <p>Reroute happens ONLY when all of (pinned in §11):
 * <ul>
 *   <li>the request is a {@code GET},</li>
 *   <li>the path is neither exactly nor a descendant of {@code /api},
 *       {@code /q}, {@code /manage} (the API keeps answering exactly as today
 *       — its own 401/404 JSON, never HTML; {@code /manage} is reserved for
 *       the future manager app and must never be claimed by the guest SPA —
 *       see {@link #isReservedPath(String)}),</li>
 *   <li>the path contains no dot (file requests fall through to the static
 *       handler / a real 404),</li>
 *   <li>the request accepts {@code text/html}.</li>
 * </ul>
 *
 * <p><b>Inert without a bundle:</b> when no app is packaged ({@code %dev}
 * backend-only, {@code %test} without the fixture) the reroute target does
 * not exist and rerouting would 404 uselessly (or loop) — so the route is
 * registered only when {@code META-INF/resources/index.html} is present on
 * the classpath, checked once at startup.
 *
 * <p><b>Route order (verify-at-implementation, resolved against Quarkus
 * 3.33.3.1):</b> {@code RouteConstants.ROUTE_ORDER_AFTER_DEFAULT} (20000) —
 * platform routes (REST, static resources) sit at
 * {@code ROUTE_ORDER_DEFAULT} (10000) and call {@code next()} on no-match,
 * so this route runs after every real handler and before the terminal 404.
 *
 * <p><b>Cache posture (same pin, findings recorded in the feature
 * handoff):</b> the static-resources handler serves everything —
 * {@code index.html} included — with {@code public, immutable,
 * max-age=86400} by default, which would pin a stale app for a day on the
 * direct {@code /} load. Two measures: {@code
 * quarkus.http.static-resources.caching-enabled=false}
 * (application.properties, part of this D7 change) stops the immutable
 * header for the direct loads, and the rerouted response additionally gets
 * an explicit {@code Cache-Control: no-cache} via a headers-end handler
 * (a plain {@code putHeader} before {@code reroute} is overwritten by the
 * static handler).
 */
@ApplicationScoped
public class SpaRoutingHandler {

    void install(@Observes Router router) {
        if (!bundlePresent()) {
            return; // no app packaged — every route keeps answering as today
        }
        router.get()
                .order(RouteConstants.ROUTE_ORDER_AFTER_DEFAULT)
                .handler(SpaRoutingHandler::reroute);
    }

    /** Package-visible for the unit test of the inert half. */
    boolean bundlePresent() {
        return getClass().getResource("/META-INF/resources/index.html") != null;
    }

    /**
     * The three prefixes the SPA fallback may never claim — <b>including
     * their exact, trailing-slash-less form</b>. A {@code startsWith("/manage/")}
     * test alone let {@code GET /manage} fall through to the rewrite and
     * answered a human typing the manager URL with the guest kiosk app
     * (review finding 🟠-1); REQ-526 reserves that path for the future
     * manager frontend, so it must 404 here exactly as it does today.
     */
    private static boolean isReservedPath(String path) {
        return path.equals("/api") || path.startsWith("/api/")
                || path.equals("/q") || path.startsWith("/q/")
                || path.equals("/manage") || path.startsWith("/manage/");
    }

    private static void reroute(RoutingContext context) {
        String path = context.normalizedPath();
        if (isReservedPath(path) || path.contains(".")) {
            context.next();
            return;
        }
        String accept = context.request().getHeader(HttpHeaders.ACCEPT);
        if (accept == null || !accept.contains("text/html")) {
            context.next();
            return;
        }
        // Headers-end on the RESPONSE object, not putHeader-now and not the
        // RoutingContext's list: the static handler serving the rerouted file
        // writes its own headers, and the context's handler list does not
        // survive a reroute — the response object does.
        context.response().headersEndHandler(
                v -> context.response().putHeader(HttpHeaders.CACHE_CONTROL, "no-cache"));
        context.reroute("/index.html");
    }
}
