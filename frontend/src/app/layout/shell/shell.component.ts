import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { PropertyContentService } from '../../core/content/property-content.service';
import { GuestAssetService } from '../../core/guest/guest-asset.service';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { StalenessPillComponent } from '../staleness-pill/staleness-pill.component';
import { StatusBarComponent } from '../status-bar/status-bar.component';

/** The bundled fallback when the property has no logo asset (REQ-513). */
const LOGO_PLACEHOLDER = 'assets/branding/logo-placeholder.svg';

/**
 * App-wide shell rendered on every route (REQ-020): the mesh-gradient
 * background (REQ-004) + the functional Sidebar (REQ-007), with a
 * `<router-outlet>` for whichever main content the active route supplies.
 * Only this component injects `PropertyContentService` for the sidebar's
 * content -- `SidebarComponent` itself stays a pure input-driven view
 * (docs/features/home-screen-prototype/02-design.md -> "Routing & the
 * shared shell").
 *
 * guest-frontend-integration: the sidebar logo is now an authorized-fetch
 * object URL (REQ-513) with the bundled placeholder as fallback, and the
 * staleness pill (REQ-519, chrome not a screen) renders under the status-bar
 * cluster while the kiosk serves cached content.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, SidebarComponent, StatusBarComponent, StalenessPillComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {
  private readonly content = inject(PropertyContentService);
  private readonly assets = inject(GuestAssetService);
  private readonly router = inject(Router);

  protected readonly property = this.content.property;
  /* The sidebar stays input-driven and injects no content service, so the
     popover's collation locale is passed in like everything else (REQ-925). */
  protected readonly locale = this.content.locale;
  protected readonly activeRoute = signal(this.router.url);

  /** Object URL when the property has a logo and it has arrived; else the placeholder. */
  protected readonly logoAsset = computed(() => {
    const assetId = this.property().logoAssetId;
    if (assetId === null) {
      return LOGO_PLACEHOLDER;
    }
    return this.assets.url(assetId)() ?? LOGO_PLACEHOLDER;
  });

  constructor() {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => this.activeRoute.set(event.urlAfterRedirects));
  }
}
