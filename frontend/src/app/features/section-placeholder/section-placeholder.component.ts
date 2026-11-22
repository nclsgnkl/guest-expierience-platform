import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

import { PropertyContentService } from '../../core/content/property-content.service';
import type { ResolvedMenuItem } from '../../core/content/property.model';
import { TranslationService } from '../../core/i18n/translation.service';
import { itemForRoute } from '../../layout/sidebar/menu-split';

/**
 * Shared stub main content for every section with no real design yet
 * (REQ-021, REQ-912). Renders only the main-content region; the background and
 * the sidebar are `ShellComponent`'s job, already present around it.
 *
 * It resolves the section **from the URL**, not from route data
 * (sidebar-catalogue-overflow 02-design.md §4.2). Two changes since the route
 * table stopped enumerating sections:
 *
 *   - there is no `data: { sectionId }` any more — one parameterised child
 *     serves the whole catalogue, so the URL is the only thing that identifies
 *     the section;
 *   - the path is read reactively rather than from `route.snapshot`. The router
 *     reuses this component instance when only `:sectionKey` changes, and a
 *     snapshot taken in the constructor would go stale on exactly that
 *     navigation.
 *
 * The lookup is by `route`, not by `id`: `key` and `route` are independent
 * columns in `menu_item_definition`, and matching on `route` is what makes
 * "every route the catalogue can name resolves" literally true. It shares
 * `itemForRoute()` with `SidebarComponent`, so the two normalise a route
 * identically and can never disagree about Home's `''` ↔ `/`.
 */
@Component({
  selector: 'app-section-placeholder',
  templateUrl: './section-placeholder.component.html',
  styleUrl: './section-placeholder.component.css',
})
export class SectionPlaceholderComponent {
  /* Both sentences are app chrome and come from the catalogue; the title
     beside them is the property's own configured label (REQ-107). */
  protected readonly t = inject(TranslationService).t;

  private readonly route = inject(ActivatedRoute);
  private readonly content = inject(PropertyContentService);

  /** `'/info'`, and `'/'` for Home's empty path — the shape `route` carries. */
  private readonly path = toSignal(this.route.url, { initialValue: [] });

  protected readonly section = computed<ResolvedMenuItem | null>(() => {
    const url =
      '/' +
      this.path()
        .map((segment) => segment.path)
        .join('/');
    const property = this.content.property();
    return itemForRoute([...property.menuItems, property.settings], url);
  });
}
