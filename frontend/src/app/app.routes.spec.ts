import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection, signal, type WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideIcons } from '@ng-icons/core';
import { beforeEach, describe, expect, it } from 'vitest';

import { routes } from './app.routes';
import { DeviceTokenStore } from './core/api/device-token.store';
import type { PropertyConfig } from './core/content/property.model';
import { GuestDataService } from './core/guest/guest-data.service';
import { REGISTERED_ICONS } from './core/icons/registered-icons';
import { PREFERENCE_GATEWAY } from './core/preferences/preference-gateway';
import { ShellComponent } from './layout/shell/shell.component';

/**
 * The route table + the state guards (REQ-506/517/520): unpaired → /pairing,
 * vacant → /standby, nothing-to-render → /connection, and the shell serving
 * every in-app route exactly as before. State is a `GuestDataService` /
 * `DeviceTokenStore` double pair — the guards read only signals, so the spec
 * drives them directly.
 */
const CONFIG: PropertyConfig = {
  propertyName: 'Testhaus',
  logoAssetId: null,
  branding: null,
  menuItems: [
    { id: 'home', label: { de: 'Home' }, icon: 'phosphorHouse', route: '' },
    { id: 'info', label: { de: 'Info' }, icon: 'phosphorInfo', route: '/info' },
    { id: 'umgebung', label: { de: 'Umgebung' }, icon: 'phosphorMapPin', route: '/umgebung' },
    {
      id: 'smart-home',
      label: { de: 'Smart Home' },
      icon: 'phosphorLightbulbFilament',
      route: '/smart-home',
    },
    { id: 'services', label: { de: 'Services' }, icon: 'phosphorShoppingBag', route: '/services' },
  ],
  settings: {
    id: 'einstellungen',
    label: { de: 'Einstellungen' },
    icon: 'phosphorGearSix',
    route: '/einstellungen',
  },
  settingsConfig: {
    languages: [{ code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' }],
    defaultLanguage: 'de',
    appearances: [{ mode: 'light', icon: 'phosphorSun' }],
    defaultAppearance: 'light',
  },
  standbyHeadline: { de: 'Testhaus-Standby' },
  standbyMessage: { de: 'Standby-Nachricht' },
  home: {
    heroSubtitle: { de: 'Untertitel' },
    headline: { de: 'Willkommen!' },
    paragraph: { de: 'Absatz.' },
    artworkAssetId: null,
    cards: [],
  },
};

describe('app.routes', () => {
  let token: WritableSignal<string | null>;
  let config: WritableSignal<PropertyConfig | null>;
  let occupancy: WritableSignal<'OCCUPIED' | 'VACANT' | null>;
  let connection: WritableSignal<'ONLINE' | 'STALE' | 'DISCONNECTED'>;

  function setup(state: {
    token?: string | null;
    config?: PropertyConfig | null;
    occupancy?: 'OCCUPIED' | 'VACANT' | null;
    connection?: 'ONLINE' | 'STALE' | 'DISCONNECTED';
  }): void {
    token = signal<string | null>(state.token !== undefined ? state.token : 'a-device-token');
    config = signal<PropertyConfig | null>(state.config !== undefined ? state.config : CONFIG);
    occupancy = signal<'OCCUPIED' | 'VACANT' | null>(state.occupancy ?? 'OCCUPIED');
    connection = signal<'ONLINE' | 'STALE' | 'DISCONNECTED'>(state.connection ?? 'ONLINE');

    TestBed.configureTestingModule({
      providers: [
        {
          provide: PREFERENCE_GATEWAY,
          useValue: {
            load: () => Promise.resolve(null),
            save: () => Promise.resolve(),
            clear: () => Promise.resolve(),
          },
        },
        { provide: DeviceTokenStore, useValue: { token: token.asReadonly() } },
        {
          provide: GuestDataService,
          useValue: {
            config: config.asReadonly(),
            occupancy: occupancy.asReadonly(),
            connection: connection.asReadonly(),
            staleSince: signal<number | null>(null),
            session: signal(null).asReadonly(),
            sessionPreference: () => null,
            bootstrap: () => Promise.resolve(),
            handleUnauthorized: () => undefined,
          },
        },
        provideZonelessChangeDetection(),
        provideRouter(routes),
        /* PairingComponent starts its create call on activation. */
        provideHttpClient(),
        provideHttpClientTesting(),
        provideIcons(REGISTERED_ICONS),
      ],
    });
  }

  it("resolves '' to Home content inside the shared shell (paired, occupied)", async () => {
    setup({});
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/', ShellComponent);
    const el: HTMLElement = harness.routeNativeElement!;

    expect(el.querySelector('.shell')).toBeTruthy();
    expect(el.querySelector('app-sidebar')).toBeTruthy();
    expect(el.querySelector('app-home-content')).toBeTruthy();
  });

  const sections: Array<[string, string]> = [
    ['/info', 'Info'],
    ['/umgebung', 'Umgebung'],
    ['/smart-home', 'Smart Home'],
    ['/services', 'Services'],
  ];

  for (const [path, expectedLabel] of sections) {
    it(`resolves ${path} to the section placeholder showing "${expectedLabel}", inside the shared shell`, async () => {
      setup({});
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(path, ShellComponent);
      const el: HTMLElement = harness.routeNativeElement!;

      expect(el.querySelector('.shell')).toBeTruthy();
      expect(el.querySelector('app-sidebar')).toBeTruthy();
      const placeholder = el.querySelector('app-section-placeholder');
      expect(placeholder).toBeTruthy();
      /* Scoped to the placeholder, not to the whole shell: the sidebar renders
         the same label, so an unscoped assertion would pass even if the
         placeholder resolved nothing (sidebar-catalogue-overflow REQ-912). */
      expect(placeholder!.textContent).toContain(expectedLabel);
    });
  }

  it('resolves any other single-segment route through :sectionKey, inside the shell', async () => {
    setup({});
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/nicht-im-menu', ShellComponent);
    const el: HTMLElement = harness.routeNativeElement!;

    /* The route table no longer enumerates sections, so a route the delivered
       menu does not claim still lands in the shell and renders the
       unavailable state — it does not 404 and does not redirect (REQ-912). */
    expect(el.querySelector('.shell')).toBeTruthy();
    expect(el.querySelector('app-section-placeholder')).toBeTruthy();
    expect(TestBed.inject(Router).url).toBe('/nicht-im-menu');
  });

  it('resolves a deeper unknown path through the shell-scoped ** child', async () => {
    setup({});
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/info/tiefer/noch-tiefer', ShellComponent);
    const el: HTMLElement = harness.routeNativeElement!;

    expect(el.querySelector('.shell')).toBeTruthy();
    expect(el.querySelector('app-section-placeholder')).toBeTruthy();
  });

  it('resolves /einstellungen to the Settings screen, inside the shared shell', async () => {
    setup({});
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/einstellungen', ShellComponent);
    const el: HTMLElement = harness.routeNativeElement!;

    expect(el.querySelector('.shell')).toBeTruthy();
    expect(el.querySelector('app-settings')).toBeTruthy();
    expect(el.querySelector('app-section-placeholder')).toBeFalsy();
  });

  it('an unpaired kiosk is routed to /pairing from anywhere (REQ-506)', async () => {
    setup({ token: null, config: null, occupancy: null, connection: 'DISCONNECTED' });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');

    expect(TestBed.inject(Router).url).toBe('/pairing');
  });

  it('a vacant room is routed to /standby, which renders the server standby texts (REQ-517)', async () => {
    setup({ occupancy: 'VACANT' });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');

    expect(TestBed.inject(Router).url).toBe('/standby');
    const el = document.body;
    expect(el.textContent).toContain('Testhaus-Standby');
    expect(el.textContent).toContain('Standby-Nachricht');
  });

  it('paired but nothing to render is routed to /connection — NEVER to /pairing (REQ-520)', async () => {
    setup({ config: null, occupancy: null, connection: 'DISCONNECTED' });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/einstellungen');

    expect(TestBed.inject(Router).url).toBe('/connection');
  });

  it('a paired+loaded kiosk deep-linking a state route is pushed back into the app', async () => {
    setup({});
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/pairing');
    expect(TestBed.inject(Router).url).toBe('/');

    await harness.navigateByUrl('/standby');
    expect(TestBed.inject(Router).url).toBe('/');

    await harness.navigateByUrl('/connection');
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('a vacant kiosk asking for /connection lands on /standby (precedence)', async () => {
    setup({ occupancy: 'VACANT' });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/connection');

    expect(TestBed.inject(Router).url).toBe('/standby');
  });
});
