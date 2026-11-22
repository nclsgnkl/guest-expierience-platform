import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorCircle,
  phosphorDotsThree,
  phosphorGearSix,
  phosphorHouse,
  phosphorInfo,
} from '@ng-icons/phosphor-icons/regular';
import { beforeEach, describe, expect, it } from 'vitest';

import { PREFERENCE_GATEWAY } from '../../core/preferences/preference-gateway';

import type { ResolvedMenuItem } from '../../core/content/property.model';
import { SIDEBAR_SLOTS } from './menu-split';
import { SidebarComponent } from './sidebar.component';

const MENU_ITEMS: ResolvedMenuItem[] = [
  { id: 'home', label: 'Testlabel Home', icon: 'phosphorHouse', route: '', module: null },
  { id: 'info', label: 'Testlabel Info', icon: 'phosphorInfo', route: '/info', module: null },
];
const SETTINGS: ResolvedMenuItem = {
  id: 'einstellungen',
  label: 'Testlabel Settings',
  icon: 'phosphorGearSix',
  route: '/einstellungen',
  module: null,
};

describe('SidebarComponent', () => {
  let fixture: ComponentFixture<SidebarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideIcons({ phosphorHouse, phosphorInfo, phosphorGearSix }),
        /* The component resolves its chrome strings through TranslationService,
           which reaches PreferencesService and therefore the gateway token —
           an inert per-spec double (frontend/CLAUDE.md: transport doubles
           live in the spec that needs them). */
        {
          provide: PREFERENCE_GATEWAY,
          useValue: {
            load: () => Promise.resolve(null),
            save: () => Promise.resolve(),
            clear: () => Promise.resolve(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SidebarComponent);
    fixture.componentRef.setInput('menuItems', MENU_ITEMS);
    fixture.componentRef.setInput('settings', SETTINGS);
    fixture.componentRef.setInput('logoAsset', 'assets/branding/test-logo.svg');
    fixture.componentRef.setInput('activeRoute', '/');
    fixture.componentRef.setInput('locale', 'de-DE');
    fixture.detectChanges();
  });

  it('renders every menuItems entry plus the settings entry (6 total for the real config shape)', () => {
    const buttons = fixture.nativeElement.querySelectorAll('.sidebar-item');
    expect(buttons.length).toBe(MENU_ITEMS.length + 1);
  });

  it('renders labels sourced from the injected input config, not a literal string', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Testlabel Home');
    expect(text).toContain('Testlabel Info');
    expect(text).toContain('Testlabel Settings');
  });

  it('marks exactly the item matching activeRoute as active', () => {
    const active = fixture.nativeElement.querySelectorAll('.sidebar-item--active');
    expect(active.length).toBe(1);
    expect((active[0].textContent as string).trim()).toContain('Testlabel Home');
  });

  it('reflects a different activeRoute onto the matching item', async () => {
    fixture.componentRef.setInput('activeRoute', '/info');
    fixture.detectChanges();

    const active = fixture.nativeElement.querySelectorAll('.sidebar-item--active');
    expect(active.length).toBe(1);
    expect((active[0].textContent as string).trim()).toContain('Testlabel Info');
  });

  it("flags only the active item's glyph for the gradient fill (REQ-023)", () => {
    const gradientIcons = fixture.nativeElement.querySelectorAll('ng-icon.icon-gradient-active');
    expect(gradientIcons.length).toBe(1);

    const activeItem = fixture.nativeElement.querySelector('.sidebar-item--active');
    expect(activeItem.querySelector('ng-icon.icon-gradient-active')).toBeTruthy();
  });

  it('moves the gradient flag with the active route (REQ-023)', () => {
    fixture.componentRef.setInput('activeRoute', '/einstellungen');
    fixture.detectChanges();

    const flagged = fixture.nativeElement.querySelectorAll('ng-icon.icon-gradient-active');
    expect(flagged.length).toBe(1);
    const settingsItem = fixture.nativeElement.querySelector('.sidebar__settings .sidebar-item');
    expect(settingsItem.querySelector('ng-icon.icon-gradient-active')).toBeTruthy();
  });

  it('renders the logo image from the injected logoAsset input, not a hardcoded reference', () => {
    const img: HTMLImageElement = fixture.nativeElement.querySelector('.sidebar__logo-img');
    expect(img.getAttribute('src')).toBe('assets/branding/test-logo.svg');
  });

  it('renders no overflow trigger at all below the slot count (REQ-931)', () => {
    expect(fixture.nativeElement.querySelector('[aria-haspopup]')).toBeNull();
    /* Absent from the DOM, not hidden — a hidden trigger is still a tab stop
       and still occupies a slot. */
    expect(fixture.nativeElement.textContent as string).not.toContain('Mehr');
  });
});

/**
 * The overflow surfaces (sidebar-catalogue-overflow REQ-916/921/923/929/931/934).
 * The split rule itself is proved in `menu-split.spec.ts`; what is proved here
 * is that this component renders it, owns the active state and promotes without
 * touching anything durable.
 *
 * Nine modules, so `%dev`'s shape: three fixed slots, one rotating, a trigger,
 * and five in the panel. Every key and label is invented for this file —
 * REQ-903 forbids a test depending on a placeholder catalogue row.
 */
const NINE_ITEMS: ResolvedMenuItem[] = [
  { id: 'home', label: 'Slot Eins', icon: 'phosphorHouse', route: '', module: null },
  { id: 'zwei', label: 'Slot Zwei', icon: 'phosphorCircle', route: '/zwei', module: null },
  { id: 'drei', label: 'Slot Drei', icon: 'phosphorCircle', route: '/drei', module: null },
  { id: 'vier', label: 'Slot Vier', icon: 'phosphorCircle', route: '/vier', module: null },
  { id: 'echo', label: 'Echo', icon: 'phosphorCircle', route: '/echo', module: null },
  { id: 'alfa', label: 'Alfa', icon: 'phosphorCircle', route: '/alfa', module: null },
  { id: 'delta', label: 'Delta', icon: 'phosphorCircle', route: '/delta', module: null },
  { id: 'bravo', label: 'Bravo', icon: 'phosphorCircle', route: '/bravo', module: null },
  { id: 'charlie', label: 'Charlie', icon: 'phosphorCircle', route: '/charlie', module: null },
];

describe('SidebarComponent — overflow', () => {
  let fixture: ComponentFixture<SidebarComponent>;
  let navigated: string[];
  let saved: unknown[];

  function trigger(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('[aria-haspopup]') as HTMLButtonElement;
  }

  function panel(): HTMLElement | null {
    return document.querySelector('.sidebar-overflow');
  }

  function tiles(): HTMLButtonElement[] {
    return Array.from(document.querySelectorAll('.sidebar-overflow__tile'));
  }

  function slotLabels(): string[] {
    return Array.from(
      fixture.nativeElement.querySelectorAll('.sidebar__menu-items .sidebar-item__label'),
    ).map((el) => ((el as HTMLElement).textContent as string).trim());
  }

  function openPanel(): void {
    trigger().click();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    navigated = [];
    saved = [];

    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideIcons({
          phosphorHouse,
          phosphorInfo,
          phosphorGearSix,
          phosphorCircle,
          phosphorDotsThree,
        }),
        { provide: Router, useValue: { navigateByUrl: (url: string) => navigated.push(url) } },
        {
          provide: PREFERENCE_GATEWAY,
          useValue: {
            load: () => Promise.resolve(null),
            /* The one durable write this app has. REQ-929 says a promotion
               must never reach it. */
            save: (value: unknown) => {
              saved.push(value);
              return Promise.resolve();
            },
            clear: () => Promise.resolve(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SidebarComponent);
    fixture.componentRef.setInput('menuItems', NINE_ITEMS);
    fixture.componentRef.setInput('settings', SETTINGS);
    fixture.componentRef.setInput('logoAsset', 'assets/branding/test-logo.svg');
    fixture.componentRef.setInput('activeRoute', '/');
    fixture.componentRef.setInput('locale', 'de-DE');
    fixture.detectChanges();
  });

  it('renders four sections plus the overflow trigger, never more (REQ-915, 916)', () => {
    expect(slotLabels()).toEqual(['Slot Eins', 'Slot Zwei', 'Slot Drei', 'Slot Vier', 'Mehr']);
    expect(slotLabels()).toHaveLength(SIDEBAR_SLOTS);
  });

  it('takes the trigger label from TranslationService, and gives it no aria-label (REQ-934)', () => {
    expect((trigger().textContent as string).trim()).toBe('Mehr');
    /* The visible label IS the accessible name; an aria-label would override
       it. `aria-haspopup`/`aria-expanded` come from the CDK. */
    expect(trigger().hasAttribute('aria-label')).toBe(false);
    expect(trigger().getAttribute('aria-expanded')).toBe('false');
  });

  it('lists exactly the modules not in the sidebar, alphabetically (REQ-919, 924)', () => {
    openPanel();

    expect(tiles().map((tile) => (tile.textContent as string).trim())).toEqual([
      'Alfa',
      'Bravo',
      'Charlie',
      'Delta',
      'Echo',
    ]);
  });

  it('names the panel from TranslationService and gives it the menu role (REQ-922, 934)', () => {
    openPanel();

    expect(panel()!.getAttribute('aria-label')).toBe('Weitere Bereiche');
    expect(panel()!.getAttribute('role')).toBe('menu');
    expect(tiles()[0].getAttribute('role')).toBe('menuitem');
  });

  it('promotes a chosen module into slot 4 and navigates there (REQ-923)', () => {
    openPanel();
    tiles()
      .find((tile) => (tile.textContent as string).includes('Charlie'))!
      .click();
    fixture.detectChanges();

    expect(navigated).toEqual(['/charlie']);
    expect(slotLabels()).toEqual(['Slot Eins', 'Slot Zwei', 'Slot Drei', 'Charlie', 'Mehr']);
  });

  it('keeps the promoted module in slot 4 once the guest navigates on (REQ-928)', () => {
    openPanel();
    tiles()
      .find((tile) => (tile.textContent as string).includes('Charlie'))!
      .click();
    fixture.detectChanges();

    /* The router reports the new URL; the active treatment moves onto it. */
    fixture.componentRef.setInput('activeRoute', '/charlie');
    fixture.detectChanges();
    expect(
      (fixture.nativeElement.querySelector('.sidebar-item--active').textContent as string).trim(),
    ).toContain('Charlie');

    /* Then back to a fixed slot: Charlie stays in slot 4, no longer active. */
    fixture.componentRef.setInput('activeRoute', '/zwei');
    fixture.detectChanges();
    expect(slotLabels()).toEqual(['Slot Eins', 'Slot Zwei', 'Slot Drei', 'Charlie', 'Mehr']);
    expect(
      (fixture.nativeElement.querySelector('.sidebar-item--active').textContent as string).trim(),
    ).toContain('Slot Zwei');
  });

  it('displaces the previous slot 4 into the panel, leaving slots 1-3 untouched (REQ-923)', () => {
    openPanel();
    tiles()
      .find((tile) => (tile.textContent as string).includes('Alfa'))!
      .click();
    fixture.detectChanges();

    expect(slotLabels().slice(0, 3)).toEqual(['Slot Eins', 'Slot Zwei', 'Slot Drei']);

    openPanel();
    expect(tiles().map((tile) => (tile.textContent as string).trim())).toEqual([
      'Bravo',
      'Charlie',
      'Delta',
      'Echo',
      'Slot Vier',
    ]);
  });

  it('promotes the active module when the guest arrives by any other route (REQ-932)', () => {
    fixture.componentRef.setInput('activeRoute', '/delta');
    fixture.detectChanges();

    expect(slotLabels()).toEqual(['Slot Eins', 'Slot Zwei', 'Slot Drei', 'Delta', 'Mehr']);
    /* "Nothing looks active" is not a reachable state. */
    expect(fixture.nativeElement.querySelectorAll('.sidebar-item--active')).toHaveLength(1);
  });

  it('never marks the overflow trigger active', () => {
    fixture.componentRef.setInput('activeRoute', '/delta');
    fixture.detectChanges();

    expect(trigger().classList.contains('sidebar-item--active')).toBe(false);
  });

  it('re-sorts the panel on a language switch, from data already held (REQ-925, 926)', () => {
    /* The same modules, resolved into English: a different order, and the
       component was handed no new list and no new locale-specific code. */
    fixture.componentRef.setInput('menuItems', [
      ...NINE_ITEMS.slice(0, 4),
      { id: 'echo', label: 'Zoo', icon: 'phosphorCircle', route: '/echo' },
      { id: 'alfa', label: 'View', icon: 'phosphorCircle', route: '/alfa' },
      { id: 'delta', label: 'Rooms', icon: 'phosphorCircle', route: '/delta' },
      { id: 'bravo', label: 'Arrival', icon: 'phosphorCircle', route: '/bravo' },
      { id: 'charlie', label: 'Food', icon: 'phosphorCircle', route: '/charlie' },
    ]);
    fixture.componentRef.setInput('locale', 'en-GB');
    fixture.detectChanges();

    openPanel();
    expect(tiles().map((tile) => (tile.textContent as string).trim())).toEqual([
      'Arrival',
      'Food',
      'Rooms',
      'View',
      'Zoo',
    ]);
  });

  it('writes nothing durable when a module is promoted (REQ-929)', () => {
    const before = JSON.stringify(localStorage);

    openPanel();
    tiles()[0].click();
    fixture.detectChanges();

    expect(JSON.stringify(localStorage)).toBe(before);
    expect(localStorage.length).toBe(0);
    /* The only persistence path this app has, and the only transport this
       component could reach through DI, was never called. */
    expect(saved).toEqual([]);
  });
});
