import { Component, computed, inject } from '@angular/core';

import { PropertyContentService } from '../../core/content/property-content.service';
import { ActionCardComponent } from './action-card/action-card.component';
import { HeadingBlockComponent } from './heading-block/heading-block.component';
import { HeroArtworkComponent } from './hero-artwork/hero-artwork.component';

/**
 * Home's main-content region (docs/design/screens/home.md -> Layout's
 * `Welcome` row): heading block + card row + hero artwork + a reserved,
 * empty `flex: 1` widget-column element (REQ-014).
 *
 * guest-frontend-integration: the content is the server's home payload
 * (REQ-511) — 0–4 cards lay out in the same flex row (fewer cards simply
 * leave space; no layout logic change), and the whole region renders only
 * while home content is held (`home()` non-null). Since
 * guest-payload-consolidation the home half arrives in the same payload as
 * the occupancy, so an occupied room can no longer be held without it — the
 * `@if` stays as defence against a payload neither the mapper nor the
 * occupancy guard should ever let through.
 */
@Component({
  selector: 'app-home-content',
  imports: [HeadingBlockComponent, ActionCardComponent, HeroArtworkComponent],
  templateUrl: './home-content.component.html',
  styleUrl: './home-content.component.css',
})
export class HomeContentComponent {
  private readonly content = inject(PropertyContentService);

  protected readonly home = computed(() => this.content.property().home);
}
