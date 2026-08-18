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
      id: 'haus-und-funktionen',
      icon: 'phosphorLightbulbFilament',
      header: 'Haus & Funktionen',
      description: 'Alles im Haus auf einen Blick',
      targetSectionId: 'smart-home',
    },
    {
      id: 'entdecken',
      icon: 'phosphorMapPin',
      header: 'Entdecken',
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
