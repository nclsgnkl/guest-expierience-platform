package de.gxp.system.entity;

import de.gxp.api.model.SystemInfo;

/**
 * Entity → generated API DTO, hand-written (no mapping framework — spec §4).
 * Public only because it is invoked from {@code de.gxp.system.control}; it is
 * still a {@code de.gxp.system} internal, and calling it from another feature
 * is a violation the architecture tests catch.
 */
public final class InstanceInfoMapper {

    private InstanceInfoMapper() {
    }

    public static SystemInfo toSystemInfo(InstanceInfo entity, String applicationVersion) {
        return new SystemInfo()
                .instanceName(entity.name)
                .applicationVersion(applicationVersion);
    }
}
