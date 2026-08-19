import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';

import { DEFAULT_CATALOG_LANGUAGE } from '../i18n/catalog';
import type { PropertySettingsConfig } from '../content/property.model';
import {
  PREFERENCE_GATEWAY,
  type PreferenceGateway,
  type StoredPreferences,
} from './preference-gateway';
import { FALLBACK_DEFAULTS, PreferencesService } from './preferences.service';

/**
 * S28 — `PreferencesService`, **state layer only**. Persistence (hydrate, the
 * coalescing writer, retry, `clear()`) is S39 and is deliberately not asserted
 * here; the gateway below is inert plumbing so the service can be constructed,
 * not a subject.
 *
 * The config is a test double, never the shipped `example-property.data.ts`
 * (`frontend/CLAUDE.md` -> Tests): it carries a third appearance option
 * (`system`) that the real property does not offer, which is exactly what
 * makes `resolvedTheme()`'s `matchMedia` branch reachable at all.
 */
const SILENT_GATEWAY: PreferenceGateway = {
  load: () => Promise.resolve<StoredPreferences | null>(null),
  save: () => Promise.resolve(),
  clear: () => Promise.resolve(),
};

const TEST_CONFIG: PropertySettingsConfig = {
  languages: [
    { code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' },
    { code: 'en', shortLabel: 'EN', label: 'English', locale: 'en-GB' },
  ],
  defaultLanguage: 'de',
  appearances: [
    { mode: 'light', icon: 'phosphorSun' },
    { mode: 'dark', icon: 'phosphorMoon' },
    { mode: 'system', icon: 'phosphorCircleHalf' },
  ],
  defaultAppearance: 'light',
};

/** The property as actually shipped: no `system` option (decision #4). */
const NO_SYSTEM_CONFIG: PropertySettingsConfig = {
  ...TEST_CONFIG,
  appearances: [
    { mode: 'light', icon: 'phosphorSun' },
    { mode: 'dark', icon: 'phosphorMoon' },
  ],
};

/**
 * jsdom implements no `matchMedia`, which is why the service guards it with
 * `globalThis.matchMedia?.(…)`. A test that wants the branch has to supply one
 * — including a working `change` event, since "follows the system live" is the
 * behaviour under test, not "reads it once".
 */
function installMatchMedia(initialMatches: boolean): (matches: boolean) => void {
  const listeners = new Set<(event: MediaQueryListEvent) => void>();
  let matches = initialMatches;

  const list = {
    get matches() {
      return matches;
    },
    media: '(prefers-color-scheme: dark)',
    addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => {
      listeners.add(listener);
    },
    removeEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => {
      listeners.delete(listener);
    },
  };

  Object.defineProperty(globalThis, 'matchMedia', {
    configurable: true,
    writable: true,
    value: () => list as unknown as MediaQueryList,
  });

  return (next: boolean) => {
    matches = next;
    for (const listener of listeners) {
      listener({ matches: next } as MediaQueryListEvent);
    }
  };
}

describe('PreferencesService (state layer)', () => {
  afterEach(() => {
    Reflect.deleteProperty(globalThis, 'matchMedia');
  });

  function setup(config?: PropertySettingsConfig): PreferencesService {
    TestBed.configureTestingModule({
      providers: [{ provide: PREFERENCE_GATEWAY, useValue: SILENT_GATEWAY }],
    });
    const service = TestBed.inject(PreferencesService);
    if (config) {
      service.applyPropertyDefaults(config);
    }
    return service;
  }

  it('starts on the fallback defaults before any property config is applied', () => {
    const service = setup();

    expect(service.language()).toBe(FALLBACK_DEFAULTS.language);
    expect(service.language()).toBe(DEFAULT_CATALOG_LANGUAGE);
    expect(service.appearance()).toBe('light');
  });

  it('effective values are the property defaults while the guest has chosen nothing', () => {
    const service = setup({ ...TEST_CONFIG, defaultLanguage: 'en', defaultAppearance: 'dark' });

    expect(service.language()).toBe('en');
    expect(service.appearance()).toBe('dark');
  });

  it("the guest's choice wins over the default", () => {
    const service = setup(TEST_CONFIG);

    service.setLanguage('en');
    service.setAppearance('dark');

    expect(service.language()).toBe('en');
    expect(service.appearance()).toBe('dark');
  });

  it('changing one setting leaves the other exactly where it was', () => {
    const service = setup(TEST_CONFIG);

    service.setLanguage('en');

    expect(service.appearance()).toBe('light');

    service.setAppearance('dark');

    expect(service.language()).toBe('en');
  });

  it('reset() drops both choices and the defaults apply again', () => {
    const service = setup(TEST_CONFIG);
    service.setLanguage('en');
    service.setAppearance('dark');

    service.reset();

    expect(service.language()).toBe('de');
    expect(service.appearance()).toBe('light');
  });

  it('ignores a language the property does not configure', () => {
    const service = setup(TEST_CONFIG);

    service.setLanguage('fr');

    expect(service.language()).toBe('de');
  });

  it('ignores an appearance the property does not configure', () => {
    const service = setup(NO_SYSTEM_CONFIG);

    service.setAppearance('system');

    expect(service.appearance()).toBe('light');
  });

  it('ignores any choice while no property config has been applied', () => {
    const service = setup();

    service.setLanguage('en');
    service.setAppearance('dark');

    expect(service.language()).toBe(FALLBACK_DEFAULTS.language);
    expect(service.appearance()).toBe(FALLBACK_DEFAULTS.appearance);
  });

  it('applyPropertyDefaults() recomputes the effective values', () => {
    const service = setup(TEST_CONFIG);
    expect(service.language()).toBe('de');

    service.applyPropertyDefaults({
      ...TEST_CONFIG,
      defaultLanguage: 'en',
      defaultAppearance: 'dark',
    });

    expect(service.language()).toBe('en');
    expect(service.appearance()).toBe('dark');
  });

  it('applyPropertyDefaults() does not overwrite a choice already in effect', () => {
    const service = setup(TEST_CONFIG);
    service.setLanguage('en');

    service.applyPropertyDefaults({ ...TEST_CONFIG, defaultLanguage: 'de' });

    expect(service.language()).toBe('en');
  });

  it('resolvedTheme() passes a concrete appearance through unchanged', () => {
    const service = setup(TEST_CONFIG);

    expect(service.resolvedTheme()).toBe('light');

    service.setAppearance('dark');

    expect(service.resolvedTheme()).toBe('dark');
  });

  it("resolvedTheme() resolves 'system' through matchMedia and follows its change event", () => {
    const emit = installMatchMedia(true);
    const service = setup(TEST_CONFIG);

    service.setAppearance('system');
    expect(service.appearance()).toBe('system');
    expect(service.resolvedTheme()).toBe('dark');

    emit(false);
    expect(service.resolvedTheme()).toBe('light');

    emit(true);
    expect(service.resolvedTheme()).toBe('dark');
  });

  it('never consults matchMedia when the property offers no system option (decision #4)', () => {
    const emit = installMatchMedia(true);
    const service = setup(NO_SYSTEM_CONFIG);

    expect(service.resolvedTheme()).toBe('light');

    emit(false);
    expect(service.resolvedTheme()).toBe('light');

    emit(true);
    expect(service.resolvedTheme()).toBe('light');
  });
});
