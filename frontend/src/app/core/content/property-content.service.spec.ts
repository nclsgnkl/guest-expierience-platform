import { signal, type WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import type { LanguageCode } from '../i18n/translation.model';
import { PreferencesService } from '../preferences/preferences.service';
import { EXAMPLE_PROPERTY } from './example-property.data';
import {
  PropertyContentService,
  resolveProperty,
  validateConfig,
} from './property-content.service';
import type { PropertyConfig } from './property.model';

/**
 * S29 — `PropertyContentService` (REQ-124, REQ-108, REQ-119).
 *
 * Two layers, for one concrete reason. The service is checked against the
 * **real shipped config** (`frontend/CLAUDE.md` sanctions this one file for
 * that), because that is the config a guest actually gets. The per-field
 * fallback and the three validation failures need a *broken* config, and the
 * raw config is a module import that the Angular unit-test system refuses to
 * let `vi.mock` replace — so those cases call the two exported pure functions
 * with a config double instead.
 *
 * `PreferencesService` is a DI double: the service reads nothing from it but
 * `language()`, and a double removes the `SessionPreferenceGateway` stand-in
 * this spec used to need only to satisfy the DI chain.
 */
describe('PropertyContentService', () => {
  function setup(initialLanguage: LanguageCode = 'de'): {
    service: PropertyContentService;
    language: WritableSignal<LanguageCode>;
  } {
    const language = signal<LanguageCode>(initialLanguage);
    TestBed.configureTestingModule({
      providers: [{ provide: PreferencesService, useValue: { language } }],
    });
    return { service: TestBed.inject(PropertyContentService), language };
  }

  it('property() returns the example property config resolved for the active language', () => {
    const { service } = setup();
    const property = service.property();

    expect(property.guestName).toBe('Lisa');
    expect(property.headline).toContain('{guestName}');
    expect(property.menuItems).toHaveLength(5);
    expect(property.settings.id).toBe('einstellungen');
    expect(property.cards).toHaveLength(4);
  });

  it('property() re-resolves every localised field when the language changes', () => {
    const { service, language } = setup('de');

    expect(service.property().headline).toBe(EXAMPLE_PROPERTY.headline['de']);
    expect(service.property().heroSubtitle).toBe(EXAMPLE_PROPERTY.heroSubtitle['de']);
    expect(service.property().menuItems[0].label).toBe(EXAMPLE_PROPERTY.menuItems[0].label['de']);
    expect(service.property().cards[0].header).toBe(EXAMPLE_PROPERTY.cards[0].header['de']);
    expect(service.property().settings.label).toBe(EXAMPLE_PROPERTY.settings.label['de']);

    language.set('en');

    expect(service.property().headline).toBe(EXAMPLE_PROPERTY.headline['en']);
    expect(service.property().heroSubtitle).toBe(EXAMPLE_PROPERTY.heroSubtitle['en']);
    expect(service.property().menuItems[0].label).toBe(EXAMPLE_PROPERTY.menuItems[0].label['en']);
    expect(service.property().cards[0].header).toBe(EXAMPLE_PROPERTY.cards[0].header['en']);
    expect(service.property().settings.label).toBe(EXAMPLE_PROPERTY.settings.label['en']);
  });

  it('property() leaves non-localised fields alone in every language', () => {
    const { service, language } = setup('de');
    language.set('en');
    const property = service.property();

    expect(property.guestName).toBe(EXAMPLE_PROPERTY.guestName);
    expect(property.logoAsset).toBe(EXAMPLE_PROPERTY.logoAsset);
    expect(property.branding).toEqual(EXAMPLE_PROPERTY.branding);
    expect(property.menuItems.map((item) => item.route)).toEqual(
      EXAMPLE_PROPERTY.menuItems.map((item) => item.route),
    );
  });

  it('settingsConfig() is language-independent', () => {
    const { service, language } = setup('de');
    const before = service.settingsConfig();

    language.set('en');

    expect(service.settingsConfig()).toBe(before);
    expect(before.defaultLanguage).toBe('de');
    expect(before.languages.map((option) => option.code)).toEqual(['de', 'en']);
  });

  it('getSectionRoute() resolves every example card targetSectionId to a real route', () => {
    const { service } = setup();
    const property = service.property();

    for (const card of property.cards) {
      const route = service.getSectionRoute(card.targetSectionId);
      const expected = [...property.menuItems, property.settings].find(
        (item) => item.id === card.targetSectionId,
      );
      expect(route).toBe(expected?.route);
    }
  });

  it('getSectionRoute() resolves both cards sharing targetSectionId "info" to the identical route', () => {
    const { service } = setup();
    const checkOut = EXAMPLE_PROPERTY.cards.find((c) => c.id === 'check-out')!;
    const wichtigeInfos = EXAMPLE_PROPERTY.cards.find((c) => c.id === 'wichtige-infos')!;

    expect(checkOut.targetSectionId).toBe('info');
    expect(wichtigeInfos.targetSectionId).toBe('info');
    expect(service.getSectionRoute(checkOut.targetSectionId)).toBe(
      service.getSectionRoute(wichtigeInfos.targetSectionId),
    );
  });

  it('getSectionRoute() throws for an unknown id', () => {
    const { service } = setup();
    expect(() => service.getSectionRoute('not-a-real-section')).toThrow();
  });

  it('validates the shipped config at construction, not at render', () => {
    // The shipped config passes; the failure cases below prove the same
    // function rejects a broken one before any template reads it.
    expect(() => setup()).not.toThrow();
    expect(() => validateConfig(EXAMPLE_PROPERTY)).not.toThrow();
  });
});

/** A minimal, valid authored config — the double the failure cases mutate. */
function makeConfig(overrides: Partial<PropertyConfig> = {}): PropertyConfig {
  return {
    guestName: 'Testgast',
    heroSubtitle: { de: 'Untertitel', en: 'Subtitle' },
    headline: { de: 'Überschrift', en: 'Headline' },
    paragraph: { de: 'Absatz', en: 'Paragraph' },
    logoAsset: 'assets/branding/logo-placeholder.svg',
    hasArtwork: false,
    branding: { primary: '#111111', primaryDeep: '#222222', secondary: '#333333' },
    menuItems: [
      { id: 'home', label: { de: 'Start', en: 'Home' }, icon: 'phosphorHouse', route: '' },
    ],
    settings: {
      id: 'einstellungen',
      label: { de: 'Einstellungen', en: 'Settings' },
      icon: 'phosphorGear',
      route: '/einstellungen',
    },
    settingsConfig: {
      languages: [
        { code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' },
        { code: 'en', shortLabel: 'EN', label: 'English', locale: 'en-GB' },
      ],
      defaultLanguage: 'de',
      appearances: [
        { mode: 'light', icon: 'phosphorSun' },
        { mode: 'dark', icon: 'phosphorMoon' },
      ],
      defaultAppearance: 'light',
    },
    cards: [
      {
        id: 'card',
        icon: 'phosphorKey',
        header: { de: 'Titel', en: 'Title' },
        description: { de: 'Beschreibung', en: 'Description' },
        targetSectionId: 'home',
      },
    ],
    ...overrides,
  };
}

describe('resolveProperty() — the per-field fallback rule (REQ-108)', () => {
  it('takes the active language when it is present', () => {
    const resolved = resolveProperty(makeConfig(), 'en');

    expect(resolved.headline).toBe('Headline');
    expect(resolved.menuItems[0].label).toBe('Home');
    expect(resolved.cards[0].description).toBe('Description');
    expect(resolved.settings.label).toBe('Settings');
  });

  it("falls back per field to the property's own defaultLanguage", () => {
    const config = makeConfig({
      paragraph: { de: 'Absatz' }, // no English entry authored
      headline: { de: 'Überschrift', en: 'Headline' },
    });

    const resolved = resolveProperty(config, 'en');

    expect(resolved.paragraph).toBe('Absatz');
    expect(resolved.headline).toBe('Headline'); // the other field is unaffected
  });

  it('treats an empty or whitespace-only entry as absent', () => {
    const config = makeConfig({ paragraph: { de: 'Absatz', en: '   ' } });

    expect(resolveProperty(config, 'en').paragraph).toBe('Absatz');
  });

  it('falls back for a language that is not authored at all', () => {
    expect(resolveProperty(makeConfig(), 'fr').headline).toBe('Überschrift');
  });

  it("throws when neither the active nor the property's default language has an entry", () => {
    // 'en' is authored but 'de' — the property's default — is not, so a guest
    // reading in any third language has nothing to fall back to.
    const config = makeConfig({ paragraph: { en: 'Paragraph' } });

    expect(() => resolveProperty(config, 'fr')).toThrow(/paragraph/);
    expect(() => resolveProperty(config, 'fr')).toThrow(/default language "de"/);
  });
});

describe('validateConfig() — the four constructor-time checks (02-design.md §5.2)', () => {
  it('throws when a localised field has no default-language entry, naming the field', () => {
    const config = makeConfig({
      cards: [
        {
          id: 'card',
          icon: 'phosphorKey',
          header: { en: 'Title' },
          description: { de: 'Beschreibung', en: 'Description' },
          targetSectionId: 'home',
        },
      ],
    });

    expect(() => validateConfig(config)).toThrow(/cards\[card\]\.header/);
  });

  it('throws when a default-language entry is present but empty', () => {
    expect(() => validateConfig(makeConfig({ headline: { de: '', en: 'Headline' } }))).toThrow(
      /headline/,
    );
  });

  it('throws when defaultLanguage is not one of the configured languages', () => {
    const base = makeConfig();
    const config = makeConfig({
      settingsConfig: { ...base.settingsConfig, defaultLanguage: 'en-US' },
    });

    expect(() => validateConfig(config)).toThrow(/defaultLanguage "en-US"/);
  });

  it('throws when defaultAppearance is not one of the configured appearances', () => {
    const base = makeConfig();
    const config = makeConfig({
      settingsConfig: { ...base.settingsConfig, defaultAppearance: 'system' },
    });

    expect(() => validateConfig(config)).toThrow(/defaultAppearance "system"/);
  });

  it('throws when a configured language has no registered translation catalogue', () => {
    const base = makeConfig();
    const config = makeConfig({
      settingsConfig: {
        ...base.settingsConfig,
        languages: [
          ...base.settingsConfig.languages,
          { code: 'fr', shortLabel: 'FR', label: 'Français', locale: 'fr-FR' },
        ],
      },
    });

    expect(() => validateConfig(config)).toThrow(/"fr" has no registered translation catalogue/);
  });

  it('accepts a config whose every localised field carries the default language', () => {
    expect(() => validateConfig(makeConfig({ paragraph: { de: 'Absatz' } }))).not.toThrow();
  });
});
