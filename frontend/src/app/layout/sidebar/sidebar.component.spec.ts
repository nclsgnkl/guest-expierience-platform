import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorGearSix,
  phosphorHouse,
  phosphorInfo,
} from '@ng-icons/phosphor-icons/regular';
import { beforeEach, describe, expect, it } from 'vitest';

import { PREFERENCE_GATEWAY } from '../../core/preferences/preference-gateway';

import type { ResolvedMenuItem } from '../../core/content/property.model';
import { SidebarComponent } from './sidebar.component';

const MENU_ITEMS: ResolvedMenuItem[] = [
  { id: 'home', label: 'Testlabel Home', icon: 'phosphorHouse', route: '' },
  { id: 'info', label: 'Testlabel Info', icon: 'phosphorInfo', route: '/info' },
];
const SETTINGS: ResolvedMenuItem = {
  id: 'einstellungen',
  label: 'Testlabel Settings',
  icon: 'phosphorGearSix',
  route: '/einstellungen',
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

  it('flags only the active item\'s glyph for the gradient fill (REQ-023)', () => {
    const gradientIcons = fixture.nativeElement.querySelectorAll(
      'ng-icon.icon-gradient-active',
    );
    expect(gradientIcons.length).toBe(1);

    const activeItem = fixture.nativeElement.querySelector('.sidebar-item--active');
    expect(activeItem.querySelector('ng-icon.icon-gradient-active')).toBeTruthy();
  });

  it('moves the gradient flag with the active route (REQ-023)', () => {
    fixture.componentRef.setInput('activeRoute', '/einstellungen');
    fixture.detectChanges();

    const flagged = fixture.nativeElement.querySelectorAll('ng-icon.icon-gradient-active');
    expect(flagged.length).toBe(1);
    const settingsItem = fixture.nativeElement.querySelector(
      '.sidebar__settings .sidebar-item',
    );
    expect(settingsItem.querySelector('ng-icon.icon-gradient-active')).toBeTruthy();
  });

  it('renders the logo image from the injected logoAsset input, not a hardcoded reference', () => {
    const img: HTMLImageElement = fixture.nativeElement.querySelector('.sidebar__logo-img');
    expect(img.getAttribute('src')).toBe('assets/branding/test-logo.svg');
  });
});
