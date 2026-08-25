package de.gxp.stay;

import java.time.ZoneOffset;

import de.gxp.api.model.AppearanceMode;
import de.gxp.api.model.LanguageCode;
import de.gxp.api.model.StayDto;
import de.gxp.api.model.StayStatus;

/** Hand-written mapper (spec §4) — package-private, static. */
final class StayMapper {

    private StayMapper() {
    }

    static StayDto toDto(Stay stay) {
        return new StayDto()
                .id(stay.id)
                .roomId(stay.roomId)
                .displayName(stay.displayName)
                .checkIn(stay.checkIn.atOffset(ZoneOffset.UTC))
                .checkOut(stay.checkOut.atOffset(ZoneOffset.UTC))
                .language(stay.language == null ? null : LanguageCode.fromValue(stay.language))
                .appearance(stay.appearance == null ? null : AppearanceMode.fromValue(stay.appearance))
                .status(StayStatus.fromValue(stay.status));
    }

    static void apply(StayDto dto, Stay stay) {
        stay.roomId = dto.getRoomId();
        stay.displayName = dto.getDisplayName();
        stay.checkIn = dto.getCheckIn().toInstant();
        stay.checkOut = dto.getCheckOut().toInstant();
        stay.language = dto.getLanguage() == null ? null : dto.getLanguage().toString();
        stay.appearance = dto.getAppearance() == null ? null : dto.getAppearance().toString();
        stay.status = dto.getStatus().toString();
    }
}
