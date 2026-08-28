import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { ClockService } from '../clock/clock.service';
import type { ConnectionState } from './guest-data.service';
import { GuestDataService } from './guest-data.service';
import { STALE_ESCALATION_MS, StalenessService } from './staleness.service';

/**
 * S-stale — the escalation level (REQ-952, REQ-943). Collaborators are DI
 * doubles built from writable signals (frontend/CLAUDE.md -> Tests): what is
 * under test is the derivation, not `GuestDataService`'s machine.
 */
const NOW = Date.parse('2026-08-27T14:00:00');
const MINUTE = 60_000;
/**
 * Every case below is expressed as an offset from the **configured**
 * threshold, never as 59/60 minutes: the value comes from the environment
 * (`STALE_ESCALATION_MS`) and the `development` configuration — the one these
 * tests build under — shortens it to a minute. A literal "59 minutes is still
 * the pill" would assert the production number against a dev build and fail,
 * and worse, would keep passing for the wrong reason if the number moved.
 */
const THRESHOLD = STALE_ESCALATION_MS;

describe('StalenessService', () => {
  const connection = signal<ConnectionState>('ONLINE');
  const staleSince = signal<number | null>(null);
  const now = signal(new Date(NOW));
  let staleness: StalenessService;

  beforeEach(() => {
    connection.set('ONLINE');
    staleSince.set(null);
    now.set(new Date(NOW));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: GuestDataService, useValue: { connection, staleSince } },
        { provide: ClockService, useValue: { now } },
      ],
    });
    staleness = TestBed.inject(StalenessService);
  });

  /** Stale since `ms` ago, as the machine would have left it. */
  function staleFor(ms: number): void {
    connection.set('STALE');
    staleSince.set(NOW - ms);
  }

  it('ONLINE is NONE — no hint of any kind', () => {
    expect(staleness.level()).toBe('NONE');
  });

  it('DISCONNECTED is NONE — the full-screen connection panel names it already', () => {
    connection.set('DISCONNECTED');
    staleSince.set(NOW - 2 * THRESHOLD);

    expect(staleness.level()).toBe('NONE');
  });

  it('one millisecond short of the threshold is still the discreet pill', () => {
    staleFor(THRESHOLD - 1);

    expect(staleness.level()).toBe('PILL');
  });

  it('reaching the threshold escalates to the banner', () => {
    staleFor(THRESHOLD);

    expect(staleness.level()).toBe('BANNER');
  });

  it('escalates on the clock tick alone — no new timer, nothing re-fetched', () => {
    staleFor(THRESHOLD - 1);
    expect(staleness.level()).toBe('PILL');

    now.set(new Date(NOW + MINUTE)); // the minute-aligned ClockService ticks

    expect(staleness.level()).toBe('BANNER');
  });

  it('two hours of 304s stay NONE — the clock measures contact, not change', () => {
    connection.set('ONLINE');
    staleSince.set(null);
    now.set(new Date(NOW + 120 * MINUTE));

    expect(staleness.level()).toBe('NONE');
  });

  it('a cold boot from a cache older than the threshold is escalated immediately', () => {
    // `adoptCache()` seeds staleSince from the cached record's storedAt.
    connection.set('STALE');
    staleSince.set(NOW - STALE_ESCALATION_MS - 1);

    expect(staleness.level()).toBe('BANNER');
  });

  it('a stale episode is always exactly one level — never both, never neither', () => {
    // The tri-state is the mechanism: pill and banner read the same signal, so
    // "banner up ⇒ pill down" holds by construction rather than by agreement.
    const ages = [0, THRESHOLD / 2, THRESHOLD - 1, THRESHOLD, THRESHOLD + 1, 10 * THRESHOLD];
    const levels = ages.map((ms) => {
      staleFor(ms);
      return staleness.level();
    });

    expect(levels).toEqual(['PILL', 'PILL', 'PILL', 'BANNER', 'BANNER', 'BANNER']);
  });

  it('the escalation is a presentation level — ConnectionState still has three members', () => {
    const STATES = ['ONLINE', 'STALE', 'DISCONNECTED'] as const;
    type Same<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;
    // Compile-time (REQ-943): a fourth state — or a removed one — makes this
    // `false` and the assignment fails to type-check.
    const unionIsUnchanged: Same<ConnectionState, (typeof STATES)[number]> = true;

    expect(unionIsUnchanged).toBe(true);
    expect(STATES).toHaveLength(3);
  });
});
