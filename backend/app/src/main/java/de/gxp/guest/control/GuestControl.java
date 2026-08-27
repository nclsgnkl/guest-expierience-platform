package de.gxp.guest.control;

import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.gxp.infrastructure.http.ApiErrors;
import de.gxp.api.model.ActionCardDto;
import de.gxp.api.model.GuestActionCard;
import de.gxp.api.model.GuestBrand;
import de.gxp.api.model.GuestContent;
import de.gxp.api.model.GuestHome;
import de.gxp.api.model.GuestMenu;
import de.gxp.api.model.GuestMenuItem;
import de.gxp.api.model.GuestPreference;
import de.gxp.api.model.GuestPreferencesUpdate;
import de.gxp.api.model.GuestProperty;
import de.gxp.api.model.GuestRoom;
import de.gxp.api.model.GuestSettings;
import de.gxp.api.model.GuestSettingsEntry;
import de.gxp.api.model.GuestStandby;
import de.gxp.api.model.OccupancyState;
import de.gxp.api.model.PropertyDto;
import de.gxp.api.model.RoomDto;
import de.gxp.api.model.StayDto;
import de.gxp.asset.control.AssetControl;
import de.gxp.auth.control.AuthControl;
import de.gxp.menu.control.MenuControl;
import de.gxp.property.control.PropertyControl;
import de.gxp.room.control.RoomControl;
import de.gxp.stay.control.StayControl;
import de.gxp.text.control.TemplateControl;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Control — the guest read model (REQ-416..426): the ONE place where
 * cross-feature composition is expected, deliberately read-only (spec §4)
 * plus the single stay-anchored preference write. Its Boundary
 * ({@code de.gxp.guest.boundary.GuestResource}) is
 * {@code @RolesAllowed("device")} class-wide, and
 * every method here derives the room EXCLUSIVELY from the authenticated device
 * identity — no method takes a room, stay, device or property parameter
 * (REQ-411, principle 4 verbatim).
 *
 * <p>Resolution is NEVER re-implemented (REQ-417): the menu ceiling lives in
 * {@link MenuControl}, occupancy in {@link StayControl}, templates in
 * {@link TemplateControl} — this Control only composes their internal guest
 * reads. Vacancy is maximally data-sparing (spec
 * decision 9): no stay-derived value leaves the server for a vacant room.
 *
 * <p>Sub-package layout (constitution v1.6.0): {@code de.gxp.guest} is the
 * second feature with NO Entity — it owns no table at all, by design. It gets
 * a {@code .boundary} and a {@code .control} and nothing else; the
 * package-private {@link GuestContentVersion} sits beside this class. Every
 * one of the seven features this Control reaches into is reached through that
 * feature's public {@code *Control}, which is the only crossing spec §4
 * allows.
 */
@ApplicationScoped
public class GuestControl {

    // The fixed settings chrome (REQ-418) — verbatim from the shipped
    // frontend data (example-property.data.ts); server-side constants, not
    // DB: no configuration can remove language/theme switching.
    static final String SETTINGS_ID = "einstellungen";
    static final String SETTINGS_ICON = "phosphorGearSix";
    static final String SETTINGS_ROUTE = "/einstellungen";
    private static final Map<String, String> SETTINGS_LABELS = Map.of(
            "de", "Einstellungen",
            "en", "Settings");

    private final SecurityIdentity identity;
    private final PropertyControl propertyControl;
    private final RoomControl roomControl;
    private final MenuControl menuControl;
    private final StayControl stayControl;
    private final AssetControl assetControl;
    private final TemplateControl templateControl;
    private final GuestContentVersion contentVersion;

    GuestControl(SecurityIdentity identity, PropertyControl propertyControl, RoomControl roomControl,
            MenuControl menuControl, StayControl stayControl, AssetControl assetControl,
            TemplateControl templateControl, GuestContentVersion contentVersion) {
        this.identity = identity;
        this.propertyControl = propertyControl;
        this.roomControl = roomControl;
        this.menuControl = menuControl;
        this.stayControl = stayControl;
        this.assetControl = assetControl;
        this.templateControl = templateControl;
        this.contentVersion = contentVersion;
    }

    /**
     * Everything the device's room renders from, in ONE document
     * (REQ-941/942/944/946/948) — property, room, occupancy, settings,
     * resolved menu, standby texts, and for an occupied room the stay-derived
     * half: display name, raw preference override and {@code home}.
     *
     * <p><b>Always throws the built 200/304 Response</b>, the same shape
     * {@code AssetControl.binary} ships: an entity-carrying
     * {@code WebApplicationException} never reaches a mapper (see
     * {@code ApiErrorMappers}) and {@code ResponseStatusFilter} leaves thrown
     * responses alone. That is what lets the 304 be answered from the version
     * stamp ALONE — matched before anything is read, composed or serialized
     * (REQ-946). The retired guest ETag response filter hashed the response
     * BYTES, so it had to resolve the whole payload to say "unchanged"; it is
     * deleted rather than bypassed, so no code path that could serialize in
     * order to tag survives.
     *
     * <p>Vacancy is expressed as ABSENCE, not as a second status code
     * (REQ-944/945): a vacant room answers 200 with the property-scoped half
     * intact and no {@code guestDisplayName}, no {@code preference} and no
     * {@code home}. Home's texts resolve against the active stay
     * ({@code {stay.displayName}}, {@code {stay.checkOut|time}}), so there is
     * nothing honest to serve for a room with no stay. {@code home} itself is
     * mandatory ({@link MenuControl#MANDATORY_KEYS}), so an occupied room
     * always carries it — the old 404 for a home that resolved disabled is
     * not handled here, it is unreachable (REQ-948).
     */
    @Transactional
    public GuestContent content(String ifNoneMatch) {
        long roomId = roomId();
        StayDto stay = stayControl.activeStayOf(roomId);
        EntityTag etag = new EntityTag(contentVersion.stampFor(roomId, stay));
        if (ifNoneMatch != null && etag.getValue().equals(ifNoneMatch.replace("\"", ""))) {
            throw new WebApplicationException(Response.notModified(etag).build());
        }

        PropertyDto property = propertyControl.guestView();
        RoomDto room = roomControl.guestView(roomId);
        List<String> languages = offeredLanguages(property);
        List<GuestMenuItem> menu = menuControl.guestResolvedMenu(roomId, languages);
        GuestContent content = new GuestContent()
                .property(new GuestProperty()
                        .name(property.getName())
                        .brand(new GuestBrand()
                                .primary(property.getBrandPrimary())
                                .primaryDeep(property.getBrandPrimaryDeep())
                                .secondary(property.getBrandSecondary()))
                        .logoAssetId(property.getLogoAssetId()))
                .room(new GuestRoom().code(room.getCode()).displayName(room.getDisplayName()))
                .occupancy(stay == null ? OccupancyState.VACANT : OccupancyState.OCCUPIED)
                .settings(new GuestSettings()
                        .languages(property.getLanguages())
                        .appearances(property.getAppearances())
                        // resolved defaults: room override, else property default
                        .defaultLanguage(room.getDefaultLanguage() != null
                                ? room.getDefaultLanguage() : property.getMainLanguage())
                        .defaultAppearance(room.getDefaultAppearance() != null
                                ? room.getDefaultAppearance() : property.getDefaultAppearance()))
                .menu(new GuestMenu()
                        .items(menu)
                        .settingsEntry(settingsEntry(languages)))
                .standby(new GuestStandby()
                        .headline(resolveLocalized(property.getStandbyHeadline(), property, null, languages))
                        .message(resolveLocalized(property.getStandbyMessage(), property, null, languages)));
        if (stay != null) {
            content.guestDisplayName(stay.getDisplayName());
            content.preference(new GuestPreference()
                    .language(stay.getLanguage() == null ? null : stay.getLanguage().toString())
                    .appearance(stay.getAppearance() == null ? null : stay.getAppearance().toString()));
            content.home(home(property, room, stay, menu, roomId, languages));
        }

        CacheControl cacheControl = new CacheControl();
        cacheControl.setNoCache(true);
        // JAX-RS defaults noTransform to true; the wire value stays exactly
        // the "no-cache" the kiosk has always been served
        cacheControl.setNoTransform(false);
        throw new WebApplicationException(Response.ok(content, MediaType.APPLICATION_JSON_TYPE)
                .tag(etag)
                .cacheControl(cacheControl)
                .build());
    }

    /**
     * The home content (REQ-419/420/421), occupied rooms only: all templates
     * resolved server-side per offered language against the active stay — no
     * braces ever leave the server. Artwork falls back room → property.
     */
    private GuestHome home(PropertyDto property, RoomDto room, StayDto stay, List<GuestMenuItem> menu,
            long roomId, List<String> languages) {
        List<GuestActionCard> cards = new ArrayList<>();
        for (ActionCardDto card : menuControl.guestResolvedCards(roomId)) {
            GuestMenuItem target = menu.stream()
                    .filter(item -> item.getKey().equals(card.getTargetMenuItemKey()))
                    .findFirst().orElse(null);
            if (target == null) {
                continue; // an impossible stranded card (invariants 2/3) — defensive skip, never a 500
            }
            cards.add(new GuestActionCard()
                    .icon(card.getIcon())
                    .title(resolveLocalized(card.getTitle(), property, stay, languages))
                    .description(resolveLocalized(card.getDescription(), property, stay, languages))
                    .targetKey(card.getTargetMenuItemKey())
                    .targetRoute(target.getRoute()));
        }
        return new GuestHome()
                .headline(resolveLocalized(property.getHeadline(), property, stay, languages))
                .subtitle(resolveLocalized(property.getHeroSubtitle(), property, stay, languages))
                .paragraph(resolveLocalized(property.getParagraph(), property, stay, languages))
                .artworkAssetId(room.getArtworkAssetId() != null
                        ? room.getArtworkAssetId() : property.getArtworkAssetId())
                .actionCards(cards);
    }

    /**
     * Guest asset delivery (REQ-422): only ids reachable from the device's
     * own resolved view — property logo, property default artwork, own-room
     * artwork. ANY other id, existing or not, is the same 404: asset ids are
     * sequential, and a guest device must not be able to enumerate stored
     * images. Delegates to {@link AssetControl}'s thrown-Response binary path
     * (ETag / 304 / long private cache).
     */
    @Transactional
    public File asset(Long assetId, String ifNoneMatch) {
        long roomId = roomId();
        PropertyDto property = propertyControl.guestView();
        RoomDto room = roomControl.guestView(roomId);
        Set<Long> reachable = new HashSet<>();
        if (property.getLogoAssetId() != null) {
            reachable.add(property.getLogoAssetId());
        }
        if (property.getArtworkAssetId() != null) {
            reachable.add(property.getArtworkAssetId());
        }
        if (room.getArtworkAssetId() != null) {
            reachable.add(room.getArtworkAssetId());
        }
        if (assetId == null || !reachable.contains(assetId)) {
            throw ApiErrors.notFound("asset-not-found", "No such asset");
        }
        return assetControl.guestBinary(assetId, ifNoneMatch);
    }

    /**
     * The guest preference write (REQ-424): whole record, both fields
     * nullable (null = follow the defaults), validated against the property's
     * offered sets (the named 400), landed on the ACTIVE stay via
     * {@link StayControl} (vacant → its named 409).
     *
     * <p>The offered-set check below DUPLICATES {@code PropertyControl}'s
     * {@code requireOffered}. It exists only because {@code requireOffered}
     * carried {@code @RolesAllowed("admin")} and would have thrown under the
     * guest's device identity, so the check had to be re-implemented on this
     * side. <b>That blocker is gone</b> (constitution v1.6.0, 2026-08-24:
     * authorization lives on the Boundary, no Control method is
     * role-restricted any more) — the duplication is now removable and should
     * be removed. It is left standing here because the fold is not free: the
     * guest 400 is {@code value-not-offered} (contract-declared for
     * {@code putGuestPreferences}) while {@code requireOffered} raises
     * {@code language-not-offered} / {@code appearance-not-offered}, and it
     * takes the typed enums rather than the update's nullable strings. That is
     * a separate delegation, not a drive-by of the annotation move.
     */
    @Transactional
    public void writePreferences(GuestPreferencesUpdate update) {
        long roomId = roomId();
        PropertyDto property = propertyControl.guestView();
        if (update.getLanguage() != null && property.getLanguages().stream()
                .noneMatch(option -> option.getCode().toString().equals(update.getLanguage()))) {
            throw ApiErrors.badRequest("value-not-offered",
                    "language is not in the property's offered set");
        }
        if (update.getAppearance() != null && property.getAppearances().stream()
                .noneMatch(option -> option.getMode().toString().equals(update.getAppearance()))) {
            throw ApiErrors.badRequest("value-not-offered",
                    "appearance is not in the property's offered set");
        }
        stayControl.writePreference(roomId, update.getLanguage(), update.getAppearance());
    }

    // ------------------------------------------------------------ internals

    /** The room — EXCLUSIVELY from the device identity (REQ-411), set by the auth flow. */
    private long roomId() {
        Long roomId = identity.getAttribute(AuthControl.DEVICE_ROOM_ATTRIBUTE);
        if (roomId == null) {
            // unreachable behind the Boundary's @RolesAllowed("device"); fail closed anyway
            throw ApiErrors.notFound("unknown-room", "No room identity");
        }
        return roomId;
    }

    /** Offered language codes, in display order (REQ-426: localized maps carry exactly these). */
    private static List<String> offeredLanguages(PropertyDto property) {
        return property.getLanguages().stream().map(option -> option.getCode().toString()).toList();
    }

    private GuestSettingsEntry settingsEntry(List<String> languages) {
        Map<String, String> label = new LinkedHashMap<>();
        for (String language : languages) {
            String constant = SETTINGS_LABELS.get(language);
            if (constant != null) {
                label.put(language, constant);
            }
        }
        return new GuestSettingsEntry().id(SETTINGS_ID).label(label).icon(SETTINGS_ICON).route(SETTINGS_ROUTE);
    }

    /**
     * Resolves one raw template map into a localized map over EXACTLY the
     * offered languages (REQ-420/426): checkout time/date in the property
     * timezone, each language's configured locale. A language the manager
     * has not filled in falls back to the main language's template — the
     * kiosk must always find every offered key (REQ-426), and honest
     * untranslated text beats a hole. {@code stay == null} only for standby
     * texts, whose allowed placeholder set has no stay fields.
     */
    private Map<String, String> resolveLocalized(Map<String, String> templates, PropertyDto property,
            StayDto stay, List<String> languages) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (templates == null) {
            return resolved;
        }
        ZoneId timezone = ZoneId.of(property.getTimezone());
        for (String language : languages) {
            String template = templates.get(language);
            if (template == null) {
                template = templates.get(property.getMainLanguage().toString());
            }
            if (template == null) {
                continue;
            }
            Locale locale = property.getLanguages().stream()
                    .filter(option -> option.getCode().toString().equals(language))
                    .map(option -> Locale.forLanguageTag(option.getLocale()))
                    .findFirst().orElse(Locale.forLanguageTag("de-DE"));
            resolved.put(language, templateControl.resolve(template, new TemplateControl.TemplateContext(
                    property.getName(),
                    stay == null ? null : stay.getDisplayName(),
                    stay == null ? null : stay.getCheckOut().toInstant(),
                    timezone, locale)));
        }
        return resolved;
    }
}
