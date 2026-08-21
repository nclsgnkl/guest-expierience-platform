import { Component, computed, inject } from '@angular/core';

import { PropertyContentService } from '../../core/content/property-content.service';
import { GuestDataService } from '../../core/guest/guest-data.service';
import { TranslationService } from '../../core/i18n/translation.service';
import { PreferencesService } from '../../core/preferences/preferences.service';

/**
 * The discreet staleness hint (guest-frontend-integration REQ-519; spec:
 * docs/design/screens-inhouse/connection.md -> "The staleness pill").
 * Chrome, not a screen: a small pill directly below the status-bar cluster,
 * visible ONLY while the kiosk renders cached/held content because the
 * backend is unreachable (`STALE`). The timestamp is the cache's `storedAt`
 * — static per stale episode (when the data was fetched, not a countdown),
 * so the `aria-live` region never chatters.
 */
@Component({
  selector: 'app-staleness-pill',
  templateUrl: './staleness-pill.component.html',
  styleUrl: './staleness-pill.component.css',
})
export class StalenessPillComponent {
  private readonly guestData = inject(GuestDataService);
  private readonly content = inject(PropertyContentService);
  private readonly preferences = inject(PreferencesService);
  protected readonly t = inject(TranslationService).t;

  protected readonly visible = computed(
    () => this.guestData.connection() === 'STALE' && this.guestData.staleSince() !== null,
  );

  /** `Stand HH:MM` / `As of HH:MM`, in the active language's own locale. */
  protected readonly label = computed(() => {
    const since = this.guestData.staleSince();
    if (since === null) {
      return '';
    }
    const language = this.preferences.language();
    const locale =
      this.content.settingsConfig().languages.find((option) => option.code === language)?.locale ??
      language;
    return this.t('stale.asOf', { time: timeFormatter(locale).format(new Date(since)) });
  });
}

/** Memoised per locale — the status bar's own convention (02-design.md §8.5). */
const formatters = new Map<string, Intl.DateTimeFormat>();

function timeFormatter(locale: string): Intl.DateTimeFormat {
  let formatter = formatters.get(locale);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit' });
    formatters.set(locale, formatter);
  }
  return formatter;
}
