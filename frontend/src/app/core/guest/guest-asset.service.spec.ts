import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { DeviceTokenStore } from '../api/device-token.store';
import { provideGuestApi } from '../api/provide-guest-api';
import { GuestAssetService } from './guest-asset.service';

/**
 * S-asset — authorized blob delivery (REQ-513): the Authorization header on
 * the asset GET, the object-URL lifecycle (create / revoke on teardown), the
 * per-id memo (one fetch per id) and the 404 → null fallback path. jsdom has
 * no `URL.createObjectURL`; the stub records calls instead.
 */
describe('GuestAssetService', () => {
  const created: string[] = [];
  const revoked: string[] = [];
  let counter = 0;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    environment.devDeviceToken = null;
    created.length = 0;
    revoked.length = 0;
    counter = 0;
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: () => {
        const url = `blob:test-${(counter += 1)}`;
        created.push(url);
        return url;
      },
      revokeObjectURL: (url: string) => revoked.push(url),
    });
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideGuestApi(), provideHttpClientTesting()],
    });
    TestBed.inject(DeviceTokenStore).set('a-device-token');
    // Captured here: the destroy test resets the TestBed, after which the
    // controller could no longer be injected in afterEach.
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('fetches with the device bearer and exposes the object URL as a signal', () => {
    const service = TestBed.inject(GuestAssetService);
    const url = service.url(7);
    expect(url()).toBeNull(); // not yet arrived

    const request = TestBed.inject(HttpTestingController).expectOne('/api/guest/assets/7');
    expect(request.request.headers.get('Authorization')).toBe('Bearer a-device-token');
    request.flush(new Blob(['x'], { type: 'image/png' }));

    expect(url()).toBe('blob:test-1');
  });

  it('memoizes per id — a second read triggers no second fetch', () => {
    const service = TestBed.inject(GuestAssetService);
    service.url(7);
    TestBed.inject(HttpTestingController)
      .expectOne('/api/guest/assets/7')
      .flush(new Blob(['x'], { type: 'image/png' }));

    expect(service.url(7)()).toBe('blob:test-1');
    TestBed.inject(HttpTestingController).expectNone('/api/guest/assets/7');
  });

  it('a 404 resolves to null — components fall back, never a broken image', () => {
    const service = TestBed.inject(GuestAssetService);
    const url = service.url(9);
    TestBed.inject(HttpTestingController)
      .expectOne('/api/guest/assets/9')
      .flush(new Blob(), { status: 404, statusText: 'Not Found' });

    expect(url()).toBeNull();
  });

  it('revokes every object URL on destroy (24/7 kiosk — no slow leak)', () => {
    const service = TestBed.inject(GuestAssetService);
    service.url(7);
    TestBed.inject(HttpTestingController)
      .expectOne('/api/guest/assets/7')
      .flush(new Blob(['x'], { type: 'image/png' }));
    expect(created).toEqual(['blob:test-1']);

    TestBed.resetTestingModule(); // destroys the root injector → DestroyRef teardown

    expect(revoked).toEqual(['blob:test-1']);
  });
});
