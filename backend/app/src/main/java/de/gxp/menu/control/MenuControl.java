package de.gxp.menu.control;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.gxp.api.ApiErrors;
import de.gxp.api.model.ActionCardDto;
import de.gxp.api.model.ApiErrorDetail;
import de.gxp.api.model.GuestMenuItem;
import de.gxp.api.model.MenuItemConfigEntryDto;
import de.gxp.api.model.MenuItemDefinitionDto;
import de.gxp.menu.entity.ActionCardConfig;
import de.gxp.menu.entity.ActionCardConfigTranslation;
import de.gxp.menu.entity.MenuItemConfig;
import de.gxp.menu.entity.MenuItemConfigTranslation;
import de.gxp.menu.entity.MenuItemDefinition;
import de.gxp.menu.entity.MenuItemDefinitionTranslation;
import de.gxp.menu.entity.MenuMapper;
import de.gxp.room.control.RoomControl;
import de.gxp.text.control.TemplateControl;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Control — menu catalogue reads, config CRUD, action-card CRUD and the
 * §5 invariants 1–3 (REQ-306..308, REQ-312..314, REQ-321/322). One Control
 * on purpose: config and cards are a single consistency unit; two Controls
 * would inject each other circularly (design decision).
 *
 * <p>Scope convention: {@code roomId == null} is the property scope.
 * Menu resolution is the CEILING rule (design amendment 2026-08-19): the
 * property is the global ceiling — a room can only offer what the property
 * offers; room overrides are pure opt-outs. Cards resolve WHOLESALE (a room
 * with ≥1 rows replaces the property set).
 */
@ApplicationScoped
public class MenuControl {

    private static final int MAX_CARDS_PER_SCOPE = 4;

    private final MenuRepository repository;
    private final RoomControl roomControl;
    private final TemplateControl templateControl;

    MenuControl(MenuRepository repository, RoomControl roomControl, TemplateControl templateControl) {
        this.repository = repository;
        this.roomControl = roomControl;
        this.templateControl = templateControl;
    }

    // ------------------------------------------------------------ catalogue

    @Transactional
    public List<MenuItemDefinitionDto> getCatalogue() {
        List<MenuItemDefinitionTranslation> translations = repository.definitionTranslations();
        return repository.catalogue().stream()
                .map(definition -> MenuMapper.toDto(definition, translations))
                .toList();
    }

    // ---------------------------------------------------------- menu config

    @Transactional
    public List<MenuItemConfigEntryDto> getConfig(Long roomId) {
        if (roomId != null) {
            roomControl.requireExists(roomId);
        }
        return configDtos(roomId);
    }

    /**
     * Replaces the scope's override entries atomically, then validates
     * invariant 3 against every affected scope INSIDE the same transaction —
     * any stranded card rolls the whole change back (REQ-314).
     */
    @Transactional
    public List<MenuItemConfigEntryDto> putConfig(Long roomId, List<MenuItemConfigEntryDto> entries) {
        if (roomId != null) {
            roomControl.requireExists(roomId);
        }
        validateConfigEntries(roomId, entries);
        repository.deleteConfigsOf(roomId);
        for (MenuItemConfigEntryDto entry : entries) {
            repository.persistConfig(MenuMapper.toEntity(entry, roomId), MenuMapper.toTranslations(entry));
        }
        // affected scopes (design's pinned algorithm): property change hits
        // the property scope and every room that may inherit it
        List<Long> affectedScopes = new ArrayList<>();
        if (roomId == null) {
            affectedScopes.add(null);
            affectedScopes.addAll(roomControl.allRoomIds());
        } else {
            affectedScopes.add(roomId);
        }
        List<ApiErrorDetail> offenders = new ArrayList<>();
        Set<Long> reportedCardIds = new HashSet<>();
        for (Long scope : affectedScopes) {
            collectStrandedCards(scope, offenders, reportedCardIds);
        }
        if (!offenders.isEmpty()) {
            throw ApiErrors.conflict("menu-item-targeted-by-cards",
                    "Disabling would leave action cards targeting a disabled menu item — nothing was changed",
                    offenders);
        }
        // ceiling amendment rule 3: a property-level disable is blocked while
        // rooms still carry an explicit "enabled" override for the item — the
        // manager clears those on confirm and retries
        if (roomId == null) {
            List<ApiErrorDetail> dependentRooms = new ArrayList<>();
            for (MenuItemConfigEntryDto entry : entries) {
                if (Boolean.FALSE.equals(entry.getEnabled())) {
                    for (MenuItemConfig roomRow : repository.roomEnableRowsOf(entry.getMenuItemKey())) {
                        dependentRooms.add(new ApiErrorDetail(
                                "Room " + roomRow.roomId + " explicitly enables '" + entry.getMenuItemKey()
                                        + "' — the property-level disable removes it there too")
                                .scope(scopeName(roomRow.roomId))
                                .reference(entry.getMenuItemKey()));
                    }
                }
            }
            if (!dependentRooms.isEmpty()) {
                throw ApiErrors.conflict("menu-item-enabled-by-rooms",
                        "Disabling would override explicit room-level enables — nothing was changed; "
                                + "clear the room overrides first",
                        dependentRooms);
            }
        }
        return configDtos(roomId);
    }

    // ---------------------------------------------------------- action cards

    /** The scope's OWN rows — a room answers its own set, possibly empty. */
    @Transactional
    public List<ActionCardDto> getCards(Long roomId) {
        if (roomId != null) {
            roomControl.requireExists(roomId);
        }
        return cardDtos(roomId);
    }

    @Transactional
    public List<ActionCardDto> putCards(Long roomId, List<ActionCardDto> cards) {
        if (roomId != null) {
            roomControl.requireExists(roomId);
        }
        validateCards(roomId, cards);
        repository.deleteCardsOf(roomId);
        for (ActionCardDto card : cards) {
            repository.persistCard(MenuMapper.toEntity(card, roomId), MenuMapper.toTranslations(card));
        }
        // ceiling amendment rule 4 (invariant 2 extended): a property-scope
        // card set is also validated against every room that INHERITS it (no
        // own card rows) — a local room opt-out of the target strands the
        // card there. Applied, then validated in the same transaction.
        if (roomId == null) {
            List<ApiErrorDetail> stranded = new ArrayList<>();
            Set<Long> reportedCardIds = new HashSet<>();
            for (Long inheritingRoom : roomControl.allRoomIds()) {
                if (repository.cardsOf(inheritingRoom).isEmpty()) {
                    collectStrandedCards(inheritingRoom, stranded, reportedCardIds);
                }
            }
            if (!stranded.isEmpty()) {
                throw ApiErrors.conflict("target-disabled",
                        "A card targets a menu item that is disabled in a room inheriting the property's cards"
                                + " (invariant 2) — nothing was changed",
                        stranded);
            }
        }
        return cardDtos(roomId);
    }

    // ------------------------------------------------------------ guest reads

    /**
     * Guest-read resolution (REQ-417): the room's resolved menu. Resolved-ENABLED items only —
     * through {@link #enabled}, THE ceiling implementation, never
     * re-implemented; icon/sort/label resolve per-field room → property →
     * catalogue (labels per language, so every offered language always
     * resolves — the catalogue carries them all); ordered by resolved sort
     * index; the route comes from the catalogue. Not HTTP-mapped,
     * {@code GuestControl} composes it.
     */
    @Transactional
    public List<GuestMenuItem> guestResolvedMenu(long roomId, List<String> languages) {
        List<MenuItemConfig> propertyConfigs = repository.configsOf(null);
        List<MenuItemConfig> roomConfigs = repository.configsOf(roomId);
        List<MenuItemConfigTranslation> propertyLabels = repository
                .configTranslationsOf(propertyConfigs.stream().map(c -> c.id).toList());
        List<MenuItemConfigTranslation> roomLabels = repository
                .configTranslationsOf(roomConfigs.stream().map(c -> c.id).toList());
        List<MenuItemDefinitionTranslation> catalogueLabels = repository.definitionTranslations();
        List<GuestMenuItem> items = new ArrayList<>();
        for (MenuItemDefinition definition : repository.catalogue()) {
            if (!enabled(roomId, definition.key)) {
                continue;
            }
            MenuItemConfig roomConfig = configOf(roomConfigs, definition.key);
            MenuItemConfig propertyConfig = configOf(propertyConfigs, definition.key);
            Map<String, String> label = new java.util.LinkedHashMap<>();
            for (String language : languages) {
                String resolved = firstNonNull(
                        configLabelOf(roomLabels, roomConfig, language),
                        configLabelOf(propertyLabels, propertyConfig, language),
                        catalogueLabels.stream()
                                .filter(t -> t.key.equals(definition.key) && t.language.equals(language))
                                .map(t -> t.label).findFirst().orElse(null));
                if (resolved != null) {
                    label.put(language, resolved);
                }
            }
            items.add(new GuestMenuItem()
                    .key(definition.key)
                    .label(label)
                    .icon(firstNonNull(roomConfig == null ? null : roomConfig.iconOverride,
                            propertyConfig == null ? null : propertyConfig.iconOverride,
                            definition.defaultIcon))
                    .route(definition.route)
                    .sortIndex(firstNonNull(roomConfig == null ? null : roomConfig.sortIndex,
                            propertyConfig == null ? null : propertyConfig.sortIndex,
                            definition.defaultSortIndex)));
        }
        items.sort(java.util.Comparator.comparing(GuestMenuItem::getSortIndex));
        return items;
    }

    /**
     * Guest-read (REQ-420): the card set the room RESOLVES to — its own rows
     * WHOLESALE, else the property's (REQ-308, the settled rule). RAW
     * templates: {@code GuestControl} resolves them per offered language
     * against the active stay. Not HTTP-mapped.
     */
    @Transactional
    public List<ActionCardDto> guestResolvedCards(long roomId) {
        List<ActionCardConfig> cards = repository.cardsOf(roomId);
        if (cards.isEmpty()) {
            cards = repository.cardsOf(null);
        }
        List<ActionCardConfigTranslation> translations = repository
                .cardTranslationsOf(cards.stream().map(c -> c.id).toList());
        return cards.stream().map(card -> MenuMapper.toDto(card, translations)).toList();
    }

    private static MenuItemConfig configOf(List<MenuItemConfig> configs, String menuItemKey) {
        return configs.stream().filter(config -> config.menuItemKey.equals(menuItemKey)).findFirst().orElse(null);
    }

    private static String configLabelOf(List<MenuItemConfigTranslation> labels, MenuItemConfig config,
            String language) {
        if (config == null) {
            return null;
        }
        return labels.stream()
                .filter(t -> t.configId.equals(config.id) && t.language.equals(language))
                .map(t -> t.label).findFirst().orElse(null);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ internals

    private List<MenuItemConfigEntryDto> configDtos(Long roomId) {
        List<MenuItemConfig> configs = repository.configsOf(roomId);
        List<MenuItemConfigTranslation> translations = repository
                .configTranslationsOf(configs.stream().map(c -> c.id).toList());
        return configs.stream().map(config -> MenuMapper.toDto(config, translations)).toList();
    }

    private List<ActionCardDto> cardDtos(Long roomId) {
        List<ActionCardConfig> cards = repository.cardsOf(roomId);
        List<ActionCardConfigTranslation> translations = repository
                .cardTranslationsOf(cards.stream().map(c -> c.id).toList());
        return cards.stream().map(card -> MenuMapper.toDto(card, translations)).toList();
    }

    private void validateConfigEntries(Long roomId, List<MenuItemConfigEntryDto> entries) {
        Set<String> catalogueKeys = new HashSet<>(catalogueKeys());
        Set<String> seen = new HashSet<>();
        List<ApiErrorDetail> aboveCeiling = new ArrayList<>();
        for (MenuItemConfigEntryDto entry : entries) {
            if (!catalogueKeys.contains(entry.getMenuItemKey())) {
                throw ApiErrors.badRequest("unknown-menu-item",
                        "'" + entry.getMenuItemKey() + "' is not in the menu catalogue");
            }
            if (!seen.add(entry.getMenuItemKey())) {
                throw ApiErrors.badRequest("duplicate-menu-item",
                        "'" + entry.getMenuItemKey() + "' appears twice in the scope's entries");
            }
            if (entry.getLabel() != null) {
                requireSupportedLanguageKeys(entry.getLabel().keySet(), "label");
            }
            // ceiling amendment rule 2: a room cannot enable what the
            // property has disabled — the hotel doesn't offer it
            if (roomId != null && Boolean.TRUE.equals(entry.getEnabled())
                    && !propertyEnabled(entry.getMenuItemKey())) {
                aboveCeiling.add(new ApiErrorDetail(
                        "'" + entry.getMenuItemKey() + "' is disabled at the property scope — the property is"
                                + " the ceiling, a room cannot enable it")
                        .scope("property")
                        .reference(entry.getMenuItemKey()));
            }
        }
        if (!aboveCeiling.isEmpty()) {
            throw ApiErrors.conflict("item-disabled-at-property",
                    "A room can only offer what the property offers — nothing was changed", aboveCeiling);
        }
    }

    private void validateCards(Long roomId, List<ActionCardDto> cards) {
        if (cards.size() > MAX_CARDS_PER_SCOPE) {
            throw ApiErrors.badRequest("too-many-cards",
                    "A scope holds at most " + MAX_CARDS_PER_SCOPE + " action cards (invariant 1)");
        }
        Set<String> catalogueKeys = new HashSet<>(catalogueKeys());
        Set<Integer> sortIndexes = new HashSet<>();
        List<ApiErrorDetail> templateOffenders = new ArrayList<>();
        List<ApiErrorDetail> disabledTargets = new ArrayList<>();
        for (ActionCardDto card : cards) {
            if (card.getSortIndex() == null || card.getSortIndex() < 0 || card.getSortIndex() > 3) {
                throw ApiErrors.badRequest("invalid-sort-index", "sortIndex must be between 0 and 3 (invariant 1)");
            }
            if (!sortIndexes.add(card.getSortIndex())) {
                throw ApiErrors.badRequest("duplicate-sort-index",
                        "sortIndex " + card.getSortIndex() + " is used twice in this scope (invariant 1)");
            }
            if (!catalogueKeys.contains(card.getTargetMenuItemKey())) {
                throw ApiErrors.badRequest("unknown-menu-item",
                        "Card target '" + card.getTargetMenuItemKey() + "' is not in the menu catalogue");
            }
            requireSupportedLanguageKeys(card.getTitle().keySet(), "title");
            requireSupportedLanguageKeys(card.getDescription().keySet(), "description");
            collectInvalidTemplates(card.getTitle(), "title", templateOffenders);
            collectInvalidTemplates(card.getDescription(), "description", templateOffenders);
            if (!enabled(roomId, card.getTargetMenuItemKey())) {
                disabledTargets.add(new ApiErrorDetail(
                        "Card '" + titleOf(card) + "' targets '" + card.getTargetMenuItemKey()
                                + "', which is disabled in scope " + scopeName(roomId))
                        .scope(scopeName(roomId))
                        .reference(card.getTargetMenuItemKey()));
            }
        }
        if (!templateOffenders.isEmpty()) {
            throw ApiErrors.badRequest("unknown-placeholder",
                    "A card template uses a placeholder that is unknown in its context", templateOffenders);
        }
        if (!disabledTargets.isEmpty()) {
            throw ApiErrors.conflict("target-disabled",
                    "A card targets a menu item that is disabled in this scope (invariant 2)", disabledTargets);
        }
    }

    private void collectInvalidTemplates(Map<String, String> localized, String field,
            List<ApiErrorDetail> offenders) {
        for (Map.Entry<String, String> entry : localized.entrySet()) {
            for (String placeholder : templateControl.findInvalidPlaceholders(entry.getValue(),
                    TemplateControl.ALL_PLACEHOLDERS)) {
                offenders.add(new ApiErrorDetail(field + "." + entry.getKey() + " uses " + placeholder)
                        .reference(placeholder));
            }
        }
    }

    private static void requireSupportedLanguageKeys(Set<String> keys, String field) {
        for (String key : keys) {
            if (!"de".equals(key) && !"en".equals(key)) {
                throw ApiErrors.badRequest("invalid-language-key",
                        field + " uses unsupported language key '" + key + "' (v1: de, en)");
            }
        }
    }

    private List<String> catalogueKeys() {
        return repository.catalogue().stream().map(definition -> definition.key).toList();
    }

    /**
     * Invariant 3 evidence for one scope: every card the scope RESOLVES to
     * (its own rows, else the inherited property set) whose target is
     * disabled in that scope's resolved menu. Each card is reported ONCE —
     * in the first affected scope that strands it — matching the design's
     * "every offending card and its scope": a property-level card inherited
     * by several rooms is one offender, not one per room.
     */
    private void collectStrandedCards(Long scopeRoomId, List<ApiErrorDetail> offenders, Set<Long> reportedCardIds) {
        List<ActionCardConfig> cards = repository.cardsOf(scopeRoomId);
        if (scopeRoomId != null && cards.isEmpty()) {
            cards = repository.cardsOf(null); // wholesale inheritance (REQ-308)
        }
        if (cards.isEmpty()) {
            return;
        }
        List<ActionCardConfigTranslation> translations = repository
                .cardTranslationsOf(cards.stream().map(c -> c.id).toList());
        for (ActionCardConfig card : cards) {
            if (!enabled(scopeRoomId, card.targetMenuItemKey) && reportedCardIds.add(card.id)) {
                String title = translations.stream()
                        .filter(t -> t.cardId.equals(card.id))
                        .filter(t -> "de".equals(t.language))
                        .map(t -> t.titleTemplate)
                        .findFirst()
                        .or(() -> translations.stream().filter(t -> t.cardId.equals(card.id))
                                .map(t -> t.titleTemplate).findFirst())
                        .orElse("(untitled)");
                offenders.add(new ApiErrorDetail(
                        "Card '" + title + "' (id " + card.id + ") targets '" + card.targetMenuItemKey
                                + "', which would be disabled in scope " + scopeName(scopeRoomId))
                        .scope(scopeName(scopeRoomId))
                        .reference(String.valueOf(card.id)));
            }
        }
    }

    /**
     * CEILING resolution (design amendment 2026-08-19, resolving review
     * 🟡 #2): {@code enabled(room, k) = enabled(property, k) AND
     * room-override(k, default true)}. A property-level disable wins over any
     * explicit room-level enable — room overrides are pure opt-outs.
     * {@code enabled(property, k)} stays property row → catalogue default.
     */
    private boolean enabled(Long scopeRoomId, String menuItemKey) {
        boolean propertyEnabled = propertyEnabled(menuItemKey);
        if (scopeRoomId == null || !propertyEnabled) {
            return propertyEnabled;
        }
        return repository.configsOf(scopeRoomId).stream()
                .filter(config -> config.menuItemKey.equals(menuItemKey))
                .map(config -> config.enabled)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(true);
    }

    /** Property-scope resolution: property row → catalogue default (enabled). */
    private boolean propertyEnabled(String menuItemKey) {
        return repository.configsOf(null).stream()
                .filter(config -> config.menuItemKey.equals(menuItemKey))
                .map(config -> config.enabled)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(true);
    }

    private static String titleOf(ActionCardDto card) {
        return card.getTitle().getOrDefault("de",
                card.getTitle().values().stream().findFirst().orElse("(untitled)"));
    }

    private static String scopeName(Long roomId) {
        return roomId == null ? "property" : "room:" + roomId;
    }
}
