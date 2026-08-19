package de.gxp.property;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** One offered appearance option (ordered; REQ-304). */
@Entity
@Table(name = "property_appearance_option")
@IdClass(PropertyAppearanceOption.Key.class)
class PropertyAppearanceOption {

    record Key(Short propertyId, String mode) implements Serializable {
    }

    @Id
    @Column(name = "property_id")
    Short propertyId;

    @Id
    String mode;

    @Column(nullable = false)
    String icon;

    @Column(name = "sort_index", nullable = false)
    int sortIndex;
}
