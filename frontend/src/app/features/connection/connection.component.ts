import { Component, inject } from '@angular/core';
import { NgIcon } from '@ng-icons/core';

import { TranslationService } from '../../core/i18n/translation.service';

/**
 * The last resort (guest-frontend-integration REQ-520; spec:
 * docs/design/screens-inhouse/connection.md): a paired kiosk cannot reach
 * the backend AND holds no last-known-good cache. Honest, guest-presentable,
 * self-healing — `GuestDataService` retries with backoff (5→30 s) and the
 * guards leave this screen unaided the moment the backend answers. No
 * button: a manual "retry" would imply the guest is responsible for
 * infrastructure. No status bar: the clock would suggest a liveness the
 * device cannot vouch for.
 */
@Component({
  selector: 'app-connection',
  imports: [NgIcon],
  templateUrl: './connection.component.html',
  styleUrl: './connection.component.css',
})
export class ConnectionComponent {
  protected readonly t = inject(TranslationService).t;
}
