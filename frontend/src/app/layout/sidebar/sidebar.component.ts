import { Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { NgIcon } from '@ng-icons/core';

import type { ResolvedMenuItem } from '../../core/content/property.model';
import { TranslationService } from '../../core/i18n/translation.service';

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
  /* The nav's accessible name is app chrome, not property content, so it
     comes from the catalogue like every other chrome string (REQ-107,
     frontend/CLAUDE.md -> Appearance & language). The menu *labels* beside it
     are property-authored and still arrive resolved, as inputs. */
  protected readonly t = inject(TranslationService).t;

  menuItems = input.required<ResolvedMenuItem[]>();
  settings = input.required<ResolvedMenuItem>();
  logoAsset = input.required<string>();
  activeRoute = input.required<string>();

  private readonly router = inject(Router);

  isActive(item: ResolvedMenuItem): boolean {
    const path = item.route === '' ? '/' : item.route;
    return this.activeRoute() === path;
  }

  navigateTo(item: ResolvedMenuItem): void {
    this.router.navigateByUrl(item.route === '' ? '/' : item.route);
  }
}
