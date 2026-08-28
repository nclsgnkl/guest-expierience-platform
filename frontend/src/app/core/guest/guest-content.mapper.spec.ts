import type { GuestContent, GuestHome } from '@gxp/api-client';
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
 *
 * Since guest-payload-consolidation there is **one** payload: `home` is
 * nested inside it and is absent exactly when the room is vacant.
 */
function contentFixture(overrides: Partial<GuestContent> = {}): GuestContent {
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
    home: homeFixture(),
    ...overrides,
  };
}

/** The vacant answer: the property half intact, the stay-derived half absent. */
function vacantFixture(): GuestContent {
  const content = contentFixture();
  delete content.guestDisplayName;
  delete content.preference;
  delete content.home;
  return { ...content, occupancy: 'VACANT' };
}

function homeFixture(overrides: Partial<GuestHome> = {}): GuestHome {
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
  it('maps the dev-seed-shaped payload to the internal config (happy path)', () => {
    const config = mapGuestContent(contentFixture());

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
  });

  it('orders menu items by sortIndex regardless of wire order', () => {
    const content = contentFixture();
    content.menu.items = [...content.menu.items].reverse();

    const config = mapGuestContent(content);
    expect(config.menuItems.map((i) => i.id)).toEqual(['home', 'info']);
  });

  it('a vacant payload maps to home: null, property half intact', () => {
    const config = mapGuestContent(vacantFixture());

    expect(config.home).toBeNull();
    expect(config.propertyName).toBe('Meine Unterkunft');
    expect(config.menuItems.map((i) => i.id)).toEqual(['home', 'info']);
  });

  it('rejects an OCCUPIED payload with no home — a server bug, not half a screen', () => {
    const content = contentFixture();
    delete content.home;

    expect(() => mapGuestContent(content)).toThrow(GuestContentMappingError);
    expect(() => mapGuestContent(content)).toThrow(/OCCUPIED but "home" is absent/);
  });

  it('rejects an invalid brand hex — branding null, everything else survives (REQ-512)', () => {
    const content = contentFixture();
    content.property.brand!.primary = '#zz0000';

    const config = mapGuestContent(content);
    expect(config.branding).toBeNull();
    expect(config.propertyName).toBe('Meine Unterkunft');
  });

  it('renders an unknown icon name as the neutral fallback glyph, never a crash', () => {
    const content = contentFixture();
    content.menu.items[0]!.icon = 'phosphorDoesNotExist';
    content.home!.actionCards[0]!.icon = '<script>alert(1)</script>';

    const config = mapGuestContent(content);
    expect(config.menuItems[0]?.icon).toBe(CONTENT_FALLBACK_ICON);
    expect(config.home?.cards[0]?.icon).toBe(CONTENT_FALLBACK_ICON);
  });

  it('accepts 0 menu items and 0 cards (the contract minima)', () => {
    const content = contentFixture({ home: homeFixture({ actionCards: [] }) });
    content.menu.items = [];

    const config = mapGuestContent(content);
    expect(config.menuItems).toEqual([]);
    expect(config.home?.cards).toEqual([]);
  });

  it('rejects more than 4 cards as a contract violation', () => {
    const home = homeFixture();
    home.actionCards = Array.from({ length: 5 }, () => home.actionCards[0]!);
    expect(() => mapGuestContent(contentFixture({ home }))).toThrow(GuestContentMappingError);
  });

  it('maps a catalogue larger than the sidebar without dropping or rejecting any item', () => {
    // REQ-901: the mapper used to reject a 6th item, citing a contract bound
    // that did not exist. The bound it enforces now is the one the contract
    // really declares (`GuestMenu.items.maxItems: 50`), and how many fit down
    // the side of the screen is the sidebar's business — a 13-row catalogue is
    // what %dev serves.
    const content = contentFixture();
    content.menu.items = Array.from({ length: 12 }, (_, i) => ({
      key: `k${i}`,
      label: { de: `Eintrag ${i}`, en: `Entry ${i}` },
      icon: 'phosphorInfo',
      route: `/k${i}`,
      sortIndex: i,
    }));

    const config = mapGuestContent(content);

    expect(config.menuItems).toHaveLength(12);
    expect(config.menuItems.map((i) => i.id)).toEqual(
      Array.from({ length: 12 }, (_, i) => `k${i}`),
    );
  });

  it('maps exactly maxItems menu items and rejects one more', () => {
    // REQ-901's acceptance criterion: the rejection message names a bound that
    // actually exists — `api/openapi.yaml` declares `GuestMenu.items.maxItems: 50`.
    const itemsOfLength = (n: number) =>
      Array.from({ length: n }, (_, i) => ({
        key: `k${i}`,
        label: { de: `Eintrag ${i}`, en: `Entry ${i}` },
        icon: 'phosphorInfo',
        route: `/k${i}`,
        sortIndex: i,
      }));

    const atTheBound = contentFixture();
    atTheBound.menu.items = itemsOfLength(50);
    expect(mapGuestContent(atTheBound).menuItems).toHaveLength(50);

    const overTheBound = contentFixture();
    overTheBound.menu.items = itemsOfLength(51);
    expect(() => mapGuestContent(overTheBound)).toThrow(
      /menu\.items exceeds the contract's 0–50/,
    );
  });

  it('rejects a localized map missing the default-language entry', () => {
    const content = contentFixture();
    content.standby.message = { en: 'Welcome' }; // defaultLanguage is 'de'
    expect(() => mapGuestContent(content)).toThrow(GuestContentMappingError);
  });

  it('rejects an offered language with no registered catalogue', () => {
    const content = contentFixture();
    content.settings.languages = [
      { code: 'fr' as never, shortLabel: 'FR', label: 'Français', locale: 'fr-FR' },
    ];
    content.settings.defaultLanguage = 'fr' as never;
    expect(() => mapGuestContent(content)).toThrow(GuestContentMappingError);
  });

  it('rejects a default outside the offered set', () => {
    const content = contentFixture();
    content.settings.defaultAppearance = 'system';
    expect(() => mapGuestContent(content)).toThrow(GuestContentMappingError);
  });
});

describe('mapStoredPreferences', () => {
  it('hands the raw stay override to hydration', () => {
    expect(mapStoredPreferences(contentFixture())).toEqual({ language: 'en' });
  });

  it('absent preference → null; junk values are dropped', () => {
    expect(mapStoredPreferences(contentFixture({ preference: undefined }))).toBeNull();
    expect(
      mapStoredPreferences(contentFixture({ preference: { language: '', appearance: 'neon' } })),
    ).toEqual({});
  });
});
