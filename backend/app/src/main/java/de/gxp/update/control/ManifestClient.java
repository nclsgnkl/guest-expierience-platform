package de.gxp.update.control;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * <b>The outbound HTTP convention</b> of this application (update-channel
 * REQ-706/729, D16, constitution v1.8.0 survival invariant) — the shape
 * remote-operations Parts B (enrollment / challenge publishing) and D (the
 * relay's outbound leg) copy rather than re-decide:
 *
 * <ul>
 * <li>JDK {@link HttpClient}, built once: connect timeout 5 s, request
 * (headers) timeout 10 s, and a hard deadline on the WHOLE exchange
 * including the body — a server that drips one byte a second is cut off
 * at the deadline, not at the byte cap. The deadline is the caller's
 * BUDGET ({@link #get(String, Duration)}, at most {@link #TOTAL_DEADLINE}):
 * a caller issuing several fetches per tick passes the remainder on, so a
 * tick costs at most 15 s of ONE worker thread in total, not per call.
 * HTTP/1.1, {@code followRedirects NEVER}, no proxy, no cookie handler.</li>
 * <li>A 64 KiB cap on {@code Content-Length} AND on the streamed body (a
 * body without a length is capped the same way).</li>
 * <li>No retry — the next tick is the retry.</li>
 * <li>A failure is an outcome code plus the exception's CLASS NAME for the
 * log, never a URL, never a body byte, never a message text. Nothing is
 * thrown outward, not even a {@code RuntimeException} from the JDK client.</li>
 * <li>Fixed request headers: {@code User-Agent: gxp-update/1} (no JDK or
 * application version — the box's software level is not telemetry) and
 * {@code Accept: application/json}.</li>
 * <li>{@code https://} only outside a test profile — enforced by the caller
 * before this class is reached (the client itself is scheme-agnostic so a
 * test can point it at a loopback {@code http://} server).</li>
 * <li>Imports nothing from {@code de.gxp.auth}, {@code de.gxp.user} or any
 * identity-related type: an outbound call carries no instance identity.</li>
 * </ul>
 */
@ApplicationScoped
class ManifestClient {

    static final int MAX_BODY_BYTES = 64 * 1024;
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    /** Headers AND body, measured from the send; REQ-706's "≤ 15 s per tick" — the budget of a whole tick. */
    static final Duration TOTAL_DEADLINE = Duration.ofSeconds(15);
    static final String USER_AGENT = "gxp-update/1";

    enum FailureKind {
        /** Non-2xx status, including a redirect (never followed). */
        HTTP_ERROR,
        /** Declared or streamed body above {@link #MAX_BODY_BYTES}. */
        TOO_LARGE,
        /** Connect/read failure, timeout (headers or body), interruption, malformed URI. */
        UNREACHABLE
    }

    /** Either {@code body} (success) or {@code kind} + the exception class name (failure). */
    record Fetched(byte[] body, FailureKind kind, String exceptionClassName) {

        static Fetched ok(byte[] body) {
            return new Fetched(body, null, null);
        }

        static Fetched failure(FailureKind kind, String exceptionClassName) {
            return new Fetched(null, kind, exceptionClassName);
        }

        boolean ok() {
            return body != null;
        }
    }

    /** Raised inside the body subscriber when the streamed body passes the cap; mapped to {@code TOO_LARGE}. */
    private static final class BodyTooLarge extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .proxy(java.net.ProxySelector.of(null))
            .build();

    /** One fetch with the full {@link #TOTAL_DEADLINE}. */
    Fetched get(String url) {
        return get(url, TOTAL_DEADLINE);
    }

    /** One fetch bounded by {@code budget} (headers by the 10 s request timeout, the whole exchange by the budget); an exhausted budget fails at once. */
    Fetched get(String url, Duration budget) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url)).GET()
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build();
        } catch (IllegalArgumentException e) {
            return Fetched.failure(FailureKind.UNREACHABLE, e.getClass().getName());
        }
        CompletableFuture<HttpResponse<byte[]>> exchange = null;
        try {
            exchange = client.sendAsync(request, ManifestClient::bodyHandler);
            HttpResponse<byte[]> response = exchange.get(Math.max(0, budget.toMillis()), TimeUnit.MILLISECONDS);
            if (response.statusCode() / 100 != 2) {
                return Fetched.failure(FailureKind.HTTP_ERROR, null);
            }
            if (response.body() == null) {
                return Fetched.failure(FailureKind.TOO_LARGE, null); // declared Content-Length above the cap
            }
            return Fetched.ok(response.body());
        } catch (TimeoutException e) {
            exchange.cancel(true); // JDK 16+: cancels the exchange and closes the connection
            return Fetched.failure(FailureKind.UNREACHABLE, e.getClass().getName());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            for (Throwable t = cause; t != null; t = t.getCause()) {
                if (t instanceof BodyTooLarge) {
                    return Fetched.failure(FailureKind.TOO_LARGE, null);
                }
            }
            return Fetched.failure(FailureKind.UNREACHABLE, cause.getClass().getName());
        } catch (InterruptedException e) {
            exchange.cancel(true);
            Thread.currentThread().interrupt();
            return Fetched.failure(FailureKind.UNREACHABLE, e.getClass().getName());
        } catch (RuntimeException e) {
            return Fetched.failure(FailureKind.UNREACHABLE, e.getClass().getName());
        }
    }

    /**
     * Non-2xx and an over-cap {@code Content-Length} are not read at all
     * ({@code null} body); everything else streams through the bounded
     * subscriber, which cancels the exchange past the cap.
     */
    private static HttpResponse.BodySubscriber<byte[]> bodyHandler(HttpResponse.ResponseInfo info) {
        if (info.statusCode() / 100 != 2) {
            return HttpResponse.BodySubscribers.replacing(new byte[0]);
        }
        OptionalLong declared = info.headers().firstValueAsLong("Content-Length");
        if (declared.isPresent() && declared.getAsLong() > MAX_BODY_BYTES) {
            return HttpResponse.BodySubscribers.replacing(null);
        }
        return new BoundedSubscriber();
    }

    /** Collects at most {@link #MAX_BODY_BYTES}; one byte more fails the exchange with {@link BodyTooLarge}. */
    private static final class BoundedSubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream collected = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int length = buffer.remaining();
                if (collected.size() + length > MAX_BODY_BYTES) {
                    subscription.cancel();
                    result.completeExceptionally(new BodyTooLarge());
                    return;
                }
                byte[] chunk = new byte[length];
                buffer.get(chunk);
                collected.write(chunk, 0, length);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(collected.toByteArray());
        }
    }
}
