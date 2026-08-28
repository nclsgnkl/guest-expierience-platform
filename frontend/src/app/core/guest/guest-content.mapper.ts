import {
  OccupancyState,
  type GuestContent,
  type GuestHome,
  type GuestMenuItem,
  type GuestSettingsEntry,
} from '@gxp/api-client';

import { TRANSLATION_CATALOGS } from '../i18n/catalog';
import { CONTENT_FALLBACK_ICON, isRegisteredIcon } from '../icons/registered-icons';
import type {
  ActionCard,
  HomeContent,
  Localized,
  MenuItem,
  PropertyBranding,
  PropertyConfig,
  PropertySettingsConfig,
} from '../content/property.model';
import type { StoredPreferences } from '../preferences/preference-gateway';

/**
 * Pure mapping + boundary validation for the guest wire payloads
 * (guest-frontend-integration REQ-510/512, 02-design.md §6). This is the one
 * place generated client models become the internal `PropertyConfig` shape —
 * nothing outside `core/guest/` and `core/api/` imports `@gxp/api-client`
 * models.
 *
 * Validation posture (02-design.md §6, verbatim): a *server* payload failing
 * validation must not throw the app away — `GuestDataService` catches
 * {@link GuestContentMappingError} and treats it as a failed fetch
 * (STALE/DISCONNECTED path). Two failure classes are deliberately softer:
 *
 *   - an invalid brand hex **rejects the branding only** (`branding: null`,
 *     token-layer defaults stay — REQ-512, an inline style is an injection
 *     surface);
 *   - an unregistered icon name renders the neutral fallback glyph
 *     (`CONTENT_FALLBACK_ICON`), never a crash — the seeded catalogue names
 *     are already the registered Phosphor names.
 *
 * Nothing here logs — a payload could carry property content, and no log may
 * (constitution principle 3).
 */
export class GuestContentMappingError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'GuestContentMappingError';
  }
}

const HEX_COLOR = /^#[0-9a-fA-F]{6}$/;
/**
 * The two array bounds `api/openapi.yaml` declares — `GuestMenu.items.maxItems`
 * and `GuestHome.actionCards.maxItems`. Both are boundary validation of an
 * untrusted wire, and both reject rather than truncate.
 *
 * `MAX_MENU_ITEMS` was `5` until REQ-901, and that was the bug: the number was
 * invented here and its message cited a contract bound that did not exist, so a
 * legitimate sixth section was rejected. The contract now really declares
 * `maxItems: 50`, so the check is back at the real number and its message is
 * true. 50 is payload sanity, not layout — how many items the sidebar shows is
 * decided in `layout/sidebar/`, and no guest-visible behaviour changes if this
 * number moves.
 */
const MAX_MENU_ITEMS = 50;
const MAX_CARDS = 4;

/**
 * Maps the one content payload into the internal config. Throws
 * {@link GuestContentMappingError} on structural violations.
 *
 * **Occupied implies `home`** (guest-payload-consolidation §1.2): the server
 * omits the whole stay-derived half exactly when the room is vacant, so an
 * OCCUPIED payload without `home` is a server bug — named here rather than
 * rendered as half a screen.
 */
export function mapGuestContent(content: GuestContent): PropertyConfig {
  const home = content.home ?? null;
  if (content.occupancy === OccupancyState.Occupied && home === null) {
    throw new GuestContentMappingError('occupancy is OCCUPIED but "home" is absent');
  }
  const settingsConfig = mapSettingsConfig(content);
  const defaultLanguage = settingsConfig.defaultLanguage;

  const name = requireText(content.property?.name, 'property.name');

  const items = content.menu?.items ?? [];
  if (items.length > MAX_MENU_ITEMS) {
    throw new GuestContentMappingError(`menu.items exceeds the contract's 0–${MAX_MENU_ITEMS}`);
  }
  const settingsEntry = content.menu?.settingsEntry;
  if (!settingsEntry) {
    throw new GuestContentMappingError('menu.settingsEntry is missing');
  }

  return {
    propertyName: name,
    logoAssetId: content.property.logoAssetId ?? null,
    branding: mapBranding(content),
    menuItems: [...items]
      .sort((a, b) => a.sortIndex - b.sortIndex)
      .map((item) => mapMenuItem(item, defaultLanguage)),
    settings: mapSettingsEntry(settingsEntry, defaultLanguage),
    settingsConfig,
    standbyHeadline: localized(content.standby?.headline, 'standby.headline', defaultLanguage),
    standbyMessage: localized(content.standby?.message, 'standby.message', defaultLanguage),
    home: home === null ? null : mapHome(home, defaultLanguage),
  };
}

/** The stay's raw preference override, handed to `PreferencesService.hydrate()`. */
export function mapStoredPreferences(content: GuestContent): StoredPreferences | null {
  const preference = content.preference;
  if (!preference) {
    return null;
  }
  const stored: StoredPreferences = {};
  if (typeof preference.language === 'string' && preference.language !== '') {
    stored.language = preference.language;
  }
  if (
    preference.appearance === 'light' ||
    preference.appearance === 'dark' ||
    preference.appearance === 'system'
  ) {
    stored.appearance = preference.appearance;
  }
  return stored;
}

function mapSettingsConfig(content: GuestContent): PropertySettingsConfig {
  const settings = content.settings;
  if (!settings || !Array.isArray(settings.languages) || settings.languages.length === 0) {
    throw new GuestContentMappingError('settings.languages is missing or empty');
  }
  if (!Array.isArray(settings.appearances) || settings.appearances.length === 0) {
    throw new GuestContentMappingError('settings.appearances is missing or empty');
  }
  const languages = settings.languages.map((option) => ({
    code: requireText(option.code, 'settings.languages[].code'),
    shortLabel: requireText(option.shortLabel, 'settings.languages[].shortLabel'),
    label: requireText(option.label, 'settings.languages[].label'),
    locale: requireText(option.locale, 'settings.languages[].locale'),
  }));
  for (const option of languages) {
    if (!TRANSLATION_CATALOGS.has(option.code)) {
      throw new GuestContentMappingError(
        `offered language "${option.code}" has no registered translation catalogue`,
      );
    }
  }
  const appearances = settings.appearances.map((option) => ({
    mode: option.mode,
    icon: safeIcon(option.icon),
  }));
  const defaultLanguage = requireText(settings.defaultLanguage, 'settings.defaultLanguage');
  if (!languages.some((option) => option.code === defaultLanguage)) {
    throw new GuestContentMappingError('settings.defaultLanguage is not an offered language');
  }
  const defaultAppearance = settings.defaultAppearance;
  if (!appearances.some((option) => option.mode === defaultAppearance)) {
    throw new GuestContentMappingError('settings.defaultAppearance is not an offered appearance');
  }
  return { languages, defaultLanguage, appearances, defaultAppearance };
}

function mapBranding(content: GuestContent): PropertyBranding | null {
  const brand = content.property?.brand;
  if (!brand) {
    return null;
  }
  const { primary, primaryDeep, secondary } = brand;
  if (!HEX_COLOR.test(primary) || !HEX_COLOR.test(primaryDeep) || !HEX_COLOR.test(secondary)) {
    return null; // rejected — token-layer defaults stay in force (REQ-512)
  }
  return { primary, primaryDeep, secondary };
}

function mapMenuItem(item: GuestMenuItem, defaultLanguage: string): MenuItem {
  return {
    id: requireText(item.key, 'menu.items[].key'),
    label: localized(item.label, `menu.items[${item.key}].label`, defaultLanguage),
    icon: safeIcon(item.icon),
    route: requireRoute(item.route, `menu.items[${item.key}].route`),
  };
}

function mapSettingsEntry(entry: GuestSettingsEntry, defaultLanguage: string): MenuItem {
  return {
    id: requireText(entry.id, 'menu.settingsEntry.id'),
    label: localized(entry.label, 'menu.settingsEntry.label', defaultLanguage),
    icon: safeIcon(entry.icon),
    route: requireRoute(entry.route, 'menu.settingsEntry.route'),
  };
}

function mapHome(home: GuestHome, defaultLanguage: string): HomeContent {
  const cards = home.actionCards ?? [];
  if (cards.length > MAX_CARDS) {
    throw new GuestContentMappingError(`actionCards exceeds the contract's 0–${MAX_CARDS}`);
  }
  return {
    heroSubtitle: localized(home.subtitle, 'home.subtitle', defaultLanguage),
    headline: localized(home.headline, 'home.headline', defaultLanguage),
    paragraph: localized(home.paragraph, 'home.paragraph', defaultLanguage),
    artworkAssetId: home.artworkAssetId ?? null,
    cards: cards.map(
      (card, index): ActionCard => ({
        // Client-derived @for track key: targetKey may repeat (two cards may
        // target the same section), so the position disambiguates.
        id: `${card.targetKey}-${index}`,
        icon: safeIcon(card.icon),
        header: localized(card.title, `actionCards[${index}].title`, defaultLanguage),
        description: localized(card.description, `actionCards[${index}].description`, defaultLanguage),
        route: requireRoute(card.targetRoute, `actionCards[${index}].targetRoute`),
      }),
    ),
  };
}

/**
 * A localized map is valid when it exists and carries a non-blank entry for
 * the property's default language — the same construction-time strictness the
 * static config enjoyed (`resolveText`'s fallback needs that entry).
 */
function localized(
  value: { [key: string]: string } | undefined,
  field: string,
  defaultLanguage: string,
): Localized<string> {
  const entry = value?.[defaultLanguage];
  if (entry === undefined || entry.trim() === '') {
    throw new GuestContentMappingError(
      `"${field}" has no entry for the default language "${defaultLanguage}"`,
    );
  }
  return { ...value };
}

function requireText(value: string | undefined | null, field: string): string {
  if (typeof value !== 'string' || value.trim() === '') {
    throw new GuestContentMappingError(`"${field}" is missing or blank`);
  }
  return value;
}

/** Routes may legitimately be the empty string (Home) — only shape-checked. */
function requireRoute(value: string | undefined | null, field: string): string {
  if (typeof value !== 'string') {
    throw new GuestContentMappingError(`"${field}" is missing`);
  }
  return value;
}

function safeIcon(name: string | undefined | null): string {
  return typeof name === 'string' && isRegisteredIcon(name) ? name : CONTENT_FALLBACK_ICON;
}
