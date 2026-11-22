import { Component, computed, inject } from '@angular/core';

import { PropertyContentService } from '../../core/content/property-content.service';
import { GuestAssetService } from '../../core/guest/guest-asset.service';
import { StalenessBannerComponent } from '../../layout/staleness-banner/staleness-banner.component';
import { StalenessPillComponent } from '../../layout/staleness-pill/staleness-pill.component';
import { StatusBarComponent } from '../../layout/status-bar/status-bar.component';

/**
 * The vacant-room screen (guest-frontend-integration REQ-517; spec:
 * docs/design/screens-inhouse/standby.md). Property-level only — the backend
 * is maximally data-sparing while vacant, and this screen extends that to
 * the UI surface: no sidebar, no cards, no navigation; nothing touchable but
 * the status bar (clock, language, appearance — all fully functional; a
 * preference write while vacant 409s silently by design).
 *
 * The texts are the SERVER-RESOLVED standby templates, rendered verbatim
 * per the active language. The logo, when the property has one, arrives via
 * the authorized asset fetch; no logo → the block starts with the headline
 * (no placeholder tile — deliberately not the sidebar's chrome treatment).
 */
@Component({
  selector: 'app-standby',
  imports: [StatusBarComponent, StalenessPillComponent, StalenessBannerComponent],
  templateUrl: './standby.component.html',
  styleUrl: './standby.component.css',
})
export class StandbyComponent {
  private readonly content = inject(PropertyContentService);
  private readonly assets = inject(GuestAssetService);

  protected readonly property = this.content.property;

  protected readonly logoUrl = computed(() => {
    const assetId = this.property().logoAssetId;
    return assetId === null ? null : this.assets.url(assetId)();
  });
}
