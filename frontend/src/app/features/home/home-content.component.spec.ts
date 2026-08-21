import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorArrowRight,
  phosphorFileText,
  phosphorKey,
  phosphorLightbulbFilament,
  phosphorMapPin,
} from '@ng-icons/phosphor-icons/regular';
import { phosphorSparkleFill } from '@ng-icons/phosphor-icons/fill';
import { beforeEach, describe, expect, it } from 'vitest';

import { HomeContentComponent } from './home-content.component';
import { PropertyContentService } from '../../core/content/property-content.service';
import type {
  ResolvedActionCard,
  ResolvedPropertyConfig,
} from '../../core/content/property.model';
import { GuestAssetService } from '../../core/guest/guest-asset.service';

/**
 * REQ-511/515 — the variable-content layout: 0 and 4 cards both render
 * without special-casing (flex handles fewer items), and the reserved
 * widget column survives the wire era.
 */
function card(id: string, icon: string): ResolvedActionCard {
  return {
    id,
    icon,
    header: `H-${id}`,
    description: `D-${id}`,
    targetSectionId: 'info',
    route: '/info',
  };
}

function config(cards: ResolvedActionCard[]): ResolvedPropertyConfig {
  return {
    propertyName: 'Testhaus',
    logoAssetId: null,
    branding: null,
    menuItems: [],
    settings: {
      id: 'einstellungen',
      label: 'Einstellungen-Test',
      icon: 'phosphorGearSix',
      route: '/einstellungen',
    },
    settingsConfig: {
      languages: [{ code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' }],
      defaultLanguage: 'de',
      appearances: [{ mode: 'light', icon: 'phosphorSun' }],
      defaultAppearance: 'light',
    },
    standbyHeadline: 'Testhaus',
    standbyMessage: 'Test-Standby',
    home: {
      heroSubtitle: 'Test-Untertitel',
      headline: 'Test-Willkommen!',
      paragraph: 'Test-Absatz.',
      artworkAssetId: null,
      cards,
    },
  };
}

describe('HomeContentComponent', () => {
  const property = signal<ResolvedPropertyConfig>(config([]));

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: PropertyContentService, useValue: { property: property.asReadonly() } },
        { provide: GuestAssetService, useValue: { url: () => signal(null).asReadonly() } },
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideIcons({
          phosphorSparkleFill,
          phosphorArrowRight,
          phosphorKey,
          phosphorLightbulbFilament,
          phosphorMapPin,
          phosphorFileText,
        }),
      ],
    });
  });

  it('renders an empty, present flex:1 widget-column element (REQ-014)', () => {
    property.set(config([]));
    const fixture = TestBed.createComponent(HomeContentComponent);
    fixture.detectChanges();

    const widgets: HTMLElement = fixture.nativeElement.querySelector('.home-content__widgets');
    expect(widgets).toBeTruthy();
    expect(widgets.textContent?.trim()).toBe('');
    expect(getComputedStyle(widgets).flexGrow).toBe('1');
  });

  it('renders 0 cards without overflow logic — the row is simply empty', () => {
    property.set(config([]));
    const fixture = TestBed.createComponent(HomeContentComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('app-action-card').length).toBe(0);
    expect(fixture.nativeElement.querySelector('.home-content__cards')).toBeTruthy();
  });

  it('renders all 4 server cards in server order', () => {
    property.set(
      config([
        card('a-0', 'phosphorKey'),
        card('b-1', 'phosphorLightbulbFilament'),
        card('c-2', 'phosphorMapPin'),
        card('d-3', 'phosphorFileText'),
      ]),
    );
    const fixture = TestBed.createComponent(HomeContentComponent);
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('app-action-card');
    expect(cards.length).toBe(4);
    expect((fixture.nativeElement.textContent as string).indexOf('H-a-0')).toBeLessThan(
      (fixture.nativeElement.textContent as string).indexOf('H-d-3'),
    );
  });
});
