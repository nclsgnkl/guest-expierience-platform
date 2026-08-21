import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PREFERENCE_GATEWAY } from '../../core/preferences/preference-gateway';
import { PairingComponent } from './pairing.component';
import { PairingService, type PairingStatus } from './pairing.service';

/**
 * S-pair (component half) — the screen per
 * docs/design/screens-inhouse/pairing.md: brandless glass panel, the code as
 * grouped hero (`XXXX XXXX`), the status line swapping per state without the
 * code block collapsing, no interactive element anywhere.
 */
describe('PairingComponent', () => {
  const code = signal<string | null>(null);
  const status = signal<PairingStatus>('renewing');
  const start = vi.fn();

  beforeEach(() => {
    code.set(null);
    status.set('renewing');
    start.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        {
          provide: PREFERENCE_GATEWAY,
          useValue: {
            load: () => Promise.resolve(null),
            save: () => Promise.resolve(),
            clear: () => Promise.resolve(),
          },
        },
      ],
    });
    /* The component provides its own (component-scoped) PairingService; the
       double must land in that same slot. */
    TestBed.overrideComponent(PairingComponent, {
      set: {
        providers: [
          {
            provide: PairingService,
            useValue: { code: code.asReadonly(), status: status.asReadonly(), start },
          },
        ],
      },
    });
  });

  function render() {
    const fixture = TestBed.createComponent(PairingComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('starts the machine and renders title, instruction and the mesh background', () => {
    const fixture = render();
    expect(start).toHaveBeenCalledOnce();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.pairing.mesh-background')).toBeTruthy();
    expect(el.textContent).toContain('Gerät koppeln');
    expect(el.textContent).toContain('Verwaltungsbereich');
  });

  it('renders the code as two groups of four (spacing-only, no separator character)', async () => {
    const fixture = render();
    code.set('ABCDEFGH');
    status.set('waiting');
    await fixture.whenStable();

    const codeBlock = fixture.nativeElement.querySelector('.pairing__code') as HTMLElement;
    expect(codeBlock.textContent?.trim()).toBe('ABCD EFGH');
  });

  it('swaps only the status line per state; the code block stays and dims while renewing', async () => {
    const fixture = render();
    code.set('ABCDEFGH');
    status.set('waiting');
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.pairing__status-text')?.textContent).toContain(
      'Warten auf Bestätigung',
    );
    expect(el.querySelector('.pairing__code--renewing')).toBeNull();

    status.set('renewing');
    await fixture.whenStable();
    expect(el.querySelector('.pairing__status-text')?.textContent).toContain('Neuer Code');
    expect(el.querySelector('.pairing__code--renewing')).toBeTruthy();
    expect(el.querySelector('.pairing__code')?.textContent?.trim()).toBe('ABCD EFGH');

    status.set('offline');
    await fixture.whenStable();
    expect(el.querySelector('.pairing__status-text')?.textContent).toContain('Keine Verbindung');
    expect(el.querySelector('.pairing__status-dot--muted')).toBeTruthy();

    status.set('cooldown');
    await fixture.whenStable();
    expect(el.querySelector('.pairing__status-text')?.textContent).toContain('Zu viele Versuche');
  });

  it('has no button, no input, no focusable element — the flow is unattended', () => {
    const fixture = render();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('button, input, a, [tabindex]')).toBeNull();
  });

  it('never renders the poll secret anywhere', async () => {
    const fixture = render();
    code.set('ABCDEFGH');
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).not.toContain('secret');
  });
});
