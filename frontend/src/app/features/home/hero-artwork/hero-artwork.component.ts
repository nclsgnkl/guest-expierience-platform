import { Component, computed, inject, input } from '@angular/core';

import { GuestAssetService } from '../../../core/guest/guest-asset.service';

/**
 * Hero artwork (REQ-010 → REQ-513). Two variants:
 *
 *   - **no artwork** (id `null`, a 404, or bytes not yet arrived): renders
 *     nothing and occupies nothing — the shell's mesh shows through, exactly
 *     the reviewed no-artwork fallback. The host is `display: contents`, so
 *     an empty template contributes no flex item and consumes no gap
 *     (frontend/CLAUDE.md -> Layout; defect D7's lesson preserved).
 *   - **artwork present**: the blob arrives through the authorized fetch
 *     (`GuestAssetService`, REQ-513 — never a bare `<img src>`) and renders
 *     as an object URL. Decorative (`alt=""`): the heading block carries the
 *     content.
 */
@Component({
  selector: 'app-hero-artwork',
  template: `
    @if (url(); as url) {
      <img class="hero-artwork__img" [src]="url" alt="" />
    }
  `,
  styles: `
    :host {
      display: contents;
    }
    .hero-artwork__img {
      align-self: stretch;
      max-width: 18rem;
      object-fit: cover;
      border-radius: var(--radius-2xl);
    }
  `,
})
export class HeroArtworkComponent {
  artworkAssetId = input.required<number | null>();

  private readonly assets = inject(GuestAssetService);

  protected readonly url = computed(() => {
    const assetId = this.artworkAssetId();
    return assetId === null ? null : this.assets.url(assetId)();
  });
}
