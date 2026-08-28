import { provideZonelessChangeDetection, signal, type WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { GuestDataService } from '../guest/guest-data.service';
import type { LanguageCode } from '../i18n/translation.model';
import { PreferencesService } from '../preferences/preferences.service';
import { PropertyContentService, resolveProperty } from './property-content.service';
import type { PropertyConfig } from './property.model';

/**
 * S29, wire edition — `PropertyContentService` (REQ-510/514/515).
 *
 * The raw config now arrives from `GuestDataService` (a DI double built from
 * a writable signal — the static data file was deleted per D6, and the
 * boundary strictness moved to `guest-content.mapper.spec.ts`). What this
 * spec owns: the component-facing semantics — per-field language resolution
 * over server maps and the loud throw on a read before any config is held.
 */
const CONFIG: PropertyConfig = {
  propertyName: 'Testhaus',
  logoAssetId: 7,
  branding: { primary: '#f45aa8', primaryDeep: '#cc49af', secondary: '#8a5cf6' },
  menuItems: [
    { id: 'home', label: { de: 'Startseite', en: 'Home' }, icon: 'phosphorHouse', route: '' },
    { id: 'info', label: { de: 'Info', en: 'Info' }, icon: 'phosphorInfo', route: '/info' },
  ],
  settings: {
    id: 'einstellungen',
    label: { de: 'Einstellungen', en: 'Settings' },
    icon: 'phosphorGearSix',
    route: '/einstellungen',
  },
  settingsConfig: {
    languages: [
      { code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' },
      { code: 'en', shortLabel: 'EN', label: 'English', locale: 'en-GB' },
    ],
    defaultLanguage: 'de',
    appearances: [{ mode: 'light', icon: 'phosphorSun' }],
    defaultAppearance: 'light',
  },
  standbyHeadline: { de: 'Testhaus', en: 'Testhaus' },
  standbyMessage: { de: 'Herzlich willkommen', en: 'Welcome' },
  home: {
    heroSubtitle: { de: 'Schön, dass ihr da seid', en: 'Great to have you here' },
    headline: { de: 'Willkommen, Anna & Tom!', en: 'Welcome, Anna & Tom!' },
    paragraph: { de: 'Entspannt euch.', en: 'Relax.' },
    artworkAssetId: null,
    cards: [
      {
        id: 'info-0',
        icon: 'phosphorKey',
        header: { de: 'Check-Out', en: 'Check-out' },
        description: { de: 'Bis 11:00', en: 'By 11:00' },
        route: '/info',
      },
    ],
  },
};

describe('PropertyContentService', () => {
  function setup(
    initialLanguage: LanguageCode = 'de',
    initialConfig: PropertyConfig | null = CONFIG,
  ): {
    service: PropertyContentService;
    language: WritableSignal<LanguageCode>;
    config: WritableSignal<PropertyConfig | null>;
  } {
    const language = signal<LanguageCode>(initialLanguage);
    const config = signal<PropertyConfig | null>(initialConfig);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PreferencesService, useValue: { language } },
        { provide: GuestDataService, useValue: { config: config.asReadonly() } },
      ],
    });
    return { service: TestBed.inject(PropertyContentService), language, config };
  }

  it('property() resolves every localized field for the active language', () => {
    const { service } = setup('de');
    const property = service.property();

    expect(property.menuItems[0]?.label).toBe('Startseite');
    expect(property.settings.label).toBe('Einstellungen');
    expect(property.standbyMessage).toBe('Herzlich willkommen');
    expect(property.home?.headline).toBe('Willkommen, Anna & Tom!');
    expect(property.home?.cards[0]?.header).toBe('Check-Out');
  });

  it('a language switch re-resolves from data already held — no fetch, no service call (REQ-514)', () => {
    const { service, language } = setup('de');
    expect(service.property().menuItems[0]?.label).toBe('Startseite');

    language.set('en');

    expect(service.property().menuItems[0]?.label).toBe('Home');
    expect(service.property().standbyMessage).toBe('Welcome');
    expect(service.property().home?.cards[0]?.description).toBe('By 11:00');
  });

  it('an unknown active language falls back to the property default per field', () => {
    const { service, language } = setup('de');
    language.set('fr');

    expect(service.property().menuItems[0]?.label).toBe('Startseite');
  });

  it('settingsConfig() is language-independent', () => {
    const { service, language } = setup('de');
    const before = service.settingsConfig();

    language.set('en');

    expect(service.settingsConfig()).toBe(before);
    expect(before.defaultLanguage).toBe('de');
  });

  it('locale() resolves from the offered language options, not the bare code (REQ-925)', () => {
    const { service } = setup('de');
    expect(service.locale()).toBe('de-DE');
  });

  it('locale() follows a language switch and fetches nothing (REQ-926)', () => {
    const { service, language, config } = setup('de');
    expect(service.locale()).toBe('de-DE');
    const configReadsBefore = config();

    language.set('en');

    expect(service.locale()).toBe('en-GB');
    // the same held config answered both — nothing was re-fetched or replaced
    expect(config()).toBe(configReadsBefore);
  });

  it('locale() falls back to the bare language code when the property offers no match', () => {
    const { service, language } = setup('de');
    language.set('fr');

    expect(service.locale()).toBe('fr');
  });

  it('branding() and loaded() reflect the held config', () => {
    const { service } = setup();
    expect(service.loaded()).toBe(true);
    expect(service.branding()?.primary).toBe('#f45aa8');
  });

  it('a rejected wire brand reads as null — the defaults stay in force (REQ-512)', () => {
    const { service } = setup('de', { ...CONFIG, branding: null });
    expect(service.branding()).toBeNull();
  });

  it('reading content before anything is loaded throws — a guard let a route through early', () => {
    const { service } = setup('de', null);
    expect(service.loaded()).toBe(false);
    expect(() => service.property()).toThrowError(/no content loaded/);
    expect(() => service.settingsConfig()).toThrowError(/no content loaded/);
  });

  it('config updates propagate (poll 200 → re-render)', () => {
    const { service, config } = setup();
    config.set({ ...CONFIG, propertyName: 'Neues Haus' });
    expect(service.property().propertyName).toBe('Neues Haus');
  });
});

describe('resolveProperty (pure)', () => {
  it('resolves a null home to null and a present home per field', () => {
    expect(resolveProperty({ ...CONFIG, home: null }, 'de').home).toBeNull();
    expect(resolveProperty(CONFIG, 'en').home?.paragraph).toBe('Relax.');
  });

  it('throws when a field lacks the default-language entry (defensive; the mapper prevents it)', () => {
    const broken: PropertyConfig = {
      ...CONFIG,
      standbyMessage: { en: 'Welcome' }, // default language is de
    };
    expect(() => resolveProperty(broken, 'de')).toThrowError(/standbyMessage/);
  });
});
