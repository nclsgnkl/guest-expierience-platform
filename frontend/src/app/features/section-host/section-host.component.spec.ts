import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import { PropertyContentService } from '../../core/content/property-content.service';
import type {
  ResolvedMenuItem,
  ResolvedModule,
  ResolvedPropertyConfig,
} from '../../core/content/property.model';
import { PREFERENCE_GATEWAY } from '../../core/preferences/preference-gateway';
import { SectionHostComponent } from './section-host.component';

/**
 * The component behind every catalogue section route (module-content REQ-974).
 * Its whole job is the two-arm choice, so the three cases below are the three
 * outcomes a guest can reach:
 *
 *   - the section carries a module → its `<h1>` and the registered renderer;
 *   - the section carries none → `SectionPlaceholderComponent`'s
 *     `section.comingSoon`, sidebar entry intact (REQ-973's landing place);
 *   - no section claims the route → `section.unavailable`.
 *
 * The content double is a `PropertyContentService` stand-in, never the real
 * service and never a data file (`frontend/CLAUDE.md` → Tests).
 */
const INFO_MODULE: ResolvedModule = {
  type: 'info',
  rows: [
    { label: 'Check-out-Label', value: 'Bis 11:00 Uhr' },
    { label: 'WLAN-Label', value: 'Meeresrauschen-77' },
  ],
};

function item(id: string, route: string, module: ResolvedModule | null): ResolvedMenuItem {
  return { id, label: `${id}-Label`, icon: 'phosphorCircle', route, module };
}

const TEST_PROPERTY = {
  propertyName: 'Testhaus',
  logoAssetId: null,
  branding: null,
  menuItems: [
    item('home', '', null),
    item('info', '/info', INFO_MODULE),
    item('umgebung', '/umgebung', null),
  ],
  settings: item('einstellungen', '/einstellungen', null),
  settingsConfig: {
    languages: [{ code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' }],
    defaultLanguage: 'de',
    appearances: [{ mode: 'light', icon: 'phosphorSun' }],
    defaultAppearance: 'light',
  },
  standbyHeadline: 'Testhaus',
  standbyMessage: 'Test-Standby',
  home: null,
} satisfies ResolvedPropertyConfig;

class MockPropertyContentService {
  readonly property = signal<ResolvedPropertyConfig>(TEST_PROPERTY).asReadonly();
}

function segmentsOf(path: string): UrlSegment[] {
  return path
    .split('/')
    .filter((part) => part !== '')
    .map((part) => new UrlSegment(part, {}));
}

function renderAtUrl(path: string) {
  const url = new BehaviorSubject<UrlSegment[]>(segmentsOf(path));

  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      { provide: PropertyContentService, useClass: MockPropertyContentService },
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

  const fixture = TestBed.createComponent(SectionHostComponent);
  fixture.detectChanges();
  const element = fixture.nativeElement as HTMLElement;
  return { fixture, element, url, text: () => element.textContent ?? '' };
}

describe('SectionHostComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('renders the section title and the registered renderer with the resolved module', () => {
    const { element, text } = renderAtUrl('/info');

    expect(element.querySelector('h1')?.textContent).toContain('info-Label');
    expect(element.querySelector('app-info-module')).not.toBeNull();
    expect(text()).toContain('Bis 11:00 Uhr');
    expect(text()).toContain('Meeresrauschen-77');
    /* The module arm never renders the placeholder. */
    expect(text()).not.toContain('Bald verfügbar.');
  });

  it('renders the placeholder for a claimed route with no module (REQ-973)', () => {
    const { element, text } = renderAtUrl('/umgebung');

    expect(element.querySelector('app-info-module')).toBeNull();
    /* The sidebar entry is untouched — the section still has its own label. */
    expect(text()).toContain('umgebung-Label');
    expect(text()).toContain('Bald verfügbar.');
  });

  it('renders the unavailable state for a route the delivered menu does not claim', () => {
    const { text } = renderAtUrl('/nicht-freigeschaltet');

    expect(text()).toContain('Dieser Bereich ist nicht verfügbar');
    expect(text()).not.toContain('Bald verfügbar.');
  });

  it('re-resolves when only :sectionKey changes, without a new instance', () => {
    const { fixture, element, url } = renderAtUrl('/info');
    expect(element.querySelector('app-info-module')).not.toBeNull();

    url.next(segmentsOf('/umgebung'));
    fixture.detectChanges();

    expect(element.querySelector('app-info-module')).toBeNull();
    expect(element.textContent).toContain('Bald verfügbar.');
  });
});
