import { DestroyRef, Injectable, inject, signal } from '@angular/core';

/**
 * The current time as a signal, updated **on the minute boundary** (REQ-114).
 *
 * Three things this gets right that a naive implementation gets wrong
 * (02-design.md §8.5):
 *
 *   - **Zoneless is a non-issue when state is a signal.** Writing `_now`
 *     marks the consuming template dirty and schedules change detection —
 *     no `NgZone`, no `ChangeDetectorRef`, no `markForCheck()`. Reaching for
 *     any of those here would be the bug, not the fix.
 *   - **Minute alignment.** A bare `setInterval(60_000)` from load ticks at
 *     whatever second the app happened to start, so the display is wrong by
 *     up to 59 s. The first timer fires at the next `:00`.
 *   - **Teardown.** Both timers are cleared through `DestroyRef`, so tests do
 *     not leak them between cases.
 *
 * Formatting is not this service's business: the status bar formats through a
 * memoised `Intl.DateTimeFormat` for the active language's locale.
 */
@Injectable({ providedIn: 'root' })
export class ClockService {
  private readonly destroyRef = inject(DestroyRef);
  private readonly _now = signal(new Date());

  readonly now = this._now.asReadonly();

  constructor() {
    let interval: ReturnType<typeof setInterval> | undefined;

    const timeout = setTimeout(() => {
      this._now.set(new Date());
      interval = setInterval(() => this._now.set(new Date()), 60_000);
    }, msUntilNextMinute(new Date()));

    this.destroyRef.onDestroy(() => {
      clearTimeout(timeout);
      if (interval !== undefined) {
        clearInterval(interval);
      }
    });
  }
}

/** Wall-clock, not `Date.now() % 60_000`: the seconds are what must hit zero. */
function msUntilNextMinute(from: Date): number {
  return (60 - from.getSeconds()) * 1_000 - from.getMilliseconds();
}
