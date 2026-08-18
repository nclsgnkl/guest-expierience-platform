import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { PropertyContentService } from './core/content/property-content.service';
import type { PropertyBranding } from './core/content/property.model';

/**
 * Runtime branding hook (REQ-003): applied once, at app start, overriding
 * only primitives (never semantic roles) -- see
 * docs/design/tokens.md -> Implementation.
 */
function applyBranding(branding: PropertyBranding): void {
  const root = document.documentElement;
  root.style.setProperty('--brand-primary', branding.primary);
  root.style.setProperty('--brand-primary-deep', branding.primaryDeep);
  root.style.setProperty('--brand-secondary', branding.secondary);
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class App {
  private readonly content = inject(PropertyContentService);

  constructor() {
    applyBranding(this.content.getProperty().branding);
  }
}
