package de.gxp.property.control;

import java.util.List;

import de.gxp.property.entity.Property;
import de.gxp.property.entity.PropertyAppearanceOption;
import de.gxp.property.entity.PropertyLanguageOption;
import de.gxp.property.entity.PropertyTranslation;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Control (data access) — package-private data access for the property
 * aggregate (the single row, its translations and its offered-option rows).
 * It lives in {@code .control} rather than beside the entities precisely so
 * it can stay package-private (spec §4).
 */
@ApplicationScoped
class PropertyRepository implements PanacheRepositoryBase<Property, Short> {

    private static final short THE_ID = 1;

    Property theRow() {
        return findById(THE_ID);
    }

    List<PropertyTranslation> translations() {
        return getEntityManager()
                .createQuery("select t from PropertyTranslation t where t.propertyId = :id order by t.language",
                        PropertyTranslation.class)
                .setParameter("id", THE_ID).getResultList();
    }

    List<PropertyLanguageOption> languageOptions() {
        return getEntityManager()
                .createQuery("select o from PropertyLanguageOption o where o.propertyId = :id order by o.sortIndex",
                        PropertyLanguageOption.class)
                .setParameter("id", THE_ID).getResultList();
    }

    List<PropertyAppearanceOption> appearanceOptions() {
        return getEntityManager()
                .createQuery("select o from PropertyAppearanceOption o where o.propertyId = :id order by o.sortIndex",
                        PropertyAppearanceOption.class)
                .setParameter("id", THE_ID).getResultList();
    }

    /**
     * Bulk deletes execute immediately — safe to re-insert the same PKs after,
     * PROVIDED no child row loaded earlier in this transaction is still
     * managed (a bulk delete bypasses the persistence context, and the audit
     * diff in {@code PropertyControl.update} reads the current children
     * first). Hence: flush the pending changes to the property row, delete,
     * clear the context, then persist the new children.
     */
    void replaceChildren(List<PropertyTranslation> translations, List<PropertyLanguageOption> languages,
            List<PropertyAppearanceOption> appearances) {
        var em = getEntityManager();
        em.flush();
        em.createQuery("delete from PropertyTranslation t where t.propertyId = :id").setParameter("id", THE_ID)
                .executeUpdate();
        em.createQuery("delete from PropertyLanguageOption o where o.propertyId = :id").setParameter("id", THE_ID)
                .executeUpdate();
        em.createQuery("delete from PropertyAppearanceOption o where o.propertyId = :id").setParameter("id", THE_ID)
                .executeUpdate();
        em.clear();
        translations.forEach(em::persist);
        languages.forEach(em::persist);
        appearances.forEach(em::persist);
    }
}
