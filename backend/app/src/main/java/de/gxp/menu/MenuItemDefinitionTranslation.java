package de.gxp.menu;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Seeded default label of a catalogue item (REQ-311). */
@Entity
@Table(name = "menu_item_definition_translation")
@IdClass(MenuItemDefinitionTranslation.Id_.class)
class MenuItemDefinitionTranslation {

    record Id_(String key, String language) implements Serializable {
    }

    @Id
    @Column(name = "key")
    String key;

    @Id
    String language;

    @Column(nullable = false)
    String label;
}
