package de.gxp.audit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.DeviceFixtures;
import de.gxp.infrastructure.testsupport.ManagerFixtures;
import de.gxp.infrastructure.testsupport.MenuFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * REQ-621 / REQ-624 (auth-hardening T-13): every domain mutation leaves its
 * row(s) in the mutation's own transaction, and a rolled-back mutation leaves
 * none. Two halves:
 *
 * <ol>
 * <li>{@link #everyMutatingControlMethodCallsTheAuditControl} — the reviewer's
 * "grep every call site", made mechanical: every {@code @Transactional public}
 * method of every {@code *Control.java} is DERIVED from the sources and must
 * either contain {@code auditControl.record} or be pinned in
 * {@link #NOT_A_MANAGER_MUTATION}; {@code AuthControl#login} (no
 * {@code @Transactional}) is the one explicit entry. A new mutation that
 * forgets the call fails the build until it is classified. Write paths that
 * bypass the audit on purpose (reviewer's record, T-13): kiosk-side
 * {@code createPairingRequest}/{@code poll}/{@code sweepExpiredPairingRequests},
 * guest {@code writePreference(s)}, {@code FirstUserSeeder}, {@code DevDataSeeder},
 * and {@code AuditControl#pruneOlderThan} itself.</li>
 * <li>The behavioural tests below drive each operation over HTTP and read the
 * rows back with native SQL (the repository is a {@code de.gxp.audit.control}
 * internal). Row shape only — the table has no value column, and the
 * property test additionally proves that no row text equals a value.</li>
 * </ol>
 */
@QuarkusTest
class AuditCoverageTest {

    /**
     * {@code @Transactional public} Control methods that are NOT manager
     * mutations and therefore carry no audit call: reads and lookups, the
     * kiosk/guest-side writes (pairing request, poll, preferences, the
     * pairing sweep), the login verification itself (its outcome is recorded
     * by {@code AuthControl#login}), and the audit feature's own writers.
     * A new {@code @Transactional public} method is neither here nor audited
     * → this test fails until it is classified, which is the review step.
     */
    private static final Set<String> NOT_A_MANAGER_MUTATION = Set.of(
            // reads and lookups
            "list", "get", "getConfig", "getCards", "getCatalogue", "guestView", "guestResolvedMenu",
            "guestResolvedCards", "requireKind", "requireOffered", "requireExists", "allRoomIds",
            "lockForStayWrite", "localeOf", "binary", "guestBinary", "activeStayOf", "session", "homeScreen",
            "asset", "ready", "info", "listDevices", "verifyToken",
            // kiosk / guest side writes (not manager mutations, REQ-621)
            "writePreference", "writePreferences", "createPairingRequest", "poll", "sweepExpiredPairingRequests",
            // the login verification — its outcome is audited by AuthControl#login
            "verifyLogin",
            // the audit feature's own writers and the retention job
            "record", "recordLoginSuccess", "recordLoginFailure", "pruneOlderThan");

    /**
     * Audited methods WITHOUT {@code @Transactional} — the login path commits
     * inside {@code verifyLogin}; the update check must not hold a transaction
     * across ~15 s of HTTP (update-channel D9), so it audits in its own.
     */
    private static final Map<String, List<String>> EXPLICIT_ENTRIES = Map.of(
            "de/gxp/auth/control/AuthControl.java", List.of("login"),
            "de/gxp/update/control/UpdateControl.java", List.of("checkNow"));

    private static final String AUDIT_CALL = "auditControl.record";

    @Inject
    EntityManager entityManager;

    private String token;
    private long rowsBefore;

    @BeforeEach
    void loginAndMark() {
        token = ManagerFixtures.login();
        rowsBefore = maxRowId();
    }

    @AfterEach
    void removeRowsWrittenHere() {
        long mark = rowsBefore;
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("delete from audit_entry where id > :mark").setParameter("mark", mark)
                .executeUpdate());
        // This class replaces the property-scope menu config (a whole-set
        // replace), which deletes V10's eight `enabled = false` opt-out rows.
        // Restore them, or the placeholder catalogue fixtures arrive ENABLED
        // at whatever class runs next in the shared database (02-design.md §3.3).
        // Database hygiene only — this class's assertions and its
        // NOT_A_MANAGER_MUTATION list are deliberately untouched (REQ-911).
        MenuFixtures.restoreCatalogueOptOut(entityManager);
    }

    // ------------------------------------------------------------ the grep guard

    @Test
    void everyMutatingControlMethodCallsTheAuditControl() throws IOException {
        Path sourceRoot = moduleRoot().resolve("src/main/java");
        List<String> missing = new ArrayList<>();
        List<String> audited = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith("Control.java")).sorted().toList()) {
                String relative = sourceRoot.relativize(file).toString();
                String text = stripCommentsAndStrings(Files.readString(file));
                assertFalse(CLASS_LEVEL_TRANSACTIONAL.matcher(text).find(), relative
                        + ": put @Transactional on the methods — the audit scanner reads method annotations");
                for (Method method : transactionalPublicMethods(text)) {
                    if (NOT_A_MANAGER_MUTATION.contains(method.name())) {
                        continue;
                    }
                    classify(relative, method, missing, audited);
                }
                for (String name : EXPLICIT_ENTRIES.getOrDefault(relative, List.of())) {
                    classify(relative, publicMethod(text, name, relative), missing, audited);
                }
            }
        }
        assertEquals(28, audited.size(), "the audited set changed — a mutation was added or dropped:\n"
                + String.join("\n", audited));
        assertTrue(missing.isEmpty(), "@Transactional public Control methods without an explicit " + AUDIT_CALL
                + " call (REQ-621, design D17 — no interceptor, every call site greppable); either add the call"
                + " or classify the method in NOT_A_MANAGER_MUTATION:\n" + String.join("\n", missing));
    }

    private static void classify(String file, Method method, List<String> missing, List<String> audited) {
        (method.body().contains(AUDIT_CALL) ? audited : missing).add(file + "#" + method.name());
    }

    private record Method(String name, String body) {
    }

    /** Class-level {@code @Transactional} would hide every method from the scanner (review 2026-08-26, 🟡 #2). */
    private static final Pattern CLASS_LEVEL_TRANSACTIONAL = Pattern.compile(
            "@Transactional(?:\\([^)]*\\))?\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)*public\\s+(?:final\\s+)?class\\b");

    private static final Pattern TRANSACTIONAL_PUBLIC = Pattern.compile(
            "@Transactional(?:\\([^)]*\\))?\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)*public\\s+[^;{(]*?\\b(\\w+)\\s*\\(");

    /** Every {@code @Transactional … public … name(} declaration with its brace-matched body. */
    private static List<Method> transactionalPublicMethods(String text) {
        List<Method> methods = new ArrayList<>();
        Matcher matcher = TRANSACTIONAL_PUBLIC.matcher(text);
        while (matcher.find()) {
            methods.add(new Method(matcher.group(1), bodyFrom(text, matcher.end() - 1)));
        }
        return methods;
    }

    /** The FIRST public method named {@code name} — for the explicit entries without {@code @Transactional}. */
    private static Method publicMethod(String text, String name, String file) {
        Matcher matcher = Pattern.compile("public\\s+[^;{(]*?\\b" + name + "\\s*\\(").matcher(text);
        if (!matcher.find()) {
            throw new IllegalStateException("no public method " + name + " in " + file + " — EXPLICIT_ENTRIES is stale");
        }
        return new Method(name, bodyFrom(text, matcher.end() - 1));
    }

    private static String bodyFrom(String text, int paren) {
        int open = text.indexOf('{', paren);
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return text.substring(open, i + 1);
            }
        }
        throw new IllegalStateException("unbalanced braces after offset " + paren);
    }

    /** Removes {@code //…}, {@code /*…*\/}, string and char literals so neither braces nor annotations in them count. */
    private static String stripCommentsAndStrings(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (text.startsWith("//", i)) {
                i = Math.max(text.indexOf('\n', i), i + 2);
            } else if (text.startsWith("/*", i)) {
                int end = text.indexOf("*/", i + 2);
                i = end < 0 ? text.length() : end + 2;
            } else if (c == '"' || c == '\'') {
                out.append(c == '"' ? "\"\"" : "' '");
                i++;
                while (i < text.length() && text.charAt(i) != c) {
                    i += text.charAt(i) == '\\' ? 2 : 1;
                }
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static Path moduleRoot() {
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            if (Files.isDirectory(directory.resolve("src/main/java/de/gxp"))) {
                return directory;
            }
        }
        throw new IllegalStateException("module root not found");
    }

    // ------------------------------------------------------------ property

    @Test
    void aPropertyUpdateLeavesOneRowPerChangedFieldAndNeverAValue() {
        Map<String, Object> property = new HashMap<>(currentProperty());
        String oldName = String.valueOf(property.get("name"));
        String newName = "Audit-Haus-" + System.nanoTime();
        property.put("name", newName);
        property.put("brandSecondary", "#0a0b0c".equals(property.get("brandSecondary")) ? "#0d0e0f" : "#0a0b0c");
        try {
            given().header("Authorization", "Bearer " + token).contentType("application/json").body(property)
                    .when().put("/api/manager/property").then().statusCode(200);
            List<Object[]> rows = newRows();
            assertEquals(2, rows.size(), "two changed fields → two rows (D18)");
            List<String> fields = rows.stream().map(row -> (String) row[6]).sorted().toList();
            assertEquals(List.of("brandSecondary", "name"), fields);
            for (Object[] row : rows) {
                assertEquals("admin", row[0]);
                assertEquals("127.0.0.1", row[1]);
                assertEquals(Boolean.FALSE, row[2]);
                assertEquals("update", row[3]);
                assertEquals("property", row[4]);
                for (Object cell : row) {
                    assertFalse(newName.equals(cell) || oldName.equals(cell), "a value must never be recorded");
                }
            }
        } finally {
            property.put("name", oldName);
            property.put("brandSecondary", currentProperty().get("brandSecondary"));
            given().header("Authorization", "Bearer " + token).contentType("application/json")
                    .body(property).when().put("/api/manager/property").then().statusCode(200);
        }
    }

    // ------------------------------------------------------------ room, menu, cards, stay

    @Test
    void roomMenuCardsAndStayOperationsLeaveTheirRowsAndARolledBackReplaceLeavesNone() {
        int roomId = given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(Map.of("code", "T13-aud", "displayName", "Audit"))
                .when().post("/api/manager/rooms").then().statusCode(201).extract().path("id");
        assertEquals(List.of("create room " + roomId), shapes());

        given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(Map.of("code", "T13-aud", "displayName", "Audit umbenannt"))
                .when().put("/api/manager/rooms/" + roomId).then().statusCode(200);
        assertEquals(List.of("create room " + roomId, "update room " + roomId + " displayName"), shapes());

        // a room update failing validation leaves no row (REQ-624)
        given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(Map.of("code", "", "displayName", "Audit"))
                .when().put("/api/manager/rooms/" + roomId).then().statusCode(400);
        assertEquals(2, shapes().size());

        given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(List.of(Map.of("menuItemKey", "info", "enabled", true)))
                .when().put("/api/manager/rooms/" + roomId + "/menu-config").then().statusCode(200);
        given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(List.of(Map.of("sortIndex", 0, "icon", "phosphorKey", "targetMenuItemKey", "info",
                        "title", Map.of("de", "Karte"), "description", Map.of("de", "Text"))))
                .when().put("/api/manager/rooms/" + roomId + "/action-cards").then().statusCode(200);
        given().header("Authorization", "Bearer " + token).contentType("application/json").body(List.of())
                .when().put("/api/manager/menu-config").then().statusCode(200);
        assertEquals(List.of("replace menu-config " + roomId, "replace action-cards " + roomId,
                "replace menu-config property"), shapes().subList(2, 5));

        // the rolled-back case: the property-level disable strands the room's card → 409, and the
        // replace row written BEFORE the invariant check is rolled back with the change
        given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(List.of(Map.of("menuItemKey", "info", "enabled", false)))
                .when().put("/api/manager/menu-config").then().statusCode(409)
                .body("code", equalTo("menu-item-targeted-by-cards"));
        assertEquals(5, shapes().size(), "a rolled-back replace leaves no row (REQ-624)");

        int stayId = given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(Map.of("roomId", roomId, "displayName", "Gast", "checkIn", "2031-03-01T15:00:00Z",
                        "checkOut", "2031-03-04T11:00:00Z", "status", "BOOKED"))
                .when().post("/api/manager/stays").then().statusCode(201).extract().path("id");
        given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(Map.of("roomId", roomId, "displayName", "Gast B", "checkIn", "2031-03-01T15:00:00Z",
                        "checkOut", "2031-03-05T11:00:00Z", "status", "BOOKED"))
                .when().put("/api/manager/stays/" + stayId).then().statusCode(200);
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/stays/" + stayId).then().statusCode(204);
        assertEquals(List.of("create stay " + stayId, "update stay " + stayId + " checkOut",
                "update stay " + stayId + " displayName", "delete stay " + stayId), shapes().subList(5, 9));

        given().header("Authorization", "Bearer " + token).contentType("application/json").body(List.of())
                .when().put("/api/manager/rooms/" + roomId + "/action-cards").then().statusCode(200);
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/rooms/" + roomId).then().statusCode(204);
        assertEquals("delete room " + roomId, shapes().get(shapes().size() - 1));
    }

    // ------------------------------------------------------------ assets

    @Test
    void assetUploadAndDeleteLeaveOneRowEach() {
        int assetId = given().header("Authorization", "Bearer " + token).contentType("image/png").body(png())
                .when().post("/api/manager/assets?kind=LOGO").then().statusCode(201).extract().path("id");
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/assets/" + assetId).then().statusCode(204);
        assertEquals(List.of("create asset " + assetId, "delete asset " + assetId), shapes());
    }

    // ------------------------------------------------------------ devices

    @Test
    void pairingConfirmRenameAndRevokeLeaveOneRowEach() {
        int roomId = given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(Map.of("code", "T13-dev", "displayName", "Device"))
                .when().post("/api/manager/rooms").then().statusCode(201).extract().path("id");
        String rawToken = DeviceFixtures.seedDevice(entityManager, roomId, "Audit-Kiosk");
        long deviceId = DeviceFixtures.deviceIdOf(entityManager, rawToken);
        try {
            Response created = given().when().post("/api/pairing/requests").then().statusCode(201).extract().response();
            String code = created.path("code");
            given().header("Authorization", "Bearer " + token).contentType("application/json")
                    .body(Map.of("roomId", roomId))
                    .when().post("/api/manager/pairings/" + code + "/confirm").then().statusCode(204);
            given().header("Authorization", "Bearer " + token).contentType("application/json")
                    .body(Map.of("name", "Flur"))
                    .when().put("/api/manager/devices/" + deviceId).then().statusCode(200);
            given().header("Authorization", "Bearer " + token)
                    .when().post("/api/manager/devices/" + deviceId + "/revoke").then().statusCode(204);
            List<String> shapes = shapes();
            assertEquals(4, shapes.size());
            assertTrue(shapes.get(1).startsWith("confirm device "), shapes.get(1));
            assertEquals(List.of("rename device " + deviceId, "revoke device " + deviceId), shapes.subList(2, 4));
            // the pairing CODE (a secret shown on the kiosk) is never in a row
            for (Object[] row : newRows()) {
                for (Object cell : row) {
                    assertFalse(code.equals(cell), "the pairing code must not be recorded");
                }
            }
        } finally {
            QuarkusTransaction.requiringNew().run(() -> {
                entityManager.createNativeQuery("delete from pairing_request where room_id = :room")
                        .setParameter("room", roomId).executeUpdate();
                DeviceFixtures.deleteDevice(entityManager, rawToken);
            });
            given().header("Authorization", "Bearer " + token)
                    .when().delete("/api/manager/rooms/" + roomId).then().statusCode(204);
        }
    }

    // ------------------------------------------------------------ helpers

    private Map<String, Object> currentProperty() {
        return given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/property").then().statusCode(200).extract().jsonPath().getMap("$");
    }

    private long maxRowId() {
        Object max = entityManager.createNativeQuery("select coalesce(max(id), 0) from audit_entry").getSingleResult();
        return ((Number) max).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> newRows() {
        return entityManager.createNativeQuery("select actor, address, via_proxy, action, entity, entity_id, field"
                + " from audit_entry where id > :mark order by id")
                .setParameter("mark", rowsBefore).getResultList();
    }

    /** {@code "<action> <entity> <entityId>[ <field>]"} of every row written since the mark, oldest first. */
    private List<String> shapes() {
        List<String> shapes = new ArrayList<>();
        for (Object[] row : newRows()) {
            assertEquals("admin", row[0]);
            shapes.add(row[3] + " " + row[4] + " " + row[5] + (row[6] == null ? "" : " " + row[6]));
        }
        return shapes;
    }

    private static byte[] png() {
        BufferedImage image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 20, 10);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
