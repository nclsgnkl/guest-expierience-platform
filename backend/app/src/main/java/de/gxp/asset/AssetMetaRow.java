package de.gxp.asset;

/**
 * Payload-free projection row for metadata reads (REQ-316/REQ-324): the JPQL
 * constructor expression selecting into this record never touches the BYTEA
 * column, so listing assets never materializes payloads.
 */
record AssetMetaRow(Long id, String mimeType, int byteSize, int width, int height, String sha256, String kind) {
}
