package de.gxp.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Binary asset stored in PostgreSQL as BYTEA (constitution: one pg_dump is
 * the entire backup). Package-private — never leaves {@code de.gxp.asset}.
 * The honest memory bound for the materialized payload is the 10 MiB upload
 * cap; metadata reads use the payload-free projection in
 * {@link AssetRepository}, never this entity.
 */
@Entity
@Table(name = "asset")
class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "mime_type", nullable = false)
    String mimeType;

    @Column(name = "byte_size", nullable = false)
    int byteSize;

    @Column(nullable = false)
    int width;

    @Column(nullable = false)
    int height;

    /** Hex SHA-256 of the STORED bytes — doubles as the strong ETag. */
    @Column(nullable = false)
    String sha256;

    @Column(nullable = false)
    String kind;

    @Column(nullable = false)
    byte[] data;
}
