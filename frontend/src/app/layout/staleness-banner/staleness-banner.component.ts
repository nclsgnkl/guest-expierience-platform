import { Component, computed, inject } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { OccupancyState } from '@gxp/api-client';

import { PropertyContentService } from '../../core/content/property-content.service';
import { GuestDataService } from '../../core/guest/guest-data.service';
import { StalenessService } from '../../core/guest/staleness.service';
import { TranslationService } from '../../core/i18n/translation.service';
import { PreferencesService } from '../../core/preferences/preferences.service';

/**
 * The escalated staleness hint (guest-payload-consolidation REQ-952,
 * constitution v1.9.0; spec: docs/design/screens-inhouse/connection.md ->
 * "The escalated staleness banner").
 *
 * The pill's anchor, grown into a BAR — deliberately not "the pill grown into
 * a panel" any more, a phrase the spec now retires: after 60 minutes without
 * successful backend contact a screen that looks perfectly normal while
 * serving day-old content stops looking normal. `StalenessService` owns
 * the single derived level, so this component and the pill can never be up
 * together, and nothing in `GuestDataService`'s state machine changed.
 *
 * Two body variants, chosen from **occupancy** and not from the host that
 * renders it: an occupied room has a guest to direct to their host, a vacant
 * one has whoever else sees the screen — a dead backend must not stay
 * invisible just because the room is empty.
 *
 * Not interactive: no dismiss, no retry. Recovery is automatic, and a button
 * would imply the guest is responsible for infrastructure.
 */
@Component({
  selector: 'app-staleness-banner',
  imports: [NgIcon],
  templateUrl: './staleness-banner.component.html',
  styleUrl: './staleness-banner.component.css',
})
export class StalenessBannerComponent {
  private readonly staleness = inject(StalenessService);
  private readonly guestData = inject(GuestDataService);
  private readonly content = inject(PropertyContentService);
  private readonly preferences = inject(PreferencesService);
  protected readonly t = inject(TranslationService).t;

  protected readonly visible = computed(() => this.staleness.level() === 'BANNER');

  protected readonly bodyKey = computed(() =>
    this.guestData.occupancy() === OccupancyState.Occupied
      ? 'stale.banner.body'
      : 'stale.banner.bodyStandby',
  );

  /** The pill's own line, kept: the information it carried is not lost. */
  protected readonly asOf = computed(() => {
    const since = this.staleness.since();
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

/** Memoised per locale — the status bar's and the pill's own convention. */
const formatters = new Map<string, Intl.DateTimeFormat>();

function timeFormatter(locale: string): Intl.DateTimeFormat {
  let formatter = formatters.get(locale);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit' });
    formatters.set(locale, formatter);
  }
  return formatter;
}
