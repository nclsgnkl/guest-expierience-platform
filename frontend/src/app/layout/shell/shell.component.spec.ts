import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Routes } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorCaretDown,
  phosphorGearSix,
  phosphorGlobe,
  phosphorHouse,
  phosphorInfo,
  phosphorSun,
  phosphorWifiSlash,
} from '@ng-icons/phosphor-icons/regular';
import { beforeEach, describe, expect, it } from 'vitest';

import { ShellComponent } from './shell.component';
import type { PropertyConfig } from '../../core/content/property.model';
import { GuestDataService } from '../../core/guest/guest-data.service';
import { STALE_ESCALATION_MS } from '../../core/guest/staleness.service';
import { PREFERENCE_GATEWAY } from '../../core/preferences/preference-gateway';

@Component({ selector: 'app-test-home', template: '<p>home content</p>' })
class TestHomeComponent {}

@Component({ selector: 'app-test-other', template: '<p>other content</p>' })
class TestOtherComponent {}

const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', component: TestHomeComponent },
      { path: 'other', component: TestOtherComponent },
    ],
  },
];

/** Wire-shaped config double — what GuestDataService would hold (REQ-510). */
const TEST_CONFIG: PropertyConfig = {
  propertyName: 'Testhaus',
  logoAssetId: null,
  branding: null,
  menuItems: [
    { id: 'home', label: { de: 'Testlabel Home' }, icon: 'phosphorHouse', route: '', module: null },
    { id: 'info', label: { de: 'Testlabel Info' }, icon: 'phosphorInfo', route: '/info', module: null },
  ],
  settings: {
    id: 'einstellungen',
    label: { de: 'Testlabel Settings' },
    icon: 'phosphorGearSix',
    route: '/einstellungen',
    module: null,
  },
  settingsConfig: {
    languages: [{ code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' }],
    defaultLanguage: 'de',
    appearances: [{ mode: 'light', icon: 'phosphorSun' }],
    defaultAppearance: 'light',
  },
  standbyHeadline: { de: 'Testhaus' },
  standbyMessage: { de: 'Testnachricht Standby' },
  home: null,
};

describe('ShellComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        /* The transport double lives here, per frontend/CLAUDE.md. */
        {
          provide: PREFERENCE_GATEWAY,
          useValue: {
            load: () => Promise.resolve(null),
            save: () => Promise.resolve(),
            clear: () => Promise.resolve(),
          },
        },
        /* The shell reads content through the REAL PropertyContentService,
           which since REQ-510 reads GuestDataService — doubled with a held
           config plus the signals the staleness chrome derives from. */
        {
          provide: GuestDataService,
          useValue: {
            config: signal<PropertyConfig | null>(TEST_CONFIG),
            connection: signal('ONLINE'),
            staleSince: signal<number | null>(null),
            occupancy: signal<string | null>('OCCUPIED'),
          },
        },
        provideZonelessChangeDetection(),
        provideRouter(routes),
        /* The Sidebar's glyphs plus the StatusBarComponent's (globe · sun ·
           caret). Leaving them out does not fail a test — it renders the bar
           degraded and prints `No icon named …` (06-review.md finding #4). */
        provideIcons({
          phosphorHouse,
          phosphorInfo,
          phosphorGearSix,
          phosphorGlobe,
          phosphorSun,
          phosphorCaretDown,
          phosphorWifiSlash,
        }),
      ],
    });
  });

  it('renders the background and Sidebar identically across two different child routes, while the routed content differs', async () => {
    const harness = await RouterTestingHarness.create();

    const homeRoot = await harness.navigateByUrl('/', ShellComponent);
    const homeElement: HTMLElement = harness.routeNativeElement!;
    expect(homeRoot).toBeTruthy();
    expect(homeElement.querySelector('.shell')).toBeTruthy();
    expect(homeElement.querySelector('.shell')!.classList.contains('mesh-background')).toBe(true);
    expect(homeElement.querySelector('app-sidebar')).toBeTruthy();
    expect(homeElement.textContent).toContain('home content');
    expect(homeElement.textContent).not.toContain('other content');

    const otherRoot = await harness.navigateByUrl('/other', ShellComponent);
    const otherElement: HTMLElement = harness.routeNativeElement!;
    expect(otherRoot).toBeTruthy();
    expect(otherElement.querySelector('.shell')).toBeTruthy();
    expect(otherElement.querySelector('app-sidebar')).toBeTruthy();
    expect(otherElement.textContent).toContain('other content');
    expect(otherElement.textContent).not.toContain('home content');
  });

  it('declares the active-glyph paint server on every route (REQ-023)', async () => {
    const harness = await RouterTestingHarness.create();

    for (const url of ['/', '/other']) {
      await harness.navigateByUrl(url, ShellComponent);
      const element: HTMLElement = harness.routeNativeElement!;
      const gradient = element.querySelector('#icon-gradient-active');

      expect(gradient).toBeTruthy();
      expect(gradient!.querySelectorAll('stop').length).toBe(2);
    }
  });

  it('feeds the sidebar the property config sourced from PropertyContentService, not a literal', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/', ShellComponent);
    const element: HTMLElement = harness.routeNativeElement!;

    expect(element.textContent).toContain('Testlabel Home');
  });

  it('falls back to the bundled placeholder when the property has no logo asset (REQ-513)', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/', ShellComponent);
    const element: HTMLElement = harness.routeNativeElement!;

    const img = element.querySelector('.sidebar__logo-img');
    expect(img?.getAttribute('src')).toBe('assets/branding/logo-placeholder.svg');
  });

  it('shows the staleness pill only in the STALE state', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/', ShellComponent);
    const element: HTMLElement = harness.routeNativeElement!;
    expect(element.querySelector('.staleness-pill')).toBeNull();

    const guestData = staleness();
    // Half the CONFIGURED threshold ago, relative to the real clock. A literal
    // ("five minutes") would decide the other case under the development
    // environment, whose threshold is one minute — and that is the environment
    // these tests build under.
    const since = Date.now() - STALE_ESCALATION_MS / 2;
    guestData.connection.set('STALE');
    guestData.staleSince.set(since);
    await harness.fixture.whenStable();

    const pill = element.querySelector('.staleness-pill');
    expect(pill).toBeTruthy();
    expect(pill!.getAttribute('role')).toBe('status');
    expect(pill!.textContent).toContain(asOf(since));

    guestData.connection.set('ONLINE');
    guestData.staleSince.set(null);
    await harness.fixture.whenStable();
    expect(element.querySelector('.staleness-pill')).toBeNull();
  });

  it('past the threshold the pill is replaced by the banner, with the GUEST wording (REQ-952)', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/', ShellComponent);
    const element: HTMLElement = harness.routeNativeElement!;

    const guestData = staleness();
    // A minute PAST the configured threshold: the real ClockService is
    // minute-aligned, so its `now()` can trail `Date.now()` by up to 59 s and
    // a value exactly at the threshold would be a coin flip.
    const since = Date.now() - STALE_ESCALATION_MS - 60_000;
    guestData.connection.set('STALE');
    guestData.staleSince.set(since);
    await harness.fixture.whenStable();

    const banner = element.querySelector('.staleness-banner');
    expect(banner).toBeTruthy();
    expect(banner!.getAttribute('role')).toBe('status');
    expect(banner!.getAttribute('aria-live')).toBe('polite');
    // Occupied: the guest is directed to their host, and the pill's own
    // timestamp line survives the escalation.
    expect(banner!.textContent).toContain('Gastgeber');
    expect(banner!.textContent).not.toContain('Verwaltung');
    expect(banner!.textContent).toContain(asOf(since));
    // Tri-state: one derived level, so the two are never up together.
    expect(element.querySelector('.staleness-pill')).toBeNull();
    // REQ-952's second half, the one D13 argued hardest for: a hard cutoff was
    // explicitly rejected, so the routed content must keep rendering behind
    // the banner — the guest still needs the door code and the WiFi password.
    expect(element.textContent).toContain('home content');
  });

  /** The staleness signals of the `GuestDataService` double. */
  function staleness() {
    return TestBed.inject(GuestDataService) as unknown as {
      connection: ReturnType<typeof signal<string>>;
      staleSince: ReturnType<typeof signal<number | null>>;
      occupancy: ReturnType<typeof signal<string | null>>;
    };
  }

  /** What the chrome renders for a `storedAt`, in the config's own locale. */
  function asOf(since: number): string {
    return new Intl.DateTimeFormat('de-DE', { hour: '2-digit', minute: '2-digit' }).format(
      new Date(since),
    );
  }
});
