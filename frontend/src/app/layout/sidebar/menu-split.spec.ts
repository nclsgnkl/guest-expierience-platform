import { describe, expect, it } from 'vitest';

import type { ResolvedMenuItem } from '../../core/content/property.model';
import {
  SIDEBAR_SLOTS,
  collatorFor,
  itemForRoute,
  routeMatches,
  sortByLabel,
  splitMenu,
} from './menu-split';

/**
 * The overflow rule as pure functions (02-design.md §6.1/§9) — tested here
 * rather than through `SidebarComponent`, the `resolveProperty` precedent.
 *
 * **Every label and key below is invented for this file.** REQ-903 forbids any
 * test depending on a placeholder catalogue row, so the umlaut case exercises
 * the *property* the fixtures were chosen for — a label whose first letter
 * collates far from where its code point sits — with a label the migration does
 * not contain. `Services` and `Umgebung` are original sections, not fixtures.
 */
function item(id: string, label: string, route = `/${id}`): ResolvedMenuItem {
  return { id, label, icon: 'phosphorCircle', route };
}

/** Nine delivered modules — the `%dev` shape: four sidebar slots + a popover. */
function nine(): ResolvedMenuItem[] {
  return [
    item('home', 'Home', ''),
    item('info', 'Info'),
    item('umgebung', 'Umgebung'),
    item('smart-home', 'Smart Home'),
    item('services', 'Services'),
    item('alpha', 'Alpha'),
    item('bravo', 'Bravo'),
    item('charlie', 'Charlie'),
    item('delta', 'Delta'),
  ];
}

const ids = (items: readonly ResolvedMenuItem[]): string[] => items.map((i) => i.id);
const labels = (items: readonly ResolvedMenuItem[]): string[] => items.map((i) => i.label);

describe('SIDEBAR_SLOTS', () => {
  it('is the single home of n (D3)', () => {
    expect(SIDEBAR_SLOTS).toBe(5);
  });
});

describe('routeMatches / itemForRoute', () => {
  it("normalises Home's '' to the router's '/' in both directions", () => {
    expect(routeMatches(item('home', 'Home', ''), '/')).toBe(true);
    expect(routeMatches(item('home', 'Home', '/'), '')).toBe(true);
  });

  it('matches on route, not on id — the two are independent columns', () => {
    expect(routeMatches(item('aerzte', 'Label', '/medical'), '/medical')).toBe(true);
    expect(routeMatches(item('aerzte', 'Label', '/medical'), '/aerzte')).toBe(false);
  });

  it('does not match a different route', () => {
    expect(routeMatches(item('info', 'Info'), '/umgebung')).toBe(false);
  });

  it('finds the delivered item claiming a url, and null when none does', () => {
    expect(itemForRoute(nine(), '/umgebung')?.id).toBe('umgebung');
    expect(itemForRoute(nine(), '/')?.id).toBe('home');
    expect(itemForRoute(nine(), '/nothing-claims-this')).toBeNull();
  });
});

describe('splitMenu — at or below the slot count (REQ-931)', () => {
  it('returns the delivered menu unchanged, with no overflow', () => {
    const delivered = nine().slice(0, SIDEBAR_SLOTS);
    const split = splitMenu(delivered, '/', null, 'de-DE');

    expect(split.hasOverflow).toBe(false);
    expect(ids(split.sidebar)).toEqual(ids(delivered));
    expect(split.overflow).toEqual([]);
  });

  it('does the same for a short menu, and ignores a chosen key entirely', () => {
    const delivered = nine().slice(0, 3);
    const split = splitMenu(delivered, '/', 'umgebung', 'de-DE');

    expect(split.hasOverflow).toBe(false);
    expect(ids(split.sidebar)).toEqual(['home', 'info', 'umgebung']);
  });
});

describe('splitMenu — above the slot count', () => {
  it('keeps ranks 1-3 fixed and fills slot 4 from the delivered order (REQ-915, 920, 923)', () => {
    const split = splitMenu(nine(), '/', null, 'de-DE');

    expect(split.hasOverflow).toBe(true);
    expect(ids(split.sidebar)).toEqual(['home', 'info', 'umgebung', 'smart-home']);
    expect(split.sidebar).toHaveLength(SIDEBAR_SLOTS - 1);
  });

  it('puts everything not in the sidebar into the popover, alphabetically (REQ-919, 924)', () => {
    const split = splitMenu(nine(), '/', null, 'de-DE');

    expect(ids(split.overflow)).toEqual(['alpha', 'bravo', 'charlie', 'delta', 'services']);
  });

  it('honours a chosen key in slot 4, leaving slots 1-3 untouched (REQ-923, 930)', () => {
    const split = splitMenu(nine(), '/', 'charlie', 'de-DE');

    expect(ids(split.sidebar)).toEqual(['home', 'info', 'umgebung', 'charlie']);
    /* The module previously in slot 4 goes back into the popover. */
    expect(ids(split.overflow)).toEqual(['alpha', 'bravo', 'delta', 'services', 'smart-home']);
  });

  it('drops a chosen key the poll no longer delivers, silently (REQ-930)', () => {
    const delivered = nine().filter((i) => i.id !== 'charlie');
    const split = splitMenu(delivered, '/', 'charlie', 'de-DE');

    expect(ids(split.sidebar)).toEqual(['home', 'info', 'umgebung', 'smart-home']);
    expect(ids(split.overflow)).toEqual(['alpha', 'bravo', 'delta', 'services']);
  });

  it('promotes the active module into slot 4 when it would be in the popover (REQ-932)', () => {
    const split = splitMenu(nine(), '/services', null, 'de-DE');

    expect(ids(split.sidebar)).toEqual(['home', 'info', 'umgebung', 'services']);
    expect(ids(split.overflow)).toEqual(['alpha', 'bravo', 'charlie', 'delta', 'smart-home']);
  });

  it('lets the active route win over an older choice — however the guest got there', () => {
    const split = splitMenu(nine(), '/bravo', 'charlie', 'de-DE');

    expect(ids(split.sidebar)).toEqual(['home', 'info', 'umgebung', 'bravo']);
    expect(ids(split.overflow)).toContain('charlie');
  });

  it('keeps the promoted module in slot 4 when the active module is fixed (REQ-928)', () => {
    const split = splitMenu(nine(), '/info', 'charlie', 'de-DE');

    expect(ids(split.sidebar)).toEqual(['home', 'info', 'umgebung', 'charlie']);
    expect(ids(split.overflow)).not.toContain('charlie');
  });

  it('falls back to the delivered order when nothing is active and nothing is chosen', () => {
    const split = splitMenu(nine(), '/no-such-route', null, 'de-DE');

    expect(ids(split.sidebar)).toEqual(['home', 'info', 'umgebung', 'smart-home']);
  });

  it('never lists the same module in both surfaces', () => {
    const split = splitMenu(nine(), '/services', 'charlie', 'de-DE');
    const overlap = ids(split.sidebar).filter((id) => ids(split.overflow).includes(id));

    expect(overlap).toEqual([]);
    expect(split.sidebar.length + split.overflow.length).toBe(9);
  });
});

describe('sortByLabel — collation (REQ-924, 925, 926, 927)', () => {
  it('sorts an umlaut-initial label by German collation, not by code point', () => {
    const sorted = sortByLabel(
      [item('c', 'Umgebung'), item('a', 'Öffnungszeiten'), item('b', 'Services')],
      'de-DE',
    );

    expect(labels(sorted)).toEqual(['Öffnungszeiten', 'Services', 'Umgebung']);
    /* Code-point order would put 'Ö' (U+00D6) last, after 'U' (U+0055). */
    expect(labels(sorted)).not.toEqual([...labels(sorted)].sort());
  });

  it('sorts by the label in the ACTIVE language, so de and en come out differently', () => {
    /* The same two modules, resolved into each language: German puts 'a'
       first, English puts it last. The sort therefore cannot be running on
       anything but the resolved label (REQ-924, REQ-926). */
    const de = [item('a', 'Aussicht'), item('b', 'Zimmer')];
    const en = [item('a', 'View'), item('b', 'Room')];

    expect(ids(sortByLabel(de, 'de-DE'))).toEqual(['a', 'b']);
    expect(ids(sortByLabel(en, 'en-GB'))).toEqual(['b', 'a']);
  });

  it('orders embedded numbers numerically, not lexically (numeric: true)', () => {
    const sorted = sortByLabel(
      [item('a', 'Etage 10'), item('b', 'Etage 2'), item('c', 'Etage 1')],
      'de-DE',
    );

    expect(labels(sorted)).toEqual(['Etage 1', 'Etage 2', 'Etage 10']);
  });

  it('breaks ties on the stable key, identically on every render (REQ-927)', () => {
    const tied = [item('zulu', 'Doppelt'), item('alpha', 'Doppelt'), item('mike', 'Doppelt')];

    expect(ids(sortByLabel(tied, 'de-DE'))).toEqual(['alpha', 'mike', 'zulu']);
    expect(ids(sortByLabel([...tied].reverse(), 'de-DE'))).toEqual(['alpha', 'mike', 'zulu']);
  });

  it('does not mutate its input', () => {
    const input = [item('b', 'Beta'), item('a', 'Alpha')];
    sortByLabel(input, 'de-DE');

    expect(ids(input)).toEqual(['b', 'a']);
  });

  it('falls back to a bare language code without throwing', () => {
    expect(labels(sortByLabel([item('b', 'Beta'), item('a', 'Alpha')], 'de'))).toEqual([
      'Alpha',
      'Beta',
    ]);
  });
});

describe('collatorFor — memoised, one per locale (REQ-925)', () => {
  it('returns the same instance for the same locale and a different one otherwise', () => {
    expect(collatorFor('de-DE')).toBe(collatorFor('de-DE'));
    expect(collatorFor('de-DE')).not.toBe(collatorFor('en-GB'));
  });
});
