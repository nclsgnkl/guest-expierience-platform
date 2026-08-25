package de.gxp.room;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/** Package-private data access for rooms. */
@ApplicationScoped
class RoomRepository implements PanacheRepository<Room> {

    Room byCode(String code) {
        return find("code", code).firstResult();
    }

    List<Room> allOrdered() {
        return list("order by id");
    }

    /**
     * The delete guard's evidence (REQ-320): stays of this room, named.
     * Deliberately a native query on the stay TABLE — the Stay entity belongs
     * to {@code de.gxp.stay} and {@code StayControl} already injects
     * {@code RoomControl}; injecting back would be circular. The FK is
     * ON DELETE RESTRICT as the database-level backstop.
     */
    @SuppressWarnings("unchecked")
    List<Object[]> staysOf(long roomId) {
        return getEntityManager().createNativeQuery(
                "select id, display_name from stay where room_id = :id order by check_in")
                .setParameter("id", roomId).getResultList();
    }

    /**
     * The delete guard's device evidence (REQ-408): ACTIVE (non-revoked)
     * devices of this room, named. Same deviation-5 shape as {@link #staysOf}:
     * a native query on the device TABLE — the Device entity belongs to
     * {@code de.gxp.device}, whose Control already injects {@code RoomControl}
     * for the confirm-time room check; reading via a Control injection back
     * would make the graph circular.
     */
    @SuppressWarnings("unchecked")
    List<Object[]> activeDevicesOf(long roomId) {
        return getEntityManager().createNativeQuery(
                "select id, name from device where room_id = :id and revoked_at is null order by id")
                .setParameter("id", roomId).getResultList();
    }

    /**
     * Rows of REVOKED devices go with their room (REQ-408) — executed in the
     * delete transaction before the room row (the FK is ON DELETE RESTRICT
     * as the database-level backstop).
     */
    void deleteRevokedDevicesOf(long roomId) {
        getEntityManager().createNativeQuery(
                "delete from device where room_id = :id and revoked_at is not null")
                .setParameter("id", roomId).executeUpdate();
    }

    /**
     * Serializes concurrent stay writes per room (invariant 5, design's
     * pinned mechanism): locks the room row until the surrounding transaction
     * commits.
     */
    void lockRow(long roomId) {
        getEntityManager().createNativeQuery("select id from room where id = :id for update")
                .setParameter("id", roomId).getResultList();
    }
}
