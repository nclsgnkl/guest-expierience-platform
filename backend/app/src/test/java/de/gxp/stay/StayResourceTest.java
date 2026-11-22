package de.gxp.stay;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import de.gxp.infrastructure.testsupport.ManagerFixtures;

/**
 * Stays CRUD + invariant 5 (REQ-309, REQ-315, REQ-323): range check, offered
 * overrides, overlap 409 naming the conflict, adjacency allowed, CANCELLED
 * ignored, and the concurrency proof (two parallel overlapping creates —
 * exactly one wins). All created rooms/stays are removed again.
 */
@QuarkusTest
class StayResourceTest {

    private static String token() {
        return ManagerFixtures.login();
    }

    private static int createRoom(String token, String code) {
        return given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", code, "displayName", code))
                .when().post("/api/manager/rooms")
                .then().statusCode(201).extract().path("id");
    }

    private static Map<String, Object> stay(int roomId, String name, String in, String out) {
        Map<String, Object> body = new HashMap<>();
        body.put("roomId", roomId);
        body.put("displayName", name);
        body.put("checkIn", in);
        body.put("checkOut", out);
        body.put("status", "BOOKED");
        return body;
    }

    private static int createStay(String token, Map<String, Object> body) {
        return given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(body)
                .when().post("/api/manager/stays")
                .then().statusCode(201).extract().path("id");
    }

    private static void deleteStay(String token, int stayId) {
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/stays/" + stayId)
                .then().statusCode(204);
    }

    private static void deleteRoom(String token, int roomId) {
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/rooms/" + roomId)
                .then().statusCode(204);
    }

    @Test
    void crudRoundtripAndRoomFilter() {
        String token = token();
        int roomA = createRoom(token, "T22-a");
        int roomB = createRoom(token, "T22-b");
        int stayA = createStay(token, stay(roomA, "Lisa", "2027-03-01T15:00:00Z", "2027-03-05T11:00:00Z"));
        int stayB = createStay(token, stay(roomB, "Ben", "2027-03-01T15:00:00Z", "2027-03-05T11:00:00Z"));
        try {
            given().header("Authorization", "Bearer " + token)
                    .when().get("/api/manager/stays?roomId=" + roomA)
                    .then().statusCode(200)
                    .body("$", hasSize(1))
                    .body("[0].displayName", equalTo("Lisa"));
            given().header("Authorization", "Bearer " + token)
                    .when().get("/api/manager/stays/" + stayA)
                    .then().statusCode(200)
                    .body("roomId", equalTo(roomA))
                    .body("status", equalTo("BOOKED"));
            // update: language override + shifted window
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(Map.of("roomId", roomA, "displayName", "Lisa M.", "language", "en",
                            "checkIn", "2027-03-02T15:00:00Z", "checkOut", "2027-03-06T11:00:00Z",
                            "status", "BOOKED"))
                    .when().put("/api/manager/stays/" + stayA)
                    .then().statusCode(200)
                    .body("displayName", equalTo("Lisa M."))
                    .body("language", equalTo("en"));
        } finally {
            deleteStay(token, stayA);
            deleteStay(token, stayB);
            deleteRoom(token, roomA);
            deleteRoom(token, roomB);
        }
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/stays/" + stayA)
                .then().statusCode(404);
    }

    @Test
    void checkInMustBeBeforeCheckOut() {
        String token = token();
        int roomId = createRoom(token, "T22-range");
        try {
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(stay(roomId, "Zeitreise", "2027-03-05T11:00:00Z", "2027-03-01T15:00:00Z"))
                    .when().post("/api/manager/stays")
                    .then().statusCode(400)
                    .body("code", equalTo("invalid-stay-range"));
        } finally {
            deleteRoom(token, roomId);
        }
    }

    @Test
    void overrideOutsideTheOfferedSetIs400() {
        String token = token();
        int roomId = createRoom(token, "T22-app");
        try {
            Map<String, Object> body = stay(roomId, "Nachtmensch", "2027-03-01T15:00:00Z", "2027-03-02T11:00:00Z");
            body.put("appearance", "system"); // valid contract value, not offered by the seed property
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(body)
                    .when().post("/api/manager/stays")
                    .then().statusCode(400)
                    .body("code", equalTo("appearance-not-offered"));
        } finally {
            deleteRoom(token, roomId);
        }
    }

    @Test
    void unknownRoomIs400() {
        given().header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .body(stay(999999, "Niemand", "2027-03-01T15:00:00Z", "2027-03-02T11:00:00Z"))
                .when().post("/api/manager/stays")
                .then().statusCode(400)
                .body("code", equalTo("unknown-room"));
    }

    @Test
    void overlapIs409AdjacentPassesCancelledNeverBlocks() {
        String token = token();
        int roomId = createRoom(token, "T22-ovl");
        int first = createStay(token, stay(roomId, "Erste", "2027-04-01T15:00:00Z", "2027-04-05T11:00:00Z"));
        try {
            // overlapping → 409 naming the conflicting stay
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(stay(roomId, "Kollision", "2027-04-04T15:00:00Z", "2027-04-06T11:00:00Z"))
                    .when().post("/api/manager/stays")
                    .then().statusCode(409)
                    .body("code", equalTo("stay-overlap"))
                    .body("details[0].reference", equalTo(String.valueOf(first)));

            // adjacent (checkout == next check-in) → allowed
            int adjacent = createStay(token,
                    stay(roomId, "Anschluss", "2027-04-05T11:00:00Z", "2027-04-08T11:00:00Z"));

            // a CANCELLED stay in the middle blocks nothing
            Map<String, Object> cancelled = stay(roomId, "Storniert", "2027-04-02T15:00:00Z",
                    "2027-04-04T11:00:00Z");
            cancelled.put("status", "CANCELLED");
            int cancelledId = createStay(token, cancelled);
            given().header("Authorization", "Bearer " + token)
                    .when().get("/api/manager/stays?roomId=" + roomId)
                    .then().statusCode(200)
                    .body("$", hasSize(3));

            deleteStay(token, adjacent);
            deleteStay(token, cancelledId);
        } finally {
            deleteStay(token, first);
            deleteRoom(token, roomId);
        }
    }

    @Test
    void concurrentOverlappingCreatesCannotBothSucceed() throws Exception {
        String token = token();
        int roomId = createRoom(token, "T22-race");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt = () -> given()
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(stay(roomId, "Rennen", "2027-05-01T15:00:00Z", "2027-05-05T11:00:00Z"))
                    .when().post("/api/manager/stays")
                    .andReturn().statusCode();
            List<Future<Integer>> results = executor.invokeAll(List.of(attempt, attempt));
            Set<Integer> statuses = Set.of(results.get(0).get(), results.get(1).get());
            assertEquals(Set.of(201, 409), statuses,
                    "the room-row lock must serialize the writes: exactly one 201, one 409");
        } finally {
            executor.shutdownNow();
            // remove whatever won the race
            List<Integer> ids = given().header("Authorization", "Bearer " + token)
                    .when().get("/api/manager/stays?roomId=" + roomId)
                    .then().statusCode(200).extract().jsonPath().getList("id");
            for (Integer id : ids) {
                deleteStay(token, id);
            }
            deleteRoom(token, roomId);
        }
    }

    @Test
    void updateExcludesItselfFromTheOverlapCheck() {
        String token = token();
        int roomId = createRoom(token, "T22-self");
        int stayId = createStay(token, stay(roomId, "Selbst", "2027-06-01T15:00:00Z", "2027-06-05T11:00:00Z"));
        try {
            // extending the same stay must not collide with itself
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(stay(roomId, "Selbst", "2027-06-01T15:00:00Z", "2027-06-07T11:00:00Z"))
                    .when().put("/api/manager/stays/" + stayId)
                    .then().statusCode(200);
        } finally {
            deleteStay(token, stayId);
            deleteRoom(token, roomId);
        }
    }
}
