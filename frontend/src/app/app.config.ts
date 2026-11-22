import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideIcons } from '@ng-icons/core';

import { routes } from './app.routes';
import { provideGuestApi } from './core/api/provide-guest-api';
import { GuestBootstrapService } from './core/guest/guest-bootstrap.service';
import { REGISTERED_ICONS } from './core/icons/registered-icons';
import { PREFERENCE_GATEWAY } from './core/preferences/preference-gateway';
import { HttpPreferenceGateway } from './core/preferences/http-preference-gateway';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes),
    /* HttpClient + the generated client's Configuration (REQ-501): the
       bearerAuth credential reads DeviceTokenStore at call time. */
    provideGuestApi(),
    /* The one line that names where the guest's preference is stored
       (REQ-122 → REQ-518): the promised swap happened here — the
       sessionStorage stand-in was deleted and this line plus one new class
       is the whole change. `PreferencesService`'s policy is untouched. */
    { provide: PREFERENCE_GATEWAY, useClass: HttpPreferenceGateway },
    /* Bootstrap ordering, and the whole of REQ-126 (a), wire edition
       (02-design.md §5): Angular holds bootstrap until this promise settles,
       so branding, defaults and the stored preference are all final before
       the first paint — no light→dark flash, no de→en flip, structurally.
       Branches (no token → pairing; no data → cache/connection) resolve
       immediately and the route guards own where the router lands. */
    provideAppInitializer(() => inject(GuestBootstrapService).run()),
    /* Every Phosphor glyph in the bundle — registration map and wire-name
       validation set in one place (core/icons/registered-icons.ts). */
    provideIcons(REGISTERED_ICONS),
  ],
};
