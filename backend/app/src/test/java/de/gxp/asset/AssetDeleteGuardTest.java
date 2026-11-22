package de.gxp.asset;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import de.gxp.infrastructure.testsupport.ManagerFixtures;

/**
 * Asset delete guard (T-16, REQ-324): deleting an asset referenced by the
 * property (logo/artwork) or by a room is a 409 NAMING the referrer; an
 * unreferenced asset deletes fine. All references are removed again — tests
 * share one database.
 */
@QuarkusTest
class AssetDeleteGuardTest {

    private static String token() {
        return ManagerFixtures.login();
    }

    private static int upload(String token, String kind) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", out);
            return given().header("Authorization", "Bearer " + token)
                    .contentType("image/png").body(out.toByteArray())
                    .when().post("/api/manager/assets?kind=" + kind)
                    .then().statusCode(201).extract().path("id");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void assetReferencedAsPropertyLogoIs409NamingTheProperty() {
        String token = token();
        int assetId = upload(token, "LOGO");
        Map<String, Object> property = given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/property")
                .then().statusCode(200).extract().jsonPath().getMap("$");
        try {
            property.put("logoAssetId", assetId);
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(property)
                    .when().put("/api/manager/property")
                    .then().statusCode(200);

            given().header("Authorization", "Bearer " + token)
                    .when().delete("/api/manager/assets/" + assetId)
                    .then().statusCode(409)
                    .body("code", equalTo("asset-in-use"))
                    .body("details[0].reference", containsString("property (logo)"));
        } finally {
            property.put("logoAssetId", null);
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(property)
                    .when().put("/api/manager/property")
                    .then().statusCode(200);
        }
        // unreferenced again — deletable
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/assets/" + assetId)
                .then().statusCode(204);
    }

    @Test
    void assetReferencedByARoomIs409NamingTheRoom() {
        String token = token();
        int assetId = upload(token, "ARTWORK");
        int roomId = given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "T16-ref", "displayName", "Referenz", "artworkAssetId", assetId))
                .when().post("/api/manager/rooms")
                .then().statusCode(201).extract().path("id");

        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/assets/" + assetId)
                .then().statusCode(409)
                .body("code", equalTo("asset-in-use"))
                .body("details[0].reference", containsString("room T16-ref"));

        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/rooms/" + roomId)
                .then().statusCode(204);
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/assets/" + assetId)
                .then().statusCode(204);
    }
}
