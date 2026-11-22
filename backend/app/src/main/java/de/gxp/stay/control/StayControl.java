package de.gxp.stay.control;

import java.time.Instant;
import java.util.List;

import de.gxp.infrastructure.http.ApiErrors;
import de.gxp.api.model.ApiErrorDetail;
import de.gxp.api.model.StayDto;
import de.gxp.audit.control.AuditControl;
import de.gxp.property.control.PropertyControl;
import de.gxp.room.control.RoomControl;
import de.gxp.stay.entity.Stay;
import de.gxp.stay.entity.StayMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Control — stays (REQ-309, REQ-315, REQ-323). Invariant 5 runs under the
 * per-room row lock ({@link RoomControl#lockForStayWrite}): two concurrent
 * overlapping writes serialize, the second sees the first — race-free at any
 * isolation level. Adjacent stays pass by strict inequalities; CANCELLED
 * never blocks.
 */
@ApplicationScoped
public class StayControl {

    private final StayRepository repository;
    private final RoomControl roomControl;
    private final PropertyControl propertyControl;
    private final AuditControl auditControl;

    StayControl(StayRepository repository, RoomControl roomControl, PropertyControl propertyControl,
            AuditControl auditControl) {
        this.repository = repository;
        this.roomControl = roomControl;
        this.propertyControl = propertyControl;
        this.auditControl = auditControl;
    }

    @Transactional
    public List<StayDto> list(Long roomId) {
        List<Stay> stays = roomId == null ? repository.allOrdered() : repository.byRoom(roomId);
        return stays.stream().map(StayMapper::toDto).toList();
    }

    @Transactional
    public StayDto create(StayDto dto) {
        validate(dto);
        roomControl.lockForStayWrite(dto.getRoomId()); // 400 unknown room + serialization
        requireNoOverlap(dto, null);
        Stay stay = new Stay();
        StayMapper.apply(dto, stay);
        repository.persist(stay);
        auditControl.record(AuditControl.ACTION_CREATE, AuditControl.ENTITY_STAY, String.valueOf(stay.id), null);
        return StayMapper.toDto(stay);
    }

    @Transactional
    public StayDto get(Long stayId) {
        return StayMapper.toDto(require(stayId));
    }

    @Transactional
    public StayDto update(Long stayId, StayDto dto) {
        Stay stay = require(stayId);
        validate(dto);
        roomControl.lockForStayWrite(dto.getRoomId());
        requireNoOverlap(dto, stay.id);
        StayDto before = StayMapper.toDto(stay);
        StayMapper.apply(dto, stay);
        StayDto after = StayMapper.toDto(stay);
        auditControl.record(AuditControl.ACTION_UPDATE, AuditControl.ENTITY_STAY, String.valueOf(stay.id),
                AuditControl.changedFields(before, after));
        return after;
    }

    @Transactional
    public void delete(Long stayId) {
        repository.delete(require(stayId));
        auditControl.record(AuditControl.ACTION_DELETE, AuditControl.ENTITY_STAY, String.valueOf(stayId), null);
    }

    /**
     * Guest-read (REQ-419): the room's ACTIVE stay — or {@code null} when
     * vacant. The occupancy
     * derivation lives HERE (with the repository's range query), never
     * re-implemented; not HTTP-mapped, {@code GuestControl} composes it.
     */
    @Transactional
    public StayDto activeStayOf(long roomId) {
        Stay stay = repository.activeOf(roomId, Instant.now());
        return stay == null ? null : StayMapper.toDto(stay);
    }

    /**
     * The guest preference write (REQ-424): whole-record, anchored on the
     * ACTIVE stay — a new stay starts clean by construction. Values arrive
     * pre-validated against the offered sets ({@code GuestControl}'s named
     * 400); a vacant room is the named 409 — the kiosk's fire-and-forget
     * write treats that as normal.
     *
     * <p>The pre-validation is on the caller only because {@code validate()}
     * reaches {@code PropertyControl.requireOffered}, which used to be
     * {@code @RolesAllowed("admin")} and would have thrown under the guest's
     * device identity — so this write bypasses {@code validate()} and
     * {@code GuestControl} re-implements the offered-set check. <b>That
     * blocker is gone</b> (constitution v1.6.0, 2026-08-24: authorization
     * lives on the Boundary, Controls carry no annotations): this method may
     * now call {@code requireOffered} itself and the duplicate can go. Left
     * standing deliberately — the two checks raise different named 400s
     * ({@code value-not-offered} here, contract-declared, versus
     * {@code language-not-offered}) and take different types, so the fold is
     * its own delegation, not a drive-by of the annotation move.
     */
    @Transactional
    public void writePreference(long roomId, String language, String appearance) {
        Stay stay = repository.activeOf(roomId, Instant.now());
        if (stay == null) {
            throw ApiErrors.conflict("room-vacant", "The room has no active stay");
        }
        stay.language = language;
        stay.appearance = appearance;
    }

    private Stay require(Long stayId) {
        Stay stay = repository.findById(stayId);
        if (stay == null) {
            throw ApiErrors.notFound("unknown-stay", "No stay " + stayId);
        }
        return stay;
    }

    private void validate(StayDto dto) {
        if (!dto.getCheckIn().toInstant().isBefore(dto.getCheckOut().toInstant())) {
            throw ApiErrors.badRequest("invalid-stay-range", "checkIn must be before checkOut");
        }
        propertyControl.requireOffered(dto.getLanguage(), dto.getAppearance());
    }

    private void requireNoOverlap(StayDto dto, Long selfId) {
        if (de.gxp.api.model.StayStatus.CANCELLED.equals(dto.getStatus())) {
            return; // a cancelled stay conflicts with nothing (invariant 5)
        }
        List<Stay> conflicts = repository.overlapping(dto.getRoomId(),
                dto.getCheckIn().toInstant(), dto.getCheckOut().toInstant(), selfId);
        if (!conflicts.isEmpty()) {
            throw ApiErrors.conflict("stay-overlap",
                    "The stay overlaps an existing non-cancelled stay of the room",
                    conflicts.stream().map(conflict -> new ApiErrorDetail(
                            "Overlaps stay '" + conflict.displayName + "' (id " + conflict.id + ", "
                                    + conflict.checkIn + " – " + conflict.checkOut + ")")
                            .reference(String.valueOf(conflict.id)))
                            .toList());
        }
    }
}
