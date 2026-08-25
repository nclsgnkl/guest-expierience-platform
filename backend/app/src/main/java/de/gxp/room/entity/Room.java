package de.gxp.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A room (REQ-305). Overrides are nullable — absent means "inherit the
 * property's default". Package-private, never leaves {@code de.gxp.room}.
 */
@Entity
@Table(name = "room")
class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true)
    String code;

    @Column(name = "display_name", nullable = false)
    String displayName;

    @Column(name = "artwork_asset_id")
    Long artworkAssetId;

    @Column(name = "default_language")
    String defaultLanguage;

    @Column(name = "default_appearance")
    String defaultAppearance;
}
