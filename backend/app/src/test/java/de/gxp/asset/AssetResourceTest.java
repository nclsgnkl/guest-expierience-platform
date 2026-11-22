package de.gxp.asset;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import de.gxp.infrastructure.testsupport.ManagerFixtures;

/**
 * Asset pipeline over the real contract paths (REQ-310, REQ-316, REQ-324,
 * REQ-335): cap, content-vs-declared verification, downscaling with
 * recomputed sha over the stored bytes, ETag/304, payload-free listing.
 * Fixtures are generated in-test (no committed binaries). Every uploaded
 * asset is deleted again — tests share one database.
 */
@QuarkusTest
class AssetResourceTest {

    private static String token() {
        return ManagerFixtures.login();
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage gradient(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, Color.PINK, width, height, Color.BLUE));
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void delete(String token, int assetId) {
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/assets/" + assetId)
                .then().statusCode(204);
    }

    @Test
    void pngUploadStoresContentTrueMetadata() throws Exception {
        String token = token();
        byte[] png = encode(gradient(100, 50), "png");
        Response response = given().header("Authorization", "Bearer " + token)
                .contentType("image/png").body(png)
                .when().post("/api/manager/assets?kind=LOGO");
        response.then().statusCode(201)
                .body("mimeType", equalTo("image/png"))
                .body("width", equalTo(100))
                .body("height", equalTo(50))
                .body("byteSize", equalTo(png.length)) // within the cap: stored as uploaded
                .body("sha256", equalTo(sha256(png)))
                .body("kind", equalTo("LOGO"));
        delete(token, response.path("id"));
    }

    @Test
    void jpegUploadStoresTheDeclaredKind() {
        String token = token();
        byte[] jpeg = encode(gradient(64, 64), "jpg");
        int id = given().header("Authorization", "Bearer " + token)
                .contentType("image/jpeg").body(jpeg)
                .when().post("/api/manager/assets?kind=ARTWORK")
                .then().statusCode(201)
                .body("kind", equalTo("ARTWORK"))
                .body("mimeType", equalTo("image/jpeg"))
                .extract().path("id");
        delete(token, id);
    }

    @Test
    void oneByteOverTheCapIs413() {
        String token = token();
        byte[] oversized = new byte[10 * 1024 * 1024 + 1]; // cap + 1 byte
        given().header("Authorization", "Bearer " + token)
                .contentType("image/png").body(oversized)
                .when().post("/api/manager/assets?kind=LOGO")
                .then().statusCode(413)
                .body("code", equalTo("asset-too-large"));
    }

    @Test
    void pngBytesDeclaredAsJpegAreRejectedByContent() {
        String token = token();
        byte[] png = encode(gradient(10, 10), "png");
        given().header("Authorization", "Bearer " + token)
                .contentType("image/jpeg").body(png)
                .when().post("/api/manager/assets?kind=LOGO")
                .then().statusCode(400)
                .body("code", equalTo("content-type-mismatch"));
    }

    @Test
    void undecodableBytesAre400() {
        String token = token();
        byte[] garbage = "definitely not an image".getBytes();
        given().header("Authorization", "Bearer " + token)
                .contentType("image/png").body(garbage)
                .when().post("/api/manager/assets?kind=LOGO")
                .then().statusCode(400);
    }

    @Test
    void oversizedImageIsDownscaledAndShaDescribesTheStoredBytes() throws Exception {
        String token = token();
        byte[] large = encode(gradient(3000, 1500), "png"); // longest edge > 2560
        Response upload = given().header("Authorization", "Bearer " + token)
                .contentType("image/png").body(large)
                .when().post("/api/manager/assets?kind=ARTWORK");
        upload.then().statusCode(201)
                .body("width", equalTo(2560))
                .body("height", equalTo(1280));
        int id = upload.path("id");
        String storedSha = upload.path("sha256");
        int storedSize = upload.path("byteSize");
        assertNotEquals(sha256(large), storedSha, "sha must describe the stored, not the uploaded bytes");

        byte[] served = given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/assets/" + id)
                .then().statusCode(200)
                .extract().asByteArray();
        assertEquals(storedSha, sha256(served));
        assertEquals(storedSize, served.length);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(served));
        assertNotNull(decoded);
        assertEquals(2560, decoded.getWidth());
        assertEquals(1280, decoded.getHeight());
        delete(token, id);
    }

    @Test
    void etagRoundtripWith304() {
        String token = token();
        byte[] png = encode(gradient(20, 20), "png");
        Response upload = given().header("Authorization", "Bearer " + token)
                .contentType("image/png").body(png)
                .when().post("/api/manager/assets?kind=LOGO");
        upload.then().statusCode(201);
        int id = upload.path("id");
        String sha = upload.path("sha256");

        String etag = given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/assets/" + id)
                .then().statusCode(200)
                .header("Cache-Control", notNullValue())
                .extract().header("ETag");
        assertEquals("\"" + sha + "\"", etag, "strong ETag is the stored sha256");

        given().header("Authorization", "Bearer " + token)
                .header("If-None-Match", etag)
                .when().get("/api/manager/assets/" + id)
                .then().statusCode(304);
        delete(token, id);
    }

    @Test
    void listReturnsMetadataThroughThePayloadFreeProjection() {
        String token = token();
        byte[] png = encode(gradient(30, 30), "png");
        int id = given().header("Authorization", "Bearer " + token)
                .contentType("image/png").body(png)
                .when().post("/api/manager/assets?kind=LOGO")
                .then().statusCode(201).extract().path("id");

        // served by AssetRepository.listMetadata(), a constructor projection
        // that cannot select the payload column (AssetMetaRow has no field
        // for it) — the response proves the projection carries everything else
        Response list = given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/assets");
        list.then().statusCode(200);
        Map<String, Object> row = list.jsonPath().getMap("find { it.id == " + id + " }");
        assertNotNull(row);
        assertEquals("image/png", row.get("mimeType"));
        assertEquals(30, row.get("width"));
        assertTrue(row.containsKey("sha256"));
        delete(token, id);
    }

    @Test
    void unknownAssetIs404() {
        String token = token();
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/assets/999999")
                .then().statusCode(404)
                .body("code", equalTo("unknown-asset"));
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/assets/999999")
                .then().statusCode(404);
    }
}
