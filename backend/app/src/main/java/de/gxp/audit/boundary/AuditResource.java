package de.gxp.audit.boundary;

import de.gxp.api.ManagerAuditApi;
import de.gxp.api.model.AuditPageDto;
import de.gxp.audit.control.AuditControl;
import jakarta.annotation.security.RolesAllowed;

/**
 * Boundary — the audit trail's READ surface over the generated
 * {@link ManagerAuditApi} (tag {@code manager-audit}). Read only: there is
 * no update or delete operation anywhere (REQ-620). Whole surface admin,
 * hence the class-level {@code @RolesAllowed}. ONE delegating line (spec §4).
 */
@RolesAllowed("admin")
public class AuditResource implements ManagerAuditApi {

    private final AuditControl auditControl;

    AuditResource(AuditControl auditControl) {
        this.auditControl = auditControl;
    }

    @Override
    public AuditPageDto listAuditEntries(Integer page, Integer size) {
        return auditControl.page(page, size);
    }
}
