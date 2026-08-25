package de.gxp.property.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * One offered appearance option (ordered; REQ-304).
 *
 * <p>{@code mode} is public because {@code PropertyControl.requireOffered}
 * reads it directly across the sub-package line; the rest stays
 * package-private.
 */
@Entity
@Table(name = "property_appearance_option")
@IdClass(PropertyAppearanceOption.Key.class)
public class PropertyAppearanceOption {

    record Key(Short propertyId, String mode) implements Serializable {
    }

    @Id
    @Column(name = "property_id")
    Short propertyId;

    @Id
    public String mode;

    @Column(nullable = false)
    String icon;

    @Column(name = "sort_index", nullable = false)
    int sortIndex;
}
