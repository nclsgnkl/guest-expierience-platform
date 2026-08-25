package de.gxp.menu.entity;

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
public class MenuItemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "menu_item_key", nullable = false)
    public String menuItemKey;

    @Column(name = "room_id")
    public Long roomId;

    public Boolean enabled;

    @Column(name = "sort_index")
    public Integer sortIndex;

    @Column(name = "icon_override")
    public String iconOverride;
}
