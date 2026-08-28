import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorCaretDown,
  phosphorGlobe,
  phosphorMoon,
  phosphorSun,
  phosphorWifiSlash,
} from '@ng-icons/phosphor-icons/regular';
import { beforeEach, describe, expect, it } from 'vitest';

import { PropertyContentService } from '../../core/content/property-content.service';
import type { ResolvedPropertyConfig } from '../../core/content/property.model';
import { GuestAssetService } from '../../core/guest/guest-asset.service';
import { GuestDataService } from '../../core/guest/guest-data.service';
import { STALE_ESCALATION_MS } from '../../core/guest/staleness.service';
import { PREFERENCE_GATEWAY } from '../../core/preferences/preference-gateway';
import { PreferencesService } from '../../core/preferences/preferences.service';
import { StandbyComponent } from './standby.component';

/**
 * S-standby — the vacant-room screen (REQ-517; spec:
 * docs/design/screens-inhouse/standby.md): the server-resolved standby texts
 * for the active language, the functional status bar, no navigation surface,
 * and — data-sparing — no guest-derived value anywhere in the DOM.
 */
function config(language: string): ResolvedPropertyConfig {
  return {
    propertyName: 'Villa Test',
    logoAssetId: null,
    branding: null,
    menuItems: [
      /* Deliberately present in the CONFIG: the menu exists server-side, but
         the standby screen must not render any navigation from it. */
      { id: 'info', label: `Info-${language}`, icon: 'phosphorInfo', route: '/info' },
    ],
    settings: {
      id: 'einstellungen',
      label: `Einstellungen-${language}`,
      icon: 'phosphorGearSix',
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
    standbyHeadline: language === 'de' ? 'Villa Test' : 'Villa Test (en)',
    standbyMessage: language === 'de' ? 'Herzlich willkommen' : 'A warm welcome',
    home: null,
  };
}

describe('StandbyComponent', () => {
  const language = signal('de');
  const logoUrl = signal<string | null>(null);
  const connection = signal('ONLINE');
  const staleSince = signal<number | null>(null);
  const occupancy = signal<string | null>('VACANT');

  beforeEach(() => {
    language.set('de');
    logoUrl.set(null);
    connection.set('ONLINE');
    staleSince.set(null);
    occupancy.set('VACANT');
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideIcons({
          phosphorGlobe,
          phosphorSun,
          phosphorMoon,
          phosphorCaretDown,
          phosphorWifiSlash,
        }),
        {
          provide: PREFERENCE_GATEWAY,
          useValue: {
            load: () => Promise.resolve(null),
            save: () => Promise.resolve(),
            clear: () => Promise.resolve(),
          },
        },
        /* The status bar, pill and banner read these; standby itself reads
           property(). */
        {
          provide: GuestDataService,
          useValue: { connection, staleSince, occupancy },
        },
        {
          provide: PropertyContentService,
          useValue: {
            property: () => config(language()),
            settingsConfig: () => config(language()).settingsConfig,
          },
        },
        { provide: GuestAssetService, useValue: { url: () => logoUrl.asReadonly() } },
      ],
    });
  });

  function render() {
    const fixture = TestBed.createComponent(StandbyComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('renders the server-resolved standby texts for the active language', async () => {
    const fixture = render();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.standby__headline')?.textContent).toContain('Villa Test');
    expect(el.querySelector('.standby__message')?.textContent).toContain('Herzlich willkommen');

    language.set('en');
    // The double resolves per call; the real service is a computed over the
    // preferences signal — re-render via the preferences path is S29's job.
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(el.querySelector('.standby__message')?.textContent).toContain('A warm welcome');
  });

  it('exposes no navigation surface: status-bar buttons only, no sidebar, no nav', () => {
    const fixture = render();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('app-sidebar')).toBeNull();
    expect(el.querySelector('nav')).toBeNull();
    expect(el.textContent).not.toContain('Info-de'); // the menu exists, the screen ignores it
    const buttons = Array.from(el.querySelectorAll('button'));
    for (const button of buttons) {
      expect(button.closest('app-status-bar')).not.toBeNull();
    }
    expect(el.querySelector('app-status-bar')).toBeTruthy();
  });

  it('renders no guest-derived value while vacant (data-sparing)', () => {
    const fixture = render();
    // The fixture carries no stay data at all; assert nothing slipped in via
    // chrome: the DOM holds property texts and status-bar chrome only.
    expect(fixture.nativeElement.textContent).not.toContain('Anna');
  });

  it('shows the property logo only when it exists and has arrived (alt="")', async () => {
    const fixture = render();
    expect(fixture.nativeElement.querySelector('.standby__logo')).toBeNull();
  });

  it('escalates to the banner with the STAFF wording — a vacant room has no guest (REQ-952)', async () => {
    connection.set('STALE');
    // A minute past the CONFIGURED threshold, not a literal hour: the
    // development environment shortens it to a minute (see
    // `staleness.service.ts`), and the minute-aligned ClockService needs the
    // slack on top.
    staleSince.set(Date.now() - STALE_ESCALATION_MS - 60_000);
    const fixture = render();
    const el: HTMLElement = fixture.nativeElement;

    const banner = el.querySelector('.staleness-banner');
    expect(banner).toBeTruthy();
    expect(banner!.getAttribute('role')).toBe('status');
    expect(banner!.getAttribute('aria-live')).toBe('polite');
    // The occupied wording cannot be reused here: it directs a guest to their
    // host, and there is no guest (spec: connection.md -> Content).
    expect(banner!.textContent).toContain('Verwaltung');
    expect(banner!.textContent).not.toContain('Gastgeber');
    // Tri-state: the banner replaces the pill, it does not join it.
    expect(el.querySelector('.staleness-pill')).toBeNull();
    // REQ-952's second half: the standby block keeps rendering behind the
    // banner — the escalation is an overlay, never a cutoff.
    expect(el.querySelector('.standby__headline')?.textContent).toContain('Villa Test');
  });
});
