import { Injectable, inject } from '@angular/core';

import { PreferencesService } from '../preferences/preferences.service';
import { DEFAULT_CATALOG_LANGUAGE, TRANSLATION_CATALOGS } from './catalog';
import type { TranslationKey, TranslationParams } from './translation.model';

/**
 * App-chrome translation (REQ-106, REQ-107). Hand-rolled and signal-based:
 * `t()` reads `language()`, and because Angular's template expressions are
 * reactive consumers under zoneless change detection, `{{ t('…') }}`
 * re-renders when the language changes — no pipe, no `markForCheck()`, no
 * subscription. A `TranslationPipe` was considered and rejected: it buys
 * nothing here, and a pure pipe would have to be made impure to see the
 * change (02-design.md §7.1).
 *
 * Nothing is fetched. The catalogues are TypeScript modules inside the
 * bundle, so a switch is on screen before anything could leave the device
 * (constitution principle 4).
 */
@Injectable({ providedIn: 'root' })
export class TranslationService {
  private readonly preferences = inject(PreferencesService);

  readonly language = this.preferences.language;

  /**
   * **An arrow-function class property, not a method** — components hold it
   * as `protected readonly t = inject(TranslationService).t;`, and a detached
   * method reference would lose `this` and throw on the first call
   * (02-design.md §7.1).
   *
   * Unknown key → the default catalogue → the key itself. The failing key is
   * never logged and no content is ever interpolated into a log line: this
   * mechanism must not become the place property content leaks out of
   * (constitution principle 3).
   */
  readonly t = (key: TranslationKey, params?: TranslationParams): string => {
    const active = TRANSLATION_CATALOGS.get(this.language());
    const fallback = TRANSLATION_CATALOGS.get(DEFAULT_CATALOG_LANGUAGE);
    const value = active?.[key] ?? fallback?.[key] ?? key;
    return params ? interpolate(value, params) : value;
  };
}

/** Replaces every `{placeholder}` that has a value; leaves the rest alone. */
function interpolate(value: string, params: TranslationParams): string {
  return value.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in params ? String(params[name]) : match,
  );
}
