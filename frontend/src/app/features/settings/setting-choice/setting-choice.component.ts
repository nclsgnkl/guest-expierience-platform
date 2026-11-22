import { Component, inject, input, output } from '@angular/core';
import { NgIcon } from '@ng-icons/core';

import { TranslationService } from '../../../core/i18n/translation.service';

/** One selectable option of a setting. `label` arrives ready to render. */
export interface SettingChoiceOption {
  value: string;
  label: string;
  icon?: string;
}

/**
 * One setting, with **every option visible at once** — the Settings screen's
 * half of REQ-117, against the status bar's compact dropdown. Both bind to the
 * same signals, so the two controls can never disagree.
 *
 * Generic on purpose: it knows a label, a hint and a list of options, so a
 * third setting needs configuration, not a third component. Selection is
 * signalled three ways — background, weight and a check glyph — never colour
 * alone (02-design.md §9.1).
 */
@Component({
  selector: 'app-setting-choice',
  imports: [NgIcon],
  templateUrl: './setting-choice.component.html',
  styleUrl: './setting-choice.component.css',
})
export class SettingChoiceComponent {
  readonly label = input.required<string>();
  readonly hint = input.required<string>();
  readonly options = input.required<SettingChoiceOption[]>();
  readonly selectedValue = input.required<string>();

  readonly optionSelected = output<string>();

  protected readonly t = inject(TranslationService).t;

  protected isSelected(option: SettingChoiceOption): boolean {
    return option.value === this.selectedValue();
  }

  protected choose(option: SettingChoiceOption): void {
    this.optionSelected.emit(option.value);
  }
}
