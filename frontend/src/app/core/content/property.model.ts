import type { LanguageCode } from '../i18n/translation.model';
import type { AppearanceMode } from '../preferences/preference-gateway';

/**
 * Content-service contract (docs/features/home-screen-prototype/02-design.md
 * -> "Content service contract"; extended by appearance-and-language
 * 02-design.md §5.1; reshaped by guest-frontend-integration REQ-510…515: the
 * config is now fed from `GET /api/guest/content` through
 * `guest-content.mapper.ts` instead of a static import — one payload since
 * guest-payload-consolidation, `home` nested inside it).
 *
 * Two shapes of the same config live here, deliberately:
 *
 *   - the **authored** one (`PropertyConfig`, `MenuItem`, `ActionCard`) —
 *     guest-facing prose is a per-language map, exactly what the wire
 *     delivers (server-resolved templates, one entry per offered language);
 *   - the **resolved** one (`ResolvedPropertyConfig`, …) — the same shape with
 *     one language already chosen, which is all any component ever sees
 *     (REQ-108). A language switch re-resolves from data already held and
 *     fetches nothing (REQ-514).
 *
 * They share one generic shape each, so the two cannot drift apart when a
 * field is added. `PropertyContentService.property` is the only thing that
 * turns the first into the second.
 *
 * Changes for the wire era (02-design.md §8):
 *   - `guestName` + the `{guestName}` interpolation are GONE — the server
 *     resolves every template (REQ-511); the client renders verbatim.
 *   - `home` is nullable: the vacant payload simply carries no `home`
 *     (REQ-517), and menu/settings/standby must render without it.
 *   - assets are ids (`logoAssetId`, `artworkAssetId`) fetched with the
 *     device token via `GuestAssetService` — never a bare `<img src>`
 *     (REQ-513).
 *   - `menuItems` and `home.cards` are server-resolved and server-ordered
 *     (REQ-511/515) — the old "exactly 5 / exactly 4" arms are gone. Their
 *     counts are bounded by the contract's own `maxItems`
 *     (`GuestMenu.items` 50, `GuestHome.actionCards` 4), which is a
 *     WIRE bound for payload sanity — not a layout one. How many menu items
 *     the sidebar shows is a layout decision and lives in
 *     `layout/sidebar/`, never here (REQ-901).
 *   - `branding` is nullable: an invalid brand hex is rejected at the
 *     boundary and the token-layer defaults stay in force (REQ-512).
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

/**
 * The content of one catalogue section, one member per module type
 * (module-content REQ-970, 02-design.md §5). A3/D14: each type has its own
 * fixed shape — the row list is `info`'s layout, never the module model.
 *
 * **This union IS properly discriminated, even though the wire union is not.**
 * The contract spells the member's `type` as a plain `string` (D9's Variant B —
 * a single-value `enum` provably ships the wrong discriminant), so nothing
 * above the wire could narrow on it. The literal below is minted by
 * `guest-content.mapper.ts`, which is the only file that ever sees the
 * generated model, so `switch (module.type)` narrows and is
 * exhaustiveness-checked EVERYWHERE above the boundary. D9's loss is confined
 * to that one file.
 */
interface InfoModuleShape<TText> {
  readonly type: 'info'; // a LITERAL, not a string — see above
  readonly rows: readonly { label: TText; value: TText }[];
}

/** The union grows by one member per module type. */
type ModuleShape<TText> = InfoModuleShape<TText>;

interface MenuItemShape<TText> {
  id: string; // the server's menu item key, e.g. 'home' | 'info' | … | 'einstellungen'
  label: TText;
  icon: string; // Phosphor glyph name (validated against the registered set at the boundary)
  route: string; // e.g. '' for Home, '/info', '/umgebung', …
  // The section's authored content, or null: nothing authored yet, a type this
  // build does not know (REQ-973), or a module dropped as structurally invalid
  // (REQ-972). All three render SectionPlaceholderComponent — the guest is
  // deliberately not asked to tell them apart (02-design.md §3.2).
  //
  // REQUIRED, never optional (02-design.md §3.1): `module?: … | null` would put
  // `undefined | null` in front of every read site and re-import into the DOMAIN
  // model exactly the wire ambiguity the boundary exists to remove. A required
  // field also stops a future menu-item producer omitting it silently.
  module: ModuleShape<TText> | null;
}

interface ActionCardShape<TText> {
  id: string; // client-derived, stable per payload — @for track only
  icon: string; // Phosphor glyph name
  header: TText;
  description: TText;
  route: string; // the server-resolved targetRoute, used directly (REQ-511)
}

/** The home-screen half — present only while the room is OCCUPIED. */
interface HomeContentShape<TText> {
  heroSubtitle: TText;
  headline: TText; // server-resolved; rendered verbatim, no client interpolation
  paragraph: TText;
  artworkAssetId: number | null; // absent → the existing no-artwork fallback
  cards: ActionCardShape<TText>[]; // 0–4, server-ordered
}

interface PropertyConfigShape<TText> {
  propertyName: string; // never translated (a name)
  logoAssetId: number | null; // absent → the bundled placeholder
  branding: PropertyBranding | null; // null = invalid/rejected → token-layer defaults stay
  // resolved-enabled, server-ordered — REQ-515. Count bounded by the
  // contract's GuestMenu.items.maxItems (a wire bound, not a layout one).
  menuItems: MenuItemShape<TText>[];
  settings: MenuItemShape<TText>; // the fixed Einstellungen chrome (server settingsEntry) — unremovable
  settingsConfig: PropertySettingsConfig; // offered options + resolved defaults — REQ-119
  standbyHeadline: TText; // server-resolved standby texts — REQ-517
  standbyMessage: TText;
  home: HomeContentShape<TText> | null; // null while VACANT (or not yet fetched)
}

/** As delivered by the wire: every guest-facing string per offered language. */
export type AuthoredModule = ModuleShape<Localized<string>>;
export type AuthoredInfoModule = InfoModuleShape<Localized<string>>;
export type MenuItem = MenuItemShape<Localized<string>>;
export type ActionCard = ActionCardShape<Localized<string>>;
export type HomeContent = HomeContentShape<Localized<string>>;
export type PropertyConfig = PropertyConfigShape<Localized<string>>;

/** As components see it: one language already chosen (02-design.md §5.2). */
export type ResolvedModule = ModuleShape<string>;
export type ResolvedInfoModule = InfoModuleShape<string>;
export type ResolvedMenuItem = MenuItemShape<string>;
export type ResolvedActionCard = ActionCardShape<string>;
export type ResolvedHomeContent = HomeContentShape<string>;
export type ResolvedPropertyConfig = PropertyConfigShape<string>;
