package de.gxp.asset;

import java.io.File;
import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;

import de.gxp.api.ManagerAssetsApi;
import de.gxp.api.model.AssetKind;
import de.gxp.api.model.AssetMetadataDto;

/**
 * Boundary — JAX-RS adapter over the generated {@link ManagerAssetsApi}.
 * Every method body is ONE delegating line (spec §4, binding).
 * {@code getAssetBinary} never actually returns: the Control throws the
 * built 200/304 Response (see {@link AssetControl#binary}).
 */
public class AssetResource implements ManagerAssetsApi {

    private final AssetControl assetControl;

    AssetResource(AssetControl assetControl) {
        this.assetControl = assetControl;
    }

    @Override
    public void deleteAsset(Long assetId) {
        assetControl.delete(assetId);
    }

    @Override
    public File getAssetBinary(Long assetId, String ifNoneMatch) {
        return assetControl.binary(assetId, ifNoneMatch);
    }

    @Override
    public List<AssetMetadataDto> listAssets() {
        return assetControl.list();
    }

    @Override
    @ResponseStatus(201) // contract: created — an HTTP-adapter concern, so it sits on the Boundary
    public AssetMetadataDto uploadAsset(AssetKind kind, File body) {
        return assetControl.upload(kind, body);
    }
}
