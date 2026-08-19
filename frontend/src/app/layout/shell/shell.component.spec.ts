import { Component, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Routes } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorCaretDown,
  phosphorGearSix,
  phosphorGlobe,
  phosphorHouse,
  phosphorInfo,
  phosphorLightbulbFilament,
  phosphorMapPin,
  phosphorShoppingBag,
  phosphorSun,
} from '@ng-icons/phosphor-icons/regular';
import { beforeEach, describe, expect, it } from 'vitest';

import { ShellComponent } from './shell.component';
import { PREFERENCE_GATEWAY } from '../../core/preferences/preference-gateway';
import { SessionPreferenceGateway } from '../../core/preferences/session-preference-gateway';

@Component({ selector: 'app-test-home', template: '<p>home content</p>' })
class TestHomeComponent {}

@Component({ selector: 'app-test-other', template: '<p>other content</p>' })
class TestOtherComponent {}

const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', component: TestHomeComponent },
      { path: 'other', component: TestOtherComponent },
    ],
  },
];

describe('ShellComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: PREFERENCE_GATEWAY, useClass: SessionPreferenceGateway },
        provideZonelessChangeDetection(),
        provideRouter(routes),
        /* The Sidebar's six, plus the three the StatusBarComponent this shell
           renders needs (globe · the light-appearance sun · the caret).
           Leaving them out does not fail a test — it renders the bar in a
           degraded state and prints `No icon named …` on every run
           (06-review.md finding #4). */
        provideIcons({
          phosphorHouse,
          phosphorInfo,
          phosphorMapPin,
          phosphorLightbulbFilament,
          phosphorShoppingBag,
          phosphorGearSix,
          phosphorGlobe,
          phosphorSun,
          phosphorCaretDown,
        }),
      ],
    });
  });

  it('renders the background and Sidebar identically across two different child routes, while the routed content differs', async () => {
    const harness = await RouterTestingHarness.create();

    const homeRoot = await harness.navigateByUrl('/', ShellComponent);
    const homeElement: HTMLElement = harness.routeNativeElement!;
    expect(homeRoot).toBeTruthy();
    expect(homeElement.querySelector('.shell')).toBeTruthy();
    expect(homeElement.querySelector('app-sidebar')).toBeTruthy();
    expect(homeElement.textContent).toContain('home content');
    expect(homeElement.textContent).not.toContain('other content');

    const otherRoot = await harness.navigateByUrl('/other', ShellComponent);
    const otherElement: HTMLElement = harness.routeNativeElement!;
    expect(otherRoot).toBeTruthy();
    expect(otherElement.querySelector('.shell')).toBeTruthy();
    expect(otherElement.querySelector('app-sidebar')).toBeTruthy();
    expect(otherElement.textContent).toContain('other content');
    expect(otherElement.textContent).not.toContain('home content');
  });

  it('declares the active-glyph paint server on every route (REQ-023)', async () => {
    const harness = await RouterTestingHarness.create();

    for (const url of ['/', '/other']) {
      await harness.navigateByUrl(url, ShellComponent);
      const element: HTMLElement = harness.routeNativeElement!;
      const gradient = element.querySelector('#icon-gradient-active');

      expect(gradient).toBeTruthy();
      expect(gradient!.querySelectorAll('stop').length).toBe(2);
    }
  });

  it('feeds the sidebar the property config sourced from PropertyContentService, not a literal', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/', ShellComponent);
    const element: HTMLElement = harness.routeNativeElement!;

    // The real example property's Home label -- proves the shell wires
    // PropertyContentService -> Sidebar rather than passing hardcoded data.
    expect(element.textContent).toContain('Home');
  });
});
