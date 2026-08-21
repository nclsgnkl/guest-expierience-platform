import { Component, computed, inject } from '@angular/core';
import { NgIcon } from '@ng-icons/core';

import { PropertyContentService } from '../../../core/content/property-content.service';

/**
 * Subtitle+Sparkle row, headline and paragraph
 * (docs/design/screens/home.md -> Heading block). Since
 * guest-frontend-integration (REQ-511) every string arrives
 * **server-resolved** (templates, `{stay.displayName}`, `|time`/`|date` —
 * already per language) and is rendered verbatim: the client's own
 * `{guestName}` interpolation is gone.
 */
@Component({
  selector: 'app-heading-block',
  imports: [NgIcon],
  templateUrl: './heading-block.component.html',
  styleUrl: './heading-block.component.css',
})
export class HeadingBlockComponent {
  private readonly content = inject(PropertyContentService);

  /** Non-null by construction: `HomeContentComponent` renders this component
      only while home content is held (occupied room, payload arrived). */
  protected readonly home = computed(() => this.content.property().home!);
}
