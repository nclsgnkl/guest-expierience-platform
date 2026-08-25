package de.gxp.system.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import de.gxp.api.model.ApiError;
import de.gxp.api.model.SystemReadiness;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The readiness outcome of {@link SystemControl} (REQ-212, REQ-325), both
 * ways. A component test rather than an HTTP one: the failure branch needs
 * the database to be unreachable, which is why it went uncovered until
 * 2026-08-25 — and why the 503 shipped with no body at all.
 *
 * <p>The contract declares {@code 503} on {@code /api/system/ready} with an
 * {@link ApiError} schema. A bare {@code ServiceUnavailableException} does not
 * satisfy that: its {@code String} constructor sets the Java exception's
 * message, not the response entity, and an entity-less
 * {@code WebApplicationException} is handed straight through by
 * {@code ApiErrorMappers.WrappedJacksonExceptionMapper}. So the assertion that
 * matters here is not the status alone — it is that the response CARRIES the
 * shared shape.
 *
 * <p>Lives in {@code de.gxp.system.control} so it can reach the
 * package-private constructor and stub the package-private
 * {@link InstanceInfoRepository#probe()}; the stub overrides that method
 * outright, so no CDI, no EntityManager and no database are involved.
 */
class SystemReadinessTest {

    /** Panache repository whose probe fails the way an unreachable database fails. */
    private static class UnreachableDatabase extends InstanceInfoRepository {
        @Override
        void probe() {
            throw new PersistenceException("connection refused to jdbc:postgresql://db:5432/gxp");
        }
    }

    @Test
    void readyAnswersReadyWhenTheProbeSucceeds() {
        SystemControl control = new SystemControl(new InstanceInfoRepository() {
            @Override
            void probe() {
                // reachable — return normally
            }
        }, "1.2.3");

        assertEquals(SystemReadiness.StatusEnum.READY, control.ready().getStatus());
    }

    @Test
    void anUnreachableDatabaseIs503CarryingTheSharedApiErrorShape() {
        SystemControl control = new SystemControl(new UnreachableDatabase(), "1.2.3");

        WebApplicationException failure = assertThrows(WebApplicationException.class, control::ready);
        Response response = failure.getResponse();

        assertEquals(503, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity(),
                "the 503 must carry the contract's ApiError entity — an entity-less"
                        + " WebApplicationException is passed through unmapped and reaches the client"
                        + " as a bodyless 503 (REQ-325)");
        assertEquals("database-unreachable", error.getCode());
    }

    @Test
    void theProbeFailureIsNeverEchoedIntoTheResponse() {
        SystemControl control = new SystemControl(new UnreachableDatabase(), "1.2.3");

        WebApplicationException failure = assertThrows(WebApplicationException.class, control::ready);
        ApiError error = (ApiError) failure.getResponse().getEntity();

        // constitution principle 3: a JDBC failure quotes connection details
        assertEquals("The database is unreachable", error.getMessage());
    }
}
