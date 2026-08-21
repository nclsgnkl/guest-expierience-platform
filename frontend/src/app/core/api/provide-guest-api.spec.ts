import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { GuestService } from '@gxp/api-client';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { DeviceTokenStore } from './device-token.store';
import { provideGuestApi } from './provide-guest-api';

/**
 * REQ-501 — the client wiring: a `GuestService` call is same-origin
 * (`basePath: ''`) and carries `Authorization: Bearer <token>` resolved from
 * the store **per request** (verify-at-implementation (b) of
 * 02-design.md §6 — a token change is effective on the next call).
 */
describe('provideGuestApi', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideGuestApi(), provideHttpClientTesting()],
    });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    localStorage.clear();
  });

  it('sends the stored token as the bearer of a same-origin /api call', () => {
    TestBed.inject(DeviceTokenStore).set('token-one');
    const http = TestBed.inject(HttpTestingController);

    TestBed.inject(GuestService).getGuestSession().subscribe();

    const request = http.expectOne('/api/guest/session');
    expect(request.request.headers.get('Authorization')).toBe('Bearer token-one');
    request.flush({});
  });

  it('resolves the credential at call time — a new token is on the very next request', () => {
    const store = TestBed.inject(DeviceTokenStore);
    const http = TestBed.inject(HttpTestingController);
    const service = TestBed.inject(GuestService);

    store.set('token-one');
    service.getGuestSession().subscribe();
    http.expectOne((r) => r.headers.get('Authorization') === 'Bearer token-one').flush({});

    store.set('token-two');
    service.getGuestSession().subscribe();
    http.expectOne((r) => r.headers.get('Authorization') === 'Bearer token-two').flush({});
  });

  it('sends no Authorization header after clear()', () => {
    const store = TestBed.inject(DeviceTokenStore);
    store.set('token-one');
    store.clear();
    const http = TestBed.inject(HttpTestingController);

    TestBed.inject(GuestService).getGuestSession().subscribe();

    const request = http.expectOne('/api/guest/session');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });
});
