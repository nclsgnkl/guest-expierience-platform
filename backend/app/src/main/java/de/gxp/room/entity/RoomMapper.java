package de.gxp.room.entity;

import de.gxp.api.model.AppearanceMode;
import de.gxp.api.model.LanguageCode;
import de.gxp.api.model.RoomDto;

/**
 * Hand-written mapper (spec §4) — static, no mapping framework. Public only
 * because {@code de.gxp.room.control} invokes it; it is still a
 * {@code de.gxp.room} internal.
 */
public final class RoomMapper {

    private RoomMapper() {
    }

    public static RoomDto toDto(Room room) {
        return new RoomDto()
                .id(room.id)
                .code(room.code)
                .displayName(room.displayName)
                .artworkAssetId(room.artworkAssetId)
                .defaultLanguage(room.defaultLanguage == null ? null : LanguageCode.fromValue(room.defaultLanguage))
                .defaultAppearance(
                        room.defaultAppearance == null ? null : AppearanceMode.fromValue(room.defaultAppearance));
    }

    public static void apply(RoomDto dto, Room room) {
        room.code = dto.getCode();
        room.displayName = dto.getDisplayName();
        room.artworkAssetId = dto.getArtworkAssetId();
        room.defaultLanguage = dto.getDefaultLanguage() == null ? null : dto.getDefaultLanguage().toString();
        room.defaultAppearance = dto.getDefaultAppearance() == null ? null : dto.getDefaultAppearance().toString();
    }
}
