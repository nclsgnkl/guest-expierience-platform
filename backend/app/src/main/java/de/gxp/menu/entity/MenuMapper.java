package de.gxp.menu.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.gxp.api.model.ActionCardDto;
import de.gxp.api.model.GuestInfoModule;
import de.gxp.api.model.GuestInfoRow;
import de.gxp.api.model.MenuItemConfigEntryDto;
import de.gxp.api.model.MenuItemDefinitionDto;

/**
 * Hand-written mappers (spec §4) — static, no mapping framework. Public only
 * because {@code de.gxp.menu.control} invokes them; they are still
 * {@code de.gxp.menu} internals.
 */
public final class MenuMapper {

    private MenuMapper() {
    }

    public static MenuItemDefinitionDto toDto(MenuItemDefinition definition,
            List<MenuItemDefinitionTranslation> translations) {
        Map<String, String> label = new LinkedHashMap<>();
        for (MenuItemDefinitionTranslation translation : translations) {
            if (translation.key.equals(definition.key)) {
                label.put(translation.language, translation.label);
            }
        }
        return new MenuItemDefinitionDto()
                .key(definition.key)
                .route(definition.route)
                .defaultIcon(definition.defaultIcon)
                .defaultSortIndex(definition.defaultSortIndex)
                .label(label)
                .introducedRelease(definition.introducedRelease);
    }

    public static MenuItemConfigEntryDto toDto(MenuItemConfig config, List<MenuItemConfigTranslation> translations) {
        Map<String, String> label = new LinkedHashMap<>();
        for (MenuItemConfigTranslation translation : translations) {
            if (translation.configId.equals(config.id)) {
                label.put(translation.language, translation.label);
            }
        }
        return new MenuItemConfigEntryDto()
                .menuItemKey(config.menuItemKey)
                .enabled(config.enabled)
                .sortIndex(config.sortIndex)
                .iconOverride(config.iconOverride)
                .label(label.isEmpty() ? null : label);
    }

    public static MenuItemConfig toEntity(MenuItemConfigEntryDto dto, Long roomId) {
        MenuItemConfig config = new MenuItemConfig();
        config.menuItemKey = dto.getMenuItemKey();
        config.roomId = roomId;
        config.enabled = dto.getEnabled();
        config.sortIndex = dto.getSortIndex();
        config.iconOverride = dto.getIconOverride();
        return config;
    }

    public static List<MenuItemConfigTranslation> toTranslations(MenuItemConfigEntryDto dto) {
        List<MenuItemConfigTranslation> rows = new ArrayList<>();
        if (dto.getLabel() != null) {
            for (Map.Entry<String, String> entry : dto.getLabel().entrySet()) {
                MenuItemConfigTranslation row = new MenuItemConfigTranslation();
                row.language = entry.getKey();
                row.label = entry.getValue();
                rows.add(row);
            }
        }
        return rows;
    }

    public static ActionCardDto toDto(ActionCardConfig card, List<ActionCardConfigTranslation> translations) {
        Map<String, String> title = new LinkedHashMap<>();
        Map<String, String> description = new LinkedHashMap<>();
        for (ActionCardConfigTranslation translation : translations) {
            if (translation.cardId.equals(card.id)) {
                title.put(translation.language, translation.titleTemplate);
                description.put(translation.language, translation.descriptionTemplate);
            }
        }
        return new ActionCardDto()
                .id(card.id)
                .sortIndex(card.sortIndex)
                .icon(card.icon)
                .targetMenuItemKey(card.targetMenuItemKey)
                .title(title)
                .description(description);
    }

    public static ActionCardConfig toEntity(ActionCardDto dto, Long roomId) {
        ActionCardConfig card = new ActionCardConfig();
        card.roomId = roomId;
        card.sortIndex = dto.getSortIndex();
        card.icon = dto.getIcon();
        card.targetMenuItemKey = dto.getTargetMenuItemKey();
        return card;
    }

    /**
     * One {@code info} module from the rows of ONE section, carrying RAW
     * templates — {@code GuestControl} resolves them, exactly as it does for
     * {@link #toDto(ActionCardConfig, List)}. {@code rows} must already be in
     * delivered order; this method preserves it and derives none.
     *
     * <p>The literal {@code "info"} is the wire discriminant pinned by
     * {@code discriminator.mapping} in {@code api/openapi.yaml} and by
     * {@code @JsonTypeName} on the generated class — see
     * {@code de.gxp.guest.GuestModuleContractTest}.
     */
    public static GuestInfoModule toInfoModule(List<InfoModuleRow> rows,
            List<InfoModuleRowTranslation> translations) {
        List<GuestInfoRow> mapped = new ArrayList<>();
        for (InfoModuleRow row : rows) {
            Map<String, String> label = new LinkedHashMap<>();
            Map<String, String> value = new LinkedHashMap<>();
            for (InfoModuleRowTranslation translation : translations) {
                if (translation.rowId.equals(row.id)) {
                    label.put(translation.language, translation.label);
                    value.put(translation.language, translation.value);
                }
            }
            mapped.add(new GuestInfoRow().label(label).value(value));
        }
        return new GuestInfoModule().type("info").rows(mapped);
    }

    public static List<ActionCardConfigTranslation> toTranslations(ActionCardDto dto) {
        List<ActionCardConfigTranslation> rows = new ArrayList<>();
        var languages = new java.util.TreeSet<String>();
        languages.addAll(dto.getTitle().keySet());
        languages.addAll(dto.getDescription().keySet());
        for (String language : languages) {
            ActionCardConfigTranslation row = new ActionCardConfigTranslation();
            row.language = language;
            row.titleTemplate = dto.getTitle().getOrDefault(language, "");
            row.descriptionTemplate = dto.getDescription().getOrDefault(language, "");
            rows.add(row);
        }
        return rows;
    }
}
