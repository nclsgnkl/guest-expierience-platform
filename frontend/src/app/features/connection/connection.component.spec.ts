import { provideZonelessChangeDetection, signal, type WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideIcons } from '@ng-icons/core';
import { phosphorWifiSlash } from '@ng-icons/phosphor-icons/regular';
import { beforeEach, describe, expect, it } from 'vitest';

import type { LanguageCode } from '../../core/i18n/translation.model';
import { PreferencesService } from '../../core/preferences/preferences.service';
import { ConnectionComponent } from './connection.component';

/**
 * S-conn — the last-resort screen (REQ-520; spec:
 * docs/design/screens-inhouse/connection.md): honest, guest-presentable,
 * catalogued copy in both languages, the WifiSlash glyph, no button (retry
 * is automatic), no status bar (no false liveness).
 */
describe('ConnectionComponent', () => {
  let language: WritableSignal<LanguageCode>;

  beforeEach(() => {
    language = signal<LanguageCode>('de');
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideIcons({ phosphorWifiSlash }),
        { provide: PreferencesService, useValue: { language } },
      ],
    });
  });

  function render() {
    const fixture = TestBed.createComponent(ConnectionComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('renders the catalogued copy for the active language, on the mesh', async () => {
    const fixture = render();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.connection.mesh-background')).toBeTruthy();
    expect(el.textContent).toContain('Keine Verbindung');
    expect(el.textContent).toContain('verbindet sich automatisch neu');

    language.set('en');
    await fixture.whenStable();
    expect(el.textContent).toContain('No connection');
    expect(el.textContent).toContain('reconnects automatically');
  });

  it('renders the WifiSlash glyph as decoration beside the text', () => {
    const fixture = render();
    const glyph = fixture.nativeElement.querySelector('ng-icon');
    expect(glyph?.getAttribute('name')).toBe('phosphorWifiSlash');
    expect(glyph?.getAttribute('aria-hidden')).toBe('true');
  });

  it('offers no button and no status bar — recovery is automatic and honest', () => {
    const fixture = render();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('button')).toBeNull();
    expect(el.querySelector('app-status-bar')).toBeNull();
  });
});
