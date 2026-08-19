import { provideZonelessChangeDetection, signal, type WritableSignal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { App } from './app.component';
import type { LanguageCode } from './core/i18n/translation.model';
import { PreferencesService } from './core/preferences/preferences.service';

/**
 * S30 — `App` (REQ-124, REQ-110).
 *
 * `App` is the only code in `src/` allowed to touch `document.documentElement`
 * (`frontend/CLAUDE.md`), so this spec owns the proof that it does: branding
 * primitives once, `data-theme` and `lang` on every change.
 *
 * `applyPropertyDefaults()` is deliberately **not** asserted here — it moved to
 * the app initializer (S38) so the defaults are settled and the stored
 * preference hydrated before the first effect run, and it is asserted in S39.
 *
 * `PreferencesService` is a DI double holding the two signals `App` reads.
 * That is also what makes "and both update on change" assertable at all: the
 * real service would need a configured property and a gateway to move.
 */
describe('App', () => {
  const root = document.documentElement;

  afterEach(() => {
    root.removeAttribute('data-theme');
    root.removeAttribute('lang');
  });

  async function setup(
    theme: 'light' | 'dark' = 'light',
    initialLanguage: LanguageCode = 'de',
  ): Promise<{
    fixture: ComponentFixture<App>;
    resolvedTheme: WritableSignal<'light' | 'dark'>;
    language: WritableSignal<LanguageCode>;
  }> {
    const resolvedTheme = signal<'light' | 'dark'>(theme);
    const language = signal<LanguageCode>(initialLanguage);

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        { provide: PreferencesService, useValue: { resolvedTheme, language } },
        provideZonelessChangeDetection(),
        provideRouter([]),
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return { fixture, resolvedTheme, language };
  }

  it('should create the app', async () => {
    const { fixture } = await setup();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('applies the property branding primitives to the document root at start', async () => {
    await setup();
    expect(root.style.getPropertyValue('--brand-primary')).toBe('#f45aa8');
    expect(root.style.getPropertyValue('--brand-primary-deep')).toBeTruthy();
    expect(root.style.getPropertyValue('--brand-secondary')).toBeTruthy();
  });

  it('writes the resolved theme and the active language to <html>', async () => {
    await setup('light', 'de');

    expect(root.dataset['theme']).toBe('light');
    expect(root.lang).toBe('de');
  });

  it('writes a dark resolved theme just as well', async () => {
    await setup('dark', 'en');

    expect(root.dataset['theme']).toBe('dark');
    expect(root.lang).toBe('en');
  });

  it('updates <html data-theme> when the resolved theme changes', async () => {
    const { fixture, resolvedTheme } = await setup('light');

    resolvedTheme.set('dark');
    await fixture.whenStable();

    expect(root.dataset['theme']).toBe('dark');

    resolvedTheme.set('light');
    await fixture.whenStable();

    expect(root.dataset['theme']).toBe('light');
  });

  it('updates <html lang> when the language changes', async () => {
    const { fixture, language } = await setup('light', 'de');

    language.set('en');
    await fixture.whenStable();

    expect(root.lang).toBe('en');
  });

  it('moves the two attributes independently of one another', async () => {
    const { fixture, resolvedTheme, language } = await setup('light', 'de');

    language.set('en');
    await fixture.whenStable();

    expect(root.lang).toBe('en');
    expect(root.dataset['theme']).toBe('light');

    resolvedTheme.set('dark');
    await fixture.whenStable();

    expect(root.lang).toBe('en');
    expect(root.dataset['theme']).toBe('dark');
  });
});
