package de.gxp.room.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A room (REQ-305). Overrides are nullable — absent means "inherit the
 * property's default".
 *
 * <p>The TYPE is {@code public} only because Java has no sub-package
 * visibility and {@code de.gxp.room.control} names it (spec §4, constitution
 * v1.6.0); it still never leaves {@code de.gxp.room}, which the architecture
 * guards enforce instead of the compiler.
 *
 * <p>Fields are widened per field: only {@code id} crosses the sub-package
 * line ({@code RoomControl} compares it for the duplicate-code check and
 * projects it for {@code allRoomIds}). The rest is read and written by
 * {@link RoomMapper} right here and stays package-private.
 */
@Entity
@Table(name = "room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

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
