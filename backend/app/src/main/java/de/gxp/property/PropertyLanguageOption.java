package de.gxp.property;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** One offered language (ordered; REQ-304). */
@Entity
@Table(name = "property_language_option")
@IdClass(PropertyLanguageOption.Key.class)
class PropertyLanguageOption {

    record Key(Short propertyId, String code) implements Serializable {
    }

    @Id
    @Column(name = "property_id")
    Short propertyId;

    @Id
    String code;

    @Column(name = "short_label", nullable = false)
    String shortLabel;

    /** Endonym — never translated. */
    @Column(nullable = false)
    String label;

    @Column(nullable = false)
    String locale;

    @Column(name = "sort_index", nullable = false)
    int sortIndex;
}
