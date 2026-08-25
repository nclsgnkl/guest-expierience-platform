package de.gxp.asset;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import de.gxp.api.ApiErrors;
import de.gxp.api.model.ApiErrorDetail;
import de.gxp.api.model.AssetKind;
import de.gxp.api.model.AssetMetadataDto;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

/**
 * Control — binary assets (REQ-310, REQ-316, REQ-324). Upload pipeline:
 * app-level 10 MiB cap (413 — the HTTP layer's limit is set higher so THIS
 * error answers), content-vs-declared verification by decoding (400),
 * downscale beyond 2560 px, sha/dimensions/size computed over the STORED
 * bytes. Delivery: strong SHA-256 ETag, 304 on If-None-Match.
 *
 * <p>Note on {@link #binary}: the generated interface returns {@code File}
 * (jaxrs-spec's binary mapping, returnResponse is global-only), which cannot
 * carry ETag/Cache-Control — so this method ALWAYS throws the fully built
 * {@link Response} (200 or 304), the same "outcomes are thrown from the
 * Control" channel every non-2xx already uses. No temp files, bytes are
 * streamed from the entity.
 */
@ApplicationScoped
public class AssetControl {

    static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024; // 10 MiB, pinned

    private static final Map<String, String> FORMAT_BY_MIME = Map.of(
            "image/png", "png",
            "image/jpeg", "jpeg");

    private final AssetRepository repository;
    private final HttpHeaders requestHeaders;

    AssetControl(AssetRepository repository, HttpHeaders requestHeaders) {
        this.repository = repository;
        this.requestHeaders = requestHeaders;
    }

    @RolesAllowed("admin")
    @Transactional
    public AssetMetadataDto upload(AssetKind kind, File body) {
        if (body == null || body.length() == 0) {
            throw ApiErrors.badRequest("empty-body", "The request carries no image bytes");
        }
        if (body.length() > MAX_UPLOAD_BYTES) {
            throw ApiErrors.payloadTooLarge("asset-too-large",
                    "The upload exceeds the 10 MiB asset cap");
        }
        String declaredMime = requestHeaders.getMediaType() == null ? ""
                : requestHeaders.getMediaType().getType() + "/" + requestHeaders.getMediaType().getSubtype();
        String expectedFormat = FORMAT_BY_MIME.get(declaredMime);
        if (expectedFormat == null) {
            throw ApiErrors.badRequest("unsupported-media-type", "Only image/png and image/jpeg are accepted");
        }
        byte[] uploaded = readAll(body);
        String actualFormat = ImageProcessor.detectFormat(uploaded);
        if (!expectedFormat.equals(actualFormat)) {
            throw ApiErrors.badRequest("content-type-mismatch",
                    "The declared content type does not match the decoded image format");
        }
        ImageProcessor.ProcessedImage stored;
        try {
            stored = ImageProcessor.process(uploaded, expectedFormat);
        } catch (IOException e) {
            stored = null;
        }
        if (stored == null) {
            throw ApiErrors.badRequest("undecodable-image", "The body does not decode as the declared image format");
        }
        Asset asset = new Asset();
        asset.mimeType = declaredMime;
        asset.byteSize = stored.bytes().length;
        asset.width = stored.width();
        asset.height = stored.height();
        asset.sha256 = stored.sha256();
        asset.kind = kind.name();
        asset.data = stored.bytes();
        repository.persist(asset);
        return AssetMapper.toDto(asset);
    }

    @RolesAllowed("admin")
    @Transactional
    public List<AssetMetadataDto> list() {
        return repository.listMetadata().stream().map(AssetMapper::toDto).toList();
    }

    /** Always throws the built 200/304 Response — see the class comment. */
    @RolesAllowed("admin")
    @Transactional
    public File binary(Long assetId, String ifNoneMatch) {
        Asset asset = repository.findById(assetId);
        if (asset == null) {
            throw ApiErrors.notFound("unknown-asset", "No asset " + assetId);
        }
        throw deliver(asset, ifNoneMatch, 3600);
    }

    /**
     * Guest delivery variant (REQ-422 — the {@code @PermitAll}-on-internal
     * precedent): the same thrown-Response binary path, with the guest cache
     * pin — asset content per id is IMMUTABLE (a replacement is a new id),
     * so {@code private, max-age=2592000}. Reachability is
     * {@code GuestControl}'s check (its named 404 answers first); the
     * unknown-id branch here is the defensive backstop with the same code.
     */
    @PermitAll
    @Transactional
    public File guestBinary(Long assetId, String ifNoneMatch) {
        Asset asset = repository.findById(assetId);
        if (asset == null) {
            throw ApiErrors.notFound("asset-not-found", "No such asset");
        }
        throw deliver(asset, ifNoneMatch, 2592000);
    }

    private static WebApplicationException deliver(Asset asset, String ifNoneMatch, int maxAgeSeconds) {
        EntityTag etag = new EntityTag(asset.sha256);
        if (ifNoneMatch != null && asset.sha256.equals(ifNoneMatch.replace("\"", ""))) {
            return new WebApplicationException(Response.notModified(etag).build());
        }
        CacheControl cacheControl = new CacheControl();
        cacheControl.setPrivate(true);
        cacheControl.setMaxAge(maxAgeSeconds);
        return new WebApplicationException(Response.ok(asset.data, asset.mimeType)
                .tag(etag)
                .cacheControl(cacheControl)
                .build());
    }

    @RolesAllowed("admin")
    @Transactional
    public void delete(Long assetId) {
        Asset asset = repository.findById(assetId);
        if (asset == null) {
            throw ApiErrors.notFound("unknown-asset", "No asset " + assetId);
        }
        List<String> referrers = repository.findReferrers(assetId);
        if (!referrers.isEmpty()) {
            throw ApiErrors.conflict("asset-in-use",
                    "The asset is still referenced — remove the references first",
                    referrers.stream()
                            .map(referrer -> new ApiErrorDetail("Referenced by " + referrer).reference(referrer))
                            .toList());
        }
        repository.delete(asset);
    }

    /**
     * Cross-feature check (Property/Room Controls inject this): the
     * referenced asset must exist and be of the expected kind.
     */
    @RolesAllowed("admin")
    @Transactional
    public void requireKind(Long assetId, AssetKind expectedKind, String fieldName) {
        if (assetId == null) {
            return;
        }
        Asset asset = repository.findById(assetId);
        if (asset == null) {
            throw ApiErrors.badRequest("unknown-asset", fieldName + " references a nonexistent asset");
        }
        if (!expectedKind.name().equals(asset.kind)) {
            throw ApiErrors.badRequest("asset-kind-mismatch",
                    fieldName + " must reference an asset of kind " + expectedKind.name());
        }
    }

    private static byte[] readAll(File body) {
        try {
            return Files.readAllBytes(body.toPath());
        } catch (IOException e) {
            throw ApiErrors.badRequest("unreadable-body", "The request body could not be read");
        }
    }
}
