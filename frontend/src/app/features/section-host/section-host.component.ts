import { Component, Type, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NgComponentOutlet } from '@angular/common';
import { ActivatedRoute } from '@angular/router';

import { PropertyContentService } from '../../core/content/property-content.service';
import type { ResolvedMenuItem, ResolvedModule } from '../../core/content/property.model';
import type { ModuleKind } from '../../core/guest/module-registry';
import { itemForRoute } from '../../layout/sidebar/menu-split';
import { InfoModuleComponent } from '../info-module/info-module.component';
import { SectionPlaceholderComponent } from '../section-placeholder/section-placeholder.component';

/**
 * The component behind every catalogue section route (module-content REQ-974,
 * 02-design.md §6.2). It replaces `SectionPlaceholderComponent` at
 * `:sectionKey` and `**` — the placeholder is now one of its two arms, not the
 * route target.
 *
 * It resolves the section **from the URL**, with `itemForRoute()` from
 * `layout/sidebar/menu-split.ts` — the shared function `SidebarComponent` and
 * `SectionPlaceholderComponent` already use, so a route normalises identically
 * everywhere and no second lookup exists (`frontend/CLAUDE.md` → Structure).
 * The path is read reactively, not from `route.snapshot`: the router reuses
 * this instance when only `:sectionKey` changes.
 *
 * Two arms, and the second one covers three situations the guest is
 * deliberately not asked to tell apart:
 *
 *   - the item's `module` is non-null → the section's own `<h1>` plus that
 *     kind's registered renderer;
 *   - `module === null`, or no item claims this route → `<app-section-placeholder />`,
 *     which keeps both of its own arms (`section.comingSoon` for a claimed
 *     route, `section.unavailable` for one the menu does not claim).
 *
 * The `<h1>` lives here, not in the module component: it is the first row of
 * `info.md`'s "What generalises" table — the section's catalogue label, which
 * every module type shares and **no module ever restates**.
 */
@Component({
  selector: 'app-section-host',
  imports: [NgComponentOutlet, SectionPlaceholderComponent],
  templateUrl: './section-host.component.html',
  styleUrl: './section-host.component.css',
})
export class SectionHostComponent {
  /**
   * The render half of the registry (02-design.md §4.4). It lives here rather
   * than in `core/guest/module-registry.ts` because that file importing a
   * feature component would be the app's first `core → features` import.
   *
   * `satisfies Record<ModuleKind, Type<unknown>>` is what keeps the split from
   * re-opening the drift REQ-970/971 exist to close: the map is total over the
   * same `ModuleKind` the registry is keyed on, so a kind with no renderer
   * fails the build, and a renderer for a kind that does not exist fails the
   * build as an excess property.
   */
  protected static readonly RENDERERS = {
    info: InfoModuleComponent,
  } satisfies Record<ModuleKind, Type<unknown>>;

  private readonly route = inject(ActivatedRoute);
  private readonly content = inject(PropertyContentService);

  /** `'/info'`, and `'/'` for Home's empty path — the shape `route` carries. */
  private readonly path = toSignal(this.route.url, { initialValue: [] });

  private readonly section = computed<ResolvedMenuItem | null>(() => {
    const url =
      '/' +
      this.path()
        .map((segment) => segment.path)
        .join('/');
    const property = this.content.property();
    return itemForRoute([...property.menuItems, property.settings], url);
  });

  protected readonly label = computed<string | null>(() => this.section()?.label ?? null);
  protected readonly module = computed<ResolvedModule | null>(() => this.section()?.module ?? null);

  protected renderer(module: ResolvedModule): Type<unknown> {
    return SectionHostComponent.RENDERERS[module.type];
  }
}
