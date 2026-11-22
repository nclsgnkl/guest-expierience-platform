package de.gxp.audit.entity;

import java.time.ZoneOffset;

import de.gxp.api.model.AuditEntryDto;

/**
 * Hand-written mapper (spec §4) — static, no mapping framework. Public only
 * because {@code de.gxp.audit.control} invokes it; a {@code de.gxp.audit}
 * internal.
 */
public final class AuditEntryMapper {

    private AuditEntryMapper() {
    }

    public static AuditEntryDto toDto(AuditEntry entry) {
        return new AuditEntryDto()
                .id(entry.id)
                .occurredAt(entry.occurredAt.atOffset(ZoneOffset.UTC))
                .actor(entry.actor)
                .address(entry.address)
                .viaProxy(entry.viaProxy)
                .action(entry.action)
                .entity(entry.entity)
                .entityId(entry.entityId)
                .field(entry.field);
    }
}
