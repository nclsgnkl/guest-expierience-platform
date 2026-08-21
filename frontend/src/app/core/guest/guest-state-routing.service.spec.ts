import { provideZonelessChangeDetection, signal, type WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router, type Event as RouterEvent } from '@angular/router';
import { OccupancyState } from '@gxp/api-client';
import { Subject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { PropertyConfig } from '../content/property.model';
import { DeviceTokenStore } from '../api/device-token.store';
import { GuestDataService } from './guest-data.service';
import { GuestStateRoutingService } from './guest-state-routing.service';

/**
 * S-route-runtime (REQ-509/517/520/524, review finding 🟡-5): the effect that
 * moves a *running* kiosk between the app and the three state screens. The
 * guards' initial arbitration is covered by `app.routes.spec.ts`; this covers
 * what happens after boot — a 401, an occupancy flip, a heal — and the
 * previous-route restore that keeps a standby episode from dumping a guest
 * from `/einstellungen` back to Home.
 *
 * The `Router` is a stub: this service's whole contract is *which url it asks
 * for, and when*, so a real router would only add navigation machinery
 * between the assertion and the behaviour.
 */
class RouterStub {
  url = '/';
  readonly events = new Subject<RouterEvent>();
  private navigationId = 0;

  readonly navigateByUrl = vi.fn(async (url: string) => {
    this.arriveAt(url);
    return true;
  });

  /** A navigation the guest triggered (or the stubbed one completing). */
  arriveAt(url: string): void {
    this.url = url;
    this.navigationId += 1;
    this.events.next(new NavigationEnd(this.navigationId, url, url));
  }
}

describe('GuestStateRoutingService', () => {
  let router: RouterStub;
  let token: WritableSignal<string | null>;
  let config: WritableSignal<PropertyConfig | null>;
  let occupancy: WritableSignal<OccupancyState | null>;

  /** A loaded config: only its non-null-ness is read by the service. */
  const LOADED = {} as PropertyConfig;

  function setup(options: {
    token?: string | null;
    config?: PropertyConfig | null;
    occupancy?: OccupancyState | null;
  }): void {
    router = new RouterStub();
    // `in`, not `??`: `null` is a meaningful value for all three axes.
    token = signal<string | null>('token' in options ? (options.token ?? null) : 'device-token');
    config = signal<PropertyConfig | null>('config' in options ? (options.config ?? null) : LOADED);
    occupancy = signal<OccupancyState | null>(
      'occupancy' in options ? (options.occupancy ?? null) : OccupancyState.Occupied,
    );

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: Router, useValue: router },
        { provide: DeviceTokenStore, useValue: { token: token.asReadonly() } },
        {
          provide: GuestDataService,
          useValue: { config: config.asReadonly(), occupancy: occupancy.asReadonly() },
        },
      ],
    });
    TestBed.inject(GuestStateRoutingService); // instantiates the effect
    TestBed.tick();
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('leaves a healthy kiosk where it is', () => {
    setup({});
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('a runtime 401 (token wiped) moves the kiosk to /pairing', () => {
    setup({});

    token.set(null);
    TestBed.tick();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/pairing');
  });

  it('an occupancy flip to VACANT moves the kiosk to /standby', () => {
    setup({});

    occupancy.set(OccupancyState.Vacant);
    TestBed.tick();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/standby');
  });

  it('restores the route the guest was on when standby releases the kiosk', () => {
    setup({});
    router.arriveAt('/einstellungen'); // the guest navigated there

    occupancy.set(OccupancyState.Vacant);
    TestBed.tick();
    expect(router.url).toBe('/standby');

    occupancy.set(OccupancyState.Occupied);
    TestBed.tick();

    expect(router.navigateByUrl).toHaveBeenLastCalledWith('/einstellungen');
  });

  it('losing everything renderable moves the kiosk to /connection, and healing brings it back', () => {
    setup({});
    router.arriveAt('/info');

    config.set(null);
    TestBed.tick();
    expect(router.url).toBe('/connection');

    config.set(LOADED);
    TestBed.tick();

    expect(router.navigateByUrl).toHaveBeenLastCalledWith('/info');
  });

  it('an unpaired kiosk is never routed to /connection — pairing wins', () => {
    setup({ token: null, config: null });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/pairing');
    expect(router.navigateByUrl).not.toHaveBeenCalledWith('/connection');
  });

  it('never navigates to the state screen it is already on', () => {
    setup({ occupancy: OccupancyState.Vacant });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/standby');

    router.navigateByUrl.mockClear();
    occupancy.set(OccupancyState.Vacant); // a poll re-confirming vacancy
    TestBed.tick();

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('a state screen is never recorded as the route to return to', () => {
    setup({});
    router.arriveAt('/einstellungen');

    // Two state screens in a row: /pairing then a re-pair that lands on
    // /standby — the restore target must still be the guest's own route.
    token.set(null);
    TestBed.tick();
    token.set('fresh-token');
    occupancy.set(OccupancyState.Vacant);
    TestBed.tick();
    occupancy.set(OccupancyState.Occupied);
    TestBed.tick();

    expect(router.navigateByUrl).toHaveBeenLastCalledWith('/einstellungen');
  });
});
