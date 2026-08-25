package de.gxp.property.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * One offered language (ordered; REQ-304).
 *
 * <p>{@code code} and {@code locale} are public because
 * {@code PropertyControl.requireOffered}/{@code localeOf} read them directly
 * across the sub-package line; the rest stays package-private.
 */
@Entity
@Table(name = "property_language_option")
@IdClass(PropertyLanguageOption.Key.class)
public class PropertyLanguageOption {

    record Key(Short propertyId, String code) implements Serializable {
    }

    @Id
    @Column(name = "property_id")
    Short propertyId;

    @Id
    public String code;

    @Column(name = "short_label", nullable = false)
    String shortLabel;

    /** Endonym — never translated. */
    @Column(nullable = false)
    String label;

    @Column(nullable = false)
    public String locale;

    @Column(name = "sort_index", nullable = false)
    int sortIndex;
}
