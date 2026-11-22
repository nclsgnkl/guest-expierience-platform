import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import { de } from './catalog/de';

/**
 * S35 (part 2) — no German literal survives in the UI layer (REQ-124,
 * REQ-107, REQ-111).
 *
 * `frontend/CLAUDE.md`: *"If you are typing German into a `.html` file, you
 * are in the wrong file."* Every user-visible string is either app chrome
 * (`TranslationService`) or property-authored text (`PropertyContentService`
 * as a per-language map) — so `layout/`, `features/` and `app.routes.ts` must
 * contain neither.
 *
 * Two nets, because either alone lets something through:
 *
 *   - **the catalogue's own values**, matched verbatim — this is what catches
 *     the exact regression (`"Bald verfügbar."` and `aria-label="Hauptnavigation"`
 *     both really were hardcoded until section G);
 *   - **German-specific characters**, which catch a *new* German string that
 *     was never in the catalogue at all.
 *
 * Comments are stripped first: they are written in English but quote German
 * where it is being explained, and a comment is not a user-visible string.
 * `*.spec.ts` is excluded — a test double naming `'Deutsch'` or `'Hell'` is
 * the point of it, not a leak.
 */
const SRC = resolve(process.cwd(), 'src', 'app');

/** Exactly the areas the TODO names: route-facing UI and app chrome. */
const SCANNED_DIRECTORIES = [resolve(SRC, 'layout'), resolve(SRC, 'features')];
const SCANNED_FILES = [resolve(SRC, 'app.routes.ts')];

interface SourceFile {
  path: string;
  /** Source with comments removed. */
  code: string;
}

function collect(directory: string): string[] {
  const found: string[] = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) {
      found.push(...collect(path));
    } else if (
      entry.isFile() &&
      (entry.name.endsWith('.ts') || entry.name.endsWith('.html')) &&
      !entry.name.endsWith('.spec.ts')
    ) {
      found.push(path);
    }
  }
  return found;
}

/** `//`, `/* … *\/` and `<!-- … -->`, in that order of appetite. */
function stripComments(source: string): string {
  return source
    .replace(/<!--[\s\S]*?-->/g, ' ')
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1');
}

const FILES: SourceFile[] = [...SCANNED_DIRECTORIES.flatMap(collect), ...SCANNED_FILES].map(
  (path) => ({ path, code: stripComments(readFileSync(path, 'utf8')) }),
);

/** Reported relative to `src/app`, so a failure names something readable. */
function shortPath(path: string): string {
  return path.slice(SRC.length + 1);
}

const CATALOGUE_VALUES = Object.values(de);
/** ß and the umlauts: present in German chrome, absent from English and from code. */
const GERMAN_CHARACTERS = /[äöüÄÖÜß]/;

describe('no German literal in the UI layer', () => {
  it('scans something — a scanner that found no files would pass silently', () => {
    expect(FILES.length).toBeGreaterThan(10);
    expect(FILES.map((file) => shortPath(file.path))).toContain('app.routes.ts');
  });

  it('contains no value from the German catalogue', () => {
    const offenders = FILES.flatMap((file) =>
      CATALOGUE_VALUES.filter((value) => file.code.includes(value)).map(
        (value) => `${shortPath(file.path)}: ${value}`,
      ),
    );

    expect(offenders).toEqual([]);
  });

  it('contains no German-specific character outside a comment', () => {
    const offenders = FILES.filter((file) => GERMAN_CHARACTERS.test(file.code)).map((file) =>
      shortPath(file.path),
    );

    expect(offenders).toEqual([]);
  });

  it('guards the guard: both nets catch a literal reintroduced into a real file', () => {
    const template = FILES.find((file) => shortPath(file.path).endsWith('sidebar.component.html'));
    expect(template).toBeDefined();

    const withCatalogueValue = template!.code.replace("t('nav.aria')", `'${de['nav.aria']}'`);
    expect(CATALOGUE_VALUES.some((value) => withCatalogueValue.includes(value))).toBe(true);

    const withNewGermanString = `${template!.code}<p>Frühstück</p>`;
    expect(GERMAN_CHARACTERS.test(withNewGermanString)).toBe(true);
  });

  it('does not mistake a German route path for a German literal', () => {
    // `/einstellungen` and `/umgebung` are URLs, not user-visible copy. The
    // catalogue's `Einstellungen` is capitalised, so the match stays exact.
    const routes = FILES.find((file) => shortPath(file.path) === 'app.routes.ts')!;
    expect(routes.code).toContain('einstellungen');
    expect(CATALOGUE_VALUES.some((value) => routes.code.includes(value))).toBe(false);
  });
});
