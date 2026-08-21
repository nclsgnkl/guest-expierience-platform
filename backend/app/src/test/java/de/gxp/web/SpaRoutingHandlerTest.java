package de.gxp.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * REQ-526 — the SPA fallback's rewrite half, against the test-classpath
 * {@code META-INF/resources/index.html} fixture (marker:
 * {@code gxp-spa-test-fixture}). The pinned rules of 02-design §11: rewrite
 * dot-less, html-accepting GETs only, and never {@code /api/}, {@code /q/}
 * or the reserved {@code /manage/}.
 */
@QuarkusTest
class SpaRoutingHandlerTest {

    private static final String MARKER = "gxp-spa-test-fixture";

    @Test
    void guestDeepLinkServesIndexHtml() {
        given().header("Accept", "text/html")
                .when().get("/einstellungen")
                .then().statusCode(200)
                .header("Cache-Control", containsString("no-cache"))
                .body(containsString(MARKER));
    }

    @Test
    void nestedDeepLinkServesIndexHtmlToo() {
        given().header("Accept", "text/html")
                .when().get("/some/future/route")
                .then().statusCode(200)
                .body(containsString(MARKER));
    }

    @Test
    void unknownApiPathKeepsAnsweringAsTheApi_neverHtml() {
        given().header("Accept", "text/html")
                .when().get("/api/nonsense")
                .then().statusCode(404)
                .body(not(containsString(MARKER)));
    }

    @Test
    void realApiBehaviorIsByteForByteUntouched() {
        // Anonymous guest call: the API's own 401 challenge, not the app shell.
        given().header("Accept", "application/json")
                .when().get("/api/guest/session")
                .then().statusCode(401)
                .body(not(containsString(MARKER)));
        // Even an html-only Accept gets the API's own answer (REST's 406
        // content negotiation) — never the rewritten app shell.
        given().header("Accept", "text/html")
                .when().get("/api/guest/session")
                .then().statusCode(406)
                .body(not(containsString(MARKER)));
    }

    @Test
    void managementPathsAreNeverRewritten() {
        // No health extension is installed, so /q/health is a genuine 404 —
        // which is exactly the proof: a dot-less, html-accepting GET under
        // /q/ did NOT become the app shell.
        given().header("Accept", "text/html")
                .when().get("/q/health")
                .then().statusCode(404)
                .body(not(containsString(MARKER)));
    }

    @Test
    void reservedManagerPathIsNeverClaimedByTheGuestApp() {
        given().header("Accept", "text/html")
                .when().get("/manage/rooms")
                .then().statusCode(404)
                .body(not(containsString(MARKER)));
    }

    @Test
    void dottedPathsFallThroughToRealFilesOrRealNotFound() {
        given().header("Accept", "text/html")
                .when().get("/logo.png")
                .then().statusCode(404)
                .body(not(containsString(MARKER)));
    }

    @Test
    void nonHtmlAcceptFallsThrough() {
        given().header("Accept", "application/json")
                .when().get("/einstellungen")
                .then().statusCode(404)
                .body(not(containsString(MARKER)));
    }
}
