package de.gxp.infrastructure.testsupport;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;

/**
 * Test-only menu state repair, the {@link DeviceFixtures} precedent: native
 * SQL that arranges database state a test needs, rather than driving it
 * through the manager API.
 *
 * <p><b>Why this exists</b> (sidebar-catalogue-overflow, 02-design.md §3.3).
 * {@code PUT /api/manager/menu-config} is a WHOLE-SET REPLACE —
 * {@code MenuControl.putConfig} deletes every config row of the scope and
 * re-inserts what the body carries. Five test classes replace or clear the
 * property-scope config, so each of them deletes the eight
 * {@code enabled = false} opt-out rows that {@code V10__catalogue_expansion}
 * writes for the placeholder catalogue fixtures. Absence of a config row
 * resolves to *enabled*, so those classes would otherwise leave the fixtures
 * ON for whatever runs next in the shared database — and
 * {@code GuestSessionTest}'s menu-size assertions would become
 * order-dependent.
 *
 * <p>Called from an {@code @AfterEach} in every class that replaces the
 * property-scope config, and from {@code CatalogueExpansionTest}'s
 * {@code @BeforeEach}, which asserts the opt-out state rather than disturbing
 * it.
 */
public final class MenuFixtures {

    /**
     * The keys {@code V10__catalogue_expansion.sql} introduces. Disposable
     * fixtures — see that migration's header. Listing them here is sanctioned
     * by REQ-903 ("one migration plus a registered-icons.ts edit"): this file,
     * the migration and the dev seeder are the only places a key may appear.
     */
    private static final String[] CATALOGUE_FIXTURE_KEYS = {
            "wlan", "anreise", "hausordnung", "muell",
            "aerztliches", "parken", "gastronomie", "aktivitaeten" };

    private MenuFixtures() {
    }

    /**
     * Restores D5's explicit property-scope {@code enabled = false} rows for
     * the placeholder catalogue entries. {@code ON CONFLICT DO NOTHING}
     * against {@code uq_menu_item_config_scope}, so a row a test deliberately
     * left in place is never rewritten.
     */
    public static void restoreCatalogueOptOut(EntityManager entityManager) {
        QuarkusTransaction.requiringNew().run(() -> {
            for (String key : CATALOGUE_FIXTURE_KEYS) {
                entityManager.createNativeQuery(
                        "insert into menu_item_config (menu_item_key, room_id, enabled)"
                                + " values (:key, null, false)"
                                + " on conflict (menu_item_key, room_id) do nothing")
                        .setParameter("key", key)
                        .executeUpdate();
            }
        });
    }
}
