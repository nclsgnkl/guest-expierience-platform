import { describe, expect, it } from 'vitest';

import { DEFAULT_CATALOG_LANGUAGE, TRANSLATION_CATALOGS } from './index';
import { de } from './de';
import { en } from './en';

/**
 * S35 (part 1) — catalogue completeness (REQ-124, REQ-111).
 *
 * The *type* system already makes a **missing** key a build error: every
 * catalogue is `Record<TranslationKey, string>` and `TranslationKey` is
 * derived from `de.ts`. What it cannot see is a key that exists but is empty,
 * or a catalogue that was registered without being typed — so those are what
 * is asserted here, plus the key-set equality itself, which is the property
 * the type derivation is *supposed* to guarantee and is therefore worth
 * pinning independently of it.
 */
describe('translation catalogues', () => {
  const catalogues = [...TRANSLATION_CATALOGS.entries()];

  it('registers at least the two shipped languages, and a default among them', () => {
    expect(catalogues.length).toBeGreaterThanOrEqual(2);
    expect(TRANSLATION_CATALOGS.has(DEFAULT_CATALOG_LANGUAGE)).toBe(true);
  });

  it('gives every registered catalogue exactly the same key set', () => {
    const reference = Object.keys(de).sort();
    expect(reference.length).toBeGreaterThan(0);

    for (const [code, catalogue] of catalogues) {
      expect(Object.keys(catalogue).sort(), `catalogue "${code}"`).toEqual(reference);
    }
  });

  it('leaves no value empty or whitespace-only in any catalogue', () => {
    for (const [code, catalogue] of catalogues) {
      for (const [key, value] of Object.entries(catalogue)) {
        expect(typeof value, `${code}/${key}`).toBe('string');
        expect(value.trim(), `${code}/${key}`).not.toBe('');
      }
    }
  });

  it('translates every key rather than copying the German through', () => {
    /* Not a rule that every string must differ — a proper noun could legitimately
       be identical — but nothing in today's chrome is, so a copy-paste that
       forgot to translate would show up here rather than on a guest's screen. */
    const untranslated = Object.keys(de).filter(
      (key) => en[key as keyof typeof de] === de[key as keyof typeof de],
    );
    expect(untranslated).toEqual([]);
  });

  it('interpolates only placeholders the caller can actually supply', () => {
    // No shipped chrome string takes a parameter today; if one gains a
    // `{placeholder}`, both catalogues must carry the same set of them.
    for (const key of Object.keys(de) as Array<keyof typeof de>) {
      const placeholders = (value: string) => (value.match(/\{(\w+)\}/g) ?? []).sort();
      expect(placeholders(en[key]), String(key)).toEqual(placeholders(de[key]));
    }
  });
});
