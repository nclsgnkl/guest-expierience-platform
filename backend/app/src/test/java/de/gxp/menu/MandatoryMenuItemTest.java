package de.gxp.menu;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.OffsetDateTime;
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
 * {@code home} is mandatory (REQ-948, owner decision 2026-08-27): a manager
 * cannot remove it or move it, at either scope, and a row written before the
 * guard existed is INERT rather than a trap. The state the requirement asks
 * to be defined and tested is exactly that — {@code home} is always in the
 * resolved menu at sort index 0, and the merged guest content always carries
 * its {@code home} half for an occupied room. There is no reachable state in
 * which the kiosk asks for a home screen it cannot have.
 *
 * <p>Its own file, deliberately: {@code MenuDisableGuardTest} owns the
 * invariant-3 matrix and {@code MenuCatalogueAndConfigTest} the CRUD surface,
 * and neither is about a key that cannot be configured at all.
 */
@QuarkusTest
class MandatoryMenuItemTest {

    @Inject
    EntityManager entityManager;

    private long roomId;
    private String deviceToken;
    private String adminToken;

    @BeforeEach
    void seed() {
        adminToken = ManagerFixtures.login();
        roomId = ((Number) entityManager
                .createNativeQuery("select id from room where code = '1'")
                .getSingleResult()).longValue();
        deviceToken = DeviceFixtures.seedDevice(entityManager, roomId, "Mandatory-Kiosk");
    }

    /**
     * One case below replaces the property-scope menu config, which is a
     * whole-set replace and therefore deletes V10's eight {@code enabled =
     * false} opt-out rows. Restore them so the shared database is handed on in
     * the state the migration left it (02-design.md §3.3). The native
     * {@code home} row of the legacy case is removed here too.
     */
    @AfterEach
    void cleanUp() {
        DeviceFixtures.deleteDevice(entityManager, deviceToken);
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("delete from stay where room_id = :id")
                    .setParameter("id", roomId).executeUpdate();
            entityManager.createNativeQuery(
                    "delete from menu_item_config where menu_item_key = 'home'").executeUpdate();
        });
        MenuFixtures.restoreCatalogueOptOut(entityManager);
    }

    private void createActiveStay() {
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("roomId", roomId, "displayName", "Anna Schmidt",
                        "checkIn", OffsetDateTime.now().minusHours(1).toString(),
                        "checkOut", OffsetDateTime.now().plusHours(3).toString(),
                        "status", "BOOKED"))
                .when().post("/api/manager/stays")
                .then().statusCode(201);
    }

    private io.restassured.response.ValidatableResponse guestContent() {
        return given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/content")
                .then().statusCode(200);
    }

    @Test
    void disablingHomeAtPropertyScopeIsRefused() {
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(List.of(Map.of("menuItemKey", "home", "enabled", false)))
                .when().put("/api/manager/menu-config")
                .then().statusCode(400)
                .body("code", equalTo("menu-item-mandatory"));
        // nothing was stored, and the guest is unaffected
        createActiveStay();
        guestContent()
                .body("menu.items[0].key", equalTo("home"))
                .body("home", notNullValue());
    }

    @Test
    void disablingHomeAtRoomScopeIsRefused() {
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(List.of(Map.of("menuItemKey", "home", "enabled", false)))
                .when().put("/api/manager/rooms/" + roomId + "/menu-config")
                .then().statusCode(400)
                .body("code", equalTo("menu-item-mandatory"));
    }

    @Test
    void reorderingHomeIsRefusedAtBothScopes() {
        // the second half of "mandatory": its position is not configurable
        // either, so the landing screen is always first
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(List.of(Map.of("menuItemKey", "home", "enabled", true, "sortIndex", 4)))
                .when().put("/api/manager/menu-config")
                .then().statusCode(400)
                .body("code", equalTo("menu-item-mandatory"));
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(List.of(Map.of("menuItemKey", "home", "enabled", true, "sortIndex", 4)))
                .when().put("/api/manager/rooms/" + roomId + "/menu-config")
                .then().statusCode(400)
                .body("code", equalTo("menu-item-mandatory"));
    }

    @Test
    void anIconOrLabelOverrideOnHomeIsStillAllowed() {
        // V11 takes away enablement and ordering ONLY — the two fields the
        // guard covers. Everything else a manager can say about `home` stands.
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(List.of(Map.of("menuItemKey", "home", "iconOverride", "phosphorHouseLine",
                        "label", Map.of("de", "Startseite"))))
                .when().put("/api/manager/menu-config")
                .then().statusCode(200);
        guestContent()
                .body("menu.items[0].key", equalTo("home"))
                .body("menu.items[0].icon", equalTo("phosphorHouseLine"))
                .body("menu.items[0].label.de", equalTo("Startseite"))
                .body("menu.items[0].sortIndex", equalTo(0));
    }

    @Test
    void aLegacyDisabledAndReorderedRowIsInert() {
        // The state V11 repairs and the guard now prevents: written before
        // either existed, so it can only be arranged past the API. Resolution
        // must ignore it entirely rather than honour it — that is what makes
        // the old 404 unreachable instead of merely handled.
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("insert into menu_item_config (menu_item_key, room_id, enabled, sort_index)"
                        + " values ('home', null, false, 7)")
                .executeUpdate());
        createActiveStay();
        guestContent()
                .body("menu.items[0].key", equalTo("home"))
                .body("menu.items[0].sortIndex", equalTo(0))
                .body("home", notNullValue())
                .body("home.headline.de", equalTo("Willkommen, Anna Schmidt!"));
    }
}
