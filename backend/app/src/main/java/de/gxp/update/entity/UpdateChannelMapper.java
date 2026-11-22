package de.gxp.update.entity;

import java.time.format.DateTimeFormatter;

import de.gxp.api.model.AvailableUpdateDto;
import de.gxp.api.model.UpdateCheckOutcome;
import de.gxp.api.model.UpdateStatusDto;

/**
 * Entity → generated API DTO, hand-written (no mapping framework — spec §4).
 * Maps the ROW's share of {@code UpdateStatusDto}; the Control adds what is
 * not in the row (running version, channel, configuration, the property's
 * timezone, the desired file's schedule, the updater state). Public only
 * because {@code de.gxp.update.control} calls it.
 */
public final class UpdateChannelMapper {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private UpdateChannelMapper() {
    }

    /** The row's fields onto a fresh status; {@code available} only when a verified manifest is stored. */
    public static UpdateStatusDto toStatus(UpdateChannel row) {
        UpdateStatusDto status = new UpdateStatusDto()
                .autoUpdateEnabled(row.autoUpdateEnabled)
                .applyAt(HH_MM.format(row.applyAt))
                .lastCheckAt(row.lastCheckAt == null ? null : row.lastCheckAt.atOffset(java.time.ZoneOffset.UTC))
                .lastCheckOutcome(UpdateCheckOutcome.fromValue(row.lastCheckOutcome));
        if (row.availableVersion != null) {
            status.available(new AvailableUpdateDto()
                    .version(row.availableVersion)
                    .publishedAt(row.availablePublishedAt.atOffset(java.time.ZoneOffset.UTC))
                    .artifactRef(row.availableArtifactRef));
        }
        return status;
    }
}
