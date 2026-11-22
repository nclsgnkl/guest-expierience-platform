import { Component, input } from '@angular/core';

import type { ResolvedInfoModule } from '../../core/content/property.model';

/**
 * The `info` module's renderer (module-content REQ-976, and
 * `docs/design/screens-inhouse/info.md` — the first module screen ever
 * designed, so this is the template the next twenty inherit).
 *
 * One glass panel holding a `<dl>` of stacked label/value rows. The `<h1>`
 * above it is `SectionHostComponent`'s, not this component's: a module never
 * restates the name of the section it fills.
 *
 * It takes one signal input and injects nothing — no service, and never a
 * generated wire model (REQ-977). The strings it renders are property content,
 * already resolved to one language; **nothing on this screen is app chrome**,
 * which is why it introduces no translation key.
 *
 * **No empty state, and that is a decision rather than an omission** (D19):
 * `info`'s first row is a stay fact always present under `OCCUPIED`, and
 * `/info` renders under nothing else — a vacant kiosk is on `/standby`. A
 * module type that can legitimately deliver nothing needs one; this one cannot.
 *
 * `/info` is also the first screen whose content area contains **no focusable
 * element at all** — no copy button, no touch target; a kiosk's clipboard
 * cannot reach the guest's phone.
 */
@Component({
  selector: 'app-info-module',
  templateUrl: './info-module.component.html',
  styleUrl: './info-module.component.css',
})
export class InfoModuleComponent {
  readonly module = input.required<ResolvedInfoModule>();
}
