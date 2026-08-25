package de.gxp.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The release menu catalogue (REQ-306): Flyway-seeded, writable through no
 * API. The structurally fixed settings entry is deliberately NOT here.
 */
@Entity
@Table(name = "menu_item_definition")
class MenuItemDefinition {

    @Id
    @Column(name = "key")
    String key;

    @Column(name = "default_icon", nullable = false)
    String defaultIcon;

    /** The home section's route is the empty string. */
    @Column(nullable = false)
    String route;

    @Column(name = "default_sort_index", nullable = false)
    int defaultSortIndex;

    @Column(name = "introduced_release", nullable = false)
    String introducedRelease;
}
