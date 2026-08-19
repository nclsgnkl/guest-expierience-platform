import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { PropertyContentService } from '../../core/content/property-content.service';
import type { ResolvedMenuItem } from '../../core/content/property.model';
import { TranslationService } from '../../core/i18n/translation.service';

/**
 * Shared stub main content for the 5 `menuItems`/`settings` destinations
 * with no real design yet (REQ-021). Reads `sectionId` from route data,
 * looks up the matching `MenuItem` via `PropertyContentService` (across
 * `menuItems` + `settings`), and renders its configured `label` -- never a
 * hardcoded per-route title. Renders only the main-content region; the
 * background and Sidebar are `ShellComponent`'s job, already present
 * around it.
 */
@Component({
  selector: 'app-section-placeholder',
  templateUrl: './section-placeholder.component.html',
  styleUrl: './section-placeholder.component.css',
})
export class SectionPlaceholderComponent {
  /* The empty-state sentence is app chrome and comes from the catalogue; the
     title beside it is the property's own configured label (REQ-107). */
  protected readonly t = inject(TranslationService).t;

  private readonly route = inject(ActivatedRoute);
  private readonly content = inject(PropertyContentService);

  protected get section(): ResolvedMenuItem | undefined {
    const sectionId = this.route.snapshot.data['sectionId'] as string;
    const property = this.content.property();
    return [...property.menuItems, property.settings].find((item) => item.id === sectionId);
  }
}
