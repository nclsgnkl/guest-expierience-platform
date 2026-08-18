import { provideZonelessChangeDetection } from '@angular/core';
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

describe('HomeContentComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
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
    const fixture = TestBed.createComponent(HomeContentComponent);
    fixture.detectChanges();

    const widgets: HTMLElement = fixture.nativeElement.querySelector('.home-content__widgets');
    expect(widgets).toBeTruthy();
    expect(widgets.textContent?.trim()).toBe('');
    expect(getComputedStyle(widgets).flexGrow).toBe('1');
  });

  it('renders all 4 action cards from the example property', () => {
    const fixture = TestBed.createComponent(HomeContentComponent);
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('app-action-card');
    expect(cards.length).toBe(4);
  });
});
