import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorArrowRight,
  phosphorCaretDown,
  phosphorCheck,
  phosphorFileText,
  phosphorGearSix,
  phosphorGlobe,
  phosphorHouse,
  phosphorInfo,
  phosphorKey,
  phosphorLightbulbFilament,
  phosphorMapPin,
  phosphorMoon,
  phosphorShoppingBag,
  phosphorSun,
} from '@ng-icons/phosphor-icons/regular';
import { phosphorSparkleFill } from '@ng-icons/phosphor-icons/fill';
import { beforeEach, describe, expect, it } from 'vitest';

import { routes } from '../../app.routes';
import { DeviceTokenStore } from '../../core/api/device-token.store';
import { PropertyContentService } from '../../core/content/property-content.service';
import type { PropertyConfig, PropertySettingsConfig } from '../../core/content/property.model';
import { GuestDataService } from '../../core/guest/guest-data.service';
import {
  PREFERENCE_GATEWAY,
  type PreferenceGateway,
} from '../../core/preferences/preference-gateway';
import { PreferencesService } from '../../core/preferences/preferences.service';
import { ShellComponent } from '../../layout/shell/shell.component';
import { SettingsComponent } from './settings.component';

/**
 * S34 (part 2) — the Settings screen and its route (REQ-124, REQ-116,
 * REQ-117), which is also what validates S19, S20 and S21.
 *
 * The **real** `PreferencesService` is used throughout: "changing one control
 * is reflected in the other" is true only because there is a single piece of
 * state, and a double would fake exactly the thing under test. What is faked
 * is the property config — except in the routing block, which drives the real
 * app routes through the real shell.
 *
 * No persistence is asserted (that is S39), so the gateway is inert.
 */
const SILENT_GATEWAY: PreferenceGateway = {
  load: () => Promise.resolve(null),
  save: () => Promise.resolve(),
  clear: () => Promise.resolve(),
};

const TEST_CONFIG: PropertySettingsConfig = {
  languages: [
    { code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' },
    { code: 'en', shortLabel: 'EN', label: 'English', locale: 'en-GB' },
  ],
  defaultLanguage: 'de',
  appearances: [
    { mode: 'light', icon: 'phosphorSun' },
    { mode: 'dark', icon: 'phosphorMoon' },
  ],
  defaultAppearance: 'light',
};

describe('SettingsComponent', () => {
  let fixture: ComponentFixture<SettingsComponent>;
  let preferences: PreferencesService;

  async function setup(config: PropertySettingsConfig = TEST_CONFIG): Promise<void> {
    // One case re-runs setup with a different config; the module built by
    // `beforeEach` has to go first.
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideIcons({ phosphorCheck, phosphorMoon, phosphorSun }),
        { provide: PREFERENCE_GATEWAY, useValue: SILENT_GATEWAY },
        { provide: PropertyContentService, useValue: { settingsConfig: signal(config) } },
      ],
    }).compileComponents();

    preferences = TestBed.inject(PreferencesService);
    preferences.applyPropertyDefaults(config);

    fixture = TestBed.createComponent(SettingsComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function groups(): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.setting-choice'));
  }

  function optionsOf(group: HTMLElement): HTMLButtonElement[] {
    return Array.from(group.querySelectorAll('.setting-choice__option'));
  }

  function selectedOf(group: HTMLElement): HTMLButtonElement | undefined {
    return optionsOf(group).find((option) =>
      option.classList.contains('setting-choice__option--selected'),
    );
  }

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    await setup();
  });

  it('renders a translated title', () => {
    expect(fixture.nativeElement.querySelector('.settings__title').textContent.trim()).toBe(
      'Einstellungen',
    );
  });

  it('renders one panel per setting, each holding one choice group', () => {
    expect(fixture.nativeElement.querySelectorAll('.settings__panel').length).toBe(2);
    expect(groups().length).toBe(2);
  });

  it('renders every configured language as an untranslated endonym', () => {
    const labels = optionsOf(groups()[0]).map((option) => option.textContent!.trim());
    expect(labels).toEqual([
      expect.stringContaining('Deutsch'),
      expect.stringContaining('English'),
    ]);
  });

  it('renders every configured appearance with a translated label', () => {
    const labels = optionsOf(groups()[1]).map((option) => option.textContent!.trim());
    expect(labels).toEqual([expect.stringContaining('Hell'), expect.stringContaining('Dunkel')]);
  });

  it('renders exactly what the property configures — no more, no fewer', async () => {
    await setup({
      ...TEST_CONFIG,
      languages: [{ code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' }],
    });

    expect(optionsOf(groups()[0]).length).toBe(1);
    expect(fixture.nativeElement.textContent).not.toContain('English');
  });

  it('marks the current value of each setting', () => {
    expect(selectedOf(groups()[0])!.textContent).toContain('Deutsch');
    expect(selectedOf(groups()[1])!.textContent).toContain('Hell');
  });

  it('selecting a language updates the shared state and the whole screen follows', async () => {
    optionsOf(groups()[0])[1].click();
    await settle();

    expect(preferences.language()).toBe('en');
    expect(fixture.nativeElement.querySelector('.settings__title').textContent.trim()).toBe(
      'Settings',
    );
    // The endonyms do not move; the translated labels do.
    expect(selectedOf(groups()[0])!.textContent).toContain('English');
    expect(optionsOf(groups()[1]).map((option) => option.textContent!.trim())).toEqual([
      expect.stringContaining('Light'),
      expect.stringContaining('Dark'),
    ]);
  });

  it('selecting an appearance updates the shared state and the resolved theme', async () => {
    optionsOf(groups()[1])[1].click();
    await settle();

    expect(preferences.appearance()).toBe('dark');
    expect(preferences.resolvedTheme()).toBe('dark');
    expect(selectedOf(groups()[1])!.textContent).toContain('Dunkel');
  });

  it('changing one setting leaves the other where it was', async () => {
    optionsOf(groups()[1])[1].click();
    await settle();

    expect(preferences.language()).toBe('de');
    expect(selectedOf(groups()[0])!.textContent).toContain('Deutsch');
  });

  it('reflects a change made elsewhere, because there is only one piece of state', async () => {
    preferences.setAppearance('dark');
    await settle();

    expect(selectedOf(groups()[1])!.textContent).toContain('Dunkel');
  });
});

/**
 * The routing half (REQ-116, S21). Real routes, real shell, real content
 * service — so this is also where the status bar and the Settings screen are
 * on screen together and REQ-117's "changing one is reflected in the other"
 * is observable rather than argued.
 */
describe('Settings routing', () => {
  /** Wire-shaped full config: what GuestDataService holds after bootstrap. */
  const FULL_CONFIG: PropertyConfig = {
    propertyName: 'Testhaus',
    logoAssetId: null,
    branding: null,
    menuItems: [
      { id: 'home', label: { de: 'Home', en: 'Home' }, icon: 'phosphorHouse', route: '' },
      { id: 'info', label: { de: 'Info', en: 'Info' }, icon: 'phosphorInfo', route: '/info' },
      {
        id: 'umgebung',
        label: { de: 'Umgebung-L', en: 'Nearby-L' },
        icon: 'phosphorMapPin',
        route: '/umgebung',
      },
      {
        id: 'smart-home',
        label: { de: 'Smart Home', en: 'Smart Home' },
        icon: 'phosphorLightbulbFilament',
        route: '/smart-home',
      },
      {
        id: 'services',
        label: { de: 'Services', en: 'Services' },
        icon: 'phosphorShoppingBag',
        route: '/services',
      },
    ],
    settings: {
      id: 'einstellungen',
      label: { de: 'Einstellungen', en: 'Settings' },
      icon: 'phosphorGearSix',
      route: '/einstellungen',
    },
    settingsConfig: TEST_CONFIG,
    standbyHeadline: { de: 'Testhaus', en: 'Testhaus' },
    standbyMessage: { de: 'Standby-Test', en: 'Standby test' },
    home: null,
  };

  function setup(): void {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter(routes),
        { provide: PREFERENCE_GATEWAY, useValue: SILENT_GATEWAY },
        /* The real routes carry the state guards (REQ-506/517/520): a paired,
           occupied, loaded kiosk is the state under test here. */
        { provide: DeviceTokenStore, useValue: { token: () => 'a-device-token' } },
        {
          provide: GuestDataService,
          useValue: {
            config: signal<PropertyConfig | null>(FULL_CONFIG),
            occupancy: signal('OCCUPIED'),
            connection: signal('ONLINE'),
            staleSince: signal<number | null>(null),
          },
        },
        provideIcons({
          phosphorArrowRight,
          phosphorCaretDown,
          phosphorCheck,
          phosphorFileText,
          phosphorGearSix,
          phosphorGlobe,
          phosphorHouse,
          phosphorInfo,
          phosphorKey,
          phosphorLightbulbFilament,
          phosphorMapPin,
          phosphorMoon,
          phosphorShoppingBag,
          phosphorSparkleFill,
          phosphorSun,
        }),
      ],
    });

    /* What the app initializer does at boot (02-design.md §6.4). Without it
       no option list is configured and every guest choice is rejected — the
       harness does not run app initializers. */
    const preferences = TestBed.inject(PreferencesService);
    preferences.applyPropertyDefaults(TestBed.inject(PropertyContentService).settingsConfig());
  }

  it('renders the Settings screen at /einstellungen, not the placeholder', async () => {
    setup();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/einstellungen', ShellComponent);
    const el: HTMLElement = harness.routeNativeElement!;

    expect(el.querySelector('app-settings')).toBeTruthy();
    expect(el.querySelector('.settings__title')).toBeTruthy();
    expect(el.querySelector('app-section-placeholder')).toBeFalsy();
  });

  it("marks the sidebar's Einstellungen item active on that route", async () => {
    setup();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/einstellungen', ShellComponent);
    const el: HTMLElement = harness.routeNativeElement!;

    const active = el.querySelectorAll('.sidebar-item--active');
    expect(active.length).toBe(1);
    expect(el.querySelector('.sidebar__settings .sidebar-item--active')).toBeTruthy();
  });

  for (const path of ['/info', '/umgebung', '/smart-home', '/services']) {
    it(`still renders the placeholder at ${path}`, async () => {
      setup();
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(path, ShellComponent);
      const el: HTMLElement = harness.routeNativeElement!;

      expect(el.querySelector('app-section-placeholder')).toBeTruthy();
      expect(el.querySelector('app-settings')).toBeFalsy();
    });
  }

  it('the status bar reflects a choice made on the Settings screen (REQ-117)', async () => {
    setup();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/einstellungen', ShellComponent);
    const el: HTMLElement = harness.routeNativeElement!;

    const languageCode = () => el.querySelector('.status-bar__language-code')!.textContent!.trim();
    expect(languageCode()).toBe('DE');

    const languageOptions: HTMLButtonElement[] = Array.from(
      el.querySelectorAll('.setting-choice')[0].querySelectorAll('.setting-choice__option'),
    );
    languageOptions[1].click();
    harness.detectChanges();

    expect(TestBed.inject(PreferencesService).language()).toBe('en');
    expect(languageCode()).toBe('EN');
    expect(el.querySelector('.settings__title')!.textContent!.trim()).toBe('Settings');
  });
});
