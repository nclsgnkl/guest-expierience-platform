import { Routes } from '@angular/router';

import { HomeContentComponent } from './features/home/home-content.component';
import { SectionPlaceholderComponent } from './features/section-placeholder/section-placeholder.component';
import { SettingsComponent } from './features/settings/settings.component';
import { ShellComponent } from './layout/shell/shell.component';

/**
 * `ShellComponent` is the parent for every route (REQ-020). Home and each
 * section placeholder are child routes rendered into its `<router-outlet>`.
 * 5 non-Home routes total -- `menuItems`' 4 non-Home entries + the fixed
 * Settings route -- not 9; the 4 action cards resolve to one of these via
 * `targetSectionId` (REQ-019) rather than owning routes of their own.
 * See docs/features/home-screen-prototype/02-design.md -> "Routing & the
 * shared shell".
 */
export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
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
