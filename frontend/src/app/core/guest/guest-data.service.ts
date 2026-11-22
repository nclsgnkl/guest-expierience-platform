import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { GuestService, type GuestContent, type OccupancyState } from '@gxp/api-client';
import { firstValueFrom, timeout } from 'rxjs';

import { DeviceTokenStore } from '../api/device-token.store';
import type { PropertyConfig } from '../content/property.model';
import type { StoredPreferences } from '../preferences/preference-gateway';
import { GuestAssetService } from './guest-asset.service';
import { mapGuestContent, mapStoredPreferences } from './guest-content.mapper';

/**
 * Owns ALL wire I/O for guest content (guest-frontend-integration
 * REQ-509/516/517/519, 02-design.md §4): the bootstrap fetch, the ETag
 * revalidation loop, the last-known-good cache and the connection state
 * machine. `PropertyContentService` reads `config()` — components never see
 * this service.
 *
 * State machine (02-design.md §4, pinned):
 *
 *   ONLINE        rendering live data; revalidate the content resource every
 *                 30 s with If-None-Match; 304 = no-op
 *   STALE         a poll failed; keep rendering what is held, show the
 *                 discreet staleness pill, keep polling at 30 s
 *   DISCONNECTED  nothing to render (no payload, no usable cache); retry
 *                 with 5→30 s doubling backoff — the connection screen
 *
 *   401 anywhere → token + cache wiped (REQ-509) → the pairing guard routes
 *   VACANT → the server's one authoritative answer simply carries no
 *            stay-derived half (no `home`, no guest name, no preference), so
 *            data-sparing on the client is a consequence of adopting it
 *
 * Zoneless-safe by construction: every state change is a signal write; the
 * timers are plain `setTimeout`s torn down via `DestroyRef` (the
 * `ClockService` pattern).
 *
 * Nothing here logs (constitution principle 3): payloads are property
 * content, the token is a credential; neither may reach a console.
 */
export type ConnectionState = 'ONLINE' | 'STALE' | 'DISCONNECTED';

export const CONTENT_CACHE_KEY = 'gxp.cache.content';

/**
 * The pre-consolidation cache keys (guest-payload-consolidation §5). Their
 * records are a different shape and every kiosk in the field still holds
 * them, so they are evicted once, where this service first touches storage.
 * Deletable in a later release.
 */
const LEGACY_CACHE_KEYS = ['gxp.cache.session', 'gxp.cache.home'] as const;

/** Bootstrap/poll fetch bound (02-design.md §4/§5 — pinned 4 s). */
export const FETCH_TIMEOUT_MS = 4_000;
/** Steady-state revalidation cadence (pinned 30 s — the ETags' purpose). */
export const POLL_INTERVAL_MS = 30_000;
/** DISCONNECTED retry backoff: 5 s doubling to a 30 s ceiling (pinned). */
export const RETRY_BACKOFF_MS = [5_000, 10_000, 20_000, 30_000] as const;

interface CacheRecord<T> {
  payload: T;
  etag: string | null;
  storedAt: number; // epoch ms — feeds the staleness pill
}

@Injectable({ providedIn: 'root' })
export class GuestDataService {
  private readonly api = inject(GuestService);
  private readonly tokenStore = inject(DeviceTokenStore);
  private readonly assets = inject(GuestAssetService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _connection = signal<ConnectionState>('DISCONNECTED');
  private readonly _occupancy = signal<OccupancyState | null>(null);
  private readonly _config = signal<PropertyConfig | null>(null);
  private readonly _payload = signal<GuestContent | null>(null);
  /** Non-null while rendering cached/held data the backend has not confirmed. */
  private readonly _staleSince = signal<number | null>(null);

  readonly connection = this._connection.asReadonly();
  readonly occupancy = this._occupancy.asReadonly();
  readonly config = this._config.asReadonly();
  readonly payload = this._payload.asReadonly();
  readonly staleSince = this._staleSince.asReadonly();

  private etag: string | null = null;
  private lastGoodAt: number | null = null;
  private retryStep = 0;
  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.destroyRef.onDestroy(() => this.stopTimer());
    for (const key of LEGACY_CACHE_KEYS) {
      storage()?.removeItem(key);
    }
  }

  /** The stay's raw preference override — `HttpPreferenceGateway.load()`'s source. */
  preferenceOverride(): StoredPreferences | null {
    const content = this._payload();
    return content ? mapStoredPreferences(content) : null;
  }

  /**
   * The bootstrap fetch (02-design.md §5): resolves — never rejects — once
   * the kiosk knows what to render (live payload, cache, or the honest
   * DISCONNECTED state). Called from the app initializer and again after a
   * pairing claim.
   */
  async bootstrap(): Promise<void> {
    if (this.tokenStore.token() === null) {
      return; // unpaired — the pairing guard owns this state
    }
    try {
      const result = await this.fetchContent();
      this.adopt(result.payload, result.etag);
      this.schedule(POLL_INTERVAL_MS);
    } catch (error) {
      if (isStatus(error, 401)) {
        this.handleUnauthorized();
        return;
      }
      if (this.adoptCache()) {
        this._connection.set('STALE');
        this.schedule(POLL_INTERVAL_MS);
      } else {
        this._connection.set('DISCONNECTED');
        this.scheduleRetry();
      }
    }
  }

  /**
   * The 401 wipe (REQ-509): token and cache cleared FIRST, then every signal
   * and every fetched image — a revoked device renders nothing it learned
   * before. Also called by `HttpPreferenceGateway` when a preference write
   * meets a 401.
   */
  handleUnauthorized(): void {
    this.stopTimer();
    this.tokenStore.clear();
    storage()?.removeItem(CONTENT_CACHE_KEY);
    // The bytes, too: object URLs pin the old property's logo/artwork and the
    // per-id memo would re-serve them after a re-pair (review 🟡-2).
    this.assets.clear();
    this.etag = null;
    this.lastGoodAt = null;
    this._config.set(null);
    this._payload.set(null);
    this._occupancy.set(null);
    this._staleSince.set(null);
    this._connection.set('DISCONNECTED');
  }

  /** One revalidation tick. Public for the spec; scheduled internally. */
  async refresh(): Promise<void> {
    if (this.tokenStore.token() === null) {
      return;
    }
    try {
      const result = await this.fetchContent(this.etag);
      const content = result.notModified ? this._payload() : result.payload;
      if (content === null) {
        throw new Error('no content held'); // → catch: STALE/DISCONNECTED path
      }
      const etag = result.notModified ? this.etag : result.etag;
      this.adopt(content, etag, !result.notModified);
    } catch (error) {
      if (isStatus(error, 401)) {
        this.handleUnauthorized();
        return;
      }
      if (this._config() !== null) {
        // Keep rendering what is held — discreet hint, keep polling (§4).
        this._connection.set('STALE');
        this._staleSince.set(this.lastGoodAt ?? Date.now());
      } else {
        this._connection.set('DISCONNECTED');
        this.scheduleRetry();
        return;
      }
    }
    this.schedule(POLL_INTERVAL_MS);
  }

  /* ------------------------------------------------------------------ */

  /**
   * Adopts a confirmed server state: maps (a mapping failure throws to the
   * caller and counts as a failed fetch, §6), writes signals + cache, and
   * resets the machine to ONLINE. Cache is written only AFTER mapping
   * succeeded — a payload the mapper rejects must not poison the cache.
   */
  private adopt(content: GuestContent, etag: string | null, changed = true): void {
    if (changed) {
      const config = mapGuestContent(content); // may throw — caller handles
      this._config.set(config);
      this._payload.set(content);
      this._occupancy.set(content.occupancy);
    }
    this.etag = etag;
    this.lastGoodAt = Date.now();
    writeCache(CONTENT_CACHE_KEY, { payload: content, etag, storedAt: this.lastGoodAt });
    this.retryStep = 0;
    this._connection.set('ONLINE');
    this._staleSince.set(null);
  }

  /** Last-known-good (D5): render the cache, or report there is none. */
  private adoptCache(): boolean {
    const record = readCache<GuestContent>(CONTENT_CACHE_KEY);
    if (record === null) {
      return false;
    }
    try {
      const config = mapGuestContent(record.payload);
      this._config.set(config);
      this._payload.set(record.payload);
      this._occupancy.set(record.payload.occupancy);
      this.etag = record.etag;
      this.lastGoodAt = record.storedAt;
      this._staleSince.set(record.storedAt);
      return true;
    } catch {
      // A cache the mapper rejects is no cache at all.
      storage()?.removeItem(CONTENT_CACHE_KEY);
      return false;
    }
  }

  private async fetchContent(): Promise<{ payload: GuestContent; etag: string | null; notModified: false }>;
  private async fetchContent(
    etag: string | null,
  ): Promise<
    | { payload: GuestContent; etag: string | null; notModified: false }
    | { notModified: true }
  >;
  private async fetchContent(etag: string | null = null) {
    try {
      const response = await firstValueFrom(
        this.api
          .getGuestContent(etag ?? undefined, 'response')
          .pipe(timeout(FETCH_TIMEOUT_MS)),
      );
      return {
        payload: response.body as GuestContent,
        etag: response.headers.get('etag'),
        notModified: false as const,
      };
    } catch (error) {
      if (etag !== null && isStatus(error, 304)) {
        return { notModified: true as const };
      }
      throw error;
    }
  }

  private schedule(delay: number): void {
    this.stopTimer();
    this.timer = setTimeout(() => void this.refresh(), delay);
  }

  private scheduleRetry(): void {
    const delay = RETRY_BACKOFF_MS[Math.min(this.retryStep, RETRY_BACKOFF_MS.length - 1)]!;
    this.retryStep += 1;
    this.schedule(delay);
  }

  private stopTimer(): void {
    if (this.timer !== undefined) {
      clearTimeout(this.timer);
      this.timer = undefined;
    }
  }
}

/* ---------------------------------------------------------------------- */

function isStatus(error: unknown, status: number): boolean {
  return error instanceof HttpErrorResponse && error.status === status;
}

function writeCache<T>(key: string, record: CacheRecord<T>): void {
  try {
    storage()?.setItem(key, JSON.stringify(record));
  } catch {
    // A full/blocked storage degrades to "no cache" — never an error surface.
  }
}

function readCache<T>(key: string): CacheRecord<T> | null {
  const raw = storage()?.getItem(key);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<CacheRecord<T>>;
    if (typeof parsed !== 'object' || parsed === null || parsed.payload === undefined) {
      return null;
    }
    return {
      payload: parsed.payload,
      etag: typeof parsed.etag === 'string' ? parsed.etag : null,
      storedAt: typeof parsed.storedAt === 'number' ? parsed.storedAt : Date.now(),
    };
  } catch {
    return null;
  }
}

function storage(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}
