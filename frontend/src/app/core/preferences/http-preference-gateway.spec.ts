import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Configuration } from '@gxp/api-client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { GuestDataService } from '../guest/guest-data.service';
import { HttpPreferenceGateway } from './http-preference-gateway';
import type { StoredPreferences } from './preference-gateway';

/**
 * S39 (part 1), HTTP edition (REQ-518, D8) — the transport that replaced the
 * deleted `SessionPreferenceGateway`. Policy (coalescing, bounded retry,
 * late-arrival) stays proven in `preferences.persistence.spec.ts` against the
 * gateway INTERFACE; here only the wire behavior is asserted: the
 * whole-record PUT, the silent-normal 400/409, the 401 wipe hand-off, and
 * that load()/clear() issue no HTTP at all.
 */
describe('HttpPreferenceGateway', () => {
  const handleUnauthorized = vi.fn();
  let preferenceOverride: StoredPreferences | null = null;

  beforeEach(() => {
    handleUnauthorized.mockReset();
    preferenceOverride = null;
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        // Same-origin base path, as provideGuestApi() wires it in the app.
        { provide: Configuration, useValue: new Configuration({ basePath: '' }) },
        HttpPreferenceGateway,
        {
          provide: GuestDataService,
          useValue: { preferenceOverride: () => preferenceOverride, handleUnauthorized },
        },
      ],
    });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  function gateway(): HttpPreferenceGateway {
    return TestBed.inject(HttpPreferenceGateway);
  }

  it('save() PUTs the whole record to /api/guest/preferences', async () => {
    const pending = gateway().save({ language: 'en', appearance: 'dark' });

    const request = TestBed.inject(HttpTestingController).expectOne('/api/guest/preferences');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ language: 'en', appearance: 'dark' });
    request.flush(null, { status: 204, statusText: 'No Content' });

    await expect(pending).resolves.toBeUndefined();
  });

  it('409 room-vacant is silent-normal — resolves, nothing retried, nothing surfaced', async () => {
    const pending = gateway().save({ language: 'en' });
    TestBed.inject(HttpTestingController)
      .expectOne('/api/guest/preferences')
      .flush({ code: 'room-vacant' }, { status: 409, statusText: 'Conflict' });

    await expect(pending).resolves.toBeUndefined();
    expect(handleUnauthorized).not.toHaveBeenCalled();
  });

  it('400 is silent-normal too', async () => {
    const pending = gateway().save({ language: 'xx' });
    TestBed.inject(HttpTestingController)
      .expectOne('/api/guest/preferences')
      .flush({}, { status: 400, statusText: 'Bad Request' });

    await expect(pending).resolves.toBeUndefined();
  });

  it('401 hands off to the kiosk-wide wipe and resolves (no doomed retry)', async () => {
    const pending = gateway().save({ language: 'en' });
    TestBed.inject(HttpTestingController)
      .expectOne('/api/guest/preferences')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    await expect(pending).resolves.toBeUndefined();
    expect(handleUnauthorized).toHaveBeenCalledOnce();
  });

  it('a 5xx rejects, so PreferencesService applies its bounded retry', async () => {
    const pending = gateway().save({ language: 'en' });
    TestBed.inject(HttpTestingController)
      .expectOne('/api/guest/preferences')
      .flush({}, { status: 503, statusText: 'Service Unavailable' });

    await expect(pending).rejects.toBeTruthy();
  });

  it('load() answers from the content payload — no second GET (D8)', async () => {
    preferenceOverride = { language: 'en' };
    await expect(gateway().load()).resolves.toEqual({ language: 'en' });
    // verify() in afterEach proves no HTTP happened
  });

  it('clear() is a client-side no-op (the override is stay-anchored server-side)', async () => {
    await expect(gateway().clear()).resolves.toBeUndefined();
  });
});
