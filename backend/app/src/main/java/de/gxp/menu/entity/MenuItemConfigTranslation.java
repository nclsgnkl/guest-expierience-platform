package de.gxp.menu;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Label override of a config row (REQ-311). */
@Entity
@Table(name = "menu_item_config_translation")
@IdClass(MenuItemConfigTranslation.Id_.class)
class MenuItemConfigTranslation {

    record Id_(Long configId, String language) implements Serializable {
    }

    @Id
    @Column(name = "config_id")
    Long configId;

    @Id
    String language;

    @Column(nullable = false)
    String label;
}
