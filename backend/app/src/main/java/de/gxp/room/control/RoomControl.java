package de.gxp.room.control;

import java.util.List;

import de.gxp.infrastructure.http.ApiErrors;
import de.gxp.api.model.ApiErrorDetail;
import de.gxp.api.model.AssetKind;
import de.gxp.api.model.RoomDto;
import de.gxp.asset.control.AssetControl;
import de.gxp.property.control.PropertyControl;
import de.gxp.room.entity.Room;
import de.gxp.room.entity.RoomMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Control — rooms (REQ-305, REQ-320). Overrides are validated against the
 * property's offered sets ({@link PropertyControl}) and the artwork kind
 * ({@link AssetControl}). Deleting a room with stays is a 409 NAMING them —
 * manager work is never silently destroyed; the room's own config-override
 * rows go with it (ON DELETE CASCADE, proven by test).
 */
@ApplicationScoped
public class RoomControl {

    private final RoomRepository repository;
    private final PropertyControl propertyControl;
    private final AssetControl assetControl;

    RoomControl(RoomRepository repository, PropertyControl propertyControl, AssetControl assetControl) {
        this.repository = repository;
        this.propertyControl = propertyControl;
        this.assetControl = assetControl;
    }

    @Transactional
    public List<RoomDto> list() {
        return repository.allOrdered().stream().map(RoomMapper::toDto).toList();
    }

    @Transactional
    public RoomDto create(RoomDto dto) {
        validateOverrides(dto);
        if (repository.byCode(dto.getCode()) != null) {
            throw duplicateCode(dto.getCode());
        }
        Room room = new Room();
        RoomMapper.apply(dto, room);
        repository.persist(room);
        return RoomMapper.toDto(room);
    }

    @Transactional
    public RoomDto get(Long roomId) {
        return RoomMapper.toDto(require(roomId));
    }

    @Transactional
    public RoomDto update(Long roomId, RoomDto dto) {
        Room room = require(roomId);
        validateOverrides(dto);
        Room sameCode = repository.byCode(dto.getCode());
        if (sameCode != null && !sameCode.id.equals(room.id)) {
            throw duplicateCode(dto.getCode());
        }
        RoomMapper.apply(dto, room);
        return RoomMapper.toDto(room);
    }

    @Transactional
    public void delete(Long roomId) {
        Room room = require(roomId);
        List<Object[]> stays = repository.staysOf(roomId);
        if (!stays.isEmpty()) {
            throw ApiErrors.conflict("room-has-stays",
                    "The room still has stays — delete or move them first",
                    stays.stream().map(row -> new ApiErrorDetail(
                            "Stay '" + row[1] + "' (id " + row[0] + ")")
                            .reference(String.valueOf(row[0])))
                            .toList());
        }
        List<Object[]> activeDevices = repository.activeDevicesOf(roomId);
        if (!activeDevices.isEmpty()) {
            throw ApiErrors.conflict("room-has-devices",
                    "The room still has active devices — revoke or re-pair them first",
                    activeDevices.stream().map(row -> new ApiErrorDetail(
                            "Device '" + row[1] + "' (id " + row[0] + ")")
                            .reference(String.valueOf(row[0])))
                            .toList());
        }
        repository.deleteRevokedDevicesOf(roomId); // revoked rows go with the room (REQ-408)
        repository.delete(room); // menu/card overrides cascade in the DB
    }

    /**
     * Guest-read (REQ-416): the device's OWN room — code, display name,
     * artwork and defaults. Not HTTP-mapped; the roomId comes exclusively
     * from the authenticated device identity (REQ-411).
     */
    @Transactional
    public RoomDto guestView(long roomId) {
        return RoomMapper.toDto(require(roomId));
    }

    /** Cross-feature existence check (menu config / cards on a room scope). */
    @Transactional
    public void requireExists(Long roomId) {
        require(roomId);
    }

    /** Cross-feature read: every room id (invariant 3's affected scopes). */
    @Transactional
    public List<Long> allRoomIds() {
        return repository.allOrdered().stream().map(room -> room.id).toList();
    }

    /**
     * Cross-feature (StayControl): asserts the room exists (400 — a stay
     * referencing a nonexistent room is invalid input, not a wrong URL) and
     * takes the per-room write lock that serializes concurrent stay writes.
     */
    @Transactional
    public void lockForStayWrite(Long roomId) {
        if (repository.findById(roomId) == null) {
            throw ApiErrors.badRequest("unknown-room", "No room " + roomId);
        }
        repository.lockRow(roomId);
    }

    private Room require(Long roomId) {
        Room room = repository.findById(roomId);
        if (room == null) {
            throw ApiErrors.notFound("unknown-room", "No room " + roomId);
        }
        return room;
    }

    private void validateOverrides(RoomDto dto) {
        propertyControl.requireOffered(dto.getDefaultLanguage(), dto.getDefaultAppearance());
        assetControl.requireKind(dto.getArtworkAssetId(), AssetKind.ARTWORK, "artworkAssetId");
    }

    private static RuntimeException duplicateCode(String code) {
        return ApiErrors.conflict("room-code-exists", "A room with code '" + code + "' already exists");
    }
}
