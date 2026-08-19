package de.gxp.property;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The single property row (invariant 4 — {@code CHECK (id = 1)} in V2).
 * Package-private; asset references are plain FK values, the kind checks go
 * through {@code AssetControl} (cross-feature Controls only, spec §4).
 */
@Entity
@Table(name = "property")
class Property {

    @Id
    Short id;

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
