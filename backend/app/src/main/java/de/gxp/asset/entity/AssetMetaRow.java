package de.gxp.asset.entity;

/**
 * Payload-free projection row for metadata reads (REQ-316/REQ-324): the JPQL
 * constructor expression selecting into this record never touches the BYTEA
 * column, so listing assets never materializes payloads.
 *
 * <p>Lives in {@code .entity} rather than beside the repository that selects
 * it, and is {@code public}, because {@link AssetMapper} carries the
 * {@code toDto} overload for it and the mapper belongs with the entity — a
 * projection of a row is a persistence shape, not a Control collaborator.
 * Keeping it in {@code .control} would have forced the mapping for it to live
 * somewhere else than the mapping for {@link Asset}.
 */
public record AssetMetaRow(Long id, String mimeType, int byteSize, int width, int height, String sha256, String kind) {
}
