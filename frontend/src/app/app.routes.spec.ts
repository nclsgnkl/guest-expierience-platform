import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorArrowRight,
  phosphorFileText,
  phosphorGearSix,
  phosphorHouse,
  phosphorInfo,
  phosphorKey,
  phosphorLightbulbFilament,
  phosphorMapPin,
  phosphorShoppingBag,
  phosphorSparkle,
} from '@ng-icons/phosphor-icons/regular';
import { describe, expect, it } from 'vitest';

import { routes } from './app.routes';
import { ShellComponent } from './layout/shell/shell.component';

describe('app.routes', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter(routes),
        provideIcons({
          phosphorArrowRight,
          phosphorFileText,
          phosphorGearSix,
          phosphorHouse,
          phosphorInfo,
          phosphorKey,
          phosphorLightbulbFilament,
          phosphorMapPin,
          phosphorShoppingBag,
          phosphorSparkle,
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

  const sections: Array<[string, string]> = [
    ['/info', 'Info'],
    ['/umgebung', 'Umgebung'],
    ['/smart-home', 'Smart Home'],
    ['/services', 'Services'],
    ['/einstellungen', 'Einstellungen'],
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
});
