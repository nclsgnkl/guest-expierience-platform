import { Injectable, computed, inject, type Signal } from '@angular/core';

import { GuestDataService } from '../guest/guest-data.service';
import type { LanguageCode } from '../i18n/translation.model';
import { PreferencesService } from '../preferences/preferences.service';
import type {
  Localized,
  PropertyBranding,
  PropertyConfig,
  PropertySettingsConfig,
  ResolvedPropertyConfig,
} from './property.model';

/**
 * Supplies per-property/guest content via Angular DI (constitution
 * principle 2). Since guest-frontend-integration (REQ-510) the raw config is
 * **fed by `GuestDataService`** — mapped and boundary-validated wire payloads
 * — instead of a static import; the component-facing surface keeps its
 * semantics, which is exactly the one-file swap principle 2 promised.
 *
 * Surface (unchanged in meaning):
 *
 *   - `settingsConfig` — language-independent options + defaults.
 *   - `property` — the config with the active language resolved per field
 *     (REQ-108/514): a language switch re-renders from data already held and
 *     fetches nothing.
 *   - `getSectionRoute()` — targetSectionId → route, unknown id throws.
 *
 * New, wire-era surface:
 *
 *   - `loaded` — whether any config is held at all; the route guards keep
 *     every consumer of the throwing signals behind this.
 *   - `branding` — nullable (an invalid wire brand was rejected at the
 *     boundary, REQ-512); `App` applies it only when present.
 *   - `locale` — the active language's `Intl` locale, resolved from the
 *     property's OWN offered options (REQ-925/926). It lives here because it
 *     *is* language resolution, which this service already owns, and because
 *     having it here is what keeps `SidebarComponent` a pure input-driven
 *     view rather than a consumer of content.
 *
 * `settingsConfig()`/`property()`/`locale()` **throw when nothing is loaded**: every
 * route that renders them sits behind the guards (pairing/connection screens
 * deliberately read no property data), so a throw here is a routing bug
 * surfacing loudly, not a state to design around.
 */
@Injectable({ providedIn: 'root' })
export class PropertyContentService {
  private readonly preferences = inject(PreferencesService);
  private readonly guestData = inject(GuestDataService);

  private readonly raw = this.guestData.config;

  readonly loaded: Signal<boolean> = computed(() => this.raw() !== null);

  readonly branding: Signal<PropertyBranding | null> = computed(
    () => this.raw()?.branding ?? null,
  );

  readonly settingsConfig: Signal<PropertySettingsConfig> = computed(
    () => requireConfig(this.raw()).settingsConfig,
  );

  readonly property: Signal<ResolvedPropertyConfig> = computed(() =>
    resolveProperty(requireConfig(this.raw()), this.preferences.language()),
  );

  /**
   * The active language's `Intl` locale — `'de-DE'`, not `'de'` — taken from
   * the property's own option list, falling back to the bare language code
   * when the property offers no matching entry (REQ-925). Re-runs on a
   * language switch and fetches nothing: it reads data already held
   * (REQ-926).
   *
   * `StalenessPillComponent` and `StatusBarComponent` still perform this
   * lookup inline; moving them onto this signal is a recorded follow-up
   * (02-design.md §11), deliberately not done here.
   */
  readonly locale: Signal<string> = computed(() => {
    const language = this.preferences.language();
    return (
      requireConfig(this.raw()).settingsConfig.languages.find((option) => option.code === language)
        ?.locale ?? language
    );
  });

  /**
   * Resolves a `targetSectionId` (e.g. an `ActionCard`'s) to the route the
   * matching `menuItems`/`settings` entry carries. Routes are not localised,
   * so this reads the raw config directly and its signature is unchanged.
   * Throws on an unknown id: a dangling reference should fail loudly in
   * tests, not silently do nothing for a guest on a touch kiosk. (Cards
   * additionally carry the server-resolved `route` directly — REQ-511.)
   */
  getSectionRoute(targetSectionId: string): string {
    const property = requireConfig(this.raw());
    const match = [...property.menuItems, property.settings].find(
      (item) => item.id === targetSectionId,
    );

    if (!match) {
      throw new Error(`Unknown targetSectionId: "${targetSectionId}"`);
    }

    return match.route;
  }
}

function requireConfig(config: PropertyConfig | null): PropertyConfig {
  if (config === null) {
    throw new Error('PropertyContentService: no content loaded — a guard let a route through early.');
  }
  return config;
}

/**
 * Per field: the active language, else the **property's own** default
 * language — not the app's idea of a default, and not the first key in the
 * object — else throw (REQ-108). The mapper guarantees the default-language
 * entry exists, so the throw is unreachable for wire-fed configs; it stays
 * for the same loud-failure reason as `getSectionRoute`'s.
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
 * Exported for its own unit test: the per-field fallback rules are only
 * reachable by calling this pure function with a config double
 * (`vi.mock` on relative imports is rejected by the Angular test system).
 * Nothing outside this module and its spec may use it: content is reached
 * through the service (frontend/CLAUDE.md).
 */
export function resolveProperty(
  config: PropertyConfig,
  language: LanguageCode,
): ResolvedPropertyConfig {
  const defaultLanguage = config.settingsConfig.defaultLanguage;
  const text = (value: Localized<string>, field: string) =>
    resolveText(value, language, defaultLanguage, field);

  const home = config.home;
  return {
    ...config,
    menuItems: config.menuItems.map((item) => ({
      ...item,
      label: text(item.label, `menuItems[${item.id}].label`),
    })),
    settings: {
      ...config.settings,
      label: text(config.settings.label, 'settings.label'),
    },
    standbyHeadline: text(config.standbyHeadline, 'standbyHeadline'),
    standbyMessage: text(config.standbyMessage, 'standbyMessage'),
    home:
      home === null
        ? null
        : {
            ...home,
            heroSubtitle: text(home.heroSubtitle, 'home.heroSubtitle'),
            headline: text(home.headline, 'home.headline'),
            paragraph: text(home.paragraph, 'home.paragraph'),
            cards: home.cards.map((card) => ({
              ...card,
              header: text(card.header, `cards[${card.id}].header`),
              description: text(card.description, `cards[${card.id}].description`),
            })),
          },
  };
}
