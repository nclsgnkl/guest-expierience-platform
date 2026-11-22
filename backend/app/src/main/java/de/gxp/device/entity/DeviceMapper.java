package de.gxp.device.entity;

import java.time.ZoneOffset;

import de.gxp.api.model.DeviceDto;

/**
 * Hand-written mapper (spec §4) — static, no mapping framework. The DTO NEVER
 * carries token material (REQ-407): {@code tokenHash} is deliberately not
 * mapped, not even redacted. Public only because
 * {@code de.gxp.device.control} invokes it; it is still a
 * {@code de.gxp.device} internal.
 */
public final class DeviceMapper {

    private DeviceMapper() {
    }

    public static DeviceDto toDto(Device device, String roomCode) {
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
