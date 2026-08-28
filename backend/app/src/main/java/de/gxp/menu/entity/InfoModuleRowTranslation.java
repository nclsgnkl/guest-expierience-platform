package de.gxp.menu.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Raw label/value templates of one {@code info} module row, per language
 * (module-content REQ-968). Both columns are NOT NULL by construction: a row
 * that exists has both in every language it was authored in, and
 * {@code GuestControl}'s existing main-language fallback fills the rest — which
 * is how "an absent value means an absent row" holds without omission code.
 * Templates are stored RAW, like
 * {@code action_card_config_translation.title_template}; resolution is
 * {@code GuestControl}'s.
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
@Table(name = "info_module_row_translation")
@IdClass(InfoModuleRowTranslation.Id_.class)
public class InfoModuleRowTranslation {

    record Id_(Long rowId, String language) implements Serializable {
    }

    @Id
    @Column(name = "row_id")
    Long rowId;

    @Id
    String language;

    @Column(nullable = false)
    String label;

    @Column(nullable = false)
    String value;
}
