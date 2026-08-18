/**
 * Content-service contract (docs/features/home-screen-prototype/02-design.md
 * -> "Content service contract"). Property-level configuration only — no
 * guest/stay data model exists (constitution principle 1; PRD -> Assumptions).
 */

export interface PropertyBranding {
  primary: string; // --brand-primary override
  primaryDeep: string; // --brand-primary-deep override
  secondary: string; // --brand-secondary override
}

export interface MenuItem {
  id: string; // e.g. 'home' | 'info' | 'umgebung' | 'smart-home' | 'services' | 'einstellungen'
  label: string;
  icon: string; // Phosphor glyph name
  route: string; // e.g. '' for Home, '/info', '/umgebung', …
}

export interface ActionCard {
  id: string;
  icon: string; // Phosphor glyph name, e.g. 'Key'
  header: string;
  description: string;
  targetSectionId: string; // must resolve to a MenuItem.id in menuItems or settings.id — REQ-019
}

export interface PropertyConfig {
  guestName: string;
  heroSubtitle: string; // "Schön, dass ihr da seid"
  headline: string; // "Willkommen im Airbnb von {guestName}!" — pre-interpolated
  paragraph: string;
  logoAsset: string; // path under assets/branding/
  hasArtwork: boolean; // false for the example property → no-artwork fallback
  branding: PropertyBranding;
  menuItems: MenuItem[]; // exactly 5, in order: Home, Info, Umgebung, Smart Home, Services — REQ-017
  settings: MenuItem; // exactly 1; position fixed in SidebarComponent, content from config — REQ-018
  cards: ActionCard[]; // exactly 4
}
