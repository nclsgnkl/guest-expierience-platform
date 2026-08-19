import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorArrowRight,
  phosphorCaretDown,
  phosphorCheck,
  phosphorFileText,
  phosphorGearSix,
  phosphorGlobe,
  phosphorHouse,
  phosphorInfo,
  phosphorKey,
  phosphorLightbulbFilament,
  phosphorMapPin,
  phosphorMoon,
  phosphorShoppingBag,
  phosphorSun,
} from '@ng-icons/phosphor-icons/regular';
import { phosphorSparkleFill } from '@ng-icons/phosphor-icons/fill';
import { describe, expect, it } from 'vitest';

import { routes } from './app.routes';
import { ShellComponent } from './layout/shell/shell.component';
import { PREFERENCE_GATEWAY } from './core/preferences/preference-gateway';
import { SessionPreferenceGateway } from './core/preferences/session-preference-gateway';

describe('app.routes', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [
        { provide: PREFERENCE_GATEWAY, useClass: SessionPreferenceGateway },
        provideZonelessChangeDetection(),
        provideRouter(routes),
        provideIcons({
          phosphorArrowRight,
          phosphorCaretDown,
          phosphorCheck,
          phosphorFileText,
          phosphorGearSix,
          phosphorGlobe,
          phosphorHouse,
          phosphorInfo,
          phosphorKey,
          phosphorLightbulbFilament,
          phosphorMapPin,
          phosphorMoon,
          phosphorShoppingBag,
          phosphorSparkleFill,
          phosphorSun,
        }),
      ],
    });
  }

  it("resolves '' to Home content inside the shared shell", async () => {
    setup();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/', ShellComponent);
    const el: HTMLElement = harness.routeNativeElement!;

    expect(el.querySelector('.shell')).toBeTruthy();
    expect(el.querySelector('app-sidebar')).toBeTruthy();
    expect(el.querySelector('app-home-content')).toBeTruthy();
  });

  /* '/einstellungen' is deliberately absent: since REQ-116 it renders a real
     screen, not the placeholder. It has its own case below, and S34 extends
     the coverage. */
  const sections: Array<[string, string]> = [
    ['/info', 'Info'],
    ['/umgebung', 'Umgebung'],
    ['/smart-home', 'Smart Home'],
    ['/services', 'Services'],
  ];

  for (const [path, expectedLabel] of sections) {
    it(`resolves ${path} to the section placeholder showing "${expectedLabel}", inside the shared shell`, async () => {
      setup();
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(path, ShellComponent);
      const el: HTMLElement = harness.routeNativeElement!;

      expect(el.querySelector('.shell')).toBeTruthy();
      expect(el.querySelector('app-sidebar')).toBeTruthy();
      expect(el.querySelector('app-section-placeholder')).toBeTruthy();
      expect(el.textContent).toContain(expectedLabel);
    });
  }

  it('resolves /einstellungen to the Settings screen, inside the shared shell', async () => {
    setup();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/einstellungen', ShellComponent);
    const el: HTMLElement = harness.routeNativeElement!;

    expect(el.querySelector('.shell')).toBeTruthy();
    expect(el.querySelector('app-sidebar')).toBeTruthy();
    expect(el.querySelector('app-settings')).toBeTruthy();
    expect(el.querySelector('app-section-placeholder')).toBeFalsy();
  });
});
