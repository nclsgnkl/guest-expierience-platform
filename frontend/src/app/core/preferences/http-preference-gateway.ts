import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { GuestService } from '@gxp/api-client';
import { firstValueFrom } from 'rxjs';

import { GuestDataService } from '../guest/guest-data.service';
import type { PreferenceGateway, StoredPreferences } from './preference-gateway';

/**
 * The real preference transport (guest-frontend-integration REQ-518, D8
 * approved 2026-08-20) — the gateway `SessionPreferenceGateway`'s design
 * promised and was deleted for. Policy (validation, late-arrival rule,
 * optimistic update, coalescing, bounded retry) lives untouched in
 * `PreferencesService`; this class is deliberately dumb:
 *
 *   - `save()` → `PUT /api/guest/preferences`, the whole record.
 *     `400`/`409 room-vacant` are **silent-normal** (resolved, not retried):
 *     a vacant room simply has no stay to anchor the choice on, and the
 *     screen keeps the choice locally as designed. A `401` triggers the
 *     kiosk-wide wipe (REQ-509) and resolves — retrying a dead token is
 *     noise. Anything else rejects, so `PreferencesService`'s bounded retry
 *     applies.
 *   - `load()` → **no HTTP call**: the stay's raw override arrives inside the
 *     session payload (02-design.md §2/§5), handed over by
 *     `GuestDataService`. One fetch feeds everything at boot.
 *   - `clear()` → client-side no-op (02-design.md §2): the override is
 *     stay-anchored server-side and clears at stay change by construction.
 *
 * Nothing here logs (02-design.md §6.5; constitution principle 3).
 */
@Injectable()
export class HttpPreferenceGateway implements PreferenceGateway {
  private readonly api = inject(GuestService);
  private readonly guestData = inject(GuestDataService);

  load(): Promise<StoredPreferences | null> {
    return Promise.resolve(this.guestData.sessionPreference());
  }

  async save(value: StoredPreferences): Promise<void> {
    try {
      await firstValueFrom(
        this.api.putGuestPreferences({
          language: value.language,
          appearance: value.appearance,
        }),
      );
    } catch (error) {
      if (error instanceof HttpErrorResponse) {
        if (error.status === 400 || error.status === 409) {
          return; // silent-normal (vacant room / rejected value) — REQ-518
        }
        if (error.status === 401) {
          this.guestData.handleUnauthorized();
          return;
        }
      }
      throw error; // network/5xx → the service's bounded retry
    }
  }

  clear(): Promise<void> {
    return Promise.resolve();
  }
}
