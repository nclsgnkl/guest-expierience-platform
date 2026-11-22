import { signal, type WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';

import { PreferencesService } from '../preferences/preferences.service';
import { de } from './catalog/de';
import { en } from './catalog/en';
import type { LanguageCode, TranslationKey } from './translation.model';
import { TranslationService } from './translation.service';

/**
 * S27 — `TranslationService` (REQ-124, REQ-106, REQ-111).
 *
 * `PreferencesService` is a DI double here on purpose: the only thing this
 * service reads from it is the `language` signal, so the double is the whole
 * of the collaborator's surface and no gateway, config or storage is dragged
 * into a test about catalogue lookup.
 */
describe('TranslationService', () => {
  function setup(initial: LanguageCode = 'de'): {
    service: TranslationService;
    language: WritableSignal<LanguageCode>;
  } {
    const language = signal<LanguageCode>(initial);
    TestBed.configureTestingModule({
      providers: [{ provide: PreferencesService, useValue: { language } }],
    });
    return { service: TestBed.inject(TranslationService), language };
  }

  it('resolves a known key from the active catalogue, per language', () => {
    const { service, language } = setup('de');
    expect(service.t('nav.aria')).toBe(de['nav.aria']);

    language.set('en');
    expect(service.t('nav.aria')).toBe(en['nav.aria']);
  });

  it('exposes the active language', () => {
    const { service, language } = setup('de');
    expect(service.language()).toBe('de');

    language.set('en');
    expect(service.language()).toBe('en');
  });

  it('falls back to the default catalogue when the active language has none', () => {
    const { service, language } = setup('de');
    // 'fr' is representable but not registered — the guard is the catalogue
    // registry, not a hardcoded list of languages.
    language.set('fr');
    expect(service.t('section.comingSoon')).toBe(de['section.comingSoon']);
  });

  it('returns the key itself when no catalogue has it', () => {
    const { service } = setup('en');
    const missing = 'not.a.real.key' as TranslationKey;
    expect(service.t(missing)).toBe('not.a.real.key');
  });

  it('never logs the missing key', () => {
    const { service } = setup('en');
    const spies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((method) =>
      vi.spyOn(console, method).mockImplementation(() => undefined),
    );

    service.t('not.a.real.key' as TranslationKey);
    service.t('nav.aria');

    for (const spy of spies) {
      expect(spy).not.toHaveBeenCalled();
      spy.mockRestore();
    }
  });

  it('interpolates parameters into the resolved value', () => {
    const { service } = setup('de');
    // No shipped catalogue value carries a placeholder today, so the
    // key-fallback path is what exercises interpolation through the public
    // API — deliberately without touching the service to make it testable.
    const templateKey = 'Hallo {name}, {count} Nachrichten' as TranslationKey;

    expect(service.t(templateKey, { name: 'Lisa', count: 3 })).toBe('Hallo Lisa, 3 Nachrichten');
  });

  it('leaves a placeholder alone when no value is supplied for it', () => {
    const { service } = setup('de');
    const templateKey = 'Hallo {name}' as TranslationKey;

    expect(service.t(templateKey, { other: 'x' })).toBe('Hallo {name}');
    expect(service.t(templateKey)).toBe('Hallo {name}');
  });

  it('works detached from the instance (t is an arrow-function property)', () => {
    const { service, language } = setup('de');
    // Exactly how components hold it:
    // `protected readonly t = inject(TranslationService).t;`
    const t = service.t;

    expect(t('nav.aria')).toBe(de['nav.aria']);

    language.set('en');
    expect(t('nav.aria')).toBe(en['nav.aria']);
  });
});
