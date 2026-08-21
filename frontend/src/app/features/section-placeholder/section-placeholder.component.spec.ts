import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { PREFERENCE_GATEWAY } from '../../core/preferences/preference-gateway';

import { PropertyContentService } from '../../core/content/property-content.service';
import type { ResolvedPropertyConfig } from '../../core/content/property.model';
import { SectionPlaceholderComponent } from './section-placeholder.component';

const TEST_PROPERTY: ResolvedPropertyConfig = {
  propertyName: 'Testhaus',
  logoAssetId: null,
  branding: null,
  menuItems: [
    { id: 'info', label: 'Info-Label', icon: 'phosphorInfo', route: '/info' },
    { id: 'umgebung', label: 'Umgebung-Label', icon: 'phosphorMapPin', route: '/umgebung' },
  ],
  settings: { id: 'einstellungen', label: 'Einstellungen-Label', icon: 'phosphorGearSix', route: '/einstellungen' },
  settingsConfig: {
    languages: [{ code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' }],
    defaultLanguage: 'de',
    appearances: [{ mode: 'light', icon: 'phosphorSun' }],
    defaultAppearance: 'light',
  },
  standbyHeadline: 'Testhaus',
  standbyMessage: 'Standby-Test',
  home: null,
};

class MockPropertyContentService {
  readonly property = signal(TEST_PROPERTY).asReadonly();
}

function renderWithSectionId(sectionId: string) {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      { provide: PropertyContentService, useClass: MockPropertyContentService },
      /* The component resolves its chrome strings through TranslationService,
         which reaches PreferencesService and therefore the gateway token —
         an inert per-spec double. */
      {
        provide: PREFERENCE_GATEWAY,
        useValue: {
          load: () => Promise.resolve(null),
          save: () => Promise.resolve(),
          clear: () => Promise.resolve(),
        },
      },
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
