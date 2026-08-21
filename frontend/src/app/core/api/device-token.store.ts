import { Injectable, signal } from '@angular/core';

import { environment } from '../../../environments/environment';

/**
 * The kiosk's own credential (guest-frontend-integration REQ-505/508, D4
 * approved 2026-08-20): the opaque device bearer token, persisted in
 * `localStorage` so a reboot does not re-pair. Revocation is server-side and
 * next-request effective — nothing here needs to expire it.
 *
 * Rules this type enforces by shape:
 *
 *   - **One reader.** The only consumer of `token()` outside this file is the
 *     `Configuration.credentials['bearerAuth']` function wired in
 *     `provide-guest-api.ts` (and `GuestDataService`'s "is there a token at
 *     all" branch). The token is never rendered, never interpolated into any
 *     string, and **never logged** (constitution principle 3).
 *   - **The dev fallback is consulted only when storage is empty** (REQ-505,
 *     D3): a production build carries `devDeviceToken: null`, so this branch
 *     is dead there — provable by grepping `dist/` for the literal.
 *   - A signal, so the pairing→app transition (`set()` on claim) and the
 *     401 wipe (`clear()`) propagate without a reload (zoneless).
 */
const STORAGE_KEY = 'gxp.device-token';

@Injectable({ providedIn: 'root' })
export class DeviceTokenStore {
  private readonly _token = signal<string | null>(null);

  /** `null` = unpaired (or wiped by a 401). */
  readonly token = this._token.asReadonly();

  constructor() {
    this.load();
  }

  /** Storage first, environment fallback second, `null` third. */
  load(): void {
    const stored = storage()?.getItem(STORAGE_KEY);
    if (stored !== null && stored !== undefined && stored !== '') {
      this._token.set(stored);
      return;
    }
    this._token.set(environment.devDeviceToken);
  }

  /** Called exactly once per pairing, with the claim response's token. */
  set(token: string): void {
    storage()?.setItem(STORAGE_KEY, token);
    this._token.set(token);
  }

  /** The 401 wipe (REQ-509). Clears storage first, then the signal. */
  clear(): void {
    storage()?.removeItem(STORAGE_KEY);
    this._token.set(null);
  }
}

/** `localStorage` throws rather than being absent in some privacy modes. */
function storage(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}
