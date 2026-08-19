import { computed, DestroyRef, Injectable, inject, signal } from '@angular/core';

import { DEFAULT_CATALOG_LANGUAGE } from '../i18n/catalog';
import type { LanguageCode } from '../i18n/translation.model';
import type { PropertySettingsConfig } from '../content/property.model';
import {
  PREFERENCE_GATEWAY,
  PREFERENCE_LOAD_TIMEOUT_MS,
  PREFERENCE_WRITE_ATTEMPTS,
  PREFERENCE_WRITE_BACKOFF_MS,
  type AppearanceMode,
  type StoredPreferences,
} from './preference-gateway';

/**
 * The property-configured defaults — the value in effect when the guest has
 * made no choice (REQ-120). Supplied by `applyPropertyDefaults()`, which is
 * the seam the future backend needs: today it is called from the app
 * initializer with static config, later it is called when the config response
 * lands, and every effective value recomputes (02-design.md §1).
 */
export interface PreferenceDefaults {
  language: LanguageCode;
  appearance: AppearanceMode;
}

/**
 * Only ever in effect for the window before `applyPropertyDefaults()` runs
 * (today: zero frames — it runs in the app initializer). The language is the
 * **first registered catalogue's** code rather than a literal `'de'`, so
 * REQ-109's "nothing in `src/` names a language" holds all the way down.
 */
export const FALLBACK_DEFAULTS: PreferenceDefaults = {
  language: DEFAULT_CATALOG_LANGUAGE,
  // `light` is what :root paints, so this can never disagree with the
  // stylesheet. It is a fallback, not a policy: the shipped property config
  // supplies the real default (02-design.md §6.1).
  appearance: 'light',
};

/**
 * The guest's effective language and appearance = the property's default ⊕
 * the guest's own choice (REQ-119, REQ-120).
 *
 * Two layers, kept apart **in the types**: the override signals are
 * `T | null`, and `null` means "no guest choice is in effect". Nothing can
 * read an override as a default or vice versa, and `reset()` is two lines.
 *
 * This service injects nothing but its own transport — never
 * `PropertyContentService`. That is what breaks the dependency cycle the
 * obvious shape has, since the content service needs the active language to
 * resolve localised text (02-design.md §1).
 *
 * All persistence **policy** lives here, above the transport (REQ-122):
 * validation, the late-arrival rule, the optimistic update, the coalescing
 * writer and its bounded retry. Swapping the gateway therefore cannot change
 * behaviour.
 *
 * **Nothing on any of these paths logs anything** (02-design.md §6.5): a
 * guest cannot fix the property's server, this project has not decided on a
 * logging convention, and constitution principle 3 forbids this mechanism
 * from becoming a "log what the guest did" hook.
 */
@Injectable({ providedIn: 'root' })
export class PreferencesService {
  private readonly destroyRef = inject(DestroyRef);
  private readonly gateway = inject(PREFERENCE_GATEWAY);

  /** Every pending timer, so `DestroyRef` can clear them all (no test leaks). */
  private readonly timers = new Set<ReturnType<typeof setTimeout>>();

  /** The coalescing writer's whole state: one flight, one dirty flag. */
  private writeInFlight = false;
  private dirty = false;

  /** The property's option lists, needed to validate any incoming value. */
  private readonly config = signal<PropertySettingsConfig | null>(null);
  private readonly defaults = signal<PreferenceDefaults>(FALLBACK_DEFAULTS);

  /** `null` = the guest has not chosen; never populated from `defaults`. */
  private readonly languageOverride = signal<LanguageCode | null>(null);
  private readonly appearanceOverride = signal<AppearanceMode | null>(null);

  /**
   * `prefers-color-scheme`, followed live. Read **only** when the effective
   * appearance is `system`, which the shipped configuration never offers
   * (decision #4, 2026-08-18): startup is the property/backend default, not
   * the operating system's preference.
   */
  private readonly systemPrefersDark = signal(false);

  readonly language = computed(() => this.languageOverride() ?? this.defaults().language);
  readonly appearance = computed(() => this.appearanceOverride() ?? this.defaults().appearance);

  /** What `<html data-theme>` is given: always resolved, never `system`. */
  readonly resolvedTheme = computed<'light' | 'dark'>(() => {
    const appearance = this.appearance();
    if (appearance === 'system') {
      return this.systemPrefersDark() ? 'dark' : 'light';
    }
    return appearance;
  });

  constructor() {
    this.watchSystemAppearance();
    this.destroyRef.onDestroy(() => {
      for (const timer of this.timers) {
        clearTimeout(timer);
      }
      this.timers.clear();
    });
  }

  /**
   * Reads the stored preference once, at bootstrap, behind a bounded timeout
   * (REQ-126 a/d). **It never rejects**: a rejected app initializer fails the
   * whole bootstrap, which would turn a preference hiccup into a blank kiosk.
   *
   * On timeout the app boots on the property defaults and renders normally —
   * and an answer that arrives *later* is still applied, under the same rule
   * as any other answer: per setting, and only while that setting's override
   * is still `null`, so a slow store can never overwrite a choice the guest
   * has already made (REQ-126 b).
   *
   * `applyPropertyDefaults()` must have run first, or there is no option list
   * to validate the stored value against (02-design.md §6.4).
   */
  hydrate(): Promise<void> {
    const load = this.gateway.load().then(
      (stored) => this.applyStored(stored),
      () => undefined, // a failed read is indistinguishable from "nothing stored"
    );

    return Promise.race([load, this.delay(PREFERENCE_LOAD_TIMEOUT_MS)]);
  }

  /**
   * Applies the property's configured options and defaults. Called before
   * anything is hydrated (02-design.md §6.4) — without it there is no option
   * list to validate a stored value against.
   */
  applyPropertyDefaults(config: PropertySettingsConfig): void {
    this.config.set(config);
    this.defaults.set({
      language: config.defaultLanguage,
      appearance: config.defaultAppearance,
    });
  }

  /**
   * The guest chose a language. The signal moves first and the screen changes
   * immediately; persisting is fire-and-forget (REQ-126 e). Unconfigured
   * values are ignored.
   */
  setLanguage(code: LanguageCode): void {
    if (!this.isConfiguredLanguage(code)) {
      return;
    }
    this.languageOverride.set(code);
    this.persist();
  }

  /** The guest chose an appearance. Same policy as `setLanguage()`. */
  setAppearance(mode: AppearanceMode): void {
    if (!this.isConfiguredAppearance(mode)) {
      return;
    }
    this.appearanceOverride.set(mode);
    this.persist();
  }

  /** Back to the property's defaults, as if the guest had chosen nothing. */
  reset(): void {
    this.languageOverride.set(null);
    this.appearanceOverride.set(null);
    this.persist();
  }

  /**
   * Applies a stored record **per setting, and only while that setting's
   * override is still `null`** (REQ-126 b) — no extra "dirty" flag exists,
   * because a non-null override already means exactly "a choice is in
   * effect". A value outside the property's configured options is
   * **discarded, not applied** (REQ-126 c).
   */
  private applyStored(stored: StoredPreferences | null): void {
    if (!stored) {
      return;
    }

    const { language, appearance } = stored;
    if (
      language !== undefined &&
      this.languageOverride() === null &&
      this.isConfiguredLanguage(language)
    ) {
      this.languageOverride.set(language);
    }
    if (
      appearance !== undefined &&
      this.appearanceOverride() === null &&
      this.isConfiguredAppearance(appearance)
    ) {
      this.appearanceOverride.set(appearance);
    }
  }

  /**
   * Persisting is a *sync-the-current-state* operation, not a queue of
   * events: while a write is in flight the next change only marks the record
   * dirty, and a fresh whole-record snapshot is sent afterwards. A guest
   * tapping through three languages produces one or two writes carrying the
   * **latest** state, never three races whose arrival order decides what the
   * store ends up holding (02-design.md §6.5).
   */
  private persist(): void {
    if (this.writeInFlight) {
      this.dirty = true;
      return;
    }

    this.writeInFlight = true;
    void this.sendCurrentState().then(() => {
      this.writeInFlight = false;
      if (this.dirty) {
        this.dirty = false;
        this.persist();
      }
    });
  }

  /**
   * Sends whatever the guest's choice currently is — no overrides at all
   * means "back to the property defaults", which is `clear()`. Never
   * rejects: when every attempt has failed the retries simply stop, the
   * screen keeps the guest's choice, and nothing is shown and nothing is
   * logged. The next change starts a fresh cycle, so a server that comes back
   * is picked up by the next tap with no retry timer running in between.
   */
  private async sendCurrentState(): Promise<void> {
    const language = this.languageOverride();
    const appearance = this.appearanceOverride();

    if (language === null && appearance === null) {
      await this.attempt(() => this.gateway.clear());
      return;
    }

    const record: StoredPreferences = {};
    if (language !== null) {
      record.language = language;
    }
    if (appearance !== null) {
      record.appearance = appearance;
    }
    await this.attempt(() => this.gateway.save(record));
  }

  /** Bounded retry with the configured backoff, then silent surrender. */
  private async attempt(operation: () => Promise<void>): Promise<void> {
    for (let attempt = 1; attempt <= PREFERENCE_WRITE_ATTEMPTS; attempt += 1) {
      try {
        await operation();
        return;
      } catch {
        const backoff = PREFERENCE_WRITE_BACKOFF_MS[attempt - 1];
        if (attempt === PREFERENCE_WRITE_ATTEMPTS || backoff === undefined) {
          return;
        }
        await this.delay(backoff);
      }
    }
  }

  /** A tracked `setTimeout`, so `DestroyRef` can clear it. */
  private delay(ms: number): Promise<void> {
    return new Promise<void>((resolve) => {
      const timer = setTimeout(() => {
        this.timers.delete(timer);
        resolve();
      }, ms);
      this.timers.add(timer);
    });
  }

  /**
   * A value that is not offered by this property is not applied — from a
   * guest tap or from the store. A property that removed English must not
   * have it resurrected by an old record, and a stored value is untrusted
   * input at a system boundary (constitution -> Constraints).
   */
  private isConfiguredLanguage(code: LanguageCode): boolean {
    return this.config()?.languages.some((option) => option.code === code) ?? false;
  }

  private isConfiguredAppearance(mode: AppearanceMode): boolean {
    return this.config()?.appearances.some((option) => option.mode === mode) ?? false;
  }

  /**
   * `matchMedia` is a platform API and issues no request. Guarded because
   * jsdom does not implement it unless a test provides it.
   */
  private watchSystemAppearance(): void {
    const query = globalThis.matchMedia?.('(prefers-color-scheme: dark)');
    if (!query) {
      return;
    }

    this.systemPrefersDark.set(query.matches);
    const onChange = (event: MediaQueryListEvent) => this.systemPrefersDark.set(event.matches);
    query.addEventListener('change', onChange);
    this.destroyRef.onDestroy(() => query.removeEventListener('change', onChange));
  }
}
