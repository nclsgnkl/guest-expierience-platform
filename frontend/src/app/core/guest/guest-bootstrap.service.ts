import { Injectable, effect, inject, untracked } from '@angular/core';

import { DeviceTokenStore } from '../api/device-token.store';
import { PropertyContentService } from '../content/property-content.service';
import { PreferencesService } from '../preferences/preferences.service';
import { GuestDataService } from './guest-data.service';

/**
 * The bootstrap sequence (guest-frontend-integration REQ-518/509/520,
 * 02-design.md §5) — one place, because it runs twice: from the app
 * initializer at start, and again after a pairing claim (the kiosk enters
 * the app **without a reload**, REQ-506).
 *
 * Branches:
 *   1. no token → resolve immediately; the pairing guard owns the router.
 *   2. token → `GuestDataService.bootstrap()` (live | cache | null), then —
 *      only when content arrived — defaults ⊕ stored-preference hydration
 *      **before first paint**, preserving the REQ-126 no-flash structure:
 *      the first value ever written to `<html data-theme>`/`<html lang>` is
 *      already the final one.
 *   3. nothing arrived → resolve; the connection guard lands the router on
 *      `/connection` and `GuestDataService` keeps retrying.
 *
 * The constructor effect re-syncs on every later session adoption (poll
 * 200): defaults may have been edited by the manager, and a preference
 * changed server-side is re-hydrated under `PreferencesService`'s existing
 * never-overwrite-a-fresh-choice rule — `hydrate()` is idempotent by that
 * rule, so calling it per adoption is safe and policy stays untouched.
 */
@Injectable({ providedIn: 'root' })
export class GuestBootstrapService {
  private readonly tokenStore = inject(DeviceTokenStore);
  private readonly guestData = inject(GuestDataService);
  private readonly content = inject(PropertyContentService);
  private readonly preferences = inject(PreferencesService);

  private applied = false;

  constructor() {
    effect(() => {
      const session = this.guestData.session();
      if (session === null || !this.applied) {
        return; // initial application happens inside run(), before first paint
      }
      untracked(() => {
        this.preferences.applyPropertyDefaults(this.content.settingsConfig());
        void this.preferences.hydrate();
      });
    });
  }

  async run(): Promise<void> {
    if (this.tokenStore.token() === null) {
      return;
    }
    await this.guestData.bootstrap();
    if (this.content.loaded()) {
      this.preferences.applyPropertyDefaults(this.content.settingsConfig());
      await this.preferences.hydrate();
    }
    this.applied = true;
  }
}
