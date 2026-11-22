package de.gxp.update.control;

import de.gxp.update.entity.UpdateChannel;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Control (data access) — package-private, the only JPA access of the
 * feature (update-channel D8). Single-row table: V8 seeds exactly one row
 * with {@code id = 1}.
 */
@ApplicationScoped
class UpdateChannelRepository implements PanacheRepository<UpdateChannel> {

    UpdateChannel theRow() {
        return findAll().singleResult();
    }
}
