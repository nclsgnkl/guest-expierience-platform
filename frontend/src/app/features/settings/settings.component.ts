import { Component, computed, inject } from '@angular/core';

import { PropertyContentService } from '../../core/content/property-content.service';
import type { AppearanceMode } from '../../core/content/property.model';
import { TranslationService } from '../../core/i18n/translation.service';
import { PreferencesService } from '../../core/preferences/preferences.service';
import {
  SettingChoiceComponent,
  type SettingChoiceOption,
} from './setting-choice/setting-choice.component';

/**
 * The `/einstellungen` screen (REQ-116 … REQ-118): both settings, every option
 * visible at once, no dropdown.
 *
 * **No Figma design exists for this screen and none was requested**
 * (02-design.md §9). Every value it uses is composed from existing tokens and
 * `docs/design/design-sheet.md`'s conventions — nothing here may be cited as
 * an extracted spec. If it is ever drawn, that becomes authoritative.
 *
 * It reads and writes exactly the signals the status bar does, so REQ-117's
 * "changing one is reflected in the other" needs no wiring at all: there is
 * only one piece of state.
 */
@Component({
  selector: 'app-settings',
  imports: [SettingChoiceComponent],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.css',
})
export class SettingsComponent {
  private readonly content = inject(PropertyContentService);
  private readonly preferences = inject(PreferencesService);

  protected readonly t = inject(TranslationService).t;

  protected readonly language = this.preferences.language;
  protected readonly appearance = this.preferences.appearance;

  /** Endonyms from the property config — a language's own name, untranslated. */
  protected readonly languageOptions = computed<SettingChoiceOption[]>(() =>
    this.content.settingsConfig().languages.map((option) => ({
      value: option.code,
      label: option.label,
    })),
  );

  protected readonly appearanceOptions = computed<SettingChoiceOption[]>(() =>
    this.content.settingsConfig().appearances.map((option) => ({
      value: option.mode,
      label: this.t(`appearance.${option.mode}`),
      icon: option.icon,
    })),
  );

  protected chooseLanguage(code: string): void {
    this.preferences.setLanguage(code);
  }

  protected chooseAppearance(mode: string): void {
    // Built from a configured `AppearanceMode` above, and re-validated inside
    // `setAppearance()` regardless — nothing here is trusted.
    this.preferences.setAppearance(mode as AppearanceMode);
  }
}
