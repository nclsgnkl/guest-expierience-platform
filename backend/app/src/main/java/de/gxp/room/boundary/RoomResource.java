package de.gxp.room.boundary;

import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;

import de.gxp.api.ManagerRoomsApi;
import de.gxp.api.model.RoomDto;
import de.gxp.room.control.RoomControl;
import jakarta.annotation.security.RolesAllowed;

/**
 * Boundary — JAX-RS adapter over the generated {@link ManagerRoomsApi}.
 * Every method body is ONE delegating line (spec §4, binding).
 */
@RolesAllowed("admin")
public class RoomResource implements ManagerRoomsApi {

    private final RoomControl roomControl;

    RoomResource(RoomControl roomControl) {
        this.roomControl = roomControl;
    }

    @Override
    @ResponseStatus(201) // contract: created — HTTP-adapter concern, so it sits on the Boundary
    public RoomDto createRoom(RoomDto roomDto) {
        return roomControl.create(roomDto);
    }

    @Override
    public void deleteRoom(Long roomId) {
        roomControl.delete(roomId);
    }

    @Override
    public RoomDto getRoom(Long roomId) {
        return roomControl.get(roomId);
    }

    @Override
    public List<RoomDto> listRooms() {
        return roomControl.list();
    }

    @Override
    public RoomDto updateRoom(Long roomId, RoomDto roomDto) {
        return roomControl.update(roomId, roomDto);
    }
}
