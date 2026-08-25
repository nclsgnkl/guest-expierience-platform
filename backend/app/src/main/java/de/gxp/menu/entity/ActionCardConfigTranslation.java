package de.gxp.menu.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Raw title/description templates of a card, per language (REQ-311/317).
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
@Table(name = "action_card_config_translation")
@IdClass(ActionCardConfigTranslation.Id_.class)
public class ActionCardConfigTranslation {

    record Id_(Long cardId, String language) implements Serializable {
    }

    @Id
    @Column(name = "card_id")
    public Long cardId;

    @Id
    public String language;

    @Column(name = "title_template", nullable = false)
    public String titleTemplate;

    @Column(name = "description_template", nullable = false)
    String descriptionTemplate;
}
