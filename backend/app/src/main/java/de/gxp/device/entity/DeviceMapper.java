package de.gxp.device;

import java.time.ZoneOffset;

import de.gxp.api.model.DeviceDto;

/**
 * Hand-written mapper (spec §4) — package-private, static. The DTO NEVER
 * carries token material (REQ-407): {@code tokenHash} is deliberately not
 * mapped, not even redacted.
 */
final class DeviceMapper {

    private DeviceMapper() {
    }

    static DeviceDto toDto(Device device, String roomCode) {
        return new DeviceDto()
                .id(device.id)
                .name(device.name)
                .roomId(device.roomId)
                .roomCode(roomCode)
                .createdAt(device.createdAt.atOffset(ZoneOffset.UTC))
                .lastSeenAt(device.lastSeenAt == null ? null : device.lastSeenAt.atOffset(ZoneOffset.UTC))
                .revokedAt(device.revokedAt == null ? null : device.revokedAt.atOffset(ZoneOffset.UTC));
    }
}
