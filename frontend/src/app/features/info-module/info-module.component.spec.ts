import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { resolveProperty } from '../../core/content/property-content.service';
import type {
  PropertyConfig,
  ResolvedInfoModule,
  ResolvedPropertyConfig,
} from '../../core/content/property.model';
import { InfoModuleComponent } from './info-module.component';

/**
 * The `info` renderer (module-content REQ-976, `docs/design/screens-inhouse/info.md`).
 *
 * jsdom computes no layout, so nothing here asserts geometry — the panel width,
 * the row gaps and the no-scroll rule are measured in a real browser at review
 * (`frontend/CLAUDE.md` → Tests). What is asserted is the DOM contract: term /
 * definition semantics, delivered order, and that a language switch re-renders
 * from data already held.
 */
const MODULE: ResolvedInfoModule = {
  type: 'info',
  rows: [
    { label: 'Check-out', value: 'Bis 11:00 Uhr am Abreisetag' },
    { label: 'WLAN-Passwort', value: 'Meeresrauschen-77' },
  ],
};

function render(module: ResolvedInfoModule) {
  TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  const fixture = TestBed.createComponent(InfoModuleComponent);
  fixture.componentRef.setInput('module', module);
  fixture.detectChanges();
  return { fixture, element: fixture.nativeElement as HTMLElement };
}

describe('InfoModuleComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('renders one dt/dd pair per row, in delivered order', () => {
    const { element } = render(MODULE);

    const terms = [...element.querySelectorAll('dt')].map((node) => node.textContent?.trim());
    const definitions = [...element.querySelectorAll('dd')].map((node) => node.textContent?.trim());

    expect(terms).toEqual(['Check-out', 'WLAN-Passwort']);
    expect(definitions).toEqual(['Bis 11:00 Uhr am Abreisetag', 'Meeresrauschen-77']);
  });

  it('renders inside a <dl> and adds no interactive element', () => {
    const { element } = render(MODULE);

    expect(element.querySelector('dl')).not.toBeNull();
    /* info.md: the first screen whose content area has no focusable element at
       all — no copy button, no touch target. */
    expect(element.querySelectorAll('button, a, input, [tabindex]')).toHaveLength(0);
  });

  it('restates nothing: the module renders no title of its own', () => {
    const { element } = render(MODULE);

    expect(element.querySelector('h1')).toBeNull();
  });

  /**
   * REQ-514's rule applied to module content: a language switch re-resolves
   * from data already held and issues no request. There is no HTTP double here
   * on purpose — `resolveProperty` is a pure function over the authored config,
   * so a second language reaching the component proves the switch needs no
   * fetch.
   */
  it('a language switch re-renders from held data and issues no request', () => {
    const authored: PropertyConfig = {
      propertyName: 'Testhaus',
      logoAssetId: null,
      branding: null,
      menuItems: [
        {
          id: 'info',
          label: { de: 'Info', en: 'Info' },
          icon: 'phosphorInfo',
          route: '/info',
          module: {
            type: 'info',
            rows: [
              {
                label: { de: 'Check-out', en: 'Check-out' },
                value: { de: 'Bis 11:00 Uhr', en: 'By 11:00' },
              },
            ],
          },
        },
      ],
      settings: {
        id: 'einstellungen',
        label: { de: 'Einstellungen', en: 'Settings' },
        icon: 'phosphorGearSix',
        route: '/einstellungen',
        module: null,
      },
      settingsConfig: {
        languages: [
          { code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' },
          { code: 'en', shortLabel: 'EN', label: 'English', locale: 'en-GB' },
        ],
        defaultLanguage: 'de',
        appearances: [{ mode: 'light', icon: 'phosphorSun' }],
        defaultAppearance: 'light',
      },
      standbyHeadline: { de: 'Testhaus', en: 'Testhaus' },
      standbyMessage: { de: 'Standby', en: 'Standby' },
      home: null,
    };

    const german: ResolvedPropertyConfig = resolveProperty(authored, 'de');
    const english: ResolvedPropertyConfig = resolveProperty(authored, 'en');

    const first = render(german.menuItems[0]?.module as ResolvedInfoModule);
    expect(first.element.querySelector('dd')?.textContent?.trim()).toBe('Bis 11:00 Uhr');

    first.fixture.componentRef.setInput('module', english.menuItems[0]?.module);
    first.fixture.detectChanges();

    expect(first.element.querySelector('dd')?.textContent?.trim()).toBe('By 11:00');
    /* No HttpClient and no PropertyContentService are provided in this TestBed
       at all, so a component that fetched anything could not have constructed. */
  });
});
