import { InjectionToken } from '@angular/core';

import type { LanguageCode } from '../i18n/translation.model';

/**
 * The contract the backend will implement for the guest's own preference
 * (REQ-121, REQ-122). The guest's choice is **persistent state owned by the
 * database** (user decision, 2026-08-18): written when it changes, delivered
 * on load, with the backend always supplying the defaults.
 *
 * Three deliberate properties (02-design.md §6.3):
 *
 *   - **Whole-record writes, not patches.** `save()` always carries both
 *     settings, so a lost or reordered write can never leave the store
 *     half-updated and the future endpoint is a plain idempotent `PUT`.
 *   - **Promises, not observables.** One load, one write per change — no
 *     stream. The HTTP implementation will `firstValueFrom()` internally;
 *     that conversion belongs inside the transport.
 *   - **Policy lives above the transport.** Validation, the late-arrival
 *     rule, the optimistic update, the retry and the fallback are all in
 *     `PreferencesService`. The gateway is dumb on purpose: swapping it
 *     cannot change behaviour, which is what makes REQ-122 testable.
 *
 * **This file imports nothing from `core/content/` and must keep importing
 * nothing from it** (02-design.md §1): the gateway is a DI leaf, and pulling
 * the content layer in here closes the very dependency cycle the whole shape
 * exists to avoid. That is also why `AppearanceMode` is declared here and
 * re-exported by `property.model.ts`, rather than the other way round.
 */

/**
 * `system` means "follow the operating system". It stays representable, but
 * the shipped configuration does not offer it (decision #4, 2026-08-18) — the
 * startup appearance is the property/backend default, and what reaches
 * `<html data-theme>` is always a resolved `light` or `dark` (REQ-105).
 */
export type AppearanceMode = 'light' | 'dark' | 'system';

/**
 * The entire stored record. **No identifier of any kind** — not a guest id,
 * not a stay id, not a session id, not a device id (REQ-127): the subject is
 * the screen, one instance per property, and this feature deliberately does
 * not pre-empt the open guest-authentication decision.
 */
export interface StoredPreferences {
  language?: LanguageCode;
  appearance?: AppearanceMode;
}

export interface PreferenceGateway {
  /** The screen's stored preference; `null` when nothing is stored. */
  load(): Promise<StoredPreferences | null>;
  /** Persists the whole record (idempotent — a PUT, not a patch). */
  save(value: StoredPreferences): Promise<void>;
  /** Removes the record; the screen falls back to the property defaults. */
  clear(): Promise<void>;
}

export const PREFERENCE_GATEWAY = new InjectionToken<PreferenceGateway>('PreferenceGateway');

/** How long the app waits for the stored preference at boot (02-design.md §6.4). */
export const PREFERENCE_LOAD_TIMEOUT_MS = 1_000;

/** How often a failing write is retried before it is given up on silently. */
export const PREFERENCE_WRITE_ATTEMPTS = 3;

/** Delay before retry 2 and retry 3, in ms. */
export const PREFERENCE_WRITE_BACKOFF_MS = [250, 1_000] as const;
