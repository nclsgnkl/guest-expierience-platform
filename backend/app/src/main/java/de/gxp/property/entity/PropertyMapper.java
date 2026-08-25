package de.gxp.property.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import de.gxp.api.model.AppearanceMode;
import de.gxp.api.model.AppearanceOptionDto;
import de.gxp.api.model.LanguageCode;
import de.gxp.api.model.LanguageOptionDto;
import de.gxp.api.model.PropertyDto;

/**
 * Hand-written mapper (spec §4) — static, no mapping framework. Public only
 * because {@code de.gxp.property.control} invokes it; the two per-option
 * {@code toDto} overloads stay package-private because nothing outside this
 * package calls them.
 */
public final class PropertyMapper {

    private PropertyMapper() {
    }

    public static PropertyDto toDto(Property property, List<PropertyTranslation> translations,
            List<PropertyLanguageOption> languages, List<PropertyAppearanceOption> appearances) {
        PropertyDto dto = new PropertyDto()
                .name(property.name)
                .mainLanguage(LanguageCode.fromValue(property.mainLanguage))
                .timezone(property.timezone)
                .brandPrimary(property.brandPrimary)
                .brandPrimaryDeep(property.brandPrimaryDeep)
                .brandSecondary(property.brandSecondary)
                .defaultAppearance(AppearanceMode.fromValue(property.defaultAppearance))
                .logoAssetId(property.logoAssetId)
                .artworkAssetId(property.artworkAssetId)
                .languages(languages.stream().map(PropertyMapper::toDto).toList())
                .appearances(appearances.stream().map(PropertyMapper::toDto).toList());
        Map<String, String> heroSubtitle = new LinkedHashMap<>();
        Map<String, String> headline = new LinkedHashMap<>();
        Map<String, String> paragraph = new LinkedHashMap<>();
        Map<String, String> standbyHeadline = new LinkedHashMap<>();
        Map<String, String> standbyMessage = new LinkedHashMap<>();
        for (PropertyTranslation translation : translations) {
            heroSubtitle.put(translation.language, translation.heroSubtitle);
            headline.put(translation.language, translation.headline);
            paragraph.put(translation.language, translation.paragraph);
            standbyHeadline.put(translation.language, translation.standbyHeadline);
            standbyMessage.put(translation.language, translation.standbyMessage);
        }
        return dto.heroSubtitle(heroSubtitle).headline(headline).paragraph(paragraph)
                .standbyHeadline(standbyHeadline).standbyMessage(standbyMessage);
    }

    static LanguageOptionDto toDto(PropertyLanguageOption option) {
        return new LanguageOptionDto()
                .code(LanguageCode.fromValue(option.code))
                .shortLabel(option.shortLabel)
                .label(option.label)
                .locale(option.locale);
    }

    static AppearanceOptionDto toDto(PropertyAppearanceOption option) {
        return new AppearanceOptionDto()
                .mode(AppearanceMode.fromValue(option.mode))
                .icon(option.icon);
    }

    /** Mutates the managed row and builds fresh child rows from the DTO. */
    public static void apply(PropertyDto dto, Property property) {
        property.name = dto.getName();
        property.mainLanguage = dto.getMainLanguage().toString();
        property.timezone = dto.getTimezone();
        property.brandPrimary = dto.getBrandPrimary();
        property.brandPrimaryDeep = dto.getBrandPrimaryDeep();
        property.brandSecondary = dto.getBrandSecondary();
        property.defaultAppearance = dto.getDefaultAppearance().toString();
        property.logoAssetId = dto.getLogoAssetId();
        property.artworkAssetId = dto.getArtworkAssetId();
    }

    public static List<PropertyTranslation> toTranslations(PropertyDto dto, Short propertyId) {
        TreeSet<String> languages = new TreeSet<>();
        languages.addAll(dto.getHeroSubtitle().keySet());
        languages.addAll(dto.getHeadline().keySet());
        languages.addAll(dto.getParagraph().keySet());
        languages.addAll(dto.getStandbyHeadline().keySet());
        languages.addAll(dto.getStandbyMessage().keySet());
        List<PropertyTranslation> rows = new ArrayList<>();
        for (String language : languages) {
            PropertyTranslation row = new PropertyTranslation();
            row.propertyId = propertyId;
            row.language = language;
            row.heroSubtitle = dto.getHeroSubtitle().getOrDefault(language, "");
            row.headline = dto.getHeadline().getOrDefault(language, "");
            row.paragraph = dto.getParagraph().getOrDefault(language, "");
            row.standbyHeadline = dto.getStandbyHeadline().getOrDefault(language, "");
            row.standbyMessage = dto.getStandbyMessage().getOrDefault(language, "");
            rows.add(row);
        }
        return rows;
    }

    public static List<PropertyLanguageOption> toLanguageOptions(PropertyDto dto, Short propertyId) {
        List<PropertyLanguageOption> rows = new ArrayList<>();
        for (int i = 0; i < dto.getLanguages().size(); i++) {
            LanguageOptionDto option = dto.getLanguages().get(i);
            PropertyLanguageOption row = new PropertyLanguageOption();
            row.propertyId = propertyId;
            row.code = option.getCode().toString();
            row.shortLabel = option.getShortLabel();
            row.label = option.getLabel();
            row.locale = option.getLocale();
            row.sortIndex = i;
            rows.add(row);
        }
        return rows;
    }

    public static List<PropertyAppearanceOption> toAppearanceOptions(PropertyDto dto, Short propertyId) {
        List<PropertyAppearanceOption> rows = new ArrayList<>();
        for (int i = 0; i < dto.getAppearances().size(); i++) {
            AppearanceOptionDto option = dto.getAppearances().get(i);
            PropertyAppearanceOption row = new PropertyAppearanceOption();
            row.propertyId = propertyId;
            row.mode = option.getMode().toString();
            row.icon = option.getIcon();
            row.sortIndex = i;
            rows.add(row);
        }
        return rows;
    }
}
