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
      /* The one section with a real screen (REQ-116). It is declared BEFORE
         `:sectionKey` and the order is load-bearing: a parameterised path
         would otherwise swallow `/einstellungen` and the real Settings screen
         would be replaced by the placeholder (REQ-914). */
      { path: 'einstellungen', component: SettingsComponent },
      /* Every other catalogue route, whatever the property has enabled
         (REQ-912; sidebar-catalogue-overflow 02-design.md §4.1, D4). The four
         hard-coded section children and their `data: { sectionId }` are gone:
         the catalogue is a database table now, so a route table that
         enumerates it would need editing every time a property enables a
         section. `SectionPlaceholderComponent` resolves the section from the
         URL instead, by `route`. */
      { path: ':sectionKey', component: SectionPlaceholderComponent },
      /* The shell's LAST CHILD, not a top-level route. `''` matches every URL
         as a prefix, so anything that is not /pairing, /standby or /connection
         (declared first, matched first) descends into these children;
         `:sectionKey` catches one segment and this catches everything deeper.
         A top-level `**` would be unreachable, and would render outside the
         shell if it ever were reached. */
      { path: '**', component: SectionPlaceholderComponent },
    ],
  },
];
