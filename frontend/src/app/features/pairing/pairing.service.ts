import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  PairingService as PairingApi,
  type PairingCompleted,
  type PairingRequestCreated,
} from '@gxp/api-client';
import { firstValueFrom } from 'rxjs';

import { DeviceTokenStore } from '../../core/api/device-token.store';
import { GuestBootstrapService } from '../../core/guest/guest-bootstrap.service';

/**
 * The kiosk-side pairing state machine (guest-frontend-integration
 * REQ-506/507, 02-design.md §3) — **component-provided**, so the poll secret
 * lives in a field that dies with the pairing screen and never exists while
 * the guest app runs. It reaches no storage, no log, no template
 * (constitution principle 3; the display code alone is worthless by the
 * backend's two-credential design).
 *
 * Pins (verbatim from §3): poll cadence 3 s; create-retry backoff 5 s → 30 s
 * doubling; `429` honors `Retry-After`; expiry/`404` renews unattended (the
 * old code stays visible, dimmed — the no-jump rule is the component's);
 * network loss during WAITING resumes the same code/secret until `expiresAt`,
 * then renews. A 401 cannot occur (pairing ops are unauthenticated).
 *
 * On claim: the token is stored, the guest bootstrap runs and the router
 * leaves — **no reload** (REQ-506); the guards route to `/` or `/standby`
 * per occupancy.
 */
export type PairingStatus = 'waiting' | 'renewing' | 'cooldown' | 'offline';

export const POLL_INTERVAL_MS = 3_000;
export const CREATE_BACKOFF_MS = [5_000, 10_000, 20_000, 30_000] as const;
export const COOLDOWN_FALLBACK_MS = 30_000;

@Injectable()
export class PairingService {
  private readonly api = inject(PairingApi);
  private readonly tokenStore = inject(DeviceTokenStore);
  private readonly bootstrap = inject(GuestBootstrapService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _code = signal<string | null>(null);
  private readonly _status = signal<PairingStatus>('renewing');

  /** The display code — the screen's hero. `null` only before the very first 201. */
  readonly code = this._code.asReadonly();
  readonly status = this._status.asReadonly();

  /** In memory only — never displayed, never persisted, never logged. */
  private pollSecret: string | null = null;
  /**
   * The current code's expiry **as a local `Date.now()` value** — the server's
   * lifetime counted on this device's clock, never the server's absolute
   * instant compared against it (see {@link localDeadline}). `null` = no local
   * bound is trustworthy; the server's own `404` drives renewal instead.
   */
  private deadline: number | null = null;
  private backoffStep = 0;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private stopped = false;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.stopped = true;
      this.stopTimer();
      this.pollSecret = null;
    });
  }

  /** Enters CREATING. Called once by the component; re-entered by the machine. */
  start(): void {
    void this.create();
  }

  private async create(): Promise<void> {
    if (this.stopped) {
      return;
    }
    this._status.set('renewing');
    try {
      const response = await firstValueFrom(this.api.createPairingRequest('response'));
      const created = response.body as PairingRequestCreated;
      this._code.set(created.code);
      this.pollSecret = created.pollSecret;
      this.deadline = localDeadline(response.headers.get('date'), created.expiresAt);
      this.backoffStep = 0;
      this._status.set('waiting');
      this.schedule(() => void this.poll(), POLL_INTERVAL_MS);
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 429) {
        this._status.set('cooldown');
        this.schedule(() => void this.create(), retryAfterMs(error));
        return;
      }
      this._status.set('offline');
      this.schedule(() => void this.create(), this.nextBackoff());
    }
  }

  private async poll(): Promise<void> {
    const code = this._code();
    if (this.stopped || code === null || this.pollSecret === null) {
      return;
    }
    if (this.deadline !== null && Date.now() >= this.deadline) {
      // Expired while waiting (e.g. through an offline stretch) — renew
      // proactively instead of provoking the 404 (§3: "until expiresAt,
      // then CREATING").
      this.pollSecret = null;
      void this.create();
      return;
    }
    try {
      const response = await firstValueFrom(
        this.api.pollPairingRequest(code, this.pollSecret, 'response'),
      );
      if (response.status === 200 && response.body) {
        await this.claim(response.body);
        return;
      }
      // 202 — pending. Calm heartbeat, keep polling at the pinned cadence.
      this._status.set('waiting');
      this.backoffStep = 0;
      this.schedule(() => void this.poll(), POLL_INTERVAL_MS);
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        // Expired or consumed — request a fresh code, unattended (REQ-507).
        this.pollSecret = null;
        void this.create();
        return;
      }
      // Network loss: resume the SAME code/secret until it expires (§3).
      this._status.set('offline');
      if (this.deadline !== null && Date.now() >= this.deadline) {
        this.pollSecret = null;
        this.schedule(() => void this.create(), this.nextBackoff());
        return;
      }
      this.schedule(() => void this.poll(), this.nextBackoff());
    }
  }

  /** Claim-once: store the token, boot the app in place, leave the screen. */
  private async claim(completed: PairingCompleted): Promise<void> {
    this.pollSecret = null;
    this.tokenStore.set(completed.deviceToken);
    await this.bootstrap.run();
    if (!this.stopped) {
      await this.router.navigateByUrl('/');
    }
  }

  private nextBackoff(): number {
    const delay = CREATE_BACKOFF_MS[Math.min(this.backoffStep, CREATE_BACKOFF_MS.length - 1)]!;
    this.backoffStep += 1;
    return delay;
  }

  private schedule(action: () => void, delay: number): void {
    this.stopTimer();
    if (!this.stopped) {
      this.timer = setTimeout(action, delay);
    }
  }

  private stopTimer(): void {
    if (this.timer !== undefined) {
      clearTimeout(this.timer);
      this.timer = undefined;
    }
  }
}

/**
 * Turns the server's `expiresAt` into a **local** deadline (review finding
 * 🟡-3, and the PRD's own clock-skew risk: "must use server-supplied
 * `expiresAt` defensively"). A kiosk has no reliable clock — it may boot
 * years off, and comparing the server's absolute instant against
 * `Date.now()` made a device whose clock runs ahead consider every fresh code
 * already expired: create → 3 s → renew → create …, until the global pairing
 * rate limit answered 429 and the screen sat in `cooldown` with no usable
 * code, forever.
 *
 * So the code's *lifetime* is measured entirely on the server's clock
 * (`expiresAt` − the response's `Date` header) and only then counted down
 * locally. When either value is missing or the arithmetic yields nothing
 * positive, no local bound is trustworthy at all and the function returns
 * `null`: the machine then simply waits for the server's `404`, which already
 * drives an unattended renewal.
 */
function localDeadline(dateHeader: string | null, expiresAt: string): number | null {
  const expires = Date.parse(expiresAt);
  const serverNow = dateHeader === null ? Number.NaN : Date.parse(dateHeader);
  if (!Number.isFinite(expires) || !Number.isFinite(serverNow)) {
    return null;
  }
  const lifetime = expires - serverNow;
  return lifetime > 0 ? Date.now() + lifetime : null;
}

/** `Retry-After` in seconds per RFC 9110; the pinned fallback when absent. */
function retryAfterMs(error: HttpErrorResponse): number {
  const header = error.headers?.get('Retry-After');
  const seconds = header === null || header === undefined ? NaN : Number(header);
  return Number.isFinite(seconds) && seconds > 0 ? seconds * 1_000 : COOLDOWN_FALLBACK_MS;
}
