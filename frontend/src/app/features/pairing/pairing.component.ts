import { Component, computed, inject } from '@angular/core';

import { TranslationService } from '../../core/i18n/translation.service';
import { PairingService } from './pairing.service';

/**
 * The pairing screen (guest-frontend-integration REQ-506/507; spec:
 * docs/design/screens-inhouse/pairing.md). Deliberately **brandless** —
 * branding arrives with the session, which needs the token this screen
 * exists to obtain — and fully unattended: no buttons, no inputs, no
 * focusable elements. The code block never moves or resizes between states
 * (no-jump rule); state changes swap only the status line.
 *
 * `PairingService` is provided HERE, not in root: the poll secret lives in a
 * component-scoped service field and is destroyed with the screen.
 */
@Component({
  selector: 'app-pairing',
  providers: [PairingService],
  templateUrl: './pairing.component.html',
  styleUrl: './pairing.component.css',
})
export class PairingComponent {
  private readonly pairing = inject(PairingService);
  protected readonly t = inject(TranslationService).t;

  protected readonly status = this.pairing.status;

  /** `XXXX XXXX` — a spacing-only grouping; the code itself has no separator. */
  protected readonly groupedCode = computed(() => {
    const code = this.pairing.code();
    return code === null ? '' : `${code.slice(0, 4)} ${code.slice(4)}`;
  });

  /** The old code dims while a replacement is fetched — it must not collapse. */
  protected readonly renewing = computed(() => this.pairing.status() === 'renewing');

  protected readonly statusText = computed(() => this.t(`pairing.${this.pairing.status()}`));

  /** Dot colour: waiting/renewing pulse on the brand role; cooldown/offline are muted. */
  protected readonly troubled = computed(
    () => this.pairing.status() === 'cooldown' || this.pairing.status() === 'offline',
  );

  constructor() {
    this.pairing.start();
  }
}
