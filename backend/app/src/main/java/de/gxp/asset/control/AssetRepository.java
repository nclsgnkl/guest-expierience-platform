package de.gxp.asset;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/** Package-private data access for assets. */
@ApplicationScoped
class AssetRepository implements PanacheRepository<Asset> {

    /** Metadata only — the payload column is deliberately never selected. */
    List<AssetMetaRow> listMetadata() {
        return getEntityManager().createQuery(
                "select new de.gxp.asset.AssetMetaRow(a.id, a.mimeType, a.byteSize, a.width, a.height, a.sha256, a.kind) "
                        + "from Asset a order by a.id",
                AssetMetaRow.class).getResultList();
    }

    /**
     * Who references this asset (delete guard, REQ-324)? Deliberately a
     * native query on the referencing TABLES: the property/room entities
     * belong to their own packages and never leave them; injecting those
     * Controls here would make the dependency circular (they already inject
     * {@link AssetControl} for kind checks). The FKs are ON DELETE RESTRICT
     * as the database-level backstop.
     */
    @SuppressWarnings("unchecked")
    List<String> findReferrers(long assetId) {
        return getEntityManager().createNativeQuery("""
                select 'property (logo)' as referrer from property where logo_asset_id = :id
                union all
                select 'property (artwork)' from property where artwork_asset_id = :id
                union all
                select 'room ' || code || ' (artwork)' from room where artwork_asset_id = :id
                order by referrer
                """).setParameter("id", assetId).getResultList();
    }
}
