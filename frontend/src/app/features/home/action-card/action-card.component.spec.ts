import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { phosphorArrowRight, phosphorKey } from '@ng-icons/phosphor-icons/regular';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PropertyContentService } from '../../../core/content/property-content.service';
import type { ActionCard } from '../../../core/content/property.model';
import { ActionCardComponent } from './action-card.component';

const TEST_CARD: ActionCard = {
  id: 'check-out',
  icon: 'phosphorKey',
  header: 'Test-Header',
  description: 'Test-Beschreibung',
  targetSectionId: 'info',
};

const RESOLVED_ROUTE = '/info';

class MockPropertyContentService {
  getSectionRoute(targetSectionId: string): string {
    if (targetSectionId !== TEST_CARD.targetSectionId) {
      throw new Error(`unexpected id: ${targetSectionId}`);
    }
    return RESOLVED_ROUTE;
  }
}

describe('ActionCardComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideIcons({ phosphorKey, phosphorArrowRight }),
        { provide: PropertyContentService, useClass: MockPropertyContentService },
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

  it('navigates to the route resolved from targetSectionId on click', () => {
    const fixture = TestBed.createComponent(ActionCardComponent);
    fixture.componentRef.setInput('card', TEST_CARD);
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.action-card');
    button.click();

    expect(navigateSpy).toHaveBeenCalledWith(RESOLVED_ROUTE);
  });
});
