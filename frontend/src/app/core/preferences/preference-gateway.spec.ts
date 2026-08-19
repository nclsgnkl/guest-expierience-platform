import { beforeEach, describe, expect, it } from 'vitest';

import type { StoredPreferences } from './preference-gateway';
import { SessionPreferenceGateway } from './session-preference-gateway';

/**
 * S39 (part 1) — the **stand-in** gateway (REQ-121, REQ-122, REQ-127).
 *
 * This is the transport, and it is deliberately dumb: it stores and returns a
 * record, and every rule about *what may be applied* lives above it in
 * `PreferencesService` (`preferences.persistence.spec.ts`). What is asserted
 * here is only what would otherwise let the day HTTP arrives break something:
 * the record's shape, the namespaced key, and that it never answers
 * synchronously.
 *
 * `SessionPreferenceGateway` is deleted, not adapted, when the HTTP gateway
 * lands — and so is this file.
 */
const STORAGE_KEY = 'gxp.preferences';

describe('SessionPreferenceGateway', () => {
  let gateway: SessionPreferenceGateway;

  beforeEach(() => {
    sessionStorage.clear();
    gateway = new SessionPreferenceGateway();
  });

  it('reads an absent record as null', async () => {
    await expect(gateway.load()).resolves.toBeNull();
  });

  it('round-trips a whole record through sessionStorage', async () => {
    await gateway.save({ language: 'en', appearance: 'dark' });

    await expect(gateway.load()).resolves.toEqual({ language: 'en', appearance: 'dark' });
  });

  it('stores under exactly one namespaced key and writes nothing else', async () => {
    await gateway.save({ language: 'en', appearance: 'dark' });

    expect(Object.keys(sessionStorage)).toEqual([STORAGE_KEY]);
  });

  it('stores nothing but the two fields — no identifier of any kind (REQ-127)', async () => {
    await gateway.save({ language: 'en', appearance: 'dark' });

    const stored = JSON.parse(sessionStorage.getItem(STORAGE_KEY)!) as Record<string, unknown>;
    expect(Object.keys(stored).sort()).toEqual(['appearance', 'language']);
  });

  it('writes the whole record, replacing the previous one rather than merging', async () => {
    await gateway.save({ language: 'en', appearance: 'dark' });
    await gateway.save({ appearance: 'light' });

    await expect(gateway.load()).resolves.toEqual({ appearance: 'light' });
  });

  it('never resolves synchronously — an inline answer would hide ordering bugs', async () => {
    await gateway.save({ language: 'en' });

    const order: string[] = [];
    const loaded = gateway.load().then(() => order.push('load'));
    const saved = gateway.save({ language: 'de' }).then(() => order.push('save'));
    const cleared = gateway.clear().then(() => order.push('clear'));
    order.push('caller');

    await Promise.all([loaded, saved, cleared]);

    expect(order[0]).toBe('caller');
  });

  it('does not write while the save promise is still pending', async () => {
    const pending = gateway.save({ language: 'en' });
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();

    await pending;
    expect(sessionStorage.getItem(STORAGE_KEY)).not.toBeNull();
  });

  it('reads a malformed record as null, never as a partial', async () => {
    sessionStorage.setItem(STORAGE_KEY, '{ not json');

    await expect(gateway.load()).resolves.toBeNull();
  });

  it('reads a well-formed but non-object record as null', async () => {
    sessionStorage.setItem(STORAGE_KEY, '42');

    await expect(gateway.load()).resolves.toBeNull();
  });

  it('drops a field of the wrong type instead of returning it', async () => {
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ language: 7, appearance: 'chartreuse', stayId: 'abc' }),
    );

    const loaded = (await gateway.load()) as StoredPreferences;
    expect(loaded).toEqual({});
  });

  it('clear() removes the record and a later load reads nothing', async () => {
    await gateway.save({ language: 'en' });

    await gateway.clear();

    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
    await expect(gateway.load()).resolves.toBeNull();
  });
});
