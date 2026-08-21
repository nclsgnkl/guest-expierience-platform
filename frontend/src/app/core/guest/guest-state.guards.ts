import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';
import { OccupancyState } from '@gxp/api-client';

import { DeviceTokenStore } from '../api/device-token.store';
import { GuestDataService } from './guest-data.service';

/**
 * Route guards for the three full-screen states (guest-frontend-integration
 * REQ-506/517/520, 02-design.md §5). They arbitrate the INITIAL url (deep
 * links, reloads); runtime flips (poll-driven occupancy, 401, healing) are
 * `GuestStateRoutingService`'s effect. Both read the same signals, so they
 * cannot disagree.
 *
 * Precedence, everywhere: unpaired → /pairing; paired but nothing to render
 * → /connection; vacant → /standby; else the app. A paired kiosk NEVER lands
 * on /pairing because the network flapped (REQ-520) — only a 401 wipes the
 * token, and then the token check routes it.
 */
export const shellGuard: CanActivateFn = () => {
  const state = read();
  if (state.unpaired) {
    return state.router.parseUrl('/pairing');
  }
  if (!state.loaded) {
    return state.router.parseUrl('/connection');
  }
  if (state.vacant) {
    return state.router.parseUrl('/standby');
  }
  return true;
};

export const pairingGuard: CanActivateFn = () => {
  const state = read();
  return state.unpaired ? true : state.router.parseUrl('/');
};

export const standbyGuard: CanActivateFn = () => {
  const state = read();
  if (state.unpaired) {
    return state.router.parseUrl('/pairing');
  }
  if (!state.loaded) {
    return state.router.parseUrl('/connection');
  }
  return state.vacant ? true : state.router.parseUrl('/');
};

export const connectionGuard: CanActivateFn = () => {
  const state = read();
  if (state.unpaired) {
    return state.router.parseUrl('/pairing');
  }
  if (!state.loaded) {
    return true;
  }
  return state.vacant ? state.router.parseUrl('/standby') : state.router.parseUrl('/');
};

function read() {
  const guestData = inject(GuestDataService);
  return {
    router: inject(Router),
    unpaired: inject(DeviceTokenStore).token() === null,
    loaded: guestData.config() !== null,
    vacant: guestData.occupancy() === OccupancyState.Vacant,
  };
}
