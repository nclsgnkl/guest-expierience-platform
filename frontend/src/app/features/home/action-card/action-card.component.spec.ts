import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { phosphorArrowRight, phosphorKey } from '@ng-icons/phosphor-icons/regular';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ResolvedActionCard } from '../../../core/content/property.model';
import { ActionCardComponent } from './action-card.component';

const TEST_CARD: ResolvedActionCard = {
  id: 'check-out-0',
  icon: 'phosphorKey',
  header: 'Test-Header',
  description: 'Test-Beschreibung',
  /* The server-resolved route (REQ-511) — used directly; nothing is looked
     up, so no content service is provided here at all: the component is
     purely input-driven. */
  route: '/info',
};

describe('ActionCardComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideIcons({ phosphorKey, phosphorArrowRight }),
      ],
    });
  });

  it('renders header, description and icon from the card input', () => {
    const fixture = TestBed.createComponent(ActionCardComponent);
    fixture.componentRef.setInput('card', TEST_CARD);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Test-Header');
    expect(text).toContain('Test-Beschreibung');
  });

  it('navigates to the card\'s server-resolved route on click (REQ-511)', () => {
    const fixture = TestBed.createComponent(ActionCardComponent);
    fixture.componentRef.setInput('card', TEST_CARD);
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.action-card');
    button.click();

    expect(navigateSpy).toHaveBeenCalledWith('/info');
  });

  it("an empty route (Home) navigates to '/'", () => {
    const fixture = TestBed.createComponent(ActionCardComponent);
    fixture.componentRef.setInput('card', { ...TEST_CARD, route: '' });
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    (fixture.nativeElement.querySelector('.action-card') as HTMLButtonElement).click();

    expect(navigateSpy).toHaveBeenCalledWith('/');
  });
});
