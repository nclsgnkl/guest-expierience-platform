import { provideZonelessChangeDetection, signal, type WritableSignal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { App } from './app.component';
import { PropertyContentService } from './core/content/property-content.service';
import type { PropertyBranding } from './core/content/property.model';
import { GuestStateRoutingService } from './core/guest/guest-state-routing.service';
import type { LanguageCode } from './core/i18n/translation.model';
import { PreferencesService } from './core/preferences/preferences.service';

/**
 * S30 — `App` (REQ-124, REQ-110 → REQ-512).
 *
 * `App` is the only code in `src/` allowed to touch `document.documentElement`
 * (`frontend/CLAUDE.md`), so this spec owns the proof that it does: branding
 * primitives whenever a validated brand is held, `data-theme` and `lang` on
 * every change — and that a `null` brand (unpaired / rejected wire value)
 * writes nothing, leaving the token-layer defaults in force.
 *
 * `PreferencesService` and `PropertyContentService` are DI doubles holding
 * the signals `App` reads; `GuestStateRoutingService` is stubbed empty (its
 * routing effect has its own spec surface via the guards).
 */
describe('App', () => {
  const root = document.documentElement;

  const BRAND: PropertyBranding = {
    primary: '#f45aa8',
    primaryDeep: '#cc49af',
    secondary: '#8a5cf6',
  };

  afterEach(() => {
    root.removeAttribute('data-theme');
    root.removeAttribute('lang');
    root.style.removeProperty('--brand-primary');
    root.style.removeProperty('--brand-primary-deep');
    root.style.removeProperty('--brand-secondary');
  });

  async function setup(
    theme: 'light' | 'dark' = 'light',
    initialLanguage: LanguageCode = 'de',
    initialBranding: PropertyBranding | null = BRAND,
  ): Promise<{
    fixture: ComponentFixture<App>;
    resolvedTheme: WritableSignal<'light' | 'dark'>;
    language: WritableSignal<LanguageCode>;
    branding: WritableSignal<PropertyBranding | null>;
  }> {
    const resolvedTheme = signal<'light' | 'dark'>(theme);
    const language = signal<LanguageCode>(initialLanguage);
    const branding = signal<PropertyBranding | null>(initialBranding);

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        { provide: PreferencesService, useValue: { resolvedTheme, language } },
        { provide: PropertyContentService, useValue: { branding } },
        { provide: GuestStateRoutingService, useValue: {} },
        provideZonelessChangeDetection(),
        provideRouter([]),
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return { fixture, resolvedTheme, language, branding };
  }

  it('should create the app', async () => {
    const { fixture } = await setup();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('applies the held branding primitives to the document root before first paint', async () => {
    await setup();
    expect(root.style.getPropertyValue('--brand-primary')).toBe('#f45aa8');
    expect(root.style.getPropertyValue('--brand-primary-deep')).toBe('#cc49af');
    expect(root.style.getPropertyValue('--brand-secondary')).toBe('#8a5cf6');
  });

  it('a null brand writes nothing — the token-layer defaults stay (REQ-512)', async () => {
    await setup('light', 'de', null);
    expect(root.style.getPropertyValue('--brand-primary')).toBe('');
    expect(root.style.getPropertyValue('--brand-secondary')).toBe('');
  });

  it('applies branding when it ARRIVES (pairing claim / poll re-brand)', async () => {
    const { fixture, branding } = await setup('light', 'de', null);

    branding.set(BRAND);
    await fixture.whenStable();

    expect(root.style.getPropertyValue('--brand-primary')).toBe('#f45aa8');
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
