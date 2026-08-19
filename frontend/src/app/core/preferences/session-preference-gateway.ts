import { Injectable } from '@angular/core';

import type { PreferenceGateway, StoredPreferences } from './preference-gateway';

/**
 * **Stand-in, not an implementation.** The guest's preference belongs in the
 * backend's database (decision #2, 2026-08-18); until that backend exists,
 * this `sessionStorage`-backed gateway reproduces every observable property
 * of the real one — a change is stored, a reload delivers it back, an absent
 * record yields the property defaults — so the whole hydrate path is
 * exercised on every reload rather than only in unit tests.
 *
 * **It is deleted, not adapted, when the HTTP gateway lands** (REQ-121). It
 * has no schema of its own: one namespaced key holding exactly the record the
 * backend will hold.
 *
 * Two rules that stop it from being a lie (02-design.md §6.2):
 *
 *   - it resolves through `Promise.resolve()` and **never synchronously**, so
 *     ordering bugs cannot hide until the day HTTP arrives;
 *   - it stores `{ language?, appearance? }` and nothing else — no
 *     identifier, ever (REQ-127).
 *
 * Nothing here logs: a storage failure is indistinguishable from "nothing
 * stored", and this project has not decided on a logging convention
 * (02-design.md §6.5; constitution principle 3).
 */
const STORAGE_KEY = 'gxp.preferences';

@Injectable()
export class SessionPreferenceGateway implements PreferenceGateway {
  load(): Promise<StoredPreferences | null> {
    return Promise.resolve(this.read());
  }

  save(value: StoredPreferences): Promise<void> {
    return Promise.resolve().then(() => {
      const record: StoredPreferences = {};
      if (value.language !== undefined) {
        record.language = value.language;
      }
      if (value.appearance !== undefined) {
        record.appearance = value.appearance;
      }
      this.storage()?.setItem(STORAGE_KEY, JSON.stringify(record));
    });
  }

  clear(): Promise<void> {
    return Promise.resolve().then(() => {
      this.storage()?.removeItem(STORAGE_KEY);
    });
  }

  /** A missing **or malformed** record reads as `null` — never as a partial. */
  private read(): StoredPreferences | null {
    const raw = this.storage()?.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(raw);
    } catch {
      return null;
    }

    if (typeof parsed !== 'object' || parsed === null) {
      return null;
    }

    const record = parsed as Record<string, unknown>;
    const language = record['language'];
    const appearance = record['appearance'];
    const stored: StoredPreferences = {};
    if (typeof language === 'string') {
      stored.language = language;
    }
    if (appearance === 'light' || appearance === 'dark' || appearance === 'system') {
      stored.appearance = appearance;
    }

    return stored;
  }

  /** `sessionStorage` throws rather than being absent in some privacy modes. */
  private storage(): Storage | null {
    try {
      return globalThis.sessionStorage ?? null;
    } catch {
      return null;
    }
  }
}
