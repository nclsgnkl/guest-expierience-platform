import { Component, effect, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { PropertyContentService } from './core/content/property-content.service';
import type { PropertyBranding } from './core/content/property.model';
import { GuestStateRoutingService } from './core/guest/guest-state-routing.service';
import { PreferencesService } from './core/preferences/preferences.service';

/**
 * Runtime branding hook (REQ-003 → REQ-512): applied whenever a (validated)
 * brand is held, overriding only primitives (never semantic roles) — see
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
  /* Instantiates the state→route effect for the app's lifetime (REQ-517/520):
     injecting it here is what turns the providedIn-root class on. */
  private readonly stateRouting = inject(GuestStateRoutingService);

  constructor() {
    /* The only code in `src/` that touches document.documentElement
       (frontend/CLAUDE.md), so all writes live side by side (REQ-110).

       Branding became an effect (guest-frontend-integration REQ-512): the
       brand now ARRIVES with the session — possibly only after a pairing
       claim, possibly re-arriving changed on a poll. The app initializer has
       already settled before this constructor runs, so on a paired kiosk the
       first effect flush (before first paint) applies the final brand — the
       no-flash structure is unchanged. A null brand (unpaired, connection
       screen, or a rejected invalid wire value) applies nothing: the
       token-layer defaults ARE the design for those states. */
    effect(() => {
      const branding = this.content.branding();
      if (branding !== null) {
        applyBranding(branding);
      }
    });
    effect(() => {
      // Always a resolved value, never `system` and never absent (REQ-105).
      document.documentElement.dataset['theme'] = this.preferences.resolvedTheme();
    });
    effect(() => {
      document.documentElement.lang = this.preferences.language();
    });
  }
}
