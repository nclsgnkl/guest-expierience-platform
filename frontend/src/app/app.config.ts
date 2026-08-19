import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorArrowRight,
  phosphorCaretDown,
  phosphorCheck,
  phosphorFileText,
  phosphorGearSix,
  phosphorGlobe,
  phosphorInfo,
  phosphorKey,
  phosphorLightbulbFilament,
  phosphorMapPin,
  phosphorMoon,
  phosphorHouse,
  phosphorShoppingBag,
  phosphorSun,
} from '@ng-icons/phosphor-icons/regular';
// The heading sparkle is the one glyph rendered in the `fill` weight: the
// reference render shows it solid and gradient-filled, where every other glyph
// is `regular` (docs/design/screens/home.md -> Heading block).
import { phosphorSparkleFill } from '@ng-icons/phosphor-icons/fill';

import { routes } from './app.routes';
import { PropertyContentService } from './core/content/property-content.service';
import { PreferencesService } from './core/preferences/preferences.service';
import { PREFERENCE_GATEWAY } from './core/preferences/preference-gateway';
import { SessionPreferenceGateway } from './core/preferences/session-preference-gateway';

// Every Phosphor glyph name that appears in property config data (sidebar
// menu items, settings, action cards) must be registered here so `NgIcon`
// can resolve it by name (docs/design/screens/home.md -> Assets to export;
// regular weight throughout, per the "decided 2026-08-17" note there).
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes),
    /* The one line that names where the guest's preference is stored
       (REQ-122). Swapping the stand-in for the future HTTP gateway is this
       line plus one new class — no component and no line of
       `PreferencesService`'s policy changes. */
    { provide: PREFERENCE_GATEWAY, useClass: SessionPreferenceGateway },
    /* Bootstrap ordering, and the whole of REQ-126 (a): Angular holds
       bootstrap until this promise settles, so `App`'s constructor, its two
       effects and the first paint all happen *after* hydration. The first
       value ever written to <html data-theme>/<html lang> is already the
       final one — there is no light->dark flash and no German->English flip,
       structurally, not by being fast.

       The defaults are applied first, because there is otherwise no option
       list to validate a stored value against (02-design.md §6.4).
       `hydrate()` resolves either way and never rejects — a rejected
       initializer fails the whole bootstrap. */
    provideAppInitializer(() => {
      const content = inject(PropertyContentService);
      const preferences = inject(PreferencesService);
      preferences.applyPropertyDefaults(content.settingsConfig());
      return preferences.hydrate();
    }),
    /* The status bar and the Settings screen add five glyphs (REQ-113,
       REQ-115). `phosphorCircleHalf` -- the natural icon for the `system`
       appearance -- is deliberately NOT registered: decision #4 (2026-08-18)
       keeps `system` out of the shipped option list, so registering it would
       be dead weight in a bundle that ships to a device with no internet.
       A property that ever configures `system` adds it here. */
    provideIcons({
      phosphorArrowRight,
      phosphorCaretDown,
      phosphorCheck,
      phosphorFileText,
      phosphorGearSix,
      phosphorGlobe,
      phosphorInfo,
      phosphorKey,
      phosphorLightbulbFilament,
      phosphorMapPin,
      phosphorMoon,
      phosphorHouse,
      phosphorShoppingBag,
      phosphorSparkleFill,
      phosphorSun,
    }),
  ],
};
