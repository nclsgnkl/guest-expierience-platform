package de.gxp.asset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Binary asset stored in PostgreSQL as BYTEA (constitution: one pg_dump is
 * the entire backup). The honest memory bound for the materialized payload is
 * the 10 MiB upload cap; metadata reads use the payload-free projection
 * {@link AssetMetaRow}, never this entity.
 *
 * <p>The TYPE is {@code public} only because Java has no sub-package
 * visibility and the repository and Control in {@code de.gxp.asset.control}
 * name it (spec §4, constitution v1.6.0). It still never leaves
 * {@code de.gxp.asset} — that is enforced by {@code FeatureInternalsImportTest}
 * and {@code EntityExposureTest}, not by the compiler.
 *
 * <p>Fields are widened per field, only where the Control genuinely crosses
 * the sub-package line: {@code AssetControl} builds an upload row and reads
 * back the delivery columns, so everything below is public EXCEPT {@code id},
 * whose only reader is {@link AssetMapper} right here.
 */
@Entity
@Table(name = "asset")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "mime_type", nullable = false)
    public String mimeType;

    @Column(name = "byte_size", nullable = false)
    public int byteSize;

    @Column(nullable = false)
    public int width;

    @Column(nullable = false)
    public int height;

    /** Hex SHA-256 of the STORED bytes — doubles as the strong ETag. */
    @Column(nullable = false)
    public String sha256;

    @Column(nullable = false)
    public String kind;

    @Column(nullable = false)
    public byte[] data;
}
