import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideIcons } from '@ng-icons/core';
import { phosphorSparkleFill } from '@ng-icons/phosphor-icons/fill';
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
        provideIcons({ phosphorSparkleFill }),
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

  it('paints the sparkle with the shared gradient paint server, in the filled weight', () => {
    // What jsdom CAN decide about a rendering defect: which glyph is requested
    // and whether it carries the class the global paint rule keys off. The
    // pixels themselves are a browser check (frontend/tools/measure.mjs and
    // the review's visual step) -- REQ-023's mechanism, applied to the
    // heading sparkle after the round-3 review found it flat and outlined.
    const fixture = TestBed.createComponent(HeadingBlockComponent);
    fixture.detectChanges();
    const icon = fixture.nativeElement.querySelector('ng-icon');

    expect(icon.getAttribute('name')).toBe('phosphorSparkleFill');
    expect(icon.classList.contains('icon-gradient-active')).toBe(true);
  });
});
