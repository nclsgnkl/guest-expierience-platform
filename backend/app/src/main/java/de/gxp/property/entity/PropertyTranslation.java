package de.gxp.property;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Typed per-entity translation row (REQ-311) — raw templates, never resolved. */
@Entity
@Table(name = "property_translation")
@IdClass(PropertyTranslation.Key.class)
class PropertyTranslation {

    record Key(Short propertyId, String language) implements Serializable {
    }

    @Id
    @Column(name = "property_id")
    Short propertyId;

    @Id
    String language;

    @Column(name = "hero_subtitle", nullable = false)
    String heroSubtitle;

    @Column(nullable = false)
    String headline;

    @Column(nullable = false)
    String paragraph;

    @Column(name = "standby_headline", nullable = false)
    String standbyHeadline;

    @Column(name = "standby_message", nullable = false)
    String standbyMessage;
}
