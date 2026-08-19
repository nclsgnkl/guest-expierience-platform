package de.gxp.stay;

import java.time.Instant;
import java.util.List;

import de.gxp.api.ApiErrors;
import de.gxp.api.model.ApiErrorDetail;
import de.gxp.api.model.StayDto;
import de.gxp.property.PropertyControl;
import de.gxp.room.RoomControl;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
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

    StayControl(StayRepository repository, RoomControl roomControl, PropertyControl propertyControl) {
        this.repository = repository;
        this.roomControl = roomControl;
        this.propertyControl = propertyControl;
    }

    @RolesAllowed("admin")
    @Transactional
    public List<StayDto> list(Long roomId) {
        List<Stay> stays = roomId == null ? repository.allOrdered() : repository.byRoom(roomId);
        return stays.stream().map(StayMapper::toDto).toList();
    }

    @RolesAllowed("admin")
    @Transactional
    public StayDto create(StayDto dto) {
        validate(dto);
        roomControl.lockForStayWrite(dto.getRoomId()); // 400 unknown room + serialization
        requireNoOverlap(dto, null);
        Stay stay = new Stay();
        StayMapper.apply(dto, stay);
        repository.persist(stay);
        return StayMapper.toDto(stay);
    }

    @RolesAllowed("admin")
    @Transactional
    public StayDto get(Long stayId) {
        return StayMapper.toDto(require(stayId));
    }

    @RolesAllowed("admin")
    @Transactional
    public StayDto update(Long stayId, StayDto dto) {
        Stay stay = require(stayId);
        validate(dto);
        roomControl.lockForStayWrite(dto.getRoomId());
        requireNoOverlap(dto, stay.id);
        StayMapper.apply(dto, stay);
        return StayMapper.toDto(stay);
    }

    @RolesAllowed("admin")
    @Transactional
    public void delete(Long stayId) {
        repository.delete(require(stayId));
    }

    /**
     * Guest-read (REQ-419, the {@code @PermitAll}-on-internal precedent): the
     * room's ACTIVE stay — or {@code null} when vacant. The occupancy
     * derivation lives HERE (with the repository's range query), never
     * re-implemented; not HTTP-mapped, {@code GuestControl} composes it.
     */
    @PermitAll
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
     */
    @PermitAll
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
