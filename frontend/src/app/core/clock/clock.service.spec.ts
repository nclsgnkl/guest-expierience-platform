import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ClockService } from './clock.service';

/**
 * S31 — `ClockService` (REQ-124, REQ-114).
 *
 * Fake timers, because the whole point of this service is *when* it fires: a
 * bare `setInterval(60_000)` from load would pass a "the time updates" test
 * while showing a minute that flips at :17 past. The boundary is the
 * behaviour, so the boundary is what is asserted.
 */
describe('ClockService', () => {
  /** 16:42:20.250 — deliberately not on a boundary. */
  const START = new Date(2026, 7, 19, 16, 42, 20, 250);
  const MS_TO_BOUNDARY = 39_750; // (60 - 20) * 1000 - 250

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(START);
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });

  it('starts at the current time', () => {
    const clock = TestBed.inject(ClockService);

    expect(clock.now().getTime()).toBe(START.getTime());
  });

  it('does not tick before the minute boundary', () => {
    const clock = TestBed.inject(ClockService);

    vi.advanceTimersByTime(MS_TO_BOUNDARY - 1);

    expect(clock.now().getTime()).toBe(START.getTime());
  });

  it('ticks exactly on the minute boundary, not 60 s after load', () => {
    const clock = TestBed.inject(ClockService);

    vi.advanceTimersByTime(MS_TO_BOUNDARY);

    const first = clock.now();
    expect(first.getMinutes()).toBe(43);
    expect(first.getSeconds()).toBe(0);
    expect(first.getMilliseconds()).toBe(0);
  });

  it('waits a full minute when the app happens to start exactly on a boundary', () => {
    vi.setSystemTime(new Date(2026, 7, 19, 16, 42, 0, 0));
    const clock = TestBed.inject(ClockService);

    vi.advanceTimersByTime(59_999);
    expect(clock.now().getMinutes()).toBe(42);

    vi.advanceTimersByTime(1);
    expect(clock.now().getMinutes()).toBe(43);
  });

  it('then ticks every 60 s, staying on the boundary', () => {
    const clock = TestBed.inject(ClockService);
    vi.advanceTimersByTime(MS_TO_BOUNDARY);

    vi.advanceTimersByTime(60_000);
    expect(clock.now().getMinutes()).toBe(44);
    expect(clock.now().getSeconds()).toBe(0);

    vi.advanceTimersByTime(60_000);
    expect(clock.now().getMinutes()).toBe(45);
    expect(clock.now().getSeconds()).toBe(0);

    vi.advanceTimersByTime(59_999);
    expect(clock.now().getMinutes()).toBe(45);
  });

  it('clears the pending boundary timeout on teardown', () => {
    const clock = TestBed.inject(ClockService);
    const before = clock.now().getTime();
    expect(vi.getTimerCount()).toBe(1);

    TestBed.resetTestingModule();

    expect(vi.getTimerCount()).toBe(0);
    vi.advanceTimersByTime(10 * 60_000);
    expect(clock.now().getTime()).toBe(before);
  });

  it('clears the running interval on teardown', () => {
    const clock = TestBed.inject(ClockService);
    vi.advanceTimersByTime(MS_TO_BOUNDARY + 60_000);
    const before = clock.now().getTime();
    expect(vi.getTimerCount()).toBe(1);

    TestBed.resetTestingModule();

    expect(vi.getTimerCount()).toBe(0);
    vi.advanceTimersByTime(10 * 60_000);
    expect(clock.now().getTime()).toBe(before);
  });
});
