import { Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { NgIcon } from '@ng-icons/core';

import { PropertyContentService } from '../../../core/content/property-content.service';
import type { ActionCard } from '../../../core/content/property.model';

/**
 * Glass Panel action card (docs/design/screens/home.md -> Glass Panel).
 * Resolves its destination from `card().targetSectionId` via
 * `PropertyContentService.getSectionRoute()` rather than owning a route of
 * its own (REQ-009, REQ-019) — the exact mechanism `SidebarComponent`'s own
 * items use, which is why a card tap and the corresponding sidebar tap are
 * indistinguishable in outcome.
 */
@Component({
  selector: 'app-action-card',
  imports: [NgIcon],
  templateUrl: './action-card.component.html',
  styleUrl: './action-card.component.css',
})
export class ActionCardComponent {
  card = input.required<ActionCard>();

  private readonly content = inject(PropertyContentService);
  private readonly router = inject(Router);

  activate(): void {
    const route = this.content.getSectionRoute(this.card().targetSectionId);
    this.router.navigateByUrl(route === '' ? '/' : route);
  }
}
