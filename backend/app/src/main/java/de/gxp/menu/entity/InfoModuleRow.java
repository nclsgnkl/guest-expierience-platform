package de.gxp.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One label/value row of an {@code info} module (module-content REQ-968).
 * Bound to a catalogue section by {@code menuItemKey} rather than to a
 * hardcoded {@code 'info'}: the TYPE is which table the row lives in (A3/D14),
 * the BINDING is the key — the same shape
 * {@code action_card_config.target_menu_item_key} already uses. Property scope
 * only: there is no room column, because module content is property-scoped
 * (A2) and D11 settled that there is no per-room override.
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
@Table(name = "info_module_row")
public class InfoModuleRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "menu_item_key", nullable = false)
    public String menuItemKey;

    @Column(name = "sort_index", nullable = false)
    int sortIndex;
}
