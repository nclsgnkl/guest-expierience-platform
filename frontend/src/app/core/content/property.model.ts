import type { LanguageCode } from '../i18n/translation.model';
import type { AppearanceMode } from '../preferences/preference-gateway';

/**
 * Content-service contract (docs/features/home-screen-prototype/02-design.md
 * -> "Content service contract"; extended by appearance-and-language
 * 02-design.md §5.1). Property-level configuration only — no guest/stay data
 * model exists (constitution principle 1; PRD -> Assumptions).
 *
 * Two shapes of the same config live here, deliberately:
 *
 *   - the **authored** one (`PropertyConfig`, `MenuItem`, `ActionCard`) —
 *     guest-facing prose is a per-language map, which is what a property
 *     actually writes down and what the future backend will serve;
 *   - the **resolved** one (`ResolvedPropertyConfig`, …) — the same shape with
 *     one language already chosen, which is all any component ever sees
 *     (REQ-108).
 *
 * They share one generic shape each, so the two cannot drift apart when a
 * field is added. `PropertyContentService.property` is the only thing that
 * turns the first into the second.
 */

export type { LanguageCode, AppearanceMode };

/** A value authored once per language, keyed by BCP-47 primary subtag. */
export type Localized<T = string> = Record<LanguageCode, T>;

export interface LanguageOption {
  code: LanguageCode; // 'de' — also written to <html lang>
  shortLabel: string; // 'DE' — the status bar's compact label
  label: string; // 'Deutsch' — endonym: a language's name is written in its own language, so it is never translated
  locale: string; // 'de-DE' — what Intl gets, including the clock's 12h/24h convention
}

/** Labels come from the translation catalogue, not from config. */
export interface AppearanceOption {
  mode: AppearanceMode;
  icon: string; // Phosphor glyph name
}

/**
 * The language-independent slice of the property's configuration: which
 * options a guest is offered and which default applies when nothing is
 * stored (REQ-119). Safe to read before any language is known, which is what
 * lets `PreferencesService` stay a leaf that injects no content service
 * (02-design.md §1).
 */
export interface PropertySettingsConfig {
  languages: LanguageOption[]; // available, in display order
  defaultLanguage: LanguageCode; // property-configured default
  appearances: AppearanceOption[]; // available, in display order
  defaultAppearance: AppearanceMode; // property-configured default
}

export interface PropertyBranding {
  primary: string; // --brand-primary override
  primaryDeep: string; // --brand-primary-deep override
  secondary: string; // --brand-secondary override
}

interface MenuItemShape<TText> {
  id: string; // e.g. 'home' | 'info' | 'umgebung' | 'smart-home' | 'services' | 'einstellungen'
  label: TText;
  icon: string; // Phosphor glyph name
  route: string; // e.g. '' for Home, '/info', '/umgebung', …
}

interface ActionCardShape<TText> {
  id: string;
  icon: string; // Phosphor glyph name, e.g. 'Key'
  header: TText;
  description: TText;
  targetSectionId: string; // must resolve to a MenuItem.id in menuItems or settings.id — REQ-019
}

interface PropertyConfigShape<TText> {
  guestName: string; // a person's name — never translated
  heroSubtitle: TText; // "Schön, dass ihr da seid"
  headline: TText; // "Willkommen im Airbnb von {guestName}!" — {guestName} interpolated at render time, in every language
  paragraph: TText;
  logoAsset: string; // path under assets/branding/
  hasArtwork: boolean; // false for the example property → no-artwork fallback
  branding: PropertyBranding;
  menuItems: MenuItemShape<TText>[]; // exactly 5, in order: Home, Info, Umgebung, Smart Home, Services — REQ-017
  settings: MenuItemShape<TText>; // exactly 1 — the sidebar's Einstellungen entry (REQ-018). NOT the settings *config* below
  settingsConfig: PropertySettingsConfig; // available languages/appearances and their defaults — REQ-119
  cards: ActionCardShape<TText>[]; // exactly 4
}

/** As authored by the property: every guest-facing string per language. */
export type MenuItem = MenuItemShape<Localized<string>>;
export type ActionCard = ActionCardShape<Localized<string>>;
export type PropertyConfig = PropertyConfigShape<Localized<string>>;

/** As components see it: one language already chosen (02-design.md §5.2). */
export type ResolvedMenuItem = MenuItemShape<string>;
export type ResolvedActionCard = ActionCardShape<string>;
export type ResolvedPropertyConfig = PropertyConfigShape<string>;
