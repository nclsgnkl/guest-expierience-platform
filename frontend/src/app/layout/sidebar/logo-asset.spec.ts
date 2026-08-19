import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { PropertyContentService } from '../../core/content/property-content.service';
import { PREFERENCE_GATEWAY } from '../../core/preferences/preference-gateway';
import { SessionPreferenceGateway } from '../../core/preferences/session-preference-gateway';

/**
 * Regression guard for defect D4 (REQ-011): the shipped logo asset was valid
 * to the eye, to `git diff` and to `file(1)`, served 200 as image/svg+xml —
 * and was rejected by the browser's XML parser, because its comment contained
 * a `--` sequence, which XML forbids inside a comment. The `<img>` fired
 * `error` and the tile rendered the broken-image placeholder.
 *
 * The existing sidebar test only asserts the `src` came from config, which is
 * why it stayed green through the bug. jsdom computes no layout and decodes no
 * images, but it does have a real XML parser — so well-formedness is exactly
 * the part of D4 a unit test can catch, and this is it.
 */
describe('branding logo asset', () => {
  // Through the injector, not `new`: the service resolves the active
  // language through `PreferencesService`, which in turn needs its gateway —
  // so this needs an injection context (the logo itself is not localised).
  TestBed.configureTestingModule({
    providers: [{ provide: PREFERENCE_GATEWAY, useClass: SessionPreferenceGateway }],
  });
  const logoAsset = TestBed.inject(PropertyContentService).property().logoAsset;
  const assetPath = resolve(process.cwd(), 'src', logoAsset);
  const source = readFileSync(assetPath, 'utf8');

  it('is well-formed XML that an SVG parser accepts', () => {
    const doc = new DOMParser().parseFromString(source, 'image/svg+xml');

    expect(doc.querySelector('parsererror')).toBeNull();
    expect(doc.documentElement.nodeName).toBe('svg');
  });

  it('declares an intrinsic size alongside its viewBox, so a failure is not mistaken for a 0x0 render', () => {
    const doc = new DOMParser().parseFromString(source, 'image/svg+xml');
    const svg = doc.documentElement;

    expect(svg.getAttribute('viewBox')).toBeTruthy();
    expect(svg.getAttribute('width')).toBeTruthy();
    expect(svg.getAttribute('height')).toBeTruthy();
  });

  it('fails on the malformed form of the same file (guards the guard)', () => {
    const malformed = source.replace('a swappable', 'a -- swappable');
    const doc = new DOMParser().parseFromString(malformed, 'image/svg+xml');

    expect(doc.querySelector('parsererror')).not.toBeNull();
  });
});
