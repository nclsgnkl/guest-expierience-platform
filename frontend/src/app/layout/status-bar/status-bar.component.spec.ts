import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorCaretDown,
  phosphorCheck,
  phosphorCircleHalf,
  phosphorGlobe,
  phosphorMoon,
  phosphorSun,
} from '@ng-icons/phosphor-icons/regular';
import { afterEach, describe, expect, it } from 'vitest';

import { ClockService } from '../../core/clock/clock.service';
import { PropertyContentService } from '../../core/content/property-content.service';
import type { PropertySettingsConfig } from '../../core/content/property.model';
import {
  PREFERENCE_GATEWAY,
  type PreferenceGateway,
} from '../../core/preferences/preference-gateway';
import { PreferencesService } from '../../core/preferences/preferences.service';
import { StatusBarComponent } from './status-bar.component';

/**
 * S33 — `StatusBarComponent` (REQ-124, REQ-113, REQ-114).
 *
 * The **real** `PreferencesService` and `TranslationService` are used: the
 * point of most of these cases is that the bar reads and writes the one piece
 * of shared state, which a double would hide. What is faked is what the bar
 * does not own — the property config and the clock.
 *
 * No persistence is asserted here (that is S39), so the gateway is inert.
 */
const SILENT_GATEWAY: PreferenceGateway = {
  load: () => Promise.resolve(null),
  save: () => Promise.resolve(),
  clear: () => Promise.resolve(),
};

/**
 * A test double, not the shipped property: it offers `system`, which the real
 * one deliberately does not (decision #4), because "the glyph follows the
 * *resolved* theme, not the selected mode" is only observable when the two
 * can differ.
 */
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

/** 2026-08-19, 16:42 local wall-clock — the TODO's worked example. */
const FIXED_NOW = new Date(2026, 7, 19, 16, 42, 7);

/**
 * jsdom implements no `matchMedia`; the service guards it. Copied from
 * `preferences.service.spec.ts` rather than exported from it — a spec file is
 * not a helper module.
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

describe('StatusBarComponent', () => {
  afterEach(() => {
    Reflect.deleteProperty(globalThis, 'matchMedia');
  });

  async function setup(config: PropertySettingsConfig = TEST_CONFIG): Promise<{
    fixture: ComponentFixture<StatusBarComponent>;
    preferences: PreferencesService;
  }> {
    const settingsConfig = signal(config);

    await TestBed.configureTestingModule({
      imports: [StatusBarComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideIcons({
          phosphorCaretDown,
          phosphorCheck,
          phosphorCircleHalf,
          phosphorGlobe,
          phosphorMoon,
          phosphorSun,
        }),
        { provide: PREFERENCE_GATEWAY, useValue: SILENT_GATEWAY },
        { provide: PropertyContentService, useValue: { settingsConfig } },
        { provide: ClockService, useValue: { now: signal(FIXED_NOW).asReadonly() } },
      ],
    }).compileComponents();

    const preferences = TestBed.inject(PreferencesService);
    preferences.applyPropertyDefaults(config);

    const fixture = TestBed.createComponent(StatusBarComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, preferences };
  }

  function triggers(fixture: ComponentFixture<StatusBarComponent>): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.status-bar-menu__trigger'));
  }

  /** The projected glyph, i.e. everything in the trigger except the caret. */
  function triggerGlyph(button: HTMLButtonElement): string | undefined {
    return button.querySelector('ng-icon:not(.status-bar-menu__caret) svg')?.innerHTML;
  }

  /** Clicking a trigger toggles, so an already-open panel has to go first. */
  async function openMenu(
    fixture: ComponentFixture<StatusBarComponent>,
    index: 0 | 1,
  ): Promise<HTMLButtonElement[]> {
    const open = document.querySelector('.status-bar-menu__panel');
    if (open) {
      const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true });
      Object.defineProperty(escape, 'keyCode', { get: () => 27 });
      open.dispatchEvent(escape);
      await fixture.whenStable();
    }

    triggers(fixture)[index].click();
    await fixture.whenStable();
    return Array.from(document.querySelectorAll('.status-bar-menu__option'));
  }

  const openLanguageMenu = (fixture: ComponentFixture<StatusBarComponent>) => openMenu(fixture, 0);
  const openAppearanceMenu = (fixture: ComponentFixture<StatusBarComponent>) =>
    openMenu(fixture, 1);

  it("renders the active language's short code from the property config", async () => {
    const { fixture } = await setup();

    expect(
      fixture.nativeElement.querySelector('.status-bar__language-code').textContent.trim(),
    ).toBe('DE');
  });

  it('moves the short code when the guest picks another language', async () => {
    const { fixture, preferences } = await setup();

    preferences.setLanguage('en');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(
      fixture.nativeElement.querySelector('.status-bar__language-code').textContent.trim(),
    ).toBe('EN');
  });

  it('renders the hairline divider, hidden from assistive technology', async () => {
    const { fixture } = await setup();

    const divider = fixture.nativeElement.querySelector('.status-bar__divider');
    expect(divider).toBeTruthy();
    expect(divider.getAttribute('aria-hidden')).toBe('true');
  });

  it('translates every accessible name, and re-translates them on a language change', async () => {
    const { fixture, preferences } = await setup();

    const named = () => ({
      language: triggers(fixture)[0].getAttribute('aria-label'),
      appearance: triggers(fixture)[1].getAttribute('aria-label'),
      time: fixture.nativeElement.querySelector('.status-bar__clock').getAttribute('aria-label'),
    });

    expect(named()).toEqual({
      language: 'Sprache wählen',
      appearance: 'Darstellung wählen',
      time: 'Aktuelle Uhrzeit',
    });

    preferences.setLanguage('en');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(named()).toEqual({
      language: 'Choose language',
      appearance: 'Choose appearance',
      time: 'Current time',
    });
  });

  it('formats the time for the active language, and carries a 24-hour datetime', async () => {
    const { fixture, preferences } = await setup();
    const clock = () => fixture.nativeElement.querySelector('.status-bar__clock');

    expect(clock().textContent.trim()).toBe('16:42');
    expect(clock().getAttribute('datetime')).toBe('16:42');

    preferences.setLanguage('en');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(clock().textContent.trim()).toBe('16:42');
    expect(clock().getAttribute('datetime')).toBe('16:42');
  });

  it('follows the configured locale, not the language code — a 12-hour locale renders 12-hour', async () => {
    const { fixture, preferences } = await setup({
      ...TEST_CONFIG,
      languages: [
        { code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' },
        { code: 'en', shortLabel: 'EN', label: 'English', locale: 'en-US' },
      ],
    });

    preferences.setLanguage('en');
    fixture.detectChanges();
    await fixture.whenStable();

    const clock = fixture.nativeElement.querySelector('.status-bar__clock');
    expect(clock.textContent).toContain('4:42');
    expect(clock.textContent).toContain('PM');
    // The machine-readable half stays 24-hour whatever the locale renders.
    expect(clock.getAttribute('datetime')).toBe('16:42');
  });

  it('lists language endonyms untranslated and appearance labels translated', async () => {
    const { fixture, preferences } = await setup();

    let rows = await openLanguageMenu(fixture);
    expect(rows.map((row) => row.textContent!.trim())).toEqual(
      expect.arrayContaining([
        expect.stringContaining('Deutsch'),
        expect.stringContaining('English'),
      ]),
    );

    preferences.setLanguage('en');
    fixture.detectChanges();
    await fixture.whenStable();

    rows = await openLanguageMenu(fixture);
    // Endonyms: a language's own name is never translated (REQ-107).
    expect(rows.map((row) => row.textContent!.trim())).toEqual(
      expect.arrayContaining([
        expect.stringContaining('Deutsch'),
        expect.stringContaining('English'),
      ]),
    );

    rows = await openAppearanceMenu(fixture);
    const labels = rows.map((row) => row.textContent!.trim());
    expect(labels.some((label) => label.includes('Light'))).toBe(true);
    expect(labels.some((label) => label.includes('Dark'))).toBe(true);
  });

  it("writes the guest's pick into the shared state", async () => {
    const { fixture, preferences } = await setup();

    const rows = await openAppearanceMenu(fixture);
    rows[1].click();
    await fixture.whenStable();

    expect(preferences.appearance()).toBe('dark');
    expect(preferences.resolvedTheme()).toBe('dark');
  });

  it('shows the glyph of the selected mode when that mode is concrete', async () => {
    const { fixture, preferences } = await setup();

    const rows = await openAppearanceMenu(fixture);
    const lightGlyph = rows[0].querySelector('.status-bar-menu__option-icon svg')!.innerHTML;
    const darkGlyph = rows[1].querySelector('.status-bar-menu__option-icon svg')!.innerHTML;
    expect(lightGlyph).not.toBe(darkGlyph);

    expect(triggerGlyph(triggers(fixture)[1])).toBe(lightGlyph);

    preferences.setAppearance('dark');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(triggerGlyph(triggers(fixture)[1])).toBe(darkGlyph);
  });

  it("shows the *resolved* theme's glyph when 'system' is selected, and follows a system flip", async () => {
    const emit = installMatchMedia(true);
    const { fixture, preferences } = await setup();

    const rows = await openAppearanceMenu(fixture);
    const lightGlyph = rows[0].querySelector('.status-bar-menu__option-icon svg')!.innerHTML;
    const darkGlyph = rows[1].querySelector('.status-bar-menu__option-icon svg')!.innerHTML;
    const systemGlyph = rows[2].querySelector('.status-bar-menu__option-icon svg')!.innerHTML;

    preferences.setAppearance('system');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(preferences.appearance()).toBe('system');
    // The mode is `system`; what the guest is *looking at* is dark, and that
    // is what the trigger reports (02-design.md §8.3).
    expect(triggerGlyph(triggers(fixture)[1])).toBe(darkGlyph);
    expect(triggerGlyph(triggers(fixture)[1])).not.toBe(systemGlyph);

    emit(false);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(triggerGlyph(triggers(fixture)[1])).toBe(lightGlyph);
  });

  it('marks the current value in each menu', async () => {
    const { fixture, preferences } = await setup();
    preferences.setLanguage('en');
    preferences.setAppearance('dark');
    fixture.detectChanges();
    await fixture.whenStable();

    const languageRows = await openLanguageMenu(fixture);
    const selectedLanguage = languageRows.filter((row) =>
      row.classList.contains('status-bar-menu__option--selected'),
    );
    expect(selectedLanguage.length).toBe(1);
    expect(selectedLanguage[0].textContent).toContain('English');

    const appearanceRows = await openAppearanceMenu(fixture);
    const selectedAppearance = appearanceRows.filter((row) =>
      row.classList.contains('status-bar-menu__option--selected'),
    );
    expect(selectedAppearance.length).toBe(1);
    expect(selectedAppearance[0].textContent).toContain('Dark');
  });
});
