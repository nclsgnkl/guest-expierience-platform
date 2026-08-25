package de.gxp.property.control;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.gxp.infrastructure.http.ApiErrors;
import de.gxp.api.model.ApiErrorDetail;
import de.gxp.api.model.AppearanceMode;
import de.gxp.api.model.AppearanceOptionDto;
import de.gxp.api.model.AssetKind;
import de.gxp.api.model.LanguageCode;
import de.gxp.api.model.LanguageOptionDto;
import de.gxp.api.model.PropertyDto;
import de.gxp.asset.control.AssetControl;
import de.gxp.property.entity.Property;
import de.gxp.property.entity.PropertyMapper;
import de.gxp.text.control.TemplateControl;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Control — the single property (REQ-303/304, REQ-317..319). Reads return
 * RAW templates; a replace validates timezone (ZoneId), defaults contained
 * in their offered sets, translation keys against the v1 language codes,
 * referenced asset kinds (via {@link AssetControl}) and every template field
 * against its allowed placeholder set (via {@link TemplateControl}).
 */
@ApplicationScoped
public class PropertyControl {

    private final PropertyRepository repository;
    private final AssetControl assetControl;
    private final TemplateControl templateControl;

    PropertyControl(PropertyRepository repository, AssetControl assetControl, TemplateControl templateControl) {
        this.repository = repository;
        this.assetControl = assetControl;
        this.templateControl = templateControl;
    }

    @Transactional
    public PropertyDto get() {
        return PropertyMapper.toDto(repository.theRow(), repository.translations(),
                repository.languageOptions(), repository.appearanceOptions());
    }

    @Transactional
    public PropertyDto update(PropertyDto dto) {
        validate(dto);
        assetControl.requireKind(dto.getLogoAssetId(), AssetKind.LOGO, "logoAssetId");
        assetControl.requireKind(dto.getArtworkAssetId(), AssetKind.ARTWORK, "artworkAssetId");
        Property property = repository.theRow();
        PropertyMapper.apply(dto, property);
        repository.replaceChildren(
                PropertyMapper.toTranslations(dto, property.id),
                PropertyMapper.toLanguageOptions(dto, property.id),
                PropertyMapper.toAppearanceOptions(dto, property.id));
        return PropertyMapper.toDto(property, repository.translations(),
                repository.languageOptions(), repository.appearanceOptions());
    }

    /**
     * Guest-read projection (REQ-416/417): the property snapshot —
     * name, brand, defaults, offered options, RAW templates, asset ids.
     * Read-only, not HTTP-mapped (no Boundary routes it); {@code GuestControl}
     * composes and resolves.
     */
    @Transactional
    public PropertyDto guestView() {
        return PropertyMapper.toDto(repository.theRow(), repository.translations(),
                repository.languageOptions(), repository.appearanceOptions());
    }

    /**
     * Cross-feature check (Room/Stay Controls inject this): an override must
     * be inside the property's offered sets (REQ-304/305/309). {@code null}
     * means "no override" and passes.
     */
    @Transactional
    public void requireOffered(LanguageCode language, AppearanceMode appearance) {
        if (language != null
                && repository.languageOptions().stream().noneMatch(o -> o.code.equals(language.toString()))) {
            throw ApiErrors.badRequest("language-not-offered",
                    "Language " + language + " is not in the property's offered set");
        }
        if (appearance != null
                && repository.appearanceOptions().stream().noneMatch(o -> o.mode.equals(appearance.toString()))) {
            throw ApiErrors.badRequest("appearance-not-offered",
                    "Appearance " + appearance + " is not in the property's offered set");
        }
    }

    /** Cross-feature read: the language's configured locale (template resolution). */
    @Transactional
    public Locale localeOf(LanguageCode language) {
        return repository.languageOptions().stream()
                .filter(o -> o.code.equals(language.toString()))
                .map(o -> Locale.forLanguageTag(o.locale))
                .findFirst()
                .orElse(Locale.forLanguageTag("de-DE"));
    }

    private void validate(PropertyDto dto) {
        try {
            ZoneId.of(dto.getTimezone());
        } catch (DateTimeException e) {
            throw ApiErrors.badRequest("invalid-timezone",
                    "timezone must be a valid IANA zone id");
        }
        Set<String> offeredLanguages = new HashSet<>();
        for (LanguageOptionDto language : dto.getLanguages()) {
            if (!offeredLanguages.add(language.getCode().toString())) {
                throw ApiErrors.badRequest("duplicate-language-option",
                        "Language " + language.getCode() + " is offered twice");
            }
        }
        if (!offeredLanguages.contains(dto.getMainLanguage().toString())) {
            throw ApiErrors.badRequest("default-not-offered",
                    "mainLanguage must be contained in the offered languages");
        }
        Set<String> offeredAppearances = new HashSet<>();
        for (AppearanceOptionDto appearance : dto.getAppearances()) {
            if (!offeredAppearances.add(appearance.getMode().toString())) {
                throw ApiErrors.badRequest("duplicate-appearance-option",
                        "Appearance " + appearance.getMode() + " is offered twice");
            }
        }
        if (!offeredAppearances.contains(dto.getDefaultAppearance().toString())) {
            throw ApiErrors.badRequest("default-not-offered",
                    "defaultAppearance must be contained in the offered appearances");
        }
        validateLanguageKeys(dto);
        validateTemplates(dto);
    }

    private void validateLanguageKeys(PropertyDto dto) {
        Map<String, Map<String, String>> localizedFields = Map.of(
                "heroSubtitle", dto.getHeroSubtitle(),
                "headline", dto.getHeadline(),
                "paragraph", dto.getParagraph(),
                "standbyHeadline", dto.getStandbyHeadline(),
                "standbyMessage", dto.getStandbyMessage());
        for (Map.Entry<String, Map<String, String>> field : localizedFields.entrySet()) {
            for (String key : field.getValue().keySet()) {
                if (!"de".equals(key) && !"en".equals(key)) {
                    throw ApiErrors.badRequest("invalid-language-key",
                            field.getKey() + " uses unsupported language key '" + key + "' (v1: de, en)");
                }
            }
        }
    }

    private void validateTemplates(PropertyDto dto) {
        List<ApiErrorDetail> offenders = new ArrayList<>();
        collectInvalid(offenders, "heroSubtitle", dto.getHeroSubtitle(), TemplateControl.ALL_PLACEHOLDERS);
        collectInvalid(offenders, "headline", dto.getHeadline(), TemplateControl.ALL_PLACEHOLDERS);
        collectInvalid(offenders, "paragraph", dto.getParagraph(), TemplateControl.ALL_PLACEHOLDERS);
        collectInvalid(offenders, "standbyHeadline", dto.getStandbyHeadline(), TemplateControl.STANDBY_PLACEHOLDERS);
        collectInvalid(offenders, "standbyMessage", dto.getStandbyMessage(), TemplateControl.STANDBY_PLACEHOLDERS);
        if (!offenders.isEmpty()) {
            throw ApiErrors.badRequest("unknown-placeholder",
                    "A template uses a placeholder that is unknown or not allowed in its context", offenders);
        }
    }

    private void collectInvalid(List<ApiErrorDetail> offenders, String field, Map<String, String> localized,
            Set<String> allowed) {
        for (Map.Entry<String, String> entry : localized.entrySet()) {
            for (String placeholder : templateControl.findInvalidPlaceholders(entry.getValue(), allowed)) {
                offenders.add(new ApiErrorDetail(
                        field + "." + entry.getKey() + " uses " + placeholder)
                        .reference(placeholder));
            }
        }
    }
}
