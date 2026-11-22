import { Component, computed, inject } from '@angular/core';
import { NgIcon } from '@ng-icons/core';

import { ClockService } from '../../core/clock/clock.service';
import { PropertyContentService } from '../../core/content/property-content.service';
import type { AppearanceMode } from '../../core/content/property.model';
import { TranslationService } from '../../core/i18n/translation.service';
import { PreferencesService } from '../../core/preferences/preferences.service';
import {
  StatusBarMenuComponent,
  type StatusBarMenuOption,
} from './status-bar-menu/status-bar-menu.component';

/**
 * App-wide status bar (REQ-112 … REQ-115): language control, appearance
 * control, a hairline divider and the clock, in the top-right corner of the
 * content area on every route — the corner
 * docs/design/assets/home-concept-ai.png shows.
 *
 * It is route-agnostic chrome, so it lives in `layout/` beside
 * `SidebarComponent`, and it is an **overlay**: `ShellComponent` positions it
 * inside its own padding box and it consumes no flow space, so no route's
 * measured geometry moves (02-design.md §8.1).
 *
 * **No `role="toolbar"`, deliberately.** That role is a promise of roving
 * `tabindex` and arrow-key traversal, which this does not implement for two
 * buttons and a clock. Two `<button>`s with real accessible names and a
 * `<time>` are correct and already keyboard-reachable.
 *
 * Both controls read and write `PreferencesService` — the very same signals
 * the Settings screen binds to, which is what makes REQ-117's "changing one
 * is reflected in the other" true by construction rather than by wiring.
 */
@Component({
  selector: 'app-status-bar',
  imports: [NgIcon, StatusBarMenuComponent],
  templateUrl: './status-bar.component.html',
  styleUrl: './status-bar.component.css',
})
export class StatusBarComponent {
  private readonly content = inject(PropertyContentService);
  private readonly preferences = inject(PreferencesService);
  private readonly clock = inject(ClockService);

  protected readonly t = inject(TranslationService).t;

  protected readonly language = this.preferences.language;
  protected readonly appearance = this.preferences.appearance;

  /** Endonyms, straight from the property's configuration — never translated. */
  protected readonly languageOptions = computed<StatusBarMenuOption[]>(() =>
    this.content.settingsConfig().languages.map((option) => ({
      value: option.code,
      label: option.label,
    })),
  );

  /** Labels come from the catalogue, the glyph from the property config. */
  protected readonly appearanceOptions = computed<StatusBarMenuOption[]>(() =>
    this.content.settingsConfig().appearances.map((option) => ({
      value: option.mode,
      label: this.t(`appearance.${option.mode}`),
      icon: option.icon,
    })),
  );

  private readonly activeLanguage = computed(() =>
    this.content.settingsConfig().languages.find((option) => option.code === this.language()),
  );

  /** `DE` — the compact label the concept render shows beside the globe. */
  protected readonly shortLabel = computed(
    () => this.activeLanguage()?.shortLabel ?? this.language().toUpperCase(),
  );

  /**
   * The trigger glyph follows the **resolved** theme, not the selected mode:
   * with `system` selected on a dark system it must show a moon, because it
   * reports what the guest is looking at (02-design.md §8.3). The *menu* below
   * still lists each configured option with its own configured glyph.
   */
  protected readonly appearanceIcon = computed(() => {
    const appearances = this.content.settingsConfig().appearances;
    const resolved = appearances.find((option) => option.mode === this.preferences.resolvedTheme());
    // A property that offers only `system` has no glyph for the resolved
    // theme; then the selected option's own glyph is the honest answer.
    return (resolved ?? appearances.find((option) => option.mode === this.appearance()))?.icon;
  });

  /** `Intl`, not `DatePipe`: `LOCALE_ID` is not runtime-switchable (§7.1). */
  protected readonly time = computed(() =>
    timeFormatter(this.activeLanguage()?.locale ?? this.language()).format(this.clock.now()),
  );

  /** The machine-readable half of `<time>`: a 24-hour local wall-clock time. */
  protected readonly datetime = computed(() => toHoursMinutes(this.clock.now()));

  protected chooseLanguage(code: string): void {
    this.preferences.setLanguage(code);
  }

  protected chooseAppearance(mode: string): void {
    // The menu emits the option's own `value`, which was built from a
    // configured `AppearanceMode` above; `setAppearance()` re-validates it
    // against the configured list regardless, so nothing here is trusted.
    this.preferences.setAppearance(mode as AppearanceMode);
  }
}

/**
 * One `Intl.DateTimeFormat` per locale, kept for the life of the module.
 * Constructing one per tick is the usual quiet performance mistake
 * (02-design.md §8.5) — and this one is rebuilt on every minute *and* on
 * every language change.
 */
const formatters = new Map<string, Intl.DateTimeFormat>();

function timeFormatter(locale: string): Intl.DateTimeFormat {
  let formatter = formatters.get(locale);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit' });
    formatters.set(locale, formatter);
  }
  return formatter;
}

function toHoursMinutes(value: Date): string {
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${pad(value.getHours())}:${pad(value.getMinutes())}`;
}
