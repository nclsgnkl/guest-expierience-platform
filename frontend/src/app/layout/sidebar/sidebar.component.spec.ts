import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorGearSix,
  phosphorHouse,
  phosphorInfo,
} from '@ng-icons/phosphor-icons/regular';
import { beforeEach, describe, expect, it } from 'vitest';

import type { MenuItem } from '../../core/content/property.model';
import { SidebarComponent } from './sidebar.component';

const MENU_ITEMS: MenuItem[] = [
  { id: 'home', label: 'Testlabel Home', icon: 'phosphorHouse', route: '' },
  { id: 'info', label: 'Testlabel Info', icon: 'phosphorInfo', route: '/info' },
];
const SETTINGS: MenuItem = {
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

  it('renders the logo image from the injected logoAsset input, not a hardcoded reference', () => {
    const img: HTMLImageElement = fixture.nativeElement.querySelector('.sidebar__logo-img');
    expect(img.getAttribute('src')).toBe('assets/branding/test-logo.svg');
  });
});
