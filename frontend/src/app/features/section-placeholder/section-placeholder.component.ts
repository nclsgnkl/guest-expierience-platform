import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { PropertyContentService } from '../../core/content/property-content.service';
import type { MenuItem } from '../../core/content/property.model';

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
  private readonly route = inject(ActivatedRoute);
  private readonly content = inject(PropertyContentService);

  protected get section(): MenuItem | undefined {
    const sectionId = this.route.snapshot.data['sectionId'] as string;
    const property = this.content.getProperty();
    return [...property.menuItems, property.settings].find((item) => item.id === sectionId);
  }
}
