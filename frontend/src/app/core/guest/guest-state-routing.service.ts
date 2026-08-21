import { Injectable, effect, inject, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { OccupancyState } from '@gxp/api-client';
import { filter } from 'rxjs';

import { DeviceTokenStore } from '../api/device-token.store';
import { GuestDataService } from './guest-data.service';

const STATE_ROUTES = ['/pairing', '/standby', '/connection'];

/**
 * Runtime state → route, both directions (guest-frontend-integration
 * REQ-509/517/520): the poll flips occupancy, a 401 wipes the token, the
 * backend heals — and the kiosk moves between /pairing, /standby,
 * /connection and the app WITHOUT any reload or touch. The guards
 * (`guest-state.guards.ts`) arbitrate initial/deep-link navigation with the
 * same precedence; this effect reacts to signal changes afterwards.
 *
 * Returning to the app restores the route the guest was on when the state
 * screen took over (OCCUPIED → previous route restored) — a standby episode
 * during a 30 s vacancy blip must not dump a guest from /einstellungen back
 * to Home.
 *
 * Instantiated by `App`'s constructor; one effect for the app's lifetime.
 */
@Injectable({ providedIn: 'root' })
export class GuestStateRoutingService {
  private readonly router = inject(Router);
  private readonly tokenStore = inject(DeviceTokenStore);
  private readonly guestData = inject(GuestDataService);

  /** The last in-app url, restored when a state screen releases the kiosk. */
  private lastShellUrl = '/';

  constructor() {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => {
        const url = event.urlAfterRedirects;
        if (!STATE_ROUTES.some((route) => url.startsWith(route))) {
          this.lastShellUrl = url;
        }
      });

    effect(() => {
      const unpaired = this.tokenStore.token() === null;
      const loaded = this.guestData.config() !== null;
      const vacant = this.guestData.occupancy() === OccupancyState.Vacant;

      untracked(() => {
        const current = this.router.url;
        const onStateRoute = STATE_ROUTES.some((route) => current.startsWith(route));
        if (unpaired) {
          if (!current.startsWith('/pairing')) {
            void this.router.navigateByUrl('/pairing');
          }
          return;
        }
        if (!loaded) {
          if (!current.startsWith('/connection') && !current.startsWith('/pairing')) {
            void this.router.navigateByUrl('/connection');
          }
          return;
        }
        if (vacant) {
          if (!current.startsWith('/standby')) {
            void this.router.navigateByUrl('/standby');
          }
          return;
        }
        if (onStateRoute) {
          void this.router.navigateByUrl(this.lastShellUrl);
        }
      });
    });
  }
}
