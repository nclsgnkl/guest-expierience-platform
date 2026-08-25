package de.gxp.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The release menu catalogue (REQ-306): Flyway-seeded, writable through no
 * API. The structurally fixed settings entry is deliberately NOT here.
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
@Table(name = "menu_item_definition")
public class MenuItemDefinition {

    @Id
    @Column(name = "key")
    public String key;

    @Column(name = "default_icon", nullable = false)
    public String defaultIcon;

    /** The home section's route is the empty string. */
    @Column(nullable = false)
    public String route;

    @Column(name = "default_sort_index", nullable = false)
    public int defaultSortIndex;

    @Column(name = "introduced_release", nullable = false)
    String introducedRelease;
}
