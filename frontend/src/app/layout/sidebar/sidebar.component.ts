import { Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { NgIcon } from '@ng-icons/core';

import type { MenuItem } from '../../core/content/property.model';

/**
 * Config-driven nav (docs/design/screens/home.md -> Sidebar). `menuItems`
 * and `settings` arrive as inputs from `ShellComponent` -- this component
 * never fetches content itself, so it renders identically no matter which
 * route hosts it (REQ-007). Settings is a separate input, not part of
 * `menuItems`, matching its structurally-fixed last-group position
 * (REQ-018; docs/features/home-screen-prototype/02-design.md -> "Sidebar
 * config & the Settings decision").
 */
@Component({
  selector: 'app-sidebar',
  imports: [NgIcon],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.css',
})
export class SidebarComponent {
  menuItems = input.required<MenuItem[]>();
  settings = input.required<MenuItem>();
  logoAsset = input.required<string>();
  activeRoute = input.required<string>();

  private readonly router = inject(Router);

  isActive(item: MenuItem): boolean {
    const path = item.route === '' ? '/' : item.route;
    return this.activeRoute() === path;
  }

  navigateTo(item: MenuItem): void {
    this.router.navigateByUrl(item.route === '' ? '/' : item.route);
  }
}
