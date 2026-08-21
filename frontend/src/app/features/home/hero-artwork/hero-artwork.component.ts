import { Component, computed, inject, input } from '@angular/core';

import { GuestAssetService } from '../../../core/guest/guest-asset.service';

/**
 * Hero artwork (REQ-010 → REQ-513). Two variants:
 *
 *   - **no artwork** (id `null`, a 404, or bytes not yet arrived): renders
 *     nothing and occupies nothing — the shell's mesh shows through, exactly
 *     the reviewed no-artwork fallback.
 *   - **artwork present**: the blob arrives through the authorized fetch
 *     (`GuestAssetService`, REQ-513 — never a bare `<img src>`) and renders
 *     as an object URL. Decorative (`alt=""`): the heading block carries the
 *     content.
 *
 * **The artwork is a layer, not a column** (`docs/design/screens/home.md` ->
 * "Hero artwork (2:5)": *bitmap, absolutely positioned `1550 × 999` at
 * `x793 / y155`, sitting above the background and below the layout*). Those
 * are raw values on the 2x Figma canvas, so the CSS box is half of each:
 * `775 × 499.5` at `x396.5 / y77.5` of the 1280 × 832 frame. Corroborated by
 * diffing `docs/design/assets/home.png` against `home-no-artwork.png`: the
 * differing pixels span `{x: 402.4, y: 82.4, w: 764.8, h: 491.2}` CSS px —
 * the same box, inset by the render's own transparent margin.
 *
 * The host is therefore `position: absolute` and out of flow in **both**
 * variants: it can neither push the content column right nor consume the
 * row's gap (guest-frontend-integration review 🔴-1, where an inline
 * `max-width: 18rem` flex item scrolled Home horizontally at both reference
 * viewports; frontend/CLAUDE.md -> Layout, defect D7's lesson). Offsets are
 * relative to `.home-content` (which is the positioned, stacking-context
 * parent), i.e. the frame coordinate minus the content area's own origin
 * (x 172 / y 24 — see the measured baseline in `home.md` -> Layout).
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
      position: absolute;
      /* 396.5 - 172 = 224.5px; 77.5 - 24 = 53.5px; 775 x 499.5px — all at the
         16px baseline root, expressed in rem so the box scales with the
         viewport-proportional root like every other size. */
      left: 14.03125rem;
      top: 3.34375rem;
      width: 48.4375rem;
      height: 31.21875rem;
      /* Behind the layout, above the mesh: .home-content is a stacking
         context, so a negative index paints after its background and before
         its in-flow content (the cards deliberately overlap the artwork, as
         in the reference render). */
      z-index: -1;
      /* Decorative: never intercepts a tap meant for what is behind it. */
      pointer-events: none;
    }
    .hero-artwork__img {
      width: 100%;
      height: 100%;
      /* contain, not cover: a commissioned per-property rendering is
         authored to its own aspect ratio and must never be cropped or
         distorted — it is centred inside the specified box instead. */
      object-fit: contain;
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
