package de.gxp.auth.control;

import java.time.Instant;

import de.gxp.auth.entity.AdminCredential;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Control (data access) — package-private data access for the single
 * credential row. It lives in {@code .control} rather than beside the entity
 * precisely so it can stay package-private (spec §4).
 */
@ApplicationScoped
class AdminCredentialRepository implements PanacheRepositoryBase<AdminCredential, Short> {

    private static final short THE_ID = 1;

    /** The single row, or {@code null} when not yet seeded. */
    AdminCredential theRow() {
        return findById(THE_ID);
    }

    void seed(String passwordHash, Instant now) {
        AdminCredential credential = new AdminCredential();
        credential.id = THE_ID;
        credential.passwordHash = passwordHash;
        credential.updatedAt = now;
        persist(credential);
    }
}
