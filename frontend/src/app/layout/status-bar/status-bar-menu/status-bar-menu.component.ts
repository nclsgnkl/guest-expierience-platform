import { Component, inject, input, output } from '@angular/core';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import type { ConnectedPosition } from '@angular/cdk/overlay';
import { NgIcon } from '@ng-icons/core';

import { TranslationService } from '../../../core/i18n/translation.service';

/** One row of a status-bar dropdown. `label` arrives translated or as an endonym. */
export interface StatusBarMenuOption {
  /** What is emitted when the row is chosen — a language code or an appearance mode. */
  value: string;
  /** Already in the right language: the caller owns the wording, not this component. */
  label: string;
  /** Optional Phosphor glyph name, from the property's configuration. */
  icon?: string;
}

/**
 * One generic dropdown, used by both status-bar controls (REQ-115). It knows
 * about options, the selected value and where to put its panel — never about
 * languages or appearances, which is what keeps a third setting from needing
 * a third component.
 *
 * Built on `@angular/cdk/menu` (already approved), unstyled: the CDK supplies
 * the keyboard model (Enter/Space to open, arrows to move, Escape to close),
 * focus movement into the panel and focus return to the trigger, plus the
 * ARIA roles. Every pixel is ours. A native `<select>` was rejected in
 * 02-design.md §8.4 — its popup is OS-rendered and cannot take the glass
 * treatment.
 *
 * The trigger's visible content is projected, so the language control can
 * show a globe and a short code while the appearance control shows a single
 * glyph, with no `@if` in here deciding which is which.
 */
@Component({
  selector: 'app-status-bar-menu',
  imports: [CdkMenu, CdkMenuItem, CdkMenuTrigger, NgIcon],
  templateUrl: './status-bar-menu.component.html',
  styleUrl: './status-bar-menu.component.css',
})
export class StatusBarMenuComponent {
  readonly options = input.required<StatusBarMenuOption[]>();
  readonly selectedValue = input.required<string>();
  /** The trigger's accessible name — translated by the caller (REQ-107). */
  readonly triggerLabel = input.required<string>();

  readonly optionSelected = output<string>();

  protected readonly t = inject(TranslationService).t;

  /**
   * Below-end first: the bar sits in the top-right corner, so a panel
   * anchored below-start would hang toward the viewport edge. The fallbacks
   * exist because a property may configure more or longer options than the
   * two this ships with. No pixel offsets — the gap between trigger and panel
   * is the panel's own `margin-top` in the stylesheet, so it stays a token.
   */
  protected readonly menuPosition: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top' },
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top' },
    { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom' },
  ];

  protected isSelected(option: StatusBarMenuOption): boolean {
    return option.value === this.selectedValue();
  }

  protected choose(option: StatusBarMenuOption): void {
    this.optionSelected.emit(option.value);
  }
}
