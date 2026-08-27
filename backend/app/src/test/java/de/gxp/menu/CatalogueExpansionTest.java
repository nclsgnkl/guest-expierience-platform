package de.gxp.menu;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.DeviceFixtures;
import de.gxp.infrastructure.testsupport.ManagerFixtures;
import de.gxp.infrastructure.testsupport.MenuFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * sidebar-catalogue-overflow T-04 (REQ-903, REQ-908, REQ-909, REQ-910):
 * what {@code V10__catalogue_expansion} actually does to a running property.
 *
 * <p>The five real kiosk sections keep positions 0..4 of the catalogue, the
 * eight placeholder fixtures follow, and a guest sees NONE of them until the
 * manager says so — D5's explicit property-scope {@code enabled = false} rows,
 * because absence of a config row resolves to *enabled* and instances are
 * patched automatically (constitution v1.8.1).
 *
 * <p><b>No fixture key is named anywhere in this class.</b> REQ-903 makes the
 * placeholder rows disposable — removing them must be one migration plus a
 * {@code registered-icons.ts} edit and nothing else — so the key under test is
 * DERIVED from the catalogue (the first entry past the five originals). The
 * only properties relied on are the ones the PRD permits: the catalogue has
 * more than five rows, and the originals come first.
 *
 * <p>{@code @BeforeEach} ARRANGES the D5 state rather than assuming it: this
 * class asserts that state, and the shared database is written by five other
 * classes whose whole-set config replacements delete those rows
 * (02-design.md §3.3).
 */
@QuarkusTest
class CatalogueExpansionTest {

    private static final List<String> ORIGINAL_KEYS =
            List.of("home", "info", "umgebung", "smart-home", "services");

    @Inject
    EntityManager entityManager;

    private long roomId;
    private String deviceToken;

    @BeforeEach
    void arrangeTheOptOutState() {
        MenuFixtures.restoreCatalogueOptOut(entityManager);
        roomId = ((Number) entityManager
                .createNativeQuery("select id from room where code = '1'")
                .getSingleResult()).longValue();
        deviceToken = DeviceFixtures.seedDevice(entityManager, roomId, "Catalogue-Kiosk");
    }

    @AfterEach
    void cleanUp() {
        DeviceFixtures.deleteDevice(entityManager, deviceToken);
        MenuFixtures.restoreCatalogueOptOut(entityManager);
    }

    // ------------------------------------------------------------ REQ-903 / REQ-910

    @Test
    void theCatalogueGrewAndTheFiveRealSectionsKeptTheirPositions() {
        List<String> keys = catalogueKeys(ManagerFixtures.login());

        // >= 12 is the only count REQ-903 fixes; 13 is what V10 leaves.
        org.junit.jupiter.api.Assertions.assertEquals(13, keys.size(),
                "V2's five sections plus V10's eight placeholder fixtures");
        org.junit.jupiter.api.Assertions.assertEquals(ORIGINAL_KEYS, keys.subList(0, 5),
                "MenuRepository.catalogue() orders by defaultSortIndex, and V10 continues at 5");
    }

    // ------------------------------------------------------------ REQ-908

    @Test
    void aRoomWithNoConfigRowsStillSeesExactlyTheOriginalFive() {
        // The acceptance criterion of REQ-908, stated directly: this is what an
        // ALREADY-RUNNING property sees the moment V10 has been applied.
        session()
                .body("menu.items", hasSize(5))
                .body("menu.items.key", equalTo(ORIGINAL_KEYS));
    }

    @Test
    void enablingOneFixtureAtPropertyScopeMakesItTheSixthItem() {
        String token = ManagerFixtures.login();
        String key = firstFixtureKey(token);
        try {
            enableAtPropertyScope(token, key);
            session()
                    .body("menu.items", hasSize(6))
                    .body("menu.items.key[0..4]", equalTo(ORIGINAL_KEYS))
                    // sort index 5 puts it directly after the originals
                    .body("menu.items[5].key", equalTo(key));
        } finally {
            disableAtPropertyScope(key);
        }
    }

    // ------------------------------------------------------------ REQ-909 — the ceiling

    @Test
    void aRoomScopeEnableOfAPropertyDisabledFixtureStaysAbsent() {
        String key = firstFixtureKey(ManagerFixtures.login());
        // The API refuses to create this state (ceiling rule 2,
        // `item-disabled-at-property`), so seed the room row directly — the
        // GuestContentTest precedent. Resolution must still ceiling it out:
        // the property is the ceiling and a room cannot rise above it.
        try {
            QuarkusTransaction.requiringNew().run(() -> entityManager
                    .createNativeQuery("insert into menu_item_config (menu_item_key, room_id, enabled)"
                            + " values (:key, :roomId, true)")
                    .setParameter("key", key)
                    .setParameter("roomId", roomId)
                    .executeUpdate());
            session()
                    .body("menu.items", hasSize(5))
                    .body("menu.items.key", not(org.hamcrest.Matchers.hasItem(key)));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> entityManager
                    .createNativeQuery("delete from menu_item_config where room_id = :roomId")
                    .setParameter("roomId", roomId)
                    .executeUpdate());
        }
    }

    // ------------------------------------------------------------ 02-design.md §3.3

    @Test
    void partialConfigReplaceReEnablesTheFixtures() {
        // PINNED, NOT ENDORSED. `PUT /api/manager/menu-config` is a whole-set
        // replace: a client that submits a partial set DELETES D5's opt-out
        // rows, and absence resolves back to *enabled*. The opt-out is durable
        // against the update channel — its purpose — but not against a
        // careless API consumer. The obligation on a future manager UI is
        // therefore: GET the scope, edit, PUT the whole set back. Making this
        // structurally impossible means making `introduced_release`
        // load-bearing, which is D5's rejected alternative.
        String token = ManagerFixtures.login();
        try {
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of())
                    .when().put("/api/manager/menu-config")
                    .then().statusCode(200);
            session().body("menu.items", hasSize(13));
        } finally {
            MenuFixtures.restoreCatalogueOptOut(entityManager);
        }
    }

    // ------------------------------------------------------------ helpers

    private io.restassured.response.ValidatableResponse session() {
        return given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/content")
                .then().statusCode(200);
    }

    private static List<String> catalogueKeys(String token) {
        return given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/menu/catalogue")
                .then().statusCode(200)
                .extract().path("key");
    }

    /**
     * The first catalogue entry past the five originals — i.e. one of V10's
     * placeholder fixtures, without this class ever naming one (REQ-903).
     */
    private static String firstFixtureKey(String token) {
        return catalogueKeys(token).get(ORIGINAL_KEYS.size());
    }

    /**
     * Flips one key on at property scope THE WAY A MANAGER CLIENT MUST: read
     * the whole scope, edit one entry, write the whole set back (the whole-set
     * replace pinned by {@link #partialConfigReplaceReEnablesTheFixtures}).
     */
    private static void enableAtPropertyScope(String token, String key) {
        List<Map<String, Object>> current = given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/menu-config")
                .then().statusCode(200)
                .extract().path("$");
        List<Map<String, Object>> next = new ArrayList<>();
        for (Map<String, Object> entry : current) {
            Map<String, Object> copy = new HashMap<>();
            copy.put("menuItemKey", entry.get("menuItemKey"));
            copy.put("enabled", key.equals(entry.get("menuItemKey")) ? Boolean.TRUE : entry.get("enabled"));
            next.add(copy);
        }
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(next)
                .when().put("/api/manager/menu-config")
                .then().statusCode(200);
    }

    /**
     * Back to D5's state. Native SQL rather than another whole-set PUT: the
     * fixture's {@code ON CONFLICT DO NOTHING} cannot flip a row that already
     * exists, so the row this test enabled has to be reset explicitly.
     */
    private void disableAtPropertyScope(String key) {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("update menu_item_config set enabled = false"
                        + " where room_id is null and menu_item_key = :key")
                .setParameter("key", key)
                .executeUpdate());
    }
}
