import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideIcons } from '@ng-icons/core';
import { phosphorSparkle } from '@ng-icons/phosphor-icons/regular';
import { beforeEach, describe, expect, it } from 'vitest';

import { PropertyContentService } from '../../../core/content/property-content.service';
import type { PropertyConfig } from '../../../core/content/property.model';
import { HeadingBlockComponent } from './heading-block.component';

const TEST_PROPERTY: PropertyConfig = {
  guestName: 'Testgast',
  heroSubtitle: 'Test-Untertitel',
  headline: 'Willkommen im Airbnb von {guestName}!',
  paragraph: 'Test-Absatz.',
  logoAsset: 'assets/branding/test-logo.svg',
  hasArtwork: false,
  branding: { primary: '#000', primaryDeep: '#000', secondary: '#000' },
  menuItems: [],
  settings: { id: 'einstellungen', label: 'Einstellungen', icon: 'phosphorGearSix', route: '/einstellungen' },
  cards: [],
};

class MockPropertyContentService {
  getProperty(): PropertyConfig {
    return TEST_PROPERTY;
  }
  getSectionRoute(): string {
    return '/x';
  }
}

describe('HeadingBlockComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideIcons({ phosphorSparkle }),
        { provide: PropertyContentService, useClass: MockPropertyContentService },
      ],
    });
  });

  it('renders subtitle, interpolated headline, and paragraph from the injected content service', () => {
    const fixture = TestBed.createComponent(HeadingBlockComponent);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Test-Untertitel');
    expect(text).toContain('Willkommen im Airbnb von Testgast!');
    expect(text).toContain('Test-Absatz.');
    expect(text).not.toContain('{guestName}');
  });
});
