import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
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
    { id: 'home', label: 'Home-Label', icon: 'phosphorHouse', route: '' },
    { id: 'info', label: 'Info-Label', icon: 'phosphorInfo', route: '/info' },
    { id: 'umgebung', label: 'Umgebung-Label', icon: 'phosphorMapPin', route: '/umgebung' },
  ],
  settings: {
    id: 'einstellungen',
    label: 'Einstellungen-Label',
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
  standbyMessage: 'Standby-Test',
  home: null,
};

class MockPropertyContentService {
  readonly property = signal(TEST_PROPERTY).asReadonly();
}

/** The url segments `ActivatedRoute` would emit for a path, e.g. `/smart-home`. */
function segmentsOf(path: string): UrlSegment[] {
  return path
    .split('/')
    .filter((part) => part !== '')
    .map((part) => new UrlSegment(part, {}));
}

/**
 * The component reads `ActivatedRoute.url` — an observable, so a `:sectionKey`
 * change re-resolves without the router re-creating the instance. The double is
 * a `BehaviorSubject` for exactly that: the spec can push a second navigation
 * into a live component.
 */
function renderAtUrl(path: string) {
  const url = new BehaviorSubject<UrlSegment[]>(segmentsOf(path));

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
      { provide: ActivatedRoute, useValue: { url } },
    ],
  });

  const fixture = TestBed.createComponent(SectionPlaceholderComponent);
  fixture.detectChanges();
  return { fixture, url, text: () => fixture.nativeElement.textContent as string };
}

describe('SectionPlaceholderComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('resolves the section from the URL and renders its configured label', () => {
    const { text } = renderAtUrl('/info');

    expect(text()).toContain('Info-Label');
    expect(text()).toContain('Bald verfügbar.');
  });

  it('resolves a different URL to a different section', () => {
    const { text } = renderAtUrl('/umgebung');

    expect(text()).toContain('Umgebung-Label');
  });

  it('resolves the settings item, which is not part of menuItems', () => {
    const { text } = renderAtUrl('/einstellungen');

    expect(text()).toContain('Einstellungen-Label');
  });

  it("normalises Home's empty route against the router's '/' (REQ-912)", () => {
    const { text } = renderAtUrl('/');

    expect(text()).toContain('Home-Label');
  });

  it('renders the unavailable state for a route the delivered menu does not claim', () => {
    const { text } = renderAtUrl('/nicht-freigeschaltet');

    expect(text()).toContain('Dieser Bereich ist nicht verfügbar');
    /* No note beside it: there is nothing coming. */
    expect(text()).not.toContain('Bald verfügbar.');
  });

  it('renders the unavailable state for a deep path, without throwing', () => {
    const { text } = renderAtUrl('/info/unterseite/tiefer');

    expect(text()).toContain('Dieser Bereich ist nicht verfügbar');
  });

  it('re-resolves when the parameter changes under a reused instance (REQ-912)', () => {
    const { fixture, url, text } = renderAtUrl('/info');
    expect(text()).toContain('Info-Label');

    url.next(segmentsOf('/umgebung'));
    fixture.detectChanges();

    expect(text()).toContain('Umgebung-Label');
    expect(text()).not.toContain('Info-Label');
  });

  it('re-resolves from a claimed route to an unclaimed one', () => {
    const { fixture, url, text } = renderAtUrl('/info');

    url.next(segmentsOf('/weg'));
    fixture.detectChanges();

    expect(text()).toContain('Dieser Bereich ist nicht verfügbar');
  });
});
