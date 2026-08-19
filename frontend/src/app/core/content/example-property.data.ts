import type { PropertyConfig } from './property.model';

/**
 * Static content for the one example property this prototype ships with
 * (docs/features/home-screen-prototype/01-prd.md -> Assumptions;
 * docs/design/screens/home.md). Never imported directly by a component —
 * only `PropertyContentService` reads this file (constitution principle 2).
 *
 * Every guest-facing string is authored per language (REQ-108). German is the
 * property's own default language; English is a real second language, not a
 * placeholder, so the switch is demonstrably complete rather than plausible.
 *
 * `headline` carries a literal `{guestName}` placeholder **in every
 * language**, replaced with `guestName` at render time — REQ-008's "guest name
 * interpolated".
 *
 * `settingsConfig` is the language-independent slice: which options a guest is
 * offered and which default applies when nothing is stored. `defaultLanguage`
 * and `defaultAppearance` are exactly what the backend will supply once it
 * exists (decision #2, 2026-08-18) — they are the property's configured
 * defaults, deliberately not "whatever the browser or the OS prefers".
 */
export const EXAMPLE_PROPERTY: PropertyConfig = {
  guestName: 'Lisa',
  heroSubtitle: {
    de: 'Schön, dass ihr da seid',
    en: 'Great to have you here',
  },
  headline: {
    de: 'Willkommen im Airbnb von {guestName}!',
    en: "Welcome to {guestName}'s Airbnb!",
  },
  paragraph: {
    de: 'Entspannt euch, genießt die Zeit und fühlt euch wie zuhause.',
    en: 'Relax, enjoy your stay and make yourself at home.',
  },
  logoAsset: 'assets/branding/logo-placeholder.svg',
  hasArtwork: false,
  branding: {
    primary: '#f45aa8',
    primaryDeep: '#cc49af',
    secondary: '#8a5cf6',
  },
  menuItems: [
    { id: 'home', label: { de: 'Home', en: 'Home' }, icon: 'phosphorHouse', route: '' },
    { id: 'info', label: { de: 'Info', en: 'Info' }, icon: 'phosphorInfo', route: '/info' },
    {
      id: 'umgebung',
      label: { de: 'Umgebung', en: 'Nearby' },
      icon: 'phosphorMapPin',
      route: '/umgebung',
    },
    {
      id: 'smart-home',
      label: { de: 'Smart Home', en: 'Smart Home' },
      icon: 'phosphorLightbulbFilament',
      route: '/smart-home',
    },
    {
      id: 'services',
      label: { de: 'Services', en: 'Services' },
      icon: 'phosphorShoppingBag',
      route: '/services',
    },
  ],
  settings: {
    id: 'einstellungen',
    label: { de: 'Einstellungen', en: 'Settings' },
    icon: 'phosphorGearSix',
    route: '/einstellungen',
  },
  settingsConfig: {
    /* Endonyms: a language's name is written in its own language, so these
       two labels are never translated. `en-GB`, not `en-US`, so the clock
       reads 16:42 rather than 04:42 PM (01-prd.md -> Assumptions) — it is a
       config value, so a property can differ. */
    languages: [
      { code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' },
      { code: 'en', shortLabel: 'EN', label: 'English', locale: 'en-GB' },
    ],
    defaultLanguage: 'de',
    /* No `system` entry, deliberately (decision #4, 2026-08-18): the mode
       stays representable, but this property does not offer it, so
       `prefers-color-scheme` is never consulted and startup is always the
       configured default below. */
    appearances: [
      { mode: 'light', icon: 'phosphorSun' },
      { mode: 'dark', icon: 'phosphorMoon' },
    ],
    defaultAppearance: 'light',
  },
  cards: [
    {
      id: 'check-out',
      icon: 'phosphorKey',
      header: { de: 'Check-Out', en: 'Check-out' },
      description: { de: 'Bis 11:00 Uhr am Abreisetag', en: 'By 11:00 on departure day' },
      targetSectionId: 'info',
    },
    {
      id: 'smart-home-karte',
      icon: 'phosphorLightbulbFilament',
      /* "Smart Home", not "Haus & Funktionen" (user decision, 2026-08-18):
         the original copy needs 167px of advance width in the card's 162px
         title box and was ellipsized in a real browser. Renaming the copy was
         chosen over widening the box, so `--space-5` card padding stands. The
         title now reads the same as the sidebar entry for the section this
         card navigates to — deliberate, not a duplicate. Content change only;
         docs/design/screens/home.md's card table records it too. English
         headers are kept at or below the German ones' advance width for the
         same reason. */
      header: { de: 'Smart Home', en: 'Smart Home' },
      description: { de: 'Alles im Haus auf einen Blick', en: 'Everything at a glance' },
      targetSectionId: 'smart-home',
    },
    {
      id: 'umgebung-karte',
      icon: 'phosphorMapPin',
      /* "Umgebung", not "Entdecken" (user decision, 2026-08-18): the card and
         the section it targets now carry the same name. Content change only —
         docs/design/screens/home.md's card table records it too. */
      header: { de: 'Umgebung', en: 'Nearby' },
      description: { de: 'Tipps, Restaurants & Aktivitäten', en: 'Tips, restaurants & activities' },
      targetSectionId: 'umgebung',
    },
    {
      id: 'wichtige-infos',
      icon: 'phosphorFileText',
      header: { de: 'Wichtige Infos', en: 'Key info' },
      description: {
        de: 'Hausregeln, WLAN, Parken & mehr',
        en: 'House rules, Wi-Fi, parking & more',
      },
      targetSectionId: 'info',
    },
  ],
};
