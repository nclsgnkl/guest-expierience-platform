import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { GuestHomeScreen, GuestSession } from '@gxp/api-client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { DeviceTokenStore } from '../api/device-token.store';
import { provideGuestApi } from '../api/provide-guest-api';
import { GuestAssetService } from './guest-asset.service';
import {
  GuestDataService,
  HOME_CACHE_KEY,
  SESSION_CACHE_KEY,
} from './guest-data.service';

/**
 * S-data — the content state machine (REQ-509/516/517/519, 02-design.md §4):
 * 200/304/error/401 transitions, the last-known-good cache, vacancy
 * data-sparing, and the conditional-request headers the poll sends.
 */
function sessionFixture(occupancy: 'OCCUPIED' | 'VACANT' = 'OCCUPIED'): GuestSession {
  return {
    property: {
      name: 'Meine Unterkunft',
      brand: { primary: '#f45aa8', primaryDeep: '#cc49af', secondary: '#8a5cf6' },
    },
    room: { code: '1', displayName: 'Zimmer 1' },
    occupancy,
    ...(occupancy === 'OCCUPIED' ? { guestDisplayName: 'Anna & Tom' } : {}),
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
  };
}

function homeFixture(): GuestHomeScreen {
  return {
    headline: { de: 'Willkommen im Airbnb von Anna & Tom!' },
    subtitle: { de: 'Schön, dass ihr da seid' },
    paragraph: { de: 'Entspannt euch.' },
    actionCards: [],
  };
}

const tick = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

describe('GuestDataService', () => {
  let http: HttpTestingController;
  let tokenStore: DeviceTokenStore;
  let service: GuestDataService;

  beforeEach(() => {
    localStorage.clear();
    environment.devDeviceToken = null;
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideGuestApi(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    tokenStore = TestBed.inject(DeviceTokenStore);
    tokenStore.set('a-device-token');
    service = TestBed.inject(GuestDataService);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  function flushSession(session: GuestSession, etag = '"s1"'): void {
    http.expectOne('/api/guest/session').flush(session, { headers: { etag } });
  }

  function flushHome(home: GuestHomeScreen, etag = '"h1"'): void {
    http.expectOne('/api/guest/screens/home').flush(home, { headers: { etag } });
  }

  async function bootOccupied(): Promise<void> {
    const pending = service.bootstrap();
    await tick();
    flushSession(sessionFixture());
    await tick();
    flushHome(homeFixture());
    await pending;
  }

  it('bootstrap: 200 OCCUPIED → session + home fetched, ONLINE, cache written', async () => {
    await bootOccupied();

    expect(service.connection()).toBe('ONLINE');
    expect(service.occupancy()).toBe('OCCUPIED');
    expect(service.config()?.home?.headline['de']).toBe('Willkommen im Airbnb von Anna & Tom!');
    expect(service.staleSince()).toBeNull();
    expect(localStorage.getItem(SESSION_CACHE_KEY)).toContain('Meine Unterkunft');
    expect(localStorage.getItem(HOME_CACHE_KEY)).toContain('Willkommen im Airbnb');
  });

  it('bootstrap: VACANT → no home request at all (409 is never provoked)', async () => {
    const pending = service.bootstrap();
    await tick();
    flushSession(sessionFixture('VACANT'));
    await pending;

    expect(service.occupancy()).toBe('VACANT');
    expect(service.config()?.home).toBeNull();
    expect(service.connection()).toBe('ONLINE');
    http.expectNone('/api/guest/screens/home');
  });

  it('bootstrap: 401 → token AND cache wiped, nothing rendered (REQ-509)', async () => {
    localStorage.setItem(SESSION_CACHE_KEY, JSON.stringify({ payload: sessionFixture(), etag: null, storedAt: 1 }));
    const pending = service.bootstrap();
    await tick();
    http.expectOne('/api/guest/session').flush({}, { status: 401, statusText: 'Unauthorized' });
    await pending;

    expect(tokenStore.token()).toBeNull();
    expect(localStorage.getItem(SESSION_CACHE_KEY)).toBeNull();
    expect(localStorage.getItem(HOME_CACHE_KEY)).toBeNull();
    expect(service.config()).toBeNull();
  });

  it('bootstrap: network error + cache → STALE rendering the cache, staleSince = storedAt', async () => {
    localStorage.setItem(
      SESSION_CACHE_KEY,
      JSON.stringify({ payload: sessionFixture('VACANT'), etag: '"s0"', storedAt: 123_456 }),
    );
    const pending = service.bootstrap();
    await tick();
    http.expectOne('/api/guest/session').error(new ProgressEvent('error'));
    await pending;

    expect(service.connection()).toBe('STALE');
    expect(service.config()).not.toBeNull();
    expect(service.staleSince()).toBe(123_456);
  });

  it('bootstrap: network error and no cache → DISCONNECTED (connection screen)', async () => {
    const pending = service.bootstrap();
    await tick();
    http.expectOne('/api/guest/session').error(new ProgressEvent('error'));
    await pending;

    expect(service.connection()).toBe('DISCONNECTED');
    expect(service.config()).toBeNull();
  });

  it('refresh: sends If-None-Match and treats 304 as a no-op', async () => {
    await bootOccupied();
    const heldConfig = service.config();

    const pending = service.refresh();
    await tick();
    const sessionRequest = http.expectOne('/api/guest/session');
    expect(sessionRequest.request.headers.get('If-None-Match')).toBe('"s1"');
    sessionRequest.flush(null, { status: 304, statusText: 'Not Modified' });
    await tick();
    const homeRequest = http.expectOne('/api/guest/screens/home');
    expect(homeRequest.request.headers.get('If-None-Match')).toBe('"h1"');
    homeRequest.flush(null, { status: 304, statusText: 'Not Modified' });
    await pending;

    expect(service.connection()).toBe('ONLINE');
    expect(service.config()).toBe(heldConfig); // same reference — nothing re-rendered
  });

  it('refresh: occupancy flip to VACANT clears home payload AND home cache', async () => {
    await bootOccupied();

    const pending = service.refresh();
    await tick();
    flushSession(sessionFixture('VACANT'), '"s2"');
    await pending;

    expect(service.occupancy()).toBe('VACANT');
    expect(service.config()?.home).toBeNull();
    expect(localStorage.getItem(HOME_CACHE_KEY)).toBeNull();
    http.expectNone('/api/guest/screens/home');
  });

  it('refresh: an error keeps rendering (STALE + staleSince); the next 200 heals to ONLINE', async () => {
    await bootOccupied();

    let pending = service.refresh();
    await tick();
    http.expectOne('/api/guest/session').error(new ProgressEvent('error'));
    await pending;

    expect(service.connection()).toBe('STALE');
    expect(service.config()).not.toBeNull();
    expect(service.staleSince()).not.toBeNull();

    pending = service.refresh();
    await tick();
    flushSession(sessionFixture(), '"s3"');
    await tick();
    flushHome(homeFixture(), '"h3"');
    await pending;

    expect(service.connection()).toBe('ONLINE');
    expect(service.staleSince()).toBeNull();
  });

  it('refresh: 401 wipes token, cache and signals (revocation, REQ-509)', async () => {
    await bootOccupied();

    const pending = service.refresh();
    await tick();
    http.expectOne('/api/guest/session').flush({}, { status: 401, statusText: 'Unauthorized' });
    await pending;

    expect(tokenStore.token()).toBeNull();
    expect(service.config()).toBeNull();
    expect(localStorage.getItem(SESSION_CACHE_KEY)).toBeNull();
    expect(localStorage.getItem(HOME_CACHE_KEY)).toBeNull();
  });

  it('refresh: 401 also revokes every fetched asset (🟡-2 — the wipe reaches the bytes)', async () => {
    await bootOccupied();
    const clear = vi.spyOn(TestBed.inject(GuestAssetService), 'clear');

    const pending = service.refresh();
    await tick();
    http.expectOne('/api/guest/session').flush({}, { status: 401, statusText: 'Unauthorized' });
    await pending;

    expect(clear).toHaveBeenCalledOnce();
  });

  it('refresh: a 409 on home is vacancy NOW — standby, not the previous guest behind a pill', async () => {
    await bootOccupied();

    const pending = service.refresh();
    await tick();
    // The session still says OCCUPIED (it was rendered a moment earlier);
    // the stay ended between the two calls of this very poll (🟡-4).
    flushSession(sessionFixture(), '"s2"');
    await tick();
    http
      .expectOne('/api/guest/screens/home')
      .flush({ code: 'room-vacant' }, { status: 409, statusText: 'Conflict' });
    await pending;

    expect(service.occupancy()).toBe('VACANT');
    expect(service.config()?.home).toBeNull();
    expect(service.connection()).toBe('ONLINE'); // the backend answered — nothing is stale
    expect(service.staleSince()).toBeNull();
    expect(localStorage.getItem(HOME_CACHE_KEY)).toBeNull();
    // Vacancy data-sparing: no stay-derived value survives, in RAM or cache.
    expect(service.session()?.guestDisplayName).toBeUndefined();
    expect(localStorage.getItem(SESSION_CACHE_KEY)).not.toContain('Anna & Tom');
  });

  it('bootstrap: a 409 on home adopts the vacant session instead of failing the boot', async () => {
    const pending = service.bootstrap();
    await tick();
    flushSession(sessionFixture());
    await tick();
    http
      .expectOne('/api/guest/screens/home')
      .flush({ code: 'room-vacant' }, { status: 409, statusText: 'Conflict' });
    await pending;

    expect(service.occupancy()).toBe('VACANT');
    expect(service.connection()).toBe('ONLINE');
    expect(service.config()?.home).toBeNull();
  });

  it('a cache the mapper rejects is discarded, not rendered', async () => {
    localStorage.setItem(
      SESSION_CACHE_KEY,
      JSON.stringify({ payload: { nonsense: true }, etag: null, storedAt: 1 }),
    );
    const pending = service.bootstrap();
    await tick();
    http.expectOne('/api/guest/session').error(new ProgressEvent('error'));
    await pending;

    expect(service.connection()).toBe('DISCONNECTED');
    expect(localStorage.getItem(SESSION_CACHE_KEY)).toBeNull();
  });
});
