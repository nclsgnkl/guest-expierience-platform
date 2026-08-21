import { Routes } from '@angular/router';

import {
  connectionGuard,
  pairingGuard,
  shellGuard,
  standbyGuard,
} from './core/guest/guest-state.guards';
import { ConnectionComponent } from './features/connection/connection.component';
import { HomeContentComponent } from './features/home/home-content.component';
import { PairingComponent } from './features/pairing/pairing.component';
import { SectionPlaceholderComponent } from './features/section-placeholder/section-placeholder.component';
import { SettingsComponent } from './features/settings/settings.component';
import { StandbyComponent } from './features/standby/standby.component';
import { ShellComponent } from './layout/shell/shell.component';

/**
 * Two worlds (guest-frontend-integration REQ-506/517/520, 02-design.md §5):
 *
 *   - the three full-screen states — /pairing, /standby, /connection —
 *     render OUTSIDE `ShellComponent` (no sidebar, no app chrome), guarded
 *     in both directions by the state guards;
 *   - everything else renders inside the shared shell (REQ-020), exactly as
 *     before. Home and each section placeholder are child routes rendered
 *     into its `<router-outlet>`; the 4 action cards resolve to these via
 *     their server-resolved routes (REQ-511) rather than owning routes of
 *     their own.
 *
 * Runtime flips between the worlds are `GuestStateRoutingService`'s effect;
 * the guards here arbitrate initial URLs and deep links with the identical
 * precedence.
 */
export const routes: Routes = [
  { path: 'pairing', component: PairingComponent, canActivate: [pairingGuard] },
  { path: 'standby', component: StandbyComponent, canActivate: [standbyGuard] },
  { path: 'connection', component: ConnectionComponent, canActivate: [connectionGuard] },
  {
    path: '',
    component: ShellComponent,
    canActivate: [shellGuard],
    children: [
      { path: '', component: HomeContentComponent },
      { path: 'info', component: SectionPlaceholderComponent, data: { sectionId: 'info' } },
      { path: 'umgebung', component: SectionPlaceholderComponent, data: { sectionId: 'umgebung' } },
      {
        path: 'smart-home',
        component: SectionPlaceholderComponent,
        data: { sectionId: 'smart-home' },
      },
      { path: 'services', component: SectionPlaceholderComponent, data: { sectionId: 'services' } },
      /* The one section with a real screen (REQ-116). It carries no
         `sectionId`: `SettingsComponent` reads no route data, and the other
         four routes keep the placeholder, which is therefore still needed and
         is not deleted. */
      { path: 'einstellungen', component: SettingsComponent },
    ],
  },
];
