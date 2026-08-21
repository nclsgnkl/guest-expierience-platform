import type { GuestHomeScreen, GuestSession } from '@gxp/api-client';
import { describe, expect, it } from 'vitest';

import { CONTENT_FALLBACK_ICON } from '../icons/registered-icons';
import {
  GuestContentMappingError,
  mapGuestContent,
  mapStoredPreferences,
} from './guest-content.mapper';

/**
 * S-map — the wire boundary (REQ-510/512, 02-design.md §6). Fixtures mirror
 * the dev seed (`DevDataSeeder`), so what the spec accepts is what a dev
 * backend actually serves.
 */
function sessionFixture(overrides: Partial<GuestSession> = {}): GuestSession {
  return {
    property: {
      name: 'Meine Unterkunft',
      brand: { primary: '#f45aa8', primaryDeep: '#cc49af', secondary: '#8a5cf6' },
      logoAssetId: 7,
    },
    room: { code: '1', displayName: 'Zimmer 1' },
    occupancy: 'OCCUPIED',
    guestDisplayName: 'Anna & Tom',
    settings: {
      languages: [
        { code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' },
        { code: 'en', shortLabel: 'EN', label: 'English', locale: 'en-GB' },
      ],
      appearances: [
        { mode: 'light', icon: 'phosphorSun' },
        { mode: 'dark', icon: 'phosphorMoon' },
      ],
      defaultLanguage: 'de',
      defaultAppearance: 'light',
    },
    preference: { language: 'en' },
    menu: {
      items: [
        { key: 'home', label: { de: 'Home', en: 'Home' }, icon: 'phosphorHouse', route: '', sortIndex: 0 },
        { key: 'info', label: { de: 'Info', en: 'Info' }, icon: 'phosphorInfo', route: '/info', sortIndex: 1 },
      ],
      settingsEntry: {
        id: 'einstellungen',
        label: { de: 'Einstellungen', en: 'Settings' },
        icon: 'phosphorGearSix',
        route: '/einstellungen',
      },
    },
    standby: {
      headline: { de: 'Meine Unterkunft', en: 'Meine Unterkunft' },
      message: { de: 'Herzlich willkommen', en: 'Welcome' },
    },
    ...overrides,
  };
}

function homeFixture(overrides: Partial<GuestHomeScreen> = {}): GuestHomeScreen {
  return {
    headline: { de: 'Willkommen im Airbnb von Anna & Tom!', en: "Welcome to Anna & Tom's Airbnb!" },
    subtitle: { de: 'Schön, dass ihr da seid', en: 'Great to have you here' },
    paragraph: { de: 'Entspannt euch.', en: 'Relax.' },
    actionCards: [
      {
        icon: 'phosphorKey',
        title: { de: 'Check-Out', en: 'Check-out' },
        description: { de: 'Bis 11:00 Uhr', en: 'By 11:00' },
        targetKey: 'info',
        targetRoute: '/info',
      },
    ],
    ...overrides,
  };
}

describe('mapGuestContent', () => {
  it('maps the dev-seed-shaped payloads to the internal config (happy path)', () => {
    const config = mapGuestContent(sessionFixture(), homeFixture());

    expect(config.propertyName).toBe('Meine Unterkunft');
    expect(config.logoAssetId).toBe(7);
    expect(config.branding).toEqual({
      primary: '#f45aa8',
      primaryDeep: '#cc49af',
      secondary: '#8a5cf6',
    });
    expect(config.menuItems.map((i) => i.id)).toEqual(['home', 'info']);
    expect(config.settings.id).toBe('einstellungen');
    expect(config.settingsConfig.defaultLanguage).toBe('de');
    expect(config.standbyMessage['de']).toBe('Herzlich willkommen');
    expect(config.home?.headline['de']).toBe('Willkommen im Airbnb von Anna & Tom!');
    expect(config.home?.cards[0]?.route).toBe('/info');
    expect(config.home?.cards[0]?.targetSectionId).toBe('info');
  });

  it('orders menu items by sortIndex regardless of wire order', () => {
    const session = sessionFixture();
    session.menu.items = [...session.menu.items].reverse();

    const config = mapGuestContent(session, null);
    expect(config.menuItems.map((i) => i.id)).toEqual(['home', 'info']);
  });

  it('a null home maps to home: null (vacant path)', () => {
    const config = mapGuestContent(sessionFixture(), null);
    expect(config.home).toBeNull();
  });

  it('rejects an invalid brand hex — branding null, everything else survives (REQ-512)', () => {
    const session = sessionFixture();
    session.property.brand.primary = '#zz0000';

    const config = mapGuestContent(session, homeFixture());
    expect(config.branding).toBeNull();
    expect(config.propertyName).toBe('Meine Unterkunft');
  });

  it('renders an unknown icon name as the neutral fallback glyph, never a crash', () => {
    const session = sessionFixture();
    session.menu.items[0]!.icon = 'phosphorDoesNotExist';
    const home = homeFixture();
    home.actionCards[0]!.icon = '<script>alert(1)</script>';

    const config = mapGuestContent(session, home);
    expect(config.menuItems[0]?.icon).toBe(CONTENT_FALLBACK_ICON);
    expect(config.home?.cards[0]?.icon).toBe(CONTENT_FALLBACK_ICON);
  });

  it('accepts 0 menu items and 0 cards (the contract minima)', () => {
    const session = sessionFixture();
    session.menu.items = [];
    const home = homeFixture({ actionCards: [] });

    const config = mapGuestContent(session, home);
    expect(config.menuItems).toEqual([]);
    expect(config.home?.cards).toEqual([]);
  });

  it('rejects more than 5 items / 4 cards as a contract violation', () => {
    const session = sessionFixture();
    session.menu.items = Array.from({ length: 6 }, (_, i) => ({
      key: `k${i}`,
      label: { de: 'X' },
      icon: 'phosphorInfo',
      route: `/k${i}`,
      sortIndex: i,
    }));
    expect(() => mapGuestContent(session, null)).toThrow(GuestContentMappingError);

    const home = homeFixture();
    home.actionCards = Array.from({ length: 5 }, () => home.actionCards[0]!);
    expect(() => mapGuestContent(sessionFixture(), home)).toThrow(GuestContentMappingError);
  });

  it('rejects a localized map missing the default-language entry', () => {
    const session = sessionFixture();
    session.standby.message = { en: 'Welcome' }; // defaultLanguage is 'de'
    expect(() => mapGuestContent(session, null)).toThrow(GuestContentMappingError);
  });

  it('rejects an offered language with no registered catalogue', () => {
    const session = sessionFixture();
    session.settings.languages = [
      { code: 'fr' as never, shortLabel: 'FR', label: 'Français', locale: 'fr-FR' },
    ];
    session.settings.defaultLanguage = 'fr' as never;
    expect(() => mapGuestContent(session, null)).toThrow(GuestContentMappingError);
  });

  it('rejects a default outside the offered set', () => {
    const session = sessionFixture();
    session.settings.defaultAppearance = 'system';
    expect(() => mapGuestContent(session, null)).toThrow(GuestContentMappingError);
  });
});

describe('mapStoredPreferences', () => {
  it('hands the raw stay override to hydration', () => {
    expect(mapStoredPreferences(sessionFixture())).toEqual({ language: 'en' });
  });

  it('absent preference → null; junk values are dropped', () => {
    expect(mapStoredPreferences(sessionFixture({ preference: undefined }))).toBeNull();
    expect(
      mapStoredPreferences(sessionFixture({ preference: { language: '', appearance: 'neon' } })),
    ).toEqual({});
  });
});
