package de.gxp.update;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * The manifest server every {@code @QuarkusTest} sees (update-channel D16):
 * a JDK {@link HttpServer} on an ephemeral loopback port, registered
 * suite-wide (no extra Quarkus boot) and handed to the app as
 * {@code gxp.update.base-url}. Serves {@code /manifest/stable.json} and
 * {@code .sig} from whatever a test last {@link #serve}d — nothing by
 * default (404), so the unrelated tests never see an update.
 */
public class ManifestServer implements QuarkusTestResourceLifecycleManager {

    private static final AtomicReference<byte[]> MANIFEST = new AtomicReference<>();
    private static final AtomicReference<byte[]> SIGNATURE = new AtomicReference<>();
    private static final AtomicInteger HITS = new AtomicInteger();
    /** Seconds each endpoint drips one byte per second (chunked, no Content-Length) before completing; 0 = answer at once. */
    private static volatile int manifestDripSeconds;
    private static volatile int signatureDripSeconds;
    private static volatile String url;
    private HttpServer server;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/manifest/stable.json", exchange -> {
            boolean signature = exchange.getRequestURI().getPath().endsWith(".sig");
            int drip = signature ? signatureDripSeconds : manifestDripSeconds;
            if (drip > 0) {
                drip(exchange, drip);
            } else {
                respond(exchange, signature ? SIGNATURE.get() : MANIFEST.get());
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
        return Map.of("gxp.update.base-url", url);
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void drip(HttpExchange exchange, int seconds) throws IOException {
        HITS.incrementAndGet();
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            for (int i = 0; i < seconds; i++) {
                out.write('x');
                out.flush();
                Thread.sleep(1_000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // the client hung up at its deadline — expected
        }
    }

    private static void respond(HttpExchange exchange, byte[] body) throws IOException {
        HITS.incrementAndGet();
        if (body == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    public static String url() {
        return url;
    }

    /** Serve this pair from now on; {@code null} for either means 404. */
    public static void serve(byte[] manifest, byte[] signatureBase64) {
        MANIFEST.set(manifest);
        SIGNATURE.set(signatureBase64);
    }

    public static void serveNothing() {
        serve(null, null);
        manifestDripSeconds = 0;
        signatureDripSeconds = 0;
    }

    /** Both endpoints answer their headers at once and then drip one byte per second for the given number of seconds. */
    public static void serveDripping(int manifestSeconds, int signatureSeconds) {
        manifestDripSeconds = manifestSeconds;
        signatureDripSeconds = signatureSeconds;
    }

    /** The shared fixture's signed manifest (test key), byte-identical to what {@code parse.sh} reads. */
    public static void serveFixture() throws IOException {
        serve(fixture("manifest.json"), fixture("manifest.json.sig"));
    }

    public static byte[] fixture(String name) throws IOException {
        return Files.readAllBytes(fixtures().resolve(name));
    }

    /** {@code backend/updater/test/fixtures}, shared with the updater's {@code parse.sh}. */
    public static Path fixtures() {
        return moduleRoot().resolve("../updater/test/fixtures").normalize();
    }

    /** The Maven module directory ({@code backend/app}), found from the working directory upwards — the one copy for the update tests. */
    public static Path moduleRoot() {
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            if (Files.isDirectory(directory.resolve("src/main/java/de/gxp"))) {
                return directory;
            }
        }
        throw new IllegalStateException("module root not found");
    }

    public static int hits() {
        return HITS.get();
    }
}
