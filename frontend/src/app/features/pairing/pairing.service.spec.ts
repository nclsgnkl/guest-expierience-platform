import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { DeviceTokenStore } from '../../core/api/device-token.store';
import { provideGuestApi } from '../../core/api/provide-guest-api';
import { GuestBootstrapService } from '../../core/guest/guest-bootstrap.service';
import { PairingService } from './pairing.service';

/**
 * S-pair — the pairing state machine (REQ-506/507, 02-design.md §3): every
 * transition incl. claim-once (token stored, bootstrap run, navigation
 * triggered), unattended renewal on 404, the offline resume-same-code rule —
 * and that no storage write ever contains the code or the poll secret.
 */
const CREATED = {
  code: 'ABCDEFGH',
  pollSecret: 'super-secret-poll-value',
  expiresAt: new Date(Date.now() + 600_000).toISOString(),
};

/**
 * The `Date` header every real HTTP response carries — the service reads it to
 * count the code's lifetime on the SERVER's clock (🟡-3). `serverSkewMs`
 * models a kiosk clock that disagrees with the server's.
 */
function created(serverSkewMs = 0, lifetimeMs = 600_000) {
  const serverNow = Date.now() + serverSkewMs;
  return {
    body: { ...CREATED, expiresAt: new Date(serverNow + lifetimeMs).toISOString() },
    options: { headers: { Date: new Date(serverNow).toUTCString() } },
  };
}

describe('PairingService', () => {
  const run = vi.fn(() => Promise.resolve());
  const navigateByUrl = vi.fn(() => Promise.resolve(true));
  let http: HttpTestingController;
  let service: PairingService;

  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    environment.devDeviceToken = null;
    run.mockClear();
    navigateByUrl.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideGuestApi(),
        provideHttpClientTesting(),
        PairingService,
        { provide: GuestBootstrapService, useValue: { run } },
        { provide: Router, useValue: { navigateByUrl } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(PairingService);
  });

  afterEach(() => {
    TestBed.resetTestingModule(); // stops the machine's timer chain
    http.verify();
    vi.useRealTimers();
    localStorage.clear();
  });

  async function settle(): Promise<void> {
    await vi.advanceTimersByTimeAsync(0);
  }

  async function startWaiting(): Promise<void> {
    service.start();
    await settle();
    const response = created();
    http.expectOne('/api/pairing/requests').flush(response.body, response.options);
    await settle();
  }

  it('CREATING → WAITING: shows the code and polls at the 3 s cadence with the secret header', async () => {
    await startWaiting();
    expect(service.code()).toBe('ABCDEFGH');
    expect(service.status()).toBe('waiting');

    await vi.advanceTimersByTimeAsync(3_000);
    const poll = http.expectOne('/api/pairing/requests/ABCDEFGH');
    expect(poll.request.headers.get('GXP-Pairing-Secret')).toBe('super-secret-poll-value');
    poll.flush(null, { status: 202, statusText: 'Accepted' });
    await settle();
    expect(service.status()).toBe('waiting');

    await vi.advanceTimersByTimeAsync(3_000);
    http.expectOne('/api/pairing/requests/ABCDEFGH').flush(null, { status: 202, statusText: 'Accepted' });
  });

  it('claim-once: 200 stores the token, boots the app and navigates — no reload', async () => {
    await startWaiting();
    await vi.advanceTimersByTimeAsync(3_000);
    http.expectOne('/api/pairing/requests/ABCDEFGH').flush({
      deviceToken: 'the-device-token',
      deviceName: 'Kiosk 1',
      roomCode: '1',
    });
    await settle();

    expect(TestBed.inject(DeviceTokenStore).token()).toBe('the-device-token');
    expect(run).toHaveBeenCalledOnce();
    expect(navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('no storage write ever contains the code or the poll secret (REQ-507)', async () => {
    await startWaiting();
    await vi.advanceTimersByTimeAsync(3_000);
    http.expectOne('/api/pairing/requests/ABCDEFGH').flush({
      deviceToken: 'the-device-token',
      deviceName: 'Kiosk 1',
      roomCode: '1',
    });
    await settle();

    for (let i = 0; i < localStorage.length; i += 1) {
      const value = localStorage.getItem(localStorage.key(i)!) ?? '';
      expect(value).not.toContain('ABCDEFGH');
      expect(value).not.toContain('super-secret-poll-value');
    }
  });

  it('404 on poll (expired/consumed) requests a fresh code unattended', async () => {
    await startWaiting();
    await vi.advanceTimersByTimeAsync(3_000);
    http
      .expectOne('/api/pairing/requests/ABCDEFGH')
      .flush({}, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(service.status()).toBe('renewing');
    http.expectOne('/api/pairing/requests').flush({ ...CREATED, code: 'ZZZZYYYY' });
    await settle();
    expect(service.code()).toBe('ZZZZYYYY');
    expect(service.status()).toBe('waiting');
  });

  it('an HTTP error at create takes the same offline path as a dead network', async () => {
    // The contract has no throttled answer any more (the `429` and the whole
    // `cooldown` state went with the backend's rate limiter, 2026-08-24), so
    // there is exactly ONE create-failure path left and every status code
    // must land in it — including a 429 from something in front of the app.
    service.start();
    await settle();
    http
      .expectOne('/api/pairing/requests')
      .flush({}, { status: 500, statusText: 'Internal Server Error' });
    await settle();
    expect(service.status()).toBe('offline');

    await vi.advanceTimersByTimeAsync(4_900);
    http.expectNone('/api/pairing/requests');
    await vi.advanceTimersByTimeAsync(100);
    http.expectOne('/api/pairing/requests').flush(CREATED);
    await settle();
    expect(service.status()).toBe('waiting');
  });

  it('network loss at create retries with 5 s → doubling backoff', async () => {
    service.start();
    await settle();
    http.expectOne('/api/pairing/requests').error(new ProgressEvent('error'));
    await settle();
    expect(service.status()).toBe('offline');

    await vi.advanceTimersByTimeAsync(5_000);
    http.expectOne('/api/pairing/requests').error(new ProgressEvent('error'));
    await settle();

    await vi.advanceTimersByTimeAsync(5_000);
    http.expectNone('/api/pairing/requests'); // second wait is 10 s now
    await vi.advanceTimersByTimeAsync(5_000);
    http.expectOne('/api/pairing/requests').flush(CREATED);
    await settle();
    expect(service.status()).toBe('waiting');
  });

  it('network loss while WAITING resumes the SAME code until it expires', async () => {
    await startWaiting();
    await vi.advanceTimersByTimeAsync(3_000);
    http.expectOne('/api/pairing/requests/ABCDEFGH').error(new ProgressEvent('error'));
    await settle();
    expect(service.status()).toBe('offline');
    expect(service.code()).toBe('ABCDEFGH'); // still displayed

    await vi.advanceTimersByTimeAsync(5_000);
    // Resumes polling the same code — no new create.
    http.expectOne('/api/pairing/requests/ABCDEFGH').flush(null, { status: 202, statusText: 'Accepted' });
    await settle();
    expect(service.status()).toBe('waiting');
  });

  it('an expired code after network loss falls back to CREATING', async () => {
    service.start();
    await settle();
    const first = created(0, 4_000);
    http.expectOne('/api/pairing/requests').flush(first.body, first.options);
    await settle();

    await vi.advanceTimersByTimeAsync(3_000);
    http.expectOne('/api/pairing/requests/ABCDEFGH').error(new ProgressEvent('error'));
    await settle();

    // 5 s backoff passes the 4 s expiry → a fresh code is requested.
    await vi.advanceTimersByTimeAsync(5_000);
    const second = created();
    http.expectOne('/api/pairing/requests').flush(second.body, second.options);
    await settle();
    expect(service.status()).toBe('waiting');
  });

  it('a kiosk clock running an hour ahead does not expire a fresh code (🟡-3)', async () => {
    service.start();
    await settle();
    // The server says "created now, expires in 10 minutes"; this device's
    // clock is an hour ahead, so the ABSOLUTE expiresAt is already in its
    // past. Counting the lifetime instead keeps the code alive.
    const response = created(-3_600_000);
    http.expectOne('/api/pairing/requests').flush(response.body, response.options);
    await settle();

    await vi.advanceTimersByTimeAsync(3_000);
    // Polls the SAME code — no premature renewal (which used to loop
    // create → 3 s → create without ever settling on a usable code).
    http
      .expectOne('/api/pairing/requests/ABCDEFGH')
      .flush(null, { status: 202, statusText: 'Accepted' });
    await settle();
    expect(service.status()).toBe('waiting');
    expect(service.code()).toBe('ABCDEFGH');
  });

  it('without a usable Date header no local deadline is invented — the 404 renews', async () => {
    service.start();
    await settle();
    // No Date header at all: any local comparison would be a guess.
    http
      .expectOne('/api/pairing/requests')
      .flush({ ...CREATED, expiresAt: new Date(Date.now() - 600_000).toISOString() });
    await settle();

    await vi.advanceTimersByTimeAsync(3_000);
    // Still polls (no proactive renewal) — and the server's 404 is what
    // drives the fresh code.
    http
      .expectOne('/api/pairing/requests/ABCDEFGH')
      .flush(null, { status: 404, statusText: 'Not Found' });
    await settle();
    const renewed = created();
    http.expectOne('/api/pairing/requests').flush(renewed.body, renewed.options);
    await settle();
    expect(service.status()).toBe('waiting');
  });
});
