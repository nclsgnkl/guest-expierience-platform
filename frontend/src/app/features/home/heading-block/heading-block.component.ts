import { Component, inject } from '@angular/core';
import { NgIcon } from '@ng-icons/core';

import { PropertyContentService } from '../../../core/content/property-content.service';

/**
 * Subtitle+Sparkle row, headline (guest name interpolated) and paragraph
 * (docs/design/screens/home.md -> Heading block). Property-level content,
 * not per-guest/per-stay personalisation (REQ-008; PRD -> Assumptions).
 */
@Component({
  selector: 'app-heading-block',
  imports: [NgIcon],
  templateUrl: './heading-block.component.html',
  styleUrl: './heading-block.component.css',
})
export class HeadingBlockComponent {
  private readonly content = inject(PropertyContentService);

  protected readonly property = this.content.getProperty();

  protected get headline(): string {
    return this.property.headline.replace('{guestName}', this.property.guestName);
  }
}
