import type { ResolvedMenuItem } from '../../core/content/property.model';

/**
 * The sidebar's overflow rule, as pure functions (sidebar-catalogue-overflow
 * 02-design.md §6.1). No service, no injectable, no state beyond one module-level
 * collator cache — the `resolveProperty` precedent: exported so the rule is unit
 * tested directly rather than through a component (frontend/CLAUDE.md -> Tests).
 *
 * Everything here operates on ALREADY language-resolved items
 * (`ResolvedMenuItem.label` is a `string`), which is REQ-926 by construction:
 * the sort cannot run before language resolution, because an unresolved label
 * is not in scope in this file.
 */

/**
 * How many slots the sidebar has, at every viewport (D3, REQ-915). **The only
 * place `n` lives.** Above this many delivered items the last slot becomes the
 * overflow trigger, so the sidebar shows `SIDEBAR_SLOTS - 1` sections plus the
 * trigger. There is deliberately no matching bound on the popover: it holds
 * whatever is delivered (REQ-917).
 */
export const SIDEBAR_SLOTS = 5;

export interface MenuSplit {
  /** In delivered order; `SIDEBAR_SLOTS - 1` long when `hasOverflow`. */
  readonly sidebar: ResolvedMenuItem[];
  /** Alphabetical by label in the active locale; empty unless `hasOverflow`. */
  readonly overflow: ResolvedMenuItem[];
  readonly hasOverflow: boolean;
}

/**
 * One normalisation of a menu route, shared by `SidebarComponent` and
 * `SectionPlaceholderComponent` so the two can never disagree about what is
 * active — Home is delivered as `''` and the router reports it as `'/'`.
 * Matching is on `route`, not on `id`: `key` and `route` are independent
 * columns in `menu_item_definition`.
 */
export function routeMatches(item: ResolvedMenuItem, url: string): boolean {
  return normalise(item.route) === normalise(url);
}

/** The delivered item claiming `url`, or `null` — the route table's inverse. */
export function itemForRoute(
  items: readonly ResolvedMenuItem[],
  url: string,
): ResolvedMenuItem | null {
  return items.find((item) => routeMatches(item, url)) ?? null;
}

/**
 * Split the delivered menu into the sidebar's slots and the popover's contents.
 *
 * Six requirements fall out of the one expression below, which is why this is a
 * pure function and not logic scattered across a component:
 *
 *   REQ-920 the only input is the delivered array — delivered order IS resolved
 *           sort-index order, and no `pinned` concept exists anywhere;
 *   REQ-923 `fixed` is never rewritten, so only slot 4 ever varies;
 *   REQ-928 active and promotion are separate terms — when the active item is
 *           one of `fixed`, the promoted module stays in slot 4, inactive;
 *   REQ-930 a `chosenKey` the poll no longer delivers falls through to the
 *           delivered order, silently;
 *   REQ-931 the early return: at or below `SIDEBAR_SLOTS` there is no trigger,
 *           no popover and no promotion — the sidebar is today's, unchanged;
 *   REQ-932 the first arm: reaching an overflow module by an action card, a deep
 *           link or browser back promotes it, so the active module is always in
 *           the sidebar and "nothing looks active" is unreachable.
 */
export function splitMenu(
  delivered: readonly ResolvedMenuItem[],
  activeUrl: string,
  chosenKey: string | null,
  locale: string,
): MenuSplit {
  if (delivered.length <= SIDEBAR_SLOTS) {
    return { sidebar: [...delivered], overflow: [], hasOverflow: false };
  }

  const fixed = delivered.slice(0, SIDEBAR_SLOTS - 2);
  const rest = delivered.slice(SIDEBAR_SLOTS - 2);
  const active = itemForRoute(delivered, activeUrl);

  const rotating =
    active && !fixed.includes(active)
      ? active
      : (rest.find((item) => item.id === chosenKey) ?? rest[0]);

  return {
    sidebar: [...fixed, rotating],
    overflow: sortByLabel(
      rest.filter((item) => item !== rotating),
      locale,
    ),
    hasOverflow: true,
  };
}

/**
 * Alphabetical by the label in the ACTIVE language (REQ-924, REQ-925), ties
 * broken on the stable section key so identical labels never reorder between
 * renders (REQ-927). The tie-break is code-point order on purpose: it is a
 * stability device, not something a guest reads, so it must not depend on the
 * locale.
 */
export function sortByLabel(
  items: readonly ResolvedMenuItem[],
  locale: string,
): ResolvedMenuItem[] {
  const collator = collatorFor(locale);
  return [...items].sort(
    (a, b) => collator.compare(a.label, b.label) || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0),
  );
}

/**
 * One `Intl.Collator` per locale, for the module's lifetime — the memoisation
 * shape `StatusBarComponent`'s `timeFormatter` already uses (REQ-925).
 * Constructing a collator per render would be the expensive mistake here. The
 * map never grows past the property's own offered languages.
 *
 * `numeric: true` so "Etage 2" sorts before "Etage 10".
 */
export function collatorFor(locale: string): Intl.Collator {
  let collator = collators.get(locale);
  if (!collator) {
    collator = new Intl.Collator(locale, { numeric: true });
    collators.set(locale, collator);
  }
  return collator;
}

const collators = new Map<string, Intl.Collator>();

function normalise(route: string): string {
  return route === '' ? '/' : route;
}
