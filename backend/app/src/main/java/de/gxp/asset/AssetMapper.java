package de.gxp.asset;

import de.gxp.api.model.AssetKind;
import de.gxp.api.model.AssetMetadataDto;

/** Hand-written mapper (spec §4) — package-private, static. */
final class AssetMapper {

    private AssetMapper() {
    }

    static AssetMetadataDto toDto(Asset asset) {
        return new AssetMetadataDto()
                .id(asset.id)
                .mimeType(AssetMetadataDto.MimeTypeEnum.fromValue(asset.mimeType))
                .byteSize(asset.byteSize)
                .width(asset.width)
                .height(asset.height)
                .sha256(asset.sha256)
                .kind(AssetKind.fromValue(asset.kind));
    }

    static AssetMetadataDto toDto(AssetMetaRow row) {
        return new AssetMetadataDto()
                .id(row.id())
                .mimeType(AssetMetadataDto.MimeTypeEnum.fromValue(row.mimeType()))
                .byteSize(row.byteSize())
                .width(row.width())
                .height(row.height())
                .sha256(row.sha256())
                .kind(AssetKind.fromValue(row.kind()));
    }
}
