# Menu overflow popover — in-repo design

**Tokens:** [../tokens.md](../tokens.md)
**Source:** none — **designed in-repo, 2026-08-27** (owner decision D6,
`sidebar-catalogue-overflow`; the convention itself was approved 2026-08-20,
`guest-frontend-integration` D1)
**Last updated:** 2026-08-27

> ⚠️ **Provenance: designed in-repo — no Figma counterpart.** **A Figma
> re-import will not produce this file**; if the panel is ever drawn in Figma,
> that extraction supersedes this file and this file becomes the deviation log.
> `docs/design/design-sheet.md` already records the status bar and settings as
> *"Selbst entworfen, ohne Figma-Vorlage"*; this is the fourth such surface,
> after `/pairing`, `/standby` and `/connection`.

## Purpose

**Chrome, not a screen.** The sidebar holds five slots
(`docs/design/screens/home.md` → Sidebar). When a property offers more sections
than that, the fifth slot becomes an **overflow trigger** and the rest of the
catalogue lives behind it in a popover. It has no route and no URL.

Requirements: `docs/features/sidebar-catalogue-overflow/01-prd.md`
REQ-915…REQ-932. Structure: that feature's `02-design.md` §7.

The panel is a **directory**, not a menu of favourites: the sidebar is curated
(the manager's order), the panel is findable (alphabetical), because a one-time
guest can predict the alphabet and cannot predict the manager.

## When it exists at all

| Enabled sections | Sidebar | Panel |
|---|---|---|
| ≤ 5 | all of them, delivered order — **exactly today's screen** | does not exist; the trigger is not in the DOM |
| > 5 | ranks 1–3 fixed, slot 4 rotating, slot 5 = the trigger | everything not in the sidebar |

There is **no upper bound**. The panel holds however many sections are
delivered; nothing truncates, nothing says "and 7 more".

## The trigger

The fifth `MenuIcons`-shaped item, identical in box, radius, focus ring and press
treatment to every other sidebar item — it is the same `.sidebar-item` element,
not a lookalike.

- Glyph **`phosphorDotsThree`**, `--size-icon-md` (1.625rem), the universal
  "more" idiom. (`SquaresFour` was considered and rejected: a grid glyph reads
  as a *section* named "dashboard", not as an affordance.)
- Label `nav.more` — **Mehr** / **More** — `--text-label`, like any inactive
  item.
- It never renders in the active state. The active section is always in the
  sidebar (see "Promotion"), so an active trigger would be a second, redundant
  way to say the same thing.
- `aria-haspopup` / `aria-expanded` come from the CDK; because the label is
  visible, the trigger carries **no** `aria-label`.

## Anchoring

Opens **to the right of the trigger, vertically centred on it** — not from the
viewport edge — so the panel stays visually attached to the thing that was
pressed.

| Order | Origin | Overlay |
|---|---|---|
| preferred | trigger `end` / `center` | panel `start` / `center` |
| fallback 1 | trigger `end` / `bottom` | panel `start` / `bottom` |
| fallback 2 | trigger `end` / `top` | panel `start` / `top` |

The gap to the trigger is the panel's own `margin-left: var(--space-4)` — a
token, not a pixel offset in the position config. This is the convention
`status-bar-menu.component.css` set with its `margin-top`.

The panel renders into the CDK overlay container on `document.body`
(`position: fixed`), so it overlays the content area and **cannot grow the
document**.

## Panel

The same glass recipe as every other panel, so it inherits dark mode with no
extra work:

| Property | Token |
|---|---|
| background | `--color-surface-glass` |
| border | `--border-glass` |
| radius | `--radius-lg` |
| shadow | `--shadow-panel` |
| backdrop | `--blur-glass` |
| text | `--color-text-primary` |
| padding | `--space-4` |

- `max-height: calc(100dvh - 2 * var(--space-6))` and `overflow-y: auto` on the
  grid. The panel stays inside the viewport and scrolls its own content — legal
  since constitution **v1.8.0** (the no-scroll rule binds the *document*; a
  bounded sub-component may scroll). `overflow: hidden` at screen level stays
  forbidden.
- **No entry animation.** The status-bar dropdown — the pattern this reuses —
  has none, and there is no motion token layer yet
  (`sidebar.component.css` still carries raw `180ms cubic-bezier(0.16, 1, 0.3, 1)`
  values). `design-sheet.md` → Motion is an unspent spec; spending it means
  introducing motion tokens, which is a separate change.
- No backdrop element. Outside click and `Escape` close it (CDK).

## Tile grid

**5 columns, row-major**, wrapping into as many rows as needed.

```
grid-template-columns: repeat(5, calc(var(--size-sidebar) - 2 * var(--space-3)));
gap: var(--space-2);
```

The track width is the **sidebar item's own derived box** — `6.25rem − 2 ×
0.5rem` = **5.25rem** — so the panel's tiles and the sidebar's items stay one
number (`sidebar.component.css` sets the same precedent: *derived, not
tokenised*).

| Columns | Panel content+padding | Border box (what a browser reports) | at 1024×768 | at 1280×832 |
|---|---|---|---|---|
| 5 | 29.25rem | 29.25rem + 2px | **376 px** | **470 px** |
| 6 | 34.875rem | 34.875rem + 2px | **448 px** | **560 px** |

The `+ 2px` is `--border-glass`'s 1 px hairline on each side. It is deliberately
not expressible in `rem` — `docs/constitution.md` → Styling exempts hairline
borders and blur radii from the rem rule — which is why the rem column and a
measured `getBoundingClientRect().width` differ by exactly 2 px and always will.
Measured 2026-08-27: 376 px / 470 px.

Both fit at the 1024×768 minimum, so measurement does not decide it. **5 wins on
three stated grounds:** the panel stays visually subordinate to the content area;
five echoes the sidebar's five slots, so the panel reads as *the rest of the same
menu*; and the `%dev` catalogue puts 9 sections in it, filling 5 + 4. Moving to
6 is a one-token change.

Each tile:

- square (`aspect-ratio: 1`), 5.25rem — **67 px at the 1024×768 floor**, well
  above `--size-touch-min` (2.75rem = 35 px there). *(The token is
  `--size-touch-min`. `--size-touch-target` does not exist.)*
- flex column, `gap: var(--space-1)`, centred — the sidebar item's internals;
- glyph `--size-icon-md`, the section's own configured icon;
- label `--text-label`, **wrapping to at most 2 lines** and clamped
  (`-webkit-line-clamp: 2`). This is the one place the panel diverges from the
  sidebar, which sets `white-space: nowrap` and lets a long label overflow: in a
  grid a wrapped label costs nothing, and the longest fixture label
  ("Anreise & Abreise", "Ärztliche Versorgung") needs two lines at 5.25rem;
- `border-radius: var(--radius-md)`, transparent background;
- focus ring identical to `.sidebar-item:focus-visible`;
- **no active variant.** A tile in the panel is by definition not the active
  route — choosing one promotes it into the sidebar first.

Fill order is **row-major**: left to right, then the next row. Alphabetical order
is only useful if alphabetically adjacent labels are visually adjacent, and it is
what makes the keyboard model below read correctly.

## Order

Alphabetical by the **label in the active language**, using a memoised
`Intl.Collator(locale, { numeric: true })` built from the property's own language
option (`de-DE`, `en-GB`, …) — never code-point order, which would sort
*Ärztliche Versorgung* after *Zimmer* instead of beside *Anreise*. Ties break on
the stable section key, so identical labels never reorder between renders.

Switching the language re-sorts from data already held. **No request is made.**

## Promotion

Choosing a section from the panel moves it into the sidebar's **slot 4** — the
rotating slot — where it renders active; whatever was there moves into the panel.
Slots 1–3 never move: they are the manager's fixed top three.

Reaching a panel section any other way — a Home action card, a deep link,
browser back — does the same thing, so **the active section is always visible in
the sidebar**. "Nothing looks active" is not a reachable state.

The promotion is **view state only**: per session, never stored, never sent. A
reload, a re-pair or a standby episode restores the manager's order, and a poll
that stops delivering the promoted section drops it silently.

## Keyboard

The panel is a `role="menu"` with `role="menuitem"` tiles, and it keeps that
role's **linear** keyboard contract:

| Key | Behaviour |
|---|---|
| ↑ / ↓ | previous / next tile **in reading order**, wrapping — which, with a row-major grid, is exactly the tile before/after the one you are reading |
| Home / End | first / last tile |
| a–z | typeahead onto the section label |
| Enter / Space | choose (promotes and navigates) |
| Escape / Tab | close, focus returns to the trigger |
| ← / → | **nothing**, deliberately |

Spatial (2-D) arrow movement is `role="grid"`'s contract, not `role="menu"`'s,
and the CDK supplies no grid keyboard model. Faking it would make the announced
semantics and the behaviour disagree — worse than an inert arrow key on a device
that has no keyboard at all. Row-major fill is what makes the linear model read
naturally.

## Content

App chrome via `TranslationService` — nothing here is written in a template:

| Key | de | en |
|---|---|---|
| `nav.more` | Mehr | More |
| `nav.more.aria` | Weitere Bereiche | More sections |

`nav.more.aria` is the panel's accessible name. Section labels inside the panel
are **property-authored** and arrive resolved through `PropertyContentService`.

## Both themes

Nothing theme-specific is authored. Every surface, border and text colour is a
semantic role, so `[data-theme='dark']` repaints the panel with the same
overrides it already applies to `--color-surface-glass`,
`--color-surface-glass-border` and `--color-text-primary`. Verified at review by
`node tools/measure.mjs` with the panel open, in both themes.

## Responsive behaviour

None, deliberately. There are no breakpoints in this UI: the root font size is
viewport-proportional, so the panel, its tiles and its type scale together from
1024×768 upward. The column count is **fixed at 5** on every viewport, and the
sidebar's slot count is fixed at 5 — a measured, per-viewport slot count would be
a second layout in all but name.

## Open questions

None. The two that would have been — column count and the 2-D keyboard model —
are resolved above with their evidence.
