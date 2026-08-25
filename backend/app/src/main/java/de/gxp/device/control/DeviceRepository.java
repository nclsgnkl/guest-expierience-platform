package de.gxp.device;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/** Package-private data access for paired devices. */
@ApplicationScoped
class DeviceRepository implements PanacheRepository<Device> {

    /** One indexed lookup by the UNIQUE token hash — every request's cost. */
    Device byTokenHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResult();
    }

    List<Device> allOrdered() {
        return list("order by id");
    }

    /**
     * The listed room codes, keyed lookup for the DTOs. Deliberately a native
     * query on the room TABLE — the Room entity belongs to {@code de.gxp.room}
     * and injecting {@code RoomControl} for a code read would put an
     * admin-annotated call into the unauthenticated claim path (deviation-5
     * precedent from domain-config).
     */
    String roomCodeOf(long roomId) {
        @SuppressWarnings("unchecked")
        List<String> codes = getEntityManager()
                .createNativeQuery("select code from room where id = :id")
                .setParameter("id", roomId).getResultList();
        return codes.isEmpty() ? null : codes.get(0);
    }
}
