import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { GuestAssetService } from '../../../core/guest/guest-asset.service';
import { HeroArtworkComponent } from './hero-artwork.component';

/**
 * REQ-010 → REQ-513: no artwork renders nothing (the reviewed fallback);
 * artwork renders the authorized object URL — never a bare /api src; and the
 * host is an out-of-flow layer in BOTH variants (review 🔴-1 — as an inline
 * flex item it pushed the content column right and scrolled Home). Geometry
 * itself is not a jsdom question: it is measured in a real browser by
 * `tools/measure-states.mjs` → ARTWORK.
 */
describe('HeroArtworkComponent', () => {
  function setup(assetId: number | null, url: string | null) {
    const urlSignal = signal<string | null>(url);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: GuestAssetService, useValue: { url: () => urlSignal.asReadonly() } },
      ],
    });
    const fixture = TestBed.createComponent(HeroArtworkComponent);
    fixture.componentRef.setInput('artworkAssetId', assetId);
    fixture.detectChanges();
    return { fixture, urlSignal };
  }

  it('renders no <img> without an artwork asset (no-artwork fallback only)', () => {
    const { fixture } = setup(null, null);
    expect((fixture.nativeElement as HTMLElement).querySelector('img')).toBeNull();
  });

  it('renders no <img> while the blob has not arrived (and after a 404)', () => {
    const { fixture } = setup(12, null);
    expect((fixture.nativeElement as HTMLElement).querySelector('img')).toBeNull();
  });

  it('never participates in the content flex row (absolutely positioned layer)', () => {
    const { fixture } = setup(12, 'blob:test-artwork');
    const host = fixture.nativeElement as HTMLElement;
    expect(getComputedStyle(host).position).toBe('absolute');
  });

  it('renders the object URL once the authorized fetch delivered it', async () => {
    const { fixture, urlSignal } = setup(12, null);

    urlSignal.set('blob:test-artwork');
    await fixture.whenStable();

    const img = (fixture.nativeElement as HTMLElement).querySelector('img');
    expect(img?.getAttribute('src')).toBe('blob:test-artwork');
    expect(img?.getAttribute('alt')).toBe('');
  });
});
