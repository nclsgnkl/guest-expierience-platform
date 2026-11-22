package de.gxp.infrastructure.testsupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Test-only log capture for the "logs nothing" criteria (auth-hardening
 * REQ-605, REQ-619; constitution principle 3). Attaches one JUL handler to
 * the application root category {@code de.gxp} AND to Quarkus' HTTP layer
 * {@code io.quarkus.vertx.http} — the category the forwarded-header parser
 * logs under — so the sweep covers what the feature logs and what the
 * framework logs on the feature's behalf. Widened from {@code de.gxp.auth}
 * alone on 2026-08-26 (review of T-01…T-04, finding #5).
 */
public final class LogSweep {

    /** Categories swept — application code plus the HTTP layer that rewrites addresses. */
    public static final List<String> CATEGORIES = List.of("de.gxp", "io.quarkus.vertx.http");

    private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    public void start() {
        CATEGORIES.forEach(category -> Logger.getLogger(category).addHandler(handler));
    }

    public void stop() {
        CATEGORIES.forEach(category -> Logger.getLogger(category).removeHandler(handler));
    }

    public void clear() {
        records.clear();
    }

    /** Snapshot of everything captured since the last {@link #clear()}. */
    public List<LogRecord> records() {
        return new ArrayList<>(records);
    }

    /** Readable form for assertion messages (category + level + message, never a stack trace). */
    public String describe() {
        StringBuilder out = new StringBuilder();
        for (LogRecord record : records()) {
            out.append(record.getLoggerName()).append(' ').append(record.getLevel())
                    .append(": ").append(record.getMessage()).append('\n');
        }
        return out.toString();
    }
}
