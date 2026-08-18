import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorArrowRight,
  phosphorFileText,
  phosphorGearSix,
  phosphorInfo,
  phosphorKey,
  phosphorLightbulbFilament,
  phosphorMapPin,
  phosphorHouse,
  phosphorShoppingBag,
  phosphorSparkle,
} from '@ng-icons/phosphor-icons/regular';

import { routes } from './app.routes';

// Every Phosphor glyph name that appears in property config data (sidebar
// menu items, settings, action cards) must be registered here so `NgIcon`
// can resolve it by name (docs/design/screens/home.md -> Assets to export;
// regular weight throughout, per the "decided 2026-08-17" note there).
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideIcons({
      phosphorArrowRight,
      phosphorFileText,
      phosphorGearSix,
      phosphorInfo,
      phosphorKey,
      phosphorLightbulbFilament,
      phosphorMapPin,
      phosphorHouse,
      phosphorShoppingBag,
      phosphorSparkle,
    }),
  ],
};
