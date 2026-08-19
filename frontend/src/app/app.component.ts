import { Component, effect, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { PropertyContentService } from './core/content/property-content.service';
import type { PropertyBranding } from './core/content/property.model';
import { PreferencesService } from './core/preferences/preferences.service';

/**
 * Runtime branding hook (REQ-003): applied once, at app start, overriding
 * only primitives (never semantic roles) -- see
 * docs/design/tokens.md -> Implementation.
 */
function applyBranding(branding: PropertyBranding): void {
  const root = document.documentElement;
  root.style.setProperty('--brand-primary', branding.primary);
  root.style.setProperty('--brand-primary-deep', branding.primaryDeep);
  root.style.setProperty('--brand-secondary', branding.secondary);
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class App {
  private readonly content = inject(PropertyContentService);
  private readonly preferences = inject(PreferencesService);

  constructor() {
    applyBranding(this.content.property().branding);

    /* The only code in `src/` that touches document.documentElement
       (frontend/CLAUDE.md), so the three writes live side by side rather than
       scattered (REQ-110).

       `applyPropertyDefaults()` is deliberately NOT here: it moved to the app
       initializer, because the defaults must be settled *and* the stored
       preference hydrated before the first effect run, or the screen paints
       the default and corrects itself a frame later (02-design.md §6.4).
       `applyBranding()` did not move — it needs no await and the constructor
       still runs before the first paint. */
    effect(() => {
      // Always a resolved value, never `system` and never absent (REQ-105).
      document.documentElement.dataset['theme'] = this.preferences.resolvedTheme();
    });
    effect(() => {
      document.documentElement.lang = this.preferences.language();
    });
  }
}
