import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { HeroArtworkComponent } from './hero-artwork.component';

describe('HeroArtworkComponent', () => {
  it('renders no <img> and no background-image (no-artwork fallback only)', () => {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    const fixture = TestBed.createComponent(HeroArtworkComponent);
    fixture.detectChanges();

    const host: HTMLElement = fixture.nativeElement;
    expect(host.querySelector('img')).toBeNull();
    expect(getComputedStyle(host).backgroundImage).toMatch(/^(none)?$/);
  });
});
