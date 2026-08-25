package de.gxp.menu;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Package-private data access for the menu consistency unit: catalogue,
 * config rows and action cards live in ONE package because invariants 2/3
 * make them one transactional unit (design decision).
 */
@ApplicationScoped
class MenuRepository implements PanacheRepositoryBase<MenuItemDefinition, String> {

    List<MenuItemDefinition> catalogue() {
        return list("order by defaultSortIndex");
    }

    List<MenuItemDefinitionTranslation> definitionTranslations() {
        return getEntityManager()
                .createQuery("select t from MenuItemDefinitionTranslation t order by t.key, t.language",
                        MenuItemDefinitionTranslation.class)
                .getResultList();
    }

    List<MenuItemConfig> configsOf(Long roomId) {
        String scope = roomId == null ? "c.roomId is null" : "c.roomId = :roomId";
        var query = getEntityManager().createQuery(
                "select c from MenuItemConfig c where " + scope + " order by c.menuItemKey", MenuItemConfig.class);
        if (roomId != null) {
            query.setParameter("roomId", roomId);
        }
        return query.getResultList();
    }

    List<MenuItemConfigTranslation> configTranslationsOf(List<Long> configIds) {
        if (configIds.isEmpty()) {
            return List.of();
        }
        return getEntityManager()
                .createQuery("select t from MenuItemConfigTranslation t where t.configId in :ids order by t.language",
                        MenuItemConfigTranslation.class)
                .setParameter("ids", configIds).getResultList();
    }

    /** Explicit room-level "enabled" overrides of one item (ceiling rule 3). */
    List<MenuItemConfig> roomEnableRowsOf(String menuItemKey) {
        return getEntityManager().createQuery(
                "select c from MenuItemConfig c where c.menuItemKey = :key"
                        + " and c.roomId is not null and c.enabled = true order by c.roomId",
                MenuItemConfig.class)
                .setParameter("key", menuItemKey).getResultList();
    }

    /** Bulk delete executes immediately; the DB cascades the translations. */
    void deleteConfigsOf(Long roomId) {
        var em = getEntityManager();
        if (roomId == null) {
            em.createQuery("delete from MenuItemConfig c where c.roomId is null").executeUpdate();
        } else {
            em.createQuery("delete from MenuItemConfig c where c.roomId = :roomId")
                    .setParameter("roomId", roomId).executeUpdate();
        }
    }

    void persistConfig(MenuItemConfig config, List<MenuItemConfigTranslation> translations) {
        var em = getEntityManager();
        em.persist(config);
        em.flush(); // the generated id keys the translations
        for (MenuItemConfigTranslation translation : translations) {
            translation.configId = config.id;
            em.persist(translation);
        }
    }

    List<ActionCardConfig> cardsOf(Long roomId) {
        String scope = roomId == null ? "c.roomId is null" : "c.roomId = :roomId";
        var query = getEntityManager().createQuery(
                "select c from ActionCardConfig c where " + scope + " order by c.sortIndex", ActionCardConfig.class);
        if (roomId != null) {
            query.setParameter("roomId", roomId);
        }
        return query.getResultList();
    }

    List<ActionCardConfigTranslation> cardTranslationsOf(List<Long> cardIds) {
        if (cardIds.isEmpty()) {
            return List.of();
        }
        return getEntityManager()
                .createQuery("select t from ActionCardConfigTranslation t where t.cardId in :ids order by t.language",
                        ActionCardConfigTranslation.class)
                .setParameter("ids", cardIds).getResultList();
    }

    void deleteCardsOf(Long roomId) {
        var em = getEntityManager();
        if (roomId == null) {
            em.createQuery("delete from ActionCardConfig c where c.roomId is null").executeUpdate();
        } else {
            em.createQuery("delete from ActionCardConfig c where c.roomId = :roomId")
                    .setParameter("roomId", roomId).executeUpdate();
        }
    }

    void persistCard(ActionCardConfig card, List<ActionCardConfigTranslation> translations) {
        var em = getEntityManager();
        em.persist(card);
        em.flush(); // the generated id keys the translations
        for (ActionCardConfigTranslation translation : translations) {
            translation.cardId = card.id;
            em.persist(translation);
        }
    }
}
