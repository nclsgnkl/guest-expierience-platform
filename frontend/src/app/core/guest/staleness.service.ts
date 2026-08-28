import { Injectable, computed, inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { ClockService } from '../clock/clock.service';
import { GuestDataService } from './guest-data.service';

/**
 * How loudly the kiosk admits it is out of date
 * (guest-payload-consolidation REQ-952, constitution v1.9.0; spec:
 * docs/design/screens-inhouse/connection.md -> "The escalated staleness
 * banner").
 *
 * **A presentation property of `STALE`, never a fourth `ConnectionState`**
 * (D13, REQ-943): the state machine in `GuestDataService` is untouched — no
 * new state, no new timer, no change to the cadence or the backoff. The level
 * is derived from what already exists: the connection state, the moment of
 * last successful contact, and the minute-aligned `ClockService`.
 *
 * **Tri-state, not two booleans**: "the banner is up and the pill is not" is
 * true by construction rather than by two components agreeing.
 *
 * `staleSince()` is the moment of last successful **contact**, not of the last
 * change: `adopt()` refreshes it on a `304` as well, so a backend answering
 * `304` forever never escalates — and `adoptCache()` seeds it from the cached
 * `storedAt`, so a cold boot from a cache older than the threshold is
 * escalated immediately.
 */
export type StalenessLevel = 'NONE' | 'PILL' | 'BANNER';

/**
 * Time without successful backend contact before the pill becomes the banner
 * — 60 minutes in production (spec: Trigger), one minute under the
 * `development` build configuration, whose `fileReplacements` swap the
 * environment file (owner, 2026-08-28: the state is otherwise unreachable
 * without waiting an hour or editing a running system). The value lives in
 * `src/environments/environment.ts`, never here.
 */
export const STALE_ESCALATION_MS = environment.staleEscalationMs;

@Injectable({ providedIn: 'root' })
export class StalenessService {
  private readonly guestData = inject(GuestDataService);
  private readonly clock = inject(ClockService);

  /** Passthrough — the banner and the pill render the same timestamp. */
  readonly since = this.guestData.staleSince;

  readonly level = computed<StalenessLevel>(() => {
    const since = this.since();
    if (this.guestData.connection() !== 'STALE' || since === null) {
      return 'NONE';
    }
    return this.clock.now().getTime() - since >= STALE_ESCALATION_MS ? 'BANNER' : 'PILL';
  });
}
