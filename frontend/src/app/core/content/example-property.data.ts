import type { PropertyConfig } from './property.model';

/**
 * Static content for the one example property this prototype ships with
 * (docs/features/home-screen-prototype/01-prd.md -> Assumptions;
 * docs/design/screens/home.md). Never imported directly by a component —
 * only `PropertyContentService` reads this file (constitution principle 2).
 *
 * `headline` carries a literal `{guestName}` placeholder, replaced with
 * `guestName` at render time — REQ-008's "guest name interpolated".
 */
export const EXAMPLE_PROPERTY: PropertyConfig = {
  guestName: 'Lisa',
  heroSubtitle: 'Schön, dass ihr da seid',
  headline: 'Willkommen im Airbnb von {guestName}!',
  paragraph: 'Entspannt euch, genießt die Zeit und fühlt euch wie zuhause.',
  logoAsset: 'assets/branding/logo-placeholder.svg',
  hasArtwork: false,
  branding: {
    primary: '#f45aa8',
    primaryDeep: '#cc49af',
    secondary: '#8a5cf6',
  },
  menuItems: [
    { id: 'home', label: 'Home', icon: 'phosphorHouse', route: '' },
    { id: 'info', label: 'Info', icon: 'phosphorInfo', route: '/info' },
    { id: 'umgebung', label: 'Umgebung', icon: 'phosphorMapPin', route: '/umgebung' },
    {
      id: 'smart-home',
      label: 'Smart Home',
      icon: 'phosphorLightbulbFilament',
      route: '/smart-home',
    },
    { id: 'services', label: 'Services', icon: 'phosphorShoppingBag', route: '/services' },
  ],
  settings: {
    id: 'einstellungen',
    label: 'Einstellungen',
    icon: 'phosphorGearSix',
    route: '/einstellungen',
  },
  cards: [
    {
      id: 'check-out',
      icon: 'phosphorKey',
      header: 'Check-Out',
      description: 'Bis 11:00 Uhr am Abreisetag',
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
         docs/design/screens/home.md's card table records it too. */
      header: 'Smart Home',
      description: 'Alles im Haus auf einen Blick',
      targetSectionId: 'smart-home',
    },
    {
      id: 'umgebung-karte',
      icon: 'phosphorMapPin',
      /* "Umgebung", not "Entdecken" (user decision, 2026-08-18): the card and
         the section it targets now carry the same name. Content change only —
         docs/design/screens/home.md's card table records it too. */
      header: 'Umgebung',
      description: 'Tipps, Restaurants & Aktivitäten',
      targetSectionId: 'umgebung',
    },
    {
      id: 'wichtige-infos',
      icon: 'phosphorFileText',
      header: 'Wichtige Infos',
      description: 'Hausregeln, WLAN, Parken & mehr',
      targetSectionId: 'info',
    },
  ],
};
