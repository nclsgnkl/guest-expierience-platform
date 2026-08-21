import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import {
  GuestService,
  OccupancyState,
  type GuestHomeScreen,
  type GuestSession,
} from '@gxp/api-client';
import { firstValueFrom, timeout, type Observable } from 'rxjs';

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
 *   ONLINE        rendering live data; revalidate session (+ home while
 *                 OCCUPIED) every 30 s with If-None-Match; 304 = no-op
 *   STALE         a poll failed; keep rendering what is held, show the
 *                 discreet staleness pill, keep polling at 30 s
 *   DISCONNECTED  nothing to render (no payload, no usable cache); retry
 *                 with 5→30 s doubling backoff — the connection screen
 *
 *   401 anywhere → token + cache wiped (REQ-509) → the pairing guard routes
 *   VACANT → home (payload + cache) cleared — data-sparing on the client too,
 *            including a `409 room-vacant` on `screens/home` (see `asVacant`)
 *
 * Zoneless-safe by construction: every state change is a signal write; the
 * timers are plain `setTimeout`s torn down via `DestroyRef` (the
 * `ClockService` pattern).
 *
 * **`If-None-Match` mechanism (verify-at-implementation, recorded):** the
 * generated `getGuestSession`/`getGuestHomeScreen` expose no header
 * parameter, but `BaseService.defaultHeaders` is public and the generated
 * method bodies capture it synchronously at call time — setting it around
 * the call is race-free and needs no contract change.
 *
 * Nothing here logs (constitution principle 3): payloads are property
 * content, the token is a credential; neither may reach a console.
 */
export type ConnectionState = 'ONLINE' | 'STALE' | 'DISCONNECTED';

export const SESSION_CACHE_KEY = 'gxp.cache.session';
export const HOME_CACHE_KEY = 'gxp.cache.home';

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
  private readonly _session = signal<GuestSession | null>(null);
  /** Non-null while rendering cached/held data the backend has not confirmed. */
  private readonly _staleSince = signal<number | null>(null);

  readonly connection = this._connection.asReadonly();
  readonly occupancy = this._occupancy.asReadonly();
  readonly config = this._config.asReadonly();
  readonly session = this._session.asReadonly();
  readonly staleSince = this._staleSince.asReadonly();

  private sessionEtag: string | null = null;
  private homeEtag: string | null = null;
  private heldHome: GuestHomeScreen | null = null;
  private lastGoodAt: number | null = null;
  private retryStep = 0;
  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.destroyRef.onDestroy(() => this.stopTimer());
  }

  /** The stay's raw preference override — `HttpPreferenceGateway.load()`'s source. */
  sessionPreference(): StoredPreferences | null {
    const session = this._session();
    return session ? mapStoredPreferences(session) : null;
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
      const session = await this.fetchSession();
      let payload = session.payload;
      let etag = session.etag;
      let home: GuestHomeScreen | null = null;
      if (payload.occupancy === OccupancyState.Occupied) {
        try {
          home = (await this.fetchHome()).payload;
          this.homeEtag = this.pendingHomeEtag;
        } catch (error) {
          if (!isStatus(error, 409)) {
            throw error;
          }
          payload = asVacant(payload); // the stay ended between the two calls
          etag = null;
          home = null;
        }
      }
      this.adopt(payload, etag, home);
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
    storage()?.removeItem(SESSION_CACHE_KEY);
    storage()?.removeItem(HOME_CACHE_KEY);
    // The bytes, too: object URLs pin the old property's logo/artwork and the
    // per-id memo would re-serve them after a re-pair (review 🟡-2).
    this.assets.clear();
    this.sessionEtag = null;
    this.homeEtag = null;
    this.heldHome = null;
    this.lastGoodAt = null;
    this._config.set(null);
    this._session.set(null);
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
      const result = await this.fetchSession(this.sessionEtag);
      const session = result.notModified ? this._session() : result.payload;
      if (session === null) {
        throw new Error('no session held'); // → catch: STALE/DISCONNECTED path
      }
      const sessionEtag = result.notModified ? this.sessionEtag : result.etag;

      let held = session;
      let heldEtag = sessionEtag;
      let vacantNow = false;
      let home: GuestHomeScreen | null = null;
      if (session.occupancy === OccupancyState.Occupied) {
        const previousEtag = this.heldHome !== null ? this.homeEtag : null;
        try {
          const homeResult = await this.fetchHome(previousEtag);
          home = homeResult.notModified ? this.heldHome : homeResult.payload;
          this.homeEtag = homeResult.notModified ? this.homeEtag : this.pendingHomeEtag;
        } catch (error) {
          if (!isStatus(error, 409)) {
            throw error;
          }
          held = asVacant(session);
          heldEtag = null;
          vacantNow = true;
        }
      }

      const changed =
        vacantNow || !result.notModified || home !== this.heldHome || session !== this._session();
      this.adopt(held, heldEtag, home, changed);
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
  private adopt(
    session: GuestSession,
    sessionEtag: string | null,
    home: GuestHomeScreen | null,
    changed = true,
  ): void {
    if (changed) {
      const config = mapGuestContent(session, home); // may throw — caller handles
      this._config.set(config);
      this._session.set(session);
      this._occupancy.set(session.occupancy);
    }
    this.sessionEtag = sessionEtag;
    this.heldHome = home;
    this.lastGoodAt = Date.now();
    writeCache(SESSION_CACHE_KEY, { payload: session, etag: sessionEtag, storedAt: this.lastGoodAt });
    if (home !== null) {
      writeCache(HOME_CACHE_KEY, { payload: home, etag: this.homeEtag, storedAt: this.lastGoodAt });
    } else if (session.occupancy === OccupancyState.Vacant) {
      // Vacancy data-sparing on the client too (§4): the home payload leaves
      // the kiosk entirely — signal, held copy AND cache.
      this.homeEtag = null;
      storage()?.removeItem(HOME_CACHE_KEY);
    }
    this.retryStep = 0;
    this._connection.set('ONLINE');
    this._staleSince.set(null);
  }

  /** Last-known-good (D5): render the cache, or report there is none. */
  private adoptCache(): boolean {
    const session = readCache<GuestSession>(SESSION_CACHE_KEY);
    if (session === null) {
      return false;
    }
    const home =
      session.payload.occupancy === OccupancyState.Occupied
        ? readCache<GuestHomeScreen>(HOME_CACHE_KEY)
        : null;
    try {
      const config = mapGuestContent(session.payload, home?.payload ?? null);
      this._config.set(config);
      this._session.set(session.payload);
      this._occupancy.set(session.payload.occupancy);
      this.sessionEtag = session.etag;
      this.heldHome = home?.payload ?? null;
      this.homeEtag = home?.etag ?? null;
      this.lastGoodAt = session.storedAt;
      this._staleSince.set(session.storedAt);
      return true;
    } catch {
      // A cache the mapper rejects is no cache at all.
      storage()?.removeItem(SESSION_CACHE_KEY);
      storage()?.removeItem(HOME_CACHE_KEY);
      return false;
    }
  }

  private async fetchSession(): Promise<{ payload: GuestSession; etag: string | null; notModified: false }>;
  private async fetchSession(
    etag: string | null,
  ): Promise<
    | { payload: GuestSession; etag: string | null; notModified: false }
    | { notModified: true }
  >;
  private async fetchSession(etag: string | null = null) {
    try {
      const response = await firstValueFrom(
        this.withIfNoneMatch(etag, () => this.api.getGuestSession('response')).pipe(
          timeout(FETCH_TIMEOUT_MS),
        ),
      );
      return {
        payload: response.body as GuestSession,
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

  /** The home response's ETag, captured beside the payload. */
  private pendingHomeEtag: string | null = null;

  private async fetchHome(
    etag: string | null = null,
  ): Promise<{ payload: GuestHomeScreen; notModified: false } | { notModified: true; payload: null }> {
    try {
      const response = await firstValueFrom(
        this.withIfNoneMatch(etag, () => this.api.getGuestHomeScreen('response')).pipe(
          timeout(FETCH_TIMEOUT_MS),
        ),
      );
      this.pendingHomeEtag = response.headers.get('etag');
      return { payload: response.body as GuestHomeScreen, notModified: false };
    } catch (error) {
      if (etag !== null && isStatus(error, 304)) {
        return { notModified: true, payload: null };
      }
      throw error;
    }
  }

  /** See the class comment — the recorded If-None-Match mechanism. */
  private withIfNoneMatch<T>(etag: string | null, call: () => Observable<T>): Observable<T> {
    this.api.defaultHeaders = etag
      ? new HttpHeaders({ 'If-None-Match': etag })
      : new HttpHeaders();
    try {
      return call(); // generated bodies capture headers synchronously
    } finally {
      this.api.defaultHeaders = new HttpHeaders();
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

/**
 * The session as the server would serve it for a vacant room — used when
 * `screens/home` answers `409 room-vacant` after a session that still said
 * OCCUPIED (the stay ended between the two calls of one poll; review finding
 * 🟡-4). Treating that as a generic fetch failure would keep the PREVIOUS
 * guest's home content on screen, behind an untruthful staleness pill, for up
 * to one poll interval; the 409 is the newer, authoritative answer, so the
 * kiosk goes to standby at once.
 *
 * Every stay-derived field is dropped, exactly as `GuestControl` omits them
 * for a vacant room — vacancy data-sparing must also hold for the session the
 * kiosk holds and caches.
 */
function asVacant(session: GuestSession): GuestSession {
  const vacant: GuestSession = { ...session, occupancy: OccupancyState.Vacant };
  delete vacant.guestDisplayName;
  delete vacant.preference;
  return vacant;
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
