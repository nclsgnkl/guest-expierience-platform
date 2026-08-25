package de.gxp.property.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The single property row (invariant 4 — {@code CHECK (id = 1)} in V2).
 * Asset references are plain FK values, the kind checks go through
 * {@code AssetControl} (cross-feature Controls only, spec §4).
 *
 * <p>The TYPE is {@code public} only because Java has no sub-package
 * visibility and {@code de.gxp.property.control} names it (spec §4,
 * constitution v1.6.0); it still never leaves {@code de.gxp.property}, which
 * the architecture guards enforce instead of the compiler.
 *
 * <p>Fields are widened per field: only {@code id} is read across the
 * sub-package line ({@code PropertyControl} passes it to the mapper's
 * child-row builders). Everything else is written and read by
 * {@link PropertyMapper} right here and stays package-private.
 */
@Entity
@Table(name = "property")
public class Property {

    @Id
    public Short id;

    @Column(nullable = false)
    String name;

    @Column(name = "main_language", nullable = false)
    String mainLanguage;

    @Column(nullable = false)
    String timezone;

    @Column(name = "brand_primary", nullable = false)
    String brandPrimary;

    @Column(name = "brand_primary_deep", nullable = false)
    String brandPrimaryDeep;

    @Column(name = "brand_secondary", nullable = false)
    String brandSecondary;

    @Column(name = "default_appearance", nullable = false)
    String defaultAppearance;

    @Column(name = "logo_asset_id")
    Long logoAssetId;

    @Column(name = "artwork_asset_id")
    Long artworkAssetId;
}
