import { Component, computed, inject, input, signal } from '@angular/core';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import type { ConnectedPosition } from '@angular/cdk/overlay';
import { Router } from '@angular/router';
import { NgIcon } from '@ng-icons/core';

import type { ResolvedMenuItem } from '../../core/content/property.model';
import { TranslationService } from '../../core/i18n/translation.service';
import { routeMatches, splitMenu } from './menu-split';

/**
 * Config-driven nav (docs/design/screens/home.md -> Sidebar). `menuItems`
 * and `settings` arrive as inputs from `ShellComponent` -- this component
 * never fetches content itself, so it renders identically no matter which
 * route hosts it (REQ-007). Settings is a separate input, not part of
 * `menuItems`, matching its structurally-fixed last-group position
 * (REQ-018; docs/features/home-screen-prototype/02-design.md -> "Sidebar
 * config & the Settings decision").
 *
 * sidebar-catalogue-overflow: it also owns the overflow split (REQ-921). The
 * rule itself lives in `menu-split.ts` as pure functions; what stays here is
 * the one piece of state it needs — which module the guest promoted into
 * slot 4 — and the markup for both surfaces. Keeping the sidebar and the
 * popover in one component is what makes "exactly one place decides what looks
 * active" structural rather than a convention (02-design.md §7.1).
 */
@Component({
  selector: 'app-sidebar',
  imports: [CdkMenu, CdkMenuItem, CdkMenuTrigger, NgIcon],
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
  /** The active language's Intl locale — the popover's collation key (REQ-925). */
  locale = input.required<string>();

  private readonly router = inject(Router);

  /**
   * The module the guest picked out of the popover, or `null` for the
   * delivered order (REQ-923). **View state only, and per session** (REQ-929):
   * it has no writer but `choose()`, it is never read back from anywhere, and
   * it dies with the component — a reload, a re-pair or a standby episode all
   * destroy the shell and take it with them. Nothing writes it to storage or to
   * a request body, and nothing here could: this component injects no gateway.
   */
  protected readonly chosen = signal<string | null>(null);

  /**
   * Right of the trigger, vertically centred on it (REQ-916) — the panel stays
   * visually attached to the tile that was pressed rather than flying out of a
   * viewport edge. The two fallbacks cover a panel taller than the space
   * remaining beside a low or a high trigger; without them the outcome would
   * depend on the CDK's default push instead of being declared.
   *
   * No pixel offsets: the gap to the trigger is the panel's own `margin-left`
   * in the stylesheet, so it stays a token — the convention
   * `status-bar-menu.component.css` set with its `margin-top`.
   */
  protected readonly menuPosition: ConnectedPosition[] = [
    { originX: 'end', originY: 'center', overlayX: 'start', overlayY: 'center' },
    { originX: 'end', originY: 'bottom', overlayX: 'start', overlayY: 'bottom' },
    { originX: 'end', originY: 'top', overlayX: 'start', overlayY: 'top' },
  ];

  /** Both rendered surfaces, from one rule (02-design.md §6.1). */
  protected readonly split = computed(() =>
    splitMenu(this.menuItems(), this.activeRoute(), this.chosen(), this.locale()),
  );

  isActive(item: ResolvedMenuItem): boolean {
    return routeMatches(item, this.activeRoute());
  }

  navigateTo(item: ResolvedMenuItem): void {
    this.router.navigateByUrl(item.route === '' ? '/' : item.route);
  }

  /**
   * Promote a module out of the popover into slot 4 and go there. Setting
   * `chosen` is what keeps it in the sidebar after the guest navigates on:
   * once the active route moves elsewhere, `splitMenu`'s active arm stops
   * applying and this is the term that holds the slot (REQ-928).
   */
  protected choose(item: ResolvedMenuItem): void {
    this.chosen.set(item.id);
    this.navigateTo(item);
  }
}
