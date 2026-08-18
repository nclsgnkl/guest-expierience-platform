import { Component } from '@angular/core';

/**
 * Renders only the no-artwork fallback (REQ-010): no `<img>`, no scrim, and
 * (since the 2026-08-18 revision) no background of its own either — the
 * mesh-gradient background it used to paint now lives in `ShellComponent`
 * (REQ-004/REQ-020), so this component's absence simply lets that shared
 * background show through underneath it. Kept as its own component so a
 * future artwork-present variant has a clear seam to slot into
 * (docs/design/screens/home.md -> Hero artwork).
 */
@Component({
  selector: 'app-hero-artwork',
  template: '',
  /* The no-artwork variant renders nothing, so it must also occupy nothing:
     an empty host is still a flex item of `.home-content` and still consumes
     that row's gap, which pushed the whole content column 32px right (defect
     D7, REQ-010). `display: none` takes it out of the flex formatting context
     entirely while keeping the component as the seam a future artwork variant
     slots into. */
  styles: ':host { display: none; }',
})
export class HeroArtworkComponent {}
