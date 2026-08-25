package de.gxp.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One scope's override row for a catalogue item (REQ-307): {@code roomId}
 * NULL is the property scope; every field is nullable and resolves per-field
 * room → property → catalogue default. Uniqueness per (item, scope) including
 * the NULL scope is the DB's {@code UNIQUE NULLS NOT DISTINCT}.
 */
@Entity
@Table(name = "menu_item_config")
class MenuItemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "menu_item_key", nullable = false)
    String menuItemKey;

    @Column(name = "room_id")
    Long roomId;

    Boolean enabled;

    @Column(name = "sort_index")
    Integer sortIndex;

    @Column(name = "icon_override")
    String iconOverride;
}
