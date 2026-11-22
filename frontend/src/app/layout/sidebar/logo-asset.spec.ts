import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * Regression guard for defect D4 (REQ-011): the shipped logo asset was valid
 * to the eye, to `git diff` and to `file(1)`, served 200 as image/svg+xml —
 * and was rejected by the browser's XML parser, because its comment contained
 * a `--` sequence, which XML forbids inside a comment. The `<img>` fired
 * `error` and the tile rendered the broken-image placeholder.
 *
 * Since guest-frontend-integration (REQ-513) the property logo is an
 * authorized-fetch object URL and this file is the **fallback** the shell
 * renders when no logo asset exists or has not arrived — still shipped, still
 * parseable-or-broken, so the guard stays. The path is the shell's
 * `LOGO_PLACEHOLDER` constant, duplicated here deliberately: if the file
 * moves, this spec failing is the reminder that the guard must follow it.
 */
const assetPath = resolve(process.cwd(), 'src', 'assets/branding/logo-placeholder.svg');
const source = readFileSync(assetPath, 'utf8');

describe('branding logo placeholder asset', () => {
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
