package de.gxp.user.control;

import de.gxp.user.entity.ManagerUser;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Control (data access) — package-private data access for manager accounts.
 * It lives in {@code .control} rather than beside the entity precisely so it
 * can stay package-private (spec §4).
 */
@ApplicationScoped
class ManagerUserRepository implements PanacheRepository<ManagerUser> {

    /** The account with that (lower-case, unique) username, or {@code null}. */
    ManagerUser byUsername(String username) {
        return find("username", username).firstResult();
    }

    /**
     * Advances the TOTP replay guard ATOMICALLY (review 2026-08-26 #1,
     * REQ-614 "refused"): the predicate re-checks {@code totpLastStep} under
     * the row lock, so two concurrent logins with the same code cannot both
     * accept step {@code step}. Returns {@code false} when another
     * transaction already moved the guard to that step or beyond.
     */
    boolean advanceTotpStep(ManagerUser user, long step) {
        return update("totpLastStep = ?1 where id = ?2 and (totpLastStep is null or totpLastStep < ?1)",
                step, user.id) == 1;
    }
}
