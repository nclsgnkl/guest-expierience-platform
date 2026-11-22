package de.gxp.menu.entity;

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
 *
 * <p>The TYPE is {@code public} only because Java has no sub-package
 * visibility and {@code de.gxp.menu.control} names it (spec §4, constitution
 * v1.6.0); it still never leaves {@code de.gxp.menu}, which the architecture
 * guards enforce instead of the compiler. Fields are widened PER FIELD — only
 * those {@code MenuControl} or {@code MenuRepository} genuinely read or write
 * across the sub-package line; the rest belongs to {@link MenuMapper}, which
 * lives right here.
 */
@Entity
@Table(name = "action_card_config")
public class ActionCardConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "room_id")
    Long roomId;

    @Column(name = "sort_index", nullable = false)
    int sortIndex;

    @Column(nullable = false)
    String icon;

    @Column(name = "target_menu_item_key", nullable = false)
    public String targetMenuItemKey;
}
