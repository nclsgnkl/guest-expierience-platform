import { provideZonelessChangeDetection } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { PropertyContentService } from '../../core/content/property-content.service';
import type { PropertyConfig } from '../../core/content/property.model';
import { SectionPlaceholderComponent } from './section-placeholder.component';

const TEST_PROPERTY: PropertyConfig = {
  guestName: 'X',
  heroSubtitle: '',
  headline: '',
  paragraph: '',
  logoAsset: '',
  hasArtwork: false,
  branding: { primary: '#000', primaryDeep: '#000', secondary: '#000' },
  menuItems: [
    { id: 'info', label: 'Info-Label', icon: 'phosphorInfo', route: '/info' },
    { id: 'umgebung', label: 'Umgebung-Label', icon: 'phosphorMapPin', route: '/umgebung' },
  ],
  settings: { id: 'einstellungen', label: 'Einstellungen-Label', icon: 'phosphorGearSix', route: '/einstellungen' },
  cards: [],
};

class MockPropertyContentService {
  getProperty(): PropertyConfig {
    return TEST_PROPERTY;
  }
}

function renderWithSectionId(sectionId: string) {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      { provide: PropertyContentService, useClass: MockPropertyContentService },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { data: { sectionId } } },
      },
    ],
  });
  const fixture = TestBed.createComponent(SectionPlaceholderComponent);
  fixture.detectChanges();
  return fixture;
}

describe('SectionPlaceholderComponent', () => {
  it('renders the label of the menuItems section matching route data', () => {
    const fixture = renderWithSectionId('info');
    expect((fixture.nativeElement.textContent as string)).toContain('Info-Label');
  });

  it('renders the label of a different section (umgebung) matching route data', () => {
    const fixture = renderWithSectionId('umgebung');
    expect((fixture.nativeElement.textContent as string)).toContain('Umgebung-Label');
  });

  it('renders the settings label when sectionId is "einstellungen"', () => {
    const fixture = renderWithSectionId('einstellungen');
    expect((fixture.nativeElement.textContent as string)).toContain('Einstellungen-Label');
  });
});
