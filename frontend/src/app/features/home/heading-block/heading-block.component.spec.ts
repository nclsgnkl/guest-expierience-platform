import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideIcons } from '@ng-icons/core';
import { phosphorSparkleFill } from '@ng-icons/phosphor-icons/fill';
import { beforeEach, describe, expect, it } from 'vitest';

import { PropertyContentService } from '../../../core/content/property-content.service';
import type { ResolvedPropertyConfig } from '../../../core/content/property.model';
import { HeadingBlockComponent } from './heading-block.component';

const TEST_PROPERTY = {
  propertyName: 'Testhaus',
  logoAssetId: null,
  branding: null,
  menuItems: [],
  settings: {
    id: 'einstellungen',
    label: 'Einstellungen-Test',
    icon: 'phosphorGearSix',
    route: '/einstellungen',
    module: null,
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
    /* Braces deliberately IN the fixture: a server-resolved string is
       rendered verbatim (REQ-511) — if any client interpolation survived,
       the assertion below would catch it replacing/normalising this. */
    headline: 'Willkommen, Testgast! {nicht.ersetzen}',
    paragraph: 'Test-Absatz.',
    artworkAssetId: null,
    cards: [],
  },
} satisfies ResolvedPropertyConfig;

class MockPropertyContentService {
  readonly property = signal<ResolvedPropertyConfig>(TEST_PROPERTY).asReadonly();
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

  it('renders subtitle, headline and paragraph VERBATIM from the server strings (REQ-511)', () => {
    const fixture = TestBed.createComponent(HeadingBlockComponent);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Test-Untertitel');
    expect(text).toContain('Willkommen, Testgast! {nicht.ersetzen}');
    expect(text).toContain('Test-Absatz.');
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
