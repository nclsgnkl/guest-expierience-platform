import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { PropertyContentService } from '../../core/content/property-content.service';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { StatusBarComponent } from '../status-bar/status-bar.component';

/**
 * App-wide shell rendered on every route (REQ-020): the mesh-gradient
 * background (REQ-004) + the functional Sidebar (REQ-007), with a
 * `<router-outlet>` for whichever main content the active route supplies.
 * Only this component injects `PropertyContentService` for the sidebar's
 * content -- `SidebarComponent` itself stays a pure input-driven view
 * (docs/features/home-screen-prototype/02-design.md -> "Routing & the
 * shared shell").
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, SidebarComponent, StatusBarComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {
  private readonly content = inject(PropertyContentService);
  private readonly router = inject(Router);

  protected readonly property = this.content.property;
  protected readonly activeRoute = signal(this.router.url);

  constructor() {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => this.activeRoute.set(event.urlAfterRedirects));
  }
}
