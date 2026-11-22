package de.gxp.update.control;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * update-channel T-07 (REQ-706, REQ-729): the outbound convention against a
 * JDK {@link HttpServer} on an ephemeral loopback port — no dependency, no
 * network. Plain JUnit.
 */
class ManifestClientTest {

    private static HttpServer server;
    private static String base;
    private static final AtomicInteger REDIRECT_TARGET_HITS = new AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicReference<String> SEEN_USER_AGENT = new java.util.concurrent.atomic.AtomicReference<>();
    private final ManifestClient client = new ManifestClient();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/ok", exchange -> {
            SEEN_USER_AGENT.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            respond(exchange, 200, "{\"schemaVersion\": 1}\n".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/drip", exchange -> {
            // headers at once, then one byte per second for 30 s — below the byte cap, above the deadline
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (int i = 0; i < 30; i++) {
                    out.write('x');
                    out.flush();
                    Thread.sleep(1_000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // the client hung up at its deadline — expected
            }
        });
        server.createContext("/missing", exchange -> respond(exchange, 404, "nope".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/big", exchange -> respond(exchange, 200, new byte[70 * 1024]));
        server.createContext("/big-chunked", exchange -> {
            exchange.sendResponseHeaders(200, 0); // chunked — no Content-Length to reject up front
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(new byte[70 * 1024]);
            }
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(12_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, new byte[1]);
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", base + "/redirected");
            respond(exchange, 302, new byte[0]);
        });
        server.createContext("/redirected", exchange -> {
            REDIRECT_TARGET_HITS.incrementAndGet();
            respond(exchange, 200, new byte[1]);
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Test
    void aTwoHundredYieldsTheExactBody() {
        ManifestClient.Fetched fetched = client.get(base + "/ok");
        assertTrue(fetched.ok());
        assertArrayEquals("{\"schemaVersion\": 1}\n".getBytes(StandardCharsets.UTF_8), fetched.body());
        assertNull(fetched.kind());
        assertEquals(ManifestClient.USER_AGENT, SEEN_USER_AGENT.get(), "a fixed User-Agent, no JDK version (REQ-729)");
        assertFalse(ManifestClient.USER_AGENT.matches(".*[0-9]+\\.[0-9]+.*"), "no version number in the agent");
    }

    @Test
    void aNonTwoHundredIsAnHttpError() {
        ManifestClient.Fetched fetched = client.get(base + "/missing");
        assertFalse(fetched.ok());
        assertEquals(ManifestClient.FailureKind.HTTP_ERROR, fetched.kind());
    }

    @Test
    void aBodyAboveSixtyFourKiBIsTooLargeDeclaredOrStreamed() {
        assertEquals(ManifestClient.FailureKind.TOO_LARGE, client.get(base + "/big").kind());
        assertEquals(ManifestClient.FailureKind.TOO_LARGE, client.get(base + "/big-chunked").kind());
    }

    @Test
    void aServerThatNeverAnswersIsUnreachableWithinTheRequestTimeout() {
        Instant start = Instant.now();
        ManifestClient.Fetched fetched = client.get(base + "/slow");
        Duration took = Duration.between(start, Instant.now());
        assertEquals(ManifestClient.FailureKind.UNREACHABLE, fetched.kind());
        assertEquals("java.net.http.HttpTimeoutException", fetched.exceptionClassName());
        assertTrue(took.compareTo(Duration.ofSeconds(15)) < 0, "took " + took);
    }

    @Test
    void aServerThatDripsTheBodyIsCutOffAtTheDeadline() {
        // review 2026-08-26 #1 (REQ-706 Must): the request timeout only bounds the headers
        Instant start = Instant.now();
        ManifestClient.Fetched fetched = client.get(base + "/drip");
        Duration took = Duration.between(start, Instant.now());
        assertEquals(ManifestClient.FailureKind.UNREACHABLE, fetched.kind());
        assertEquals("java.util.concurrent.TimeoutException", fetched.exceptionClassName());
        assertTrue(took.compareTo(Duration.ofSeconds(20)) < 0, "took " + took); // slack for the Dev Services load flake (R2-3)
        assertTrue(took.compareTo(Duration.ofSeconds(14)) > 0, "the deadline, not the byte cap, ended it: " + took);
    }

    @Test
    void aSmallerBudgetCutsTheSameDripEarlierAndAnExhaustedBudgetFailsAtOnce() {
        Instant start = Instant.now();
        ManifestClient.Fetched fetched = client.get(base + "/drip", Duration.ofSeconds(3));
        Duration took = Duration.between(start, Instant.now());
        assertEquals(ManifestClient.FailureKind.UNREACHABLE, fetched.kind());
        assertEquals("java.util.concurrent.TimeoutException", fetched.exceptionClassName());
        assertTrue(took.compareTo(Duration.ofSeconds(2)) > 0 && took.compareTo(Duration.ofSeconds(8)) < 0, "took " + took);
        assertEquals(ManifestClient.FailureKind.UNREACHABLE, client.get(base + "/ok", Duration.ZERO).kind());
        assertEquals(ManifestClient.FailureKind.UNREACHABLE, client.get(base + "/ok", Duration.ofSeconds(-5)).kind());
    }

    @Test
    void aRedirectIsAnHttpErrorAndIsNeverFollowed() {
        ManifestClient.Fetched fetched = client.get(base + "/redirect");
        assertEquals(ManifestClient.FailureKind.HTTP_ERROR, fetched.kind());
        assertEquals(0, REDIRECT_TARGET_HITS.get(), "the redirect target must not be requested");
    }

    @Test
    void aClosedPortIsUnreachableFast() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        Instant start = Instant.now();
        ManifestClient.Fetched fetched = client.get("http://127.0.0.1:" + closedPort + "/manifest/stable.json");
        assertEquals(ManifestClient.FailureKind.UNREACHABLE, fetched.kind());
        assertNotNull(fetched.exceptionClassName());
        assertTrue(Duration.between(start, Instant.now()).compareTo(Duration.ofSeconds(5)) < 0);
    }

    @Test
    void aMalformedUrlIsUnreachableWithoutARequest() {
        assertEquals(ManifestClient.FailureKind.UNREACHABLE, client.get("not a url").kind());
    }

    @Test
    void theConventionImportsNoIdentity() throws IOException {
        Path source = de.gxp.update.ManifestServer.moduleRoot().resolve("src/main/java/de/gxp/update/control/ManifestClient.java");
        String text = Files.readString(source);
        assertFalse(text.contains("import de.gxp.auth"), "REQ-729: no auth import");
        assertFalse(text.contains("import de.gxp.user"), "REQ-729: no user import");
        assertFalse(text.contains("import de.gxp.device"), "REQ-729: no device identity import");
        assertTrue(text.contains("The outbound HTTP convention"), "the javadoc names the convention (REQ-729)");
    }
}
