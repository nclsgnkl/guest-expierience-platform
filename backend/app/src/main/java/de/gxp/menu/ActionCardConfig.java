package de.gxp.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One action card of a scope (REQ-308): {@code roomId} NULL is the property
 * scope; a room with ≥1 rows replaces the property set WHOLESALE. The target
 * key is deliberately not unique (several cards may target the same item);
 * ≤4 per scope holds by construction (sort_index 0..3 + per-scope unique).
 */
@Entity
@Table(name = "action_card_config")
class ActionCardConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "room_id")
    Long roomId;

    @Column(name = "sort_index", nullable = false)
    int sortIndex;

    @Column(nullable = false)
    String icon;

    @Column(name = "target_menu_item_key", nullable = false)
    String targetMenuItemKey;
}
