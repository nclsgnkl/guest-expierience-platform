package de.gxp.guest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.testsupport.DeviceFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * T-23 (REQ-422): guest asset delivery — own-room artwork served with the
 * stored-SHA ETag and 304 on replay; ANOTHER room's artwork id is the same
 * 404 as a nonexistent id (no enumeration); long private Cache-Control.
 */
@QuarkusTest
class GuestAssetTest {

    @Inject
    EntityManager entityManager;

    private String adminToken;
    private long roomId;
    private String deviceToken;
    private int ownArtworkId;
    private int otherRoomId;
    private int otherArtworkId;

    private static byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, (x * 7 + y * 13) & 0xFFFFFF);
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int uploadArtwork(byte[] png) {
        return given().header("Authorization", "Bearer " + adminToken)
                .contentType("image/png").body(png)
                .when().post("/api/manager/assets?kind=ARTWORK")
                .then().statusCode(201).extract().path("id");
    }

    @BeforeEach
    void seed() {
        adminToken = given().contentType("application/json")
                .body(Map.of("password", "test-admin-password-1"))
                .when().post("/api/manager/login")
                .then().statusCode(200).extract().path("token");
        roomId = ((Number) entityManager
                .createNativeQuery("select id from room where code = '1'")
                .getSingleResult()).longValue();
        ownArtworkId = uploadArtwork(png(40, 30));
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("code", "1", "displayName", "Zimmer 1", "artworkAssetId", ownArtworkId))
                .when().put("/api/manager/rooms/" + roomId)
                .then().statusCode(200);
        otherArtworkId = uploadArtwork(png(30, 40));
        otherRoomId = given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("code", "ASSET-B", "displayName", "Anderes Zimmer",
                        "artworkAssetId", otherArtworkId))
                .when().post("/api/manager/rooms")
                .then().statusCode(201).extract().path("id");
        deviceToken = DeviceFixtures.seedDevice(entityManager, roomId, "Asset-Kiosk");
    }

    @AfterEach
    void cleanUp() {
        DeviceFixtures.deleteDevice(entityManager, deviceToken);
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("code", "1", "displayName", "Zimmer 1"))
                .when().put("/api/manager/rooms/" + roomId)
                .then().statusCode(200);
        given().header("Authorization", "Bearer " + adminToken)
                .when().delete("/api/manager/rooms/" + otherRoomId)
                .then().statusCode(204);
        for (int assetId : new int[] { ownArtworkId, otherArtworkId }) {
            given().header("Authorization", "Bearer " + adminToken)
                    .when().delete("/api/manager/assets/" + assetId)
                    .then().statusCode(204);
        }
    }

    @Test
    void ownRoomArtworkServesWithEtagAnd304() {
        Response first = given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/assets/" + ownArtworkId)
                .then().statusCode(200).extract().response();
        String etag = first.header("ETag");
        assertNotNull(etag, "guest asset must carry the stored-SHA ETag");
        assertEquals("image/png", first.contentType());
        String cacheControl = first.header("Cache-Control");
        assertNotNull(cacheControl);
        assertEquals(true, cacheControl.contains("private") && cacheControl.contains("max-age=2592000"),
                "guest asset cache pin: " + cacheControl);

        given().header("Authorization", "Bearer " + deviceToken)
                .header("If-None-Match", etag)
                .when().get("/api/guest/assets/" + ownArtworkId)
                .then().statusCode(304);
    }

    @Test
    void unreachableAndUnknownIdsAreTheSame404() {
        // another room's artwork — EXISTS, but is not in this device's view
        Response unreachable = given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/assets/" + otherArtworkId)
                .then().statusCode(404).extract().response();
        // a nonexistent id
        Response unknown = given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/assets/999999")
                .then().statusCode(404).extract().response();
        assertEquals(unreachable.asString(), unknown.asString(),
                "existing-but-unreachable must be indistinguishable from nonexistent");
        assertEquals("asset-not-found", unknown.path("code"));
    }

    @Test
    void managerAssetSurfaceStaysClosedToDevices() {
        given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/manager/assets/" + ownArtworkId)
                .then().statusCode(403);
        given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/manager/assets")
                .then().statusCode(403)
                .header("GXP-Token-Expires", org.hamcrest.Matchers.nullValue());
    }

    @Test
    void homeScreenArtworkFallsBackRoomToProperty() {
        // with a room artwork set, the home screen serves the room's id
        // (needs an active stay — create and remove one around the check)
        int stayId = given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("roomId", roomId, "displayName", "Gast",
                        "checkIn", java.time.OffsetDateTime.now().minusHours(1).toString(),
                        "checkOut", java.time.OffsetDateTime.now().plusHours(3).toString(),
                        "status", "BOOKED"))
                .when().post("/api/manager/stays")
                .then().statusCode(201).extract().path("id");
        try {
            given().header("Authorization", "Bearer " + deviceToken)
                    .when().get("/api/guest/screens/home")
                    .then().statusCode(200)
                    .body("artworkAssetId", equalTo(ownArtworkId));
            // remove the room override → falls back to the property default (none set → null)
            given().header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json")
                    .body(Map.of("code", "1", "displayName", "Zimmer 1"))
                    .when().put("/api/manager/rooms/" + roomId)
                    .then().statusCode(200);
            given().header("Authorization", "Bearer " + deviceToken)
                    .when().get("/api/guest/screens/home")
                    .then().statusCode(200)
                    .body("artworkAssetId", org.hamcrest.Matchers.nullValue());
        } finally {
            given().header("Authorization", "Bearer " + adminToken)
                    .when().delete("/api/manager/stays/" + stayId)
                    .then().statusCode(204);
        }
    }
}
