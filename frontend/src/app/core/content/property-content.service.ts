import { Injectable, computed, inject, signal, type Signal } from '@angular/core';

import { TRANSLATION_CATALOGS } from '../i18n/catalog';
import type { LanguageCode } from '../i18n/translation.model';
import { PreferencesService } from '../preferences/preferences.service';
import { EXAMPLE_PROPERTY } from './example-property.data';
import type {
  Localized,
  PropertyConfig,
  PropertySettingsConfig,
  ResolvedPropertyConfig,
} from './property.model';

/**
 * Supplies per-property/guest content via Angular DI (constitution
 * principle 2). Components inject this service and never import
 * `example-property.data.ts` directly — this is what keeps a future
 * Quarkus-backed implementation a one-file change.
 *
 * Two signals, one method (02-design.md §5.2):
 *
 *   - `settingsConfig` — language-independent, so it is safely readable
 *     before any language is known. Named `settingsConfig`, not `settings`,
 *     because `PropertyConfig.settings` is the sidebar's Einstellungen menu
 *     item and `content.settings()` vs `content.property().settings` is a
 *     trap.
 *   - `property` — the same config with the active language resolved per
 *     field (REQ-108). A language switch therefore propagates without a
 *     single subscription: the computed changes, and every template reading
 *     it re-renders.
 */
@Injectable({ providedIn: 'root' })
export class PropertyContentService {
  private readonly preferences = inject(PreferencesService);

  /** Static today; a signal so an HTTP-backed version only changes here. */
  private readonly raw = signal<PropertyConfig>(EXAMPLE_PROPERTY).asReadonly();

  readonly settingsConfig: Signal<PropertySettingsConfig> = computed(
    () => this.raw().settingsConfig,
  );

  readonly property: Signal<ResolvedPropertyConfig> = computed(() =>
    resolveProperty(this.raw(), this.preferences.language()),
  );

  constructor() {
    /* Eagerly, once, at construction — deliberately not inside the computed
       above. A throw from a computed surfaces during rendering, at whatever
       moment the guest happens to switch language; a throw here fails the app
       at boot and fails every test immediately. This config is the boundary
       that becomes an HTTP response later, which is exactly when unvalidated
       shapes start arriving (constitution -> "Input validated at every system
       boundary"). */
    validateConfig(this.raw());
  }

  /**
   * Resolves a `targetSectionId` (e.g. an `ActionCard`'s) to the route the
   * matching `menuItems`/`settings` entry carries — the single place
   * REQ-019's "must resolve to a real id" invariant is enforced. Routes are
   * not localised, so this reads the authored config directly and its
   * signature is unchanged. Throws on an unknown id: a dangling reference is
   * a content-authoring bug that should fail loudly in tests, not silently do
   * nothing for a guest on a touch kiosk.
   */
  getSectionRoute(targetSectionId: string): string {
    const property = this.raw();
    const match = [...property.menuItems, property.settings].find(
      (item) => item.id === targetSectionId,
    );

    if (!match) {
      throw new Error(`Unknown targetSectionId: "${targetSectionId}"`);
    }

    return match.route;
  }
}

/**
 * Every localised field in the config, as `[path, value]`. One list, used by
 * both the validation and the resolution, so neither can forget a field.
 */
function localizedFields(config: PropertyConfig): [string, Localized<string>][] {
  return [
    ['heroSubtitle', config.heroSubtitle],
    ['headline', config.headline],
    ['paragraph', config.paragraph],
    ...config.menuItems.map((item): [string, Localized<string>] => [
      `menuItems[${item.id}].label`,
      item.label,
    ]),
    ['settings.label', config.settings.label],
    ...config.cards.flatMap((card): [string, Localized<string>][] => [
      [`cards[${card.id}].header`, card.header],
      [`cards[${card.id}].description`, card.description],
    ]),
  ];
}

/**
 * Per field: the active language, else the **property's own** default
 * language — not the app's idea of a default, and not the first key in the
 * object — else throw (REQ-108). A config whose default-language entry is
 * missing is a content-authoring bug, and the alternative on a touch kiosk is
 * a guest staring at a blank headline.
 */
function resolveText(
  value: Localized<string>,
  language: LanguageCode,
  defaultLanguage: LanguageCode,
  field: string,
): string {
  const active = value[language];
  if (active !== undefined && active.trim() !== '') {
    return active;
  }

  const fallback = value[defaultLanguage];
  if (fallback !== undefined && fallback.trim() !== '') {
    return fallback;
  }

  throw new Error(
    `Property config: "${field}" has no entry for the property's default language "${defaultLanguage}".`,
  );
}

/**
 * Exported for its own unit test (S29). The raw config is a module import,
 * and the Angular unit-test system refuses `vi.mock` on relative imports
 * ("Please use Angular TestBed for mocking dependencies"), so the per-field
 * fallback and the three validation failures are only reachable by calling
 * these two pure functions with a config double. Nothing outside this module
 * and its spec may use them: content is reached through the service
 * (frontend/CLAUDE.md).
 */
export function resolveProperty(
  config: PropertyConfig,
  language: LanguageCode,
): ResolvedPropertyConfig {
  const defaultLanguage = config.settingsConfig.defaultLanguage;
  const text = (value: Localized<string>, field: string) =>
    resolveText(value, language, defaultLanguage, field);

  return {
    ...config,
    heroSubtitle: text(config.heroSubtitle, 'heroSubtitle'),
    headline: text(config.headline, 'headline'),
    paragraph: text(config.paragraph, 'paragraph'),
    menuItems: config.menuItems.map((item) => ({
      ...item,
      label: text(item.label, `menuItems[${item.id}].label`),
    })),
    settings: {
      ...config.settings,
      label: text(config.settings.label, 'settings.label'),
    },
    cards: config.cards.map((card) => ({
      ...card,
      header: text(card.header, `cards[${card.id}].header`),
      description: text(card.description, `cards[${card.id}].description`),
    })),
  };
}

/** The four checks of 02-design.md §5.2, run once at construction. Exported for the same reason as `resolveProperty` above. */
export function validateConfig(config: PropertyConfig): void {
  const { languages, defaultLanguage, appearances, defaultAppearance } = config.settingsConfig;

  if (!languages.some((option) => option.code === defaultLanguage)) {
    throw new Error(
      `Property config: defaultLanguage "${defaultLanguage}" is not one of the configured languages.`,
    );
  }

  /* Symmetric with the language check above, and not cosmetic: a
     `defaultAppearance` outside `appearances` (e.g. 'system' beside light and
     dark) is accepted by the type system, and leaves the Settings screen with
     no option marked selected until the guest taps one. */
  if (!appearances.some((option) => option.mode === defaultAppearance)) {
    throw new Error(
      `Property config: defaultAppearance "${defaultAppearance}" is not one of the configured appearances.`,
    );
  }

  for (const option of languages) {
    if (!TRANSLATION_CATALOGS.has(option.code)) {
      throw new Error(
        `Property config: configured language "${option.code}" has no registered translation catalogue.`,
      );
    }
  }

  for (const [field, value] of localizedFields(config)) {
    const entry = value[defaultLanguage];
    if (entry === undefined || entry.trim() === '') {
      throw new Error(
        `Property config: "${field}" has no entry for the property's default language "${defaultLanguage}".`,
      );
    }
  }
}
