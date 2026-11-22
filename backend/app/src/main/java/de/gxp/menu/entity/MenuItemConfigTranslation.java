package de.gxp.menu.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Label override of a config row (REQ-311).
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
@Table(name = "menu_item_config_translation")
@IdClass(MenuItemConfigTranslation.Id_.class)
public class MenuItemConfigTranslation {

    record Id_(Long configId, String language) implements Serializable {
    }

    @Id
    @Column(name = "config_id")
    public Long configId;

    @Id
    public String language;

    @Column(nullable = false)
    public String label;
}
