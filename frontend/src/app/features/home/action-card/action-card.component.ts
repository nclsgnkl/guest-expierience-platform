import { Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { NgIcon } from '@ng-icons/core';

import type { ResolvedActionCard } from '../../../core/content/property.model';

/**
 * Glass Panel action card (docs/design/screens/home.md -> Glass Panel).
 * Navigates via the card's **server-resolved `targetRoute`**
 * (guest-frontend-integration REQ-511): route resolution moved server-side
 * with the ceiling rule, so the wire value is authoritative — re-deriving it
 * client-side from the card's target key would re-implement enablement
 * resolution the server already owns (REQ-515).
 */
@Component({
  selector: 'app-action-card',
  imports: [NgIcon],
  templateUrl: './action-card.component.html',
  styleUrl: './action-card.component.css',
})
export class ActionCardComponent {
  card = input.required<ResolvedActionCard>();

  private readonly router = inject(Router);

  activate(): void {
    const route = this.card().route;
    this.router.navigateByUrl(route === '' ? '/' : route);
  }
}
