import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../../environments/environment';
import { DeviceTokenStore } from './device-token.store';

/**
 * REQ-505/508 — the device token store: persistence round-trip, the 401
 * wipe, and the environment-fallback precedence (storage wins, fallback only
 * when storage is empty).
 *
 * The unit-test builder compiles the DEVELOPMENT configuration (with
 * `fileReplacements`), so the imported `environment` carries the dev token
 * here. Every test pins `devDeviceToken` explicitly and restores it after —
 * no assertion depends on which file the builder picked.
 */
const STORAGE_KEY = 'gxp.device-token';

describe('DeviceTokenStore', () => {
  const originalFallback = environment.devDeviceToken;

  beforeEach(() => {
    localStorage.clear();
    environment.devDeviceToken = null;
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  });

  afterEach(() => {
    environment.devDeviceToken = originalFallback;
    localStorage.clear();
  });

  it('starts unpaired when storage is empty and no fallback is configured', () => {
    const store = TestBed.inject(DeviceTokenStore);
    expect(store.token()).toBeNull();
  });

  it('set() persists and the token survives a fresh load()', () => {
    const store = TestBed.inject(DeviceTokenStore);
    store.set('a-device-token');

    expect(store.token()).toBe('a-device-token');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('a-device-token');

    store.load(); // what a reboot does
    expect(store.token()).toBe('a-device-token');
  });

  it('clear() wipes both the signal and the storage (REQ-509)', () => {
    const store = TestBed.inject(DeviceTokenStore);
    store.set('a-device-token');

    store.clear();

    expect(store.token()).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('consults the environment fallback only when storage is empty', () => {
    environment.devDeviceToken = 'fallback-token';
    const store = TestBed.inject(DeviceTokenStore);
    expect(store.token()).toBe('fallback-token');
  });

  it('a stored token beats the environment fallback', () => {
    environment.devDeviceToken = 'fallback-token';
    localStorage.setItem(STORAGE_KEY, 'stored-token');
    const store = TestBed.inject(DeviceTokenStore);
    expect(store.token()).toBe('stored-token');
  });

  it('the fallback is NOT re-adopted after an explicit clear() within a session', () => {
    environment.devDeviceToken = 'fallback-token';
    const store = TestBed.inject(DeviceTokenStore);
    store.clear();
    // In-session: a 401 wipe must land on the pairing screen, not silently
    // retry with the same doomed fallback. (A later reload may re-consult it —
    // dev-only behavior by construction, the fallback is null in prod.)
    expect(store.token()).toBeNull();
  });
});
