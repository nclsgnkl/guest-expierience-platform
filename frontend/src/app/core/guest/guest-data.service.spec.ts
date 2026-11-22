import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { GuestContent, GuestInfoModule, GuestMenuItem } from '@gxp/api-client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { DeviceTokenStore } from '../api/device-token.store';
import { provideGuestApi } from '../api/provide-guest-api';
import { GuestAssetService } from './guest-asset.service';
import { mapGuestContent } from './guest-content.mapper';
import { CONTENT_CACHE_KEY, GuestDataService } from './guest-data.service';

/**
 * S-data — the content state machine (REQ-509/516/517/519, 02-design.md §4):
 * 200/304/error/401 transitions, the last-known-good cache, vacancy
 * data-sparing, and the conditional request the poll sends.
 *
 * Since guest-payload-consolidation there is **one** resource
 * (`/api/guest/content`), so one poll is one request: every case below asserts
 * the same observable behaviour it asserted against the two-request machine.
 */
const DEVICE_TOKEN = 'a-device-token';
const LEGACY_KEYS = ['gxp.cache.session', 'gxp.cache.home'] as const;
/** The cache payload's allow-list (02-design.md §5) — the occupied full set. */
const ALLOWED_PAYLOAD_KEYS = [
  'property',
  'room',
  'occupancy',
  'guestDisplayName',
  'settings',
  'preference',
  'menu',
  'standby',
  'home',
] as const;

/**
 * The compile-time half of the allow-list (REQ-951). The runtime assertion
 * below only ever sees what `contentFixture()` puts in the cache, so on its
 * own it goes green on an unclassified new wire field. These two declarations
 * make `api/openapi.yaml` the thing that fails: a field added to
 * `GuestContent` breaks the build here until it is classified above, and a
 * key deleted from the allow-list breaks it too.
 */
type PayloadKey = keyof Required<GuestContent>;
const _allowListIsExhaustive: Record<PayloadKey, true> = {
  property: true,
  room: true,
  occupancy: true,
  guestDisplayName: true,
  settings: true,
  preference: true,
  menu: true,
  standby: true,
  home: true,
};
const _allowListMatchesTheContract: readonly PayloadKey[] = ALLOWED_PAYLOAD_KEYS;
void _allowListIsExhaustive;
void _allowListMatchesTheContract;

/**
 * The same guard, nested (module-content REQ-979, 02-design.md §7). The
 * top-level table above classifies `menu` as one word, so a field added
 * **inside** a menu item or inside a module would pass it silently — and the
 * module is exactly where the WiFi password now lives. Two more tables, one per
 * level the module introduced, so `api/openapi.yaml` still fails the build.
 *
 * Its residual, recorded rather than discovered later: these are hand-written
 * tables, not recursion. A third level would need a third table, and nothing
 * here notices that it is missing.
 */
type MenuItemKey = keyof Required<GuestMenuItem>;
const _menuItemIsClassified: Record<MenuItemKey, true> = {
  key: true,
  label: true,
  icon: true,
  route: true,
  sortIndex: true,
  module: true,
};
type InfoModuleKey = keyof Required<GuestInfoModule>;
const _infoModuleIsClassified: Record<InfoModuleKey, true> = { type: true, rows: true };
void _menuItemIsClassified;
void _infoModuleIsClassified;

function contentFixture(occupancy: 'OCCUPIED' | 'VACANT' = 'OCCUPIED'): GuestContent {
  return {
    property: {
      name: 'Meine Unterkunft',
      brand: { primary: '#f45aa8', primaryDeep: '#cc49af', secondary: '#8a5cf6' },
    },
    room: { code: '1', displayName: 'Zimmer 1' },
    occupancy,
    ...(occupancy === 'OCCUPIED'
      ? { guestDisplayName: 'Anna & Tom', preference: { language: 'de' } }
      : {}),
    settings: {
      languages: [{ code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' }],
      appearances: [
        { mode: 'light', icon: 'phosphorSun' },
        { mode: 'dark', icon: 'phosphorMoon' },
      ],
      defaultLanguage: 'de',
      defaultAppearance: 'light',
    },
    menu: {
      items: [
        { key: 'home', label: { de: 'Home' }, icon: 'phosphorHouse', route: '', sortIndex: 0 },
      ],
      settingsEntry: {
        id: 'einstellungen',
        label: { de: 'Einstellungen' },
        icon: 'phosphorGearSix',
        route: '/einstellungen',
      },
    },
    standby: { headline: { de: 'Meine Unterkunft' }, message: { de: 'Herzlich willkommen' } },
    ...(occupancy === 'OCCUPIED'
      ? {
          home: {
            headline: { de: 'Willkommen im Airbnb von Anna & Tom!' },
            subtitle: { de: 'Schön, dass ihr da seid' },
            paragraph: { de: 'Entspannt euch.' },
            actionCards: [],
          },
        }
      : {}),
  };
}

/**
 * The same payload with one module-carrying section appended (module-content
 * REQ-979). Derived here rather than added to `contentFixture()`: the shared
 * fixture's single menu item is asserted verbatim by REQ-945's vacancy case,
 * and this feature must not move an assertion it has no business moving.
 *
 * The WiFi row is deliberate. Constitution v1.9.0 says the cache MAY hold it —
 * a value the property put on a guest-facing screen is one the guest may read
 * — so what is proved below is that it survives caching **intact**, not that it
 * is stripped.
 */
function contentWithModule(): GuestContent {
  const content = contentFixture();
  return {
    ...content,
    menu: {
      ...content.menu,
      items: [
        ...content.menu.items,
        {
          key: 'info',
          label: { de: 'Info' },
          icon: 'phosphorInfo',
          route: '/info',
          sortIndex: 1,
          module: {
            type: 'info',
            rows: [
              { label: { de: 'Check-out' }, value: { de: 'Bis 11:00 Uhr am Abreisetag' } },
              { label: { de: 'WLAN-Passwort' }, value: { de: 'Meeresrauschen-77' } },
            ],
          },
        },
      ],
    },
  };
}

const tick = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

describe('GuestDataService', () => {
  let http: HttpTestingController;
  let tokenStore: DeviceTokenStore;
  let service: GuestDataService;

  beforeEach(() => {
    localStorage.clear();
    // Seeded before the service is constructed: every upgraded kiosk in the
    // field holds these two pre-consolidation records (§5).
    for (const key of LEGACY_KEYS) {
      localStorage.setItem(key, '{"payload":{},"etag":null,"storedAt":1}');
    }
    environment.devDeviceToken = null;
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideGuestApi(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    tokenStore = TestBed.inject(DeviceTokenStore);
    tokenStore.set(DEVICE_TOKEN);
    service = TestBed.inject(GuestDataService);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  function flushContent(content: GuestContent, etag = '"c1"'): void {
    http.expectOne('/api/guest/content').flush(content, { headers: { etag } });
  }

  async function bootOccupied(content: GuestContent = contentFixture()): Promise<void> {
    const pending = service.bootstrap();
    await tick();
    flushContent(content);
    await pending;
  }

  it('bootstrap: 200 OCCUPIED → one request, ONLINE, cache written', async () => {
    await bootOccupied();

    expect(service.connection()).toBe('ONLINE');
    expect(service.occupancy()).toBe('OCCUPIED');
    expect(service.config()?.home?.headline['de']).toBe('Willkommen im Airbnb von Anna & Tom!');
    expect(service.staleSince()).toBeNull();
    const cached = localStorage.getItem(CONTENT_CACHE_KEY);
    expect(cached).toContain('Meine Unterkunft');
    expect(cached).toContain('Willkommen im Airbnb');
  });

  it('bootstrap: VACANT → the same single request, home absent, ONLINE', async () => {
    const pending = service.bootstrap();
    await tick();
    flushContent(contentFixture('VACANT'));
    await pending;

    expect(service.occupancy()).toBe('VACANT');
    expect(service.config()?.home).toBeNull();
    expect(service.connection()).toBe('ONLINE');
    http.expectNone('/api/guest/content'); // one poll is one request
  });

  it('bootstrap: 401 → token AND cache wiped, nothing rendered (REQ-509)', async () => {
    localStorage.setItem(
      CONTENT_CACHE_KEY,
      JSON.stringify({ payload: contentFixture(), etag: null, storedAt: 1 }),
    );
    const pending = service.bootstrap();
    await tick();
    http.expectOne('/api/guest/content').flush({}, { status: 401, statusText: 'Unauthorized' });
    await pending;

    expect(tokenStore.token()).toBeNull();
    expect(localStorage.getItem(CONTENT_CACHE_KEY)).toBeNull();
    expect(service.config()).toBeNull();
  });

  it('bootstrap: network error + cache → STALE rendering the cache, staleSince = storedAt', async () => {
    localStorage.setItem(
      CONTENT_CACHE_KEY,
      JSON.stringify({ payload: contentFixture('VACANT'), etag: '"c0"', storedAt: 123_456 }),
    );
    const pending = service.bootstrap();
    await tick();
    http.expectOne('/api/guest/content').error(new ProgressEvent('error'));
    await pending;

    expect(service.connection()).toBe('STALE');
    expect(service.config()).not.toBeNull();
    expect(service.staleSince()).toBe(123_456);
  });

  it('bootstrap: network error and no cache → DISCONNECTED (connection screen)', async () => {
    const pending = service.bootstrap();
    await tick();
    http.expectOne('/api/guest/content').error(new ProgressEvent('error'));
    await pending;

    expect(service.connection()).toBe('DISCONNECTED');
    expect(service.config()).toBeNull();
  });

  it('refresh: sends If-None-Match and treats 304 as a no-op', async () => {
    await bootOccupied();
    const heldConfig = service.config();

    const pending = service.refresh();
    await tick();
    const request = http.expectOne('/api/guest/content');
    expect(request.request.headers.get('If-None-Match')).toBe('"c1"');
    request.flush(null, { status: 304, statusText: 'Not Modified' });
    await pending;

    expect(service.connection()).toBe('ONLINE');
    expect(service.config()).toBe(heldConfig); // same reference — nothing re-rendered
  });

  it('refresh: occupancy flip to VACANT clears the home half from RAM and cache', async () => {
    await bootOccupied();

    const pending = service.refresh();
    await tick();
    flushContent(contentFixture('VACANT'), '"c2"');
    await pending;

    expect(service.occupancy()).toBe('VACANT');
    expect(service.config()?.home).toBeNull();
    expect(localStorage.getItem(CONTENT_CACHE_KEY)).not.toContain('Willkommen im Airbnb');
    http.expectNone('/api/guest/content');
  });

  it('refresh: an error keeps rendering (STALE + staleSince); the next 200 heals to ONLINE', async () => {
    await bootOccupied();

    let pending = service.refresh();
    await tick();
    http.expectOne('/api/guest/content').error(new ProgressEvent('error'));
    await pending;

    expect(service.connection()).toBe('STALE');
    expect(service.config()).not.toBeNull();
    expect(service.staleSince()).not.toBeNull();

    pending = service.refresh();
    await tick();
    flushContent(contentFixture(), '"c3"');
    await pending;

    expect(service.connection()).toBe('ONLINE');
    expect(service.staleSince()).toBeNull();
  });

  it('refresh: 401 wipes token, cache and signals (revocation, REQ-509)', async () => {
    await bootOccupied();

    const pending = service.refresh();
    await tick();
    http.expectOne('/api/guest/content').flush({}, { status: 401, statusText: 'Unauthorized' });
    await pending;

    expect(tokenStore.token()).toBeNull();
    expect(service.config()).toBeNull();
    expect(localStorage.getItem(CONTENT_CACHE_KEY)).toBeNull();
  });

  it('refresh: 401 also revokes every fetched asset (🟡-2 — the wipe reaches the bytes)', async () => {
    await bootOccupied();
    const clear = vi.spyOn(TestBed.inject(GuestAssetService), 'clear');

    const pending = service.refresh();
    await tick();
    http.expectOne('/api/guest/content').flush({}, { status: 401, statusText: 'Unauthorized' });
    await pending;

    expect(clear).toHaveBeenCalledOnce();
  });

  it('refresh: the server answers vacant — standby at once, not the previous guest behind a pill', async () => {
    await bootOccupied();

    // Was two cases and a `409 room-vacant` race: the stay could end between a
    // poll's session call and its home call. One request cannot disagree with
    // itself, so the race is gone — but the behaviour it protected is not: the
    // previous guest's content must leave the screen on this very poll.
    const pending = service.refresh();
    await tick();
    flushContent(contentFixture('VACANT'), '"c2"');
    await pending;

    expect(service.occupancy()).toBe('VACANT');
    expect(service.config()?.home).toBeNull();
    expect(service.connection()).toBe('ONLINE'); // the backend answered — nothing is stale
    expect(service.staleSince()).toBeNull();
    // Vacancy data-sparing: no stay-derived value survives, in RAM or cache.
    expect(service.payload()?.guestDisplayName).toBeUndefined();
    expect(service.preferenceOverride()).toBeNull();
    expect(localStorage.getItem(CONTENT_CACHE_KEY)).not.toContain('Anna & Tom');
  });

  it('bootstrap: a vacant answer is adopted, not treated as a failed boot', async () => {
    const pending = service.bootstrap();
    await tick();
    flushContent(contentFixture('VACANT'));
    await pending;

    expect(service.occupancy()).toBe('VACANT');
    expect(service.connection()).toBe('ONLINE');
    expect(service.config()?.home).toBeNull();
  });

  it('the property half survives a vacancy episode, in RAM and in the cache (REQ-945)', async () => {
    await bootOccupied();

    const pending = service.refresh();
    await tick();
    flushContent(contentFixture('VACANT'), '"c2"');
    await pending;

    expect(service.config()?.propertyName).toBe('Meine Unterkunft');
    expect(service.config()?.branding).not.toBeNull();
    expect(service.config()?.menuItems.map((item) => item.id)).toEqual(['home']);
    expect(service.config()?.standbyMessage['de']).toBe('Herzlich willkommen');

    const cached = localStorage.getItem(CONTENT_CACHE_KEY) ?? '';
    expect(cached).toContain('Meine Unterkunft');
    expect(cached).toContain('Herzlich willkommen');
    expect(cached).not.toContain('Anna & Tom');
  });

  it('a cache the mapper rejects is discarded, not rendered', async () => {
    localStorage.setItem(
      CONTENT_CACHE_KEY,
      JSON.stringify({ payload: { nonsense: true }, etag: null, storedAt: 1 }),
    );
    const pending = service.bootstrap();
    await tick();
    http.expectOne('/api/guest/content').error(new ProgressEvent('error'));
    await pending;

    expect(service.connection()).toBe('DISCONNECTED');
    expect(localStorage.getItem(CONTENT_CACHE_KEY)).toBeNull();
  });

  it('evicts the two pre-consolidation cache records once, at construction (§5)', () => {
    for (const key of LEGACY_KEYS) {
      expect(localStorage.getItem(key)).toBeNull();
    }
  });

  it('caches only classified content — never a credential (REQ-951)', async () => {
    await bootOccupied(contentWithModule());
    const raw = localStorage.getItem(CONTENT_CACHE_KEY) ?? '';
    const record = JSON.parse(raw) as Record<string, unknown>;

    expect(Object.keys(record).sort()).toEqual(['etag', 'payload', 'storedAt']);
    // Every top-level payload key is classified, and an unclassified new wire
    // field fails here until someone classifies it.
    expect(Object.keys(record['payload'] as object).sort()).toEqual(
      [...ALLOWED_PAYLOAD_KEYS].sort(),
    );
    // Constitution v1.9.0: the cache may hold guest-facing content the manager
    // deliberately displays — a WiFi password is content. What it may never
    // hold is a credential that reaches past this device. So the assertion is
    // about the credentials themselves, NOT about field names: a filter on
    // `password`/`code`/`secret` would assert close to the opposite rule.
    expect(raw).not.toContain(DEVICE_TOKEN);
    expect(raw.toLowerCase()).not.toContain('bearer');
    expect(raw.toLowerCase()).not.toContain('authorization');

    /* Reached into EXPLICITLY, not left to the top-level sweep: `menu` is one
       classified word up there, and everything the module added sits under it
       (REQ-979, 02-design.md §7). */
    const payload = record['payload'] as GuestContent;
    const item = payload.menu.items[1];
    expect(Object.keys(item as object).sort()).toEqual(
      ['icon', 'key', 'label', 'module', 'route', 'sortIndex'].sort(),
    );
    const module = item?.module as GuestInfoModule;
    expect(Object.keys(module).sort()).toEqual(['rows', 'type'].sort());
    expect(module.rows.map((row) => row.value['de'])).toEqual([
      'Bis 11:00 Uhr am Abreisetag',
      'Meeresrauschen-77',
    ]);
  });

  /**
   * REQ-979's other half: `/info` must render **identically** from the cache —
   * no degradation, no hidden row. The module is guest-facing content the
   * manager chose to display, so purging it would deny the door code to
   * exactly the guest whose backend is unreachable (constitution v1.9.0).
   */
  it('renders the module from the cache when the backend is unreachable', async () => {
    await bootOccupied(contentWithModule());

    const cached = JSON.parse(localStorage.getItem(CONTENT_CACHE_KEY) ?? '{}') as {
      payload: GuestContent;
    };
    const config = mapGuestContent(cached.payload);

    const module = config.menuItems[1]?.module;
    expect(module?.type).toBe('info');
    expect(module?.rows.map((row) => row.value['de'])).toEqual([
      'Bis 11:00 Uhr am Abreisetag',
      'Meeresrauschen-77',
    ]);
  });
});
