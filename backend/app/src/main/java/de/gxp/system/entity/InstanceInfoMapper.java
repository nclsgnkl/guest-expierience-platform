package de.gxp.system;

import de.gxp.api.model.SystemInfo;

/**
 * Entity → generated API DTO, hand-written (no mapping framework — spec §4).
 * Package-private; invoked by the Control only.
 */
final class InstanceInfoMapper {

    private InstanceInfoMapper() {
    }

    static SystemInfo toSystemInfo(InstanceInfo entity, String applicationVersion) {
        return new SystemInfo()
                .instanceName(entity.name)
                .applicationVersion(applicationVersion);
    }
}
