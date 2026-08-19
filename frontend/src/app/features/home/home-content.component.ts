import { Component, inject } from '@angular/core';

import { PropertyContentService } from '../../core/content/property-content.service';
import { ActionCardComponent } from './action-card/action-card.component';
import { HeadingBlockComponent } from './heading-block/heading-block.component';
import { HeroArtworkComponent } from './hero-artwork/hero-artwork.component';

/**
 * Home's main-content region (docs/design/screens/home.md -> Layout's
 * `Welcome` row): heading block + card row + hero artwork + a reserved,
 * empty `flex: 1` widget-column element (REQ-014). No longer composes the
 * Sidebar or the background -- both moved to `ShellComponent`, since
 * they're shared by every route now.
 */
@Component({
  selector: 'app-home-content',
  imports: [HeadingBlockComponent, ActionCardComponent, HeroArtworkComponent],
  templateUrl: './home-content.component.html',
  styleUrl: './home-content.component.css',
})
export class HomeContentComponent {
  private readonly content = inject(PropertyContentService);

  protected readonly property = this.content.property;
}
