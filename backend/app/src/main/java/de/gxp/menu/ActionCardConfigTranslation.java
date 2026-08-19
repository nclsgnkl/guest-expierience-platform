package de.gxp.menu;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Raw title/description templates of a card, per language (REQ-311/317). */
@Entity
@Table(name = "action_card_config_translation")
@IdClass(ActionCardConfigTranslation.Id_.class)
class ActionCardConfigTranslation {

    record Id_(Long cardId, String language) implements Serializable {
    }

    @Id
    @Column(name = "card_id")
    Long cardId;

    @Id
    String language;

    @Column(name = "title_template", nullable = false)
    String titleTemplate;

    @Column(name = "description_template", nullable = false)
    String descriptionTemplate;
}
