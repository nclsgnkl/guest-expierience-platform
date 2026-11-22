# TODO: Home Screen Prototype
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** Rounds 1–5 implemented. Round 4 (2026-08-18, `dev`) applied the round-3 review's findings; round 5 (2026-08-18, `dev`) wrote the `frontend/CLAUDE.md` rewording the user then approved verbatim (findings #4/#7/#9) and corrected the stale `aspect-[24/24]` glyph claims in `docs/design/screens/home.md`. **Nothing is left open.** Awaiting `reviewer` for a re-verification pass (build/test + the re-measured geometry are recorded below).

> Each task traces to a REQ-ID. Tasks are small and sequential — `dev` should
> complete one, build/test, then move to the next. Steps marked
> `[scaffolding]` relax the usual build/test-per-step and 5-file guardrails
> per `.claude/agents/dev.md`.
>
> **Revised 2026-08-18** to match `02-design.md`'s shared app-shell /
> config-driven sidebar / card-target-section architecture. Superseded tasks
> (generic `PlaceholderComponent`, code-constant `nav-items.ts`, per-card
> routes, widget-column omission) are replaced below, not left alongside the
> new ones.

## Setup

- [x] Scaffold the Angular project into `frontend/` (standalone components,
      routing enabled, CSS styles, strict TypeScript, Vitest+jsdom via CLI
      default) — _REQ-001_ `[scaffolding]`
  - Files: `frontend/**` (generated)
  - Validate: `ng new` completes clean; note the chosen CLI flags in the
    commit/report for traceability
- [x] Verify the generated scaffold runs and tests execute — _REQ-001_ `[scaffolding]`
  - Files: none (verification only)
  - Validate: dev server starts (`ng serve` / `npm start`); `npm test` runs
    the default suite green
- [x] Self-host Plus Jakarta Sans: add font files under
      `frontend/src/assets/fonts/plus-jakarta-sans/`, `@font-face` rules in
      `frontend/src/styles/global.css`, remove any CLI-generated external font
      `<link>` from `index.html` — _REQ-002_
  - Files: `frontend/src/assets/fonts/**`, `frontend/src/styles/global.css`,
    `frontend/src/index.html`
  - Validate: build output has no `<link>`/`fetch` to an external host;
    manual check of `index.html` and the served network tab
- [x] Add `@angular/cdk`, `@ng-icons/core`, `@ng-icons/phosphor-icons` per the
      constitution's pre-approved list — _REQ-007, REQ-009_ `[scaffolding]`
  - Files: `frontend/package.json` (dependency add only — flag before
    editing per constitution's "no dependency-manifest edits without
    approval"; these three are pre-approved by name, so proceed, but state
    the exact versions added in the report)
  - Validate: `npm install` succeeds; a smoke import compiles

## Implementation — token layer & theming

- [x] Create `frontend/src/styles/tokens.css`: primitive layer
      (`--brand-primary`, `--brand-primary-deep`, `--brand-secondary`,
      `--ink-900`, `--ink-600`, `--mesh-1`…`--mesh-16`) and semantic layer
      (`--color-text-primary`, `--color-text-secondary`,
      `--color-surface-glass`, `--color-surface-menu`, `--gradient-icon`,
      `--gradient-menu-active`, spacing/radii/sizing/typography tokens) per
      `docs/design/tokens.md` — _REQ-003_
  - Files: `frontend/src/styles/tokens.css`
  - Validate: values match `docs/design/tokens.md` tables (`rem` units,
    hairline border and blur stay `px`); imported once globally
- [x] Wire the `data-theme` attribute mechanism on `<html>` (absent / `light`
      / `dark` — light values only, per `docs/design/tokens.md` → "Still
      blocked on design") — _REQ-003_
  - Files: `frontend/src/styles/tokens.css`, `frontend/src/index.html`
  - Validate: toggling the attribute manually in devtools does not error;
    no dark values needed to render correctly yet (out of scope)
- [x] Implement the mesh-gradient background as 16 stacked
      `radial-gradient` layers on `ShellComponent`'s root element (not
      `HeroArtworkComponent` — see `02-design.md` → "Mesh gradient
      background"), positioned at `0% / 33.33% / 66.67% / 100%`, colours from
      `--mesh-1..16` — _REQ-004_
  - Files: `frontend/src/app/layout/shell/shell.component.css`
  - Validate: visual check against `docs/design/assets/home-no-artwork.png`
    (intent only, not pixel values); unit test asserts the background is
    present regardless of which child route is active (see Tests)

## Implementation — content service

- [x] Create `PropertyConfig`/`MenuItem`/`ActionCard`/`PropertyBranding`
      interfaces per `02-design.md` → "Content service contract"
      (`ActionCard` carries `targetSectionId`, not a `route`;
      `PropertyConfig` carries `menuItems: MenuItem[]` and
      `settings: MenuItem`) — _REQ-006, REQ-017, REQ-018, REQ-019_
  - Files: `frontend/src/app/core/content/property.model.ts`
  - Validate: interfaces compile; shape matches the design doc exactly
- [x] Create `example-property.data.ts` with the confirmed content from
      `docs/design/screens/home.md` and this session's decisions: guest name
      "Lisa", corrected "Airbnb" spelling, headline/subtitle/paragraph
      (property-level, per PRD → Assumptions), `menuItems` (5: Home, Info,
      Umgebung, Smart Home, Services), `settings` (1: Einstellungen), 4 card
      texts each with the confirmed `targetSectionId` (Check-Out→`info`,
      Haus & Funktionen→`smart-home`, Entdecken→`umgebung`, Wichtige
      Infos→`info`), `logoAsset` path, branding primitives — _REQ-006,
      REQ-017, REQ-018, REQ-019_
  - Files: `frontend/src/app/core/content/example-property.data.ts`
  - Validate: object shape matches the interfaces; every `targetSectionId`
    matches a real `menuItems`/`settings` id; content matches the PRD's
    Assumptions section verbatim
- [x] Create `PropertyContentService` (`providedIn: 'root'`) exposing
      `getProperty(): PropertyConfig` and
      `getSectionRoute(targetSectionId: string): string` (resolves across
      `[...menuItems, settings]`, throws on an unknown id per `02-design.md`
      → "Content service contract"), backed by the static data above — no
      component may import the data file directly — _REQ-005, REQ-019_
  - Files: `frontend/src/app/core/content/property-content.service.ts`
  - Validate: unit test injects the service via `TestBed` and asserts the
    returned shape; a second test asserts `getSectionRoute()` resolves all 4
    example cards' `targetSectionId` values and throws for an unknown one;
    `grep` for stray direct imports of `example-property.data.ts` outside
    the service finds none

## Implementation — shared shell & sidebar

- [x] Build `SidebarComponent`: renders `menuItems` (5, config-driven, Home
      included) plus the `settings` entry in its own fixed last group —
      inputs, not an internal fetch, so it can render the same way from
      `ShellComponent` on every route; active state driven by the current
      route; semantic tokens only, regular weight Phosphor icons throughout
      — _REQ-007, REQ-017, REQ-018_
  - Files: `frontend/src/app/layout/sidebar/sidebar.component.ts/.html/.css`
  - Validate: unit test asserts 5 `menuItems` + 1 `settings` item render (6
    total), exactly one carries the active class/style for a given route,
    and none of the rendered labels/icons/routes are literal strings in the
    component (all traced to the injected config)
- [x] Build the logo slot in `SidebarComponent`: renders
      `PropertyConfig.logoAsset` in the 112px→`--size-icon-tile`-scaled tile
      with `--gradient-icon`, not a hardcoded `AngularLogo` — _REQ-011_
  - Files: `frontend/src/assets/branding/logo-placeholder.svg`,
    `sidebar.component.html`
  - Validate: unit test asserts the rendered `src`/`href` comes from the
    injected config, not a literal string in the component
- [x] Add minimal `:focus-visible` and `:active` states to
      `SidebarComponent` items and `ActionCardComponent`, per
      `02-design.md` → "Interaction states" — _REQ-013_
  - Files: `sidebar.component.css`, `action-card.component.css`
  - Validate: manual keyboard-tab check shows a visible ring; no
    `outline: none` without a replacement
- [x] Build `ShellComponent`: renders the mesh-gradient background (moved
      here from `HeroArtworkComponent` — REQ-004), `SidebarComponent` fed
      from `PropertyContentService.getProperty()` and the current route, and
      a `<router-outlet>` for the main-content region, per `02-design.md` →
      "Routing & the shared shell" — _REQ-004, REQ-007, REQ-020_
  - Files: `frontend/src/app/layout/shell/shell.component.ts/.html/.css`
  - Validate: unit test navigates the shell to two different child routes
    and asserts the background and Sidebar markup are present and identical
    both times, while the router-outlet content differs

## Implementation — Home content

- [x] Build `HeadingBlockComponent`: subtitle+`Sparkle` row, headline
      (guest name interpolated), paragraph, sourced from
      `PropertyContentService` — _REQ-008_
  - Files: `frontend/src/app/features/home/heading-block/heading-block.component.ts/.html/.css`
  - Validate: unit test asserts rendered text matches the injected content
    service's values (via a test double)
- [x] Build `ActionCardComponent`: icon tile (`--gradient-icon`), header,
      description, `ArrowRight` affordance; renders as a `<button>`, resolves
      its destination via `content.getSectionRoute(card.targetSectionId)`
      and navigates there on click and on Enter/Space — _REQ-009, REQ-019_
  - Files: `frontend/src/app/features/home/action-card/action-card.component.ts/.html/.css`
  - Validate: unit test simulates click and keydown(Enter) for a card whose
    `targetSectionId` resolves to a known route, asserts navigation is
    triggered with that resolved route (not a literal route on the card)
- [x] Build `HeroArtworkComponent`: renders only the no-artwork fallback —
      no `<img>`, no scrim, and (per the 2026-08-18 revision) no background
      of its own either, since that now lives in `ShellComponent` — _REQ-010_
  - Files: `frontend/src/app/features/home/hero-artwork/hero-artwork.component.ts/.html/.css`
  - Validate: unit test asserts no `img`/background-image-with-url is
    present in the rendered DOM
- [x] Compose `HomeContentComponent`: lays out heading block + card row +
      hero artwork + a reserved, empty `flex: 1` widget-column element per
      `docs/design/screens/home.md` → Layout's `Welcome` row and
      `02-design.md` → "Home content layout & the widget-column
      reservation"; no longer composes Sidebar or background (that's
      `ShellComponent`'s job) — _REQ-008, REQ-009, REQ-014_
  - Files: `frontend/src/app/features/home/home-content.component.ts/.html/.css`
  - Validate: unit test asserts a widget-column element **is present** in
    the DOM (empty, `flex: 1`) — not absent; visual check against
    `docs/design/assets/home-no-artwork.png` (intent only)

## Implementation — routing & section placeholders

- [x] Build `SectionPlaceholderComponent`: reads
      `route.snapshot.data['sectionId']`, resolves the matching `MenuItem`
      via `PropertyContentService` (across `menuItems` + `settings`), and
      renders a minimal token-styled "coming soon" message using that item's
      `label` — never a hardcoded per-route title — _REQ-021_
  - Files: `frontend/src/app/features/section-placeholder/section-placeholder.component.ts/.html/.css`
  - Validate: unit test renders with a mocked route `sectionId` and a mocked
    content service, asserts the resolved section's `label` appears
- [x] Wire `app.routes.ts`: `ShellComponent` as parent at `''`, with
      children `''` → `HomeContentComponent` and `info` / `umgebung` /
      `smart-home` / `services` / `einstellungen` → `SectionPlaceholderComponent`
      (5 routes, not 9 — cards have no routes of their own), per
      `02-design.md` → "Routing & the shared shell" — _REQ-012, REQ-020_
  - Files: `frontend/src/app/app.routes.ts`
  - Validate: unit test navigates to each of the 5 non-Home routes plus `''`
    via `Router`, asserts the resolved component and that `ShellComponent`
    remains the outer host each time; manual click-through in the dev
    server, including tapping each of the 4 cards
- [x] Add the `min-width` guard rail for viewports below 1280px per
      `docs/design/tokens.md` → Breakpoints — _REQ-016_
  - Files: `frontend/src/styles/global.css`
  - Validate: resizing the viewport below 1280px shows horizontal scroll,
    not a broken/overlapping layout

## Tests

- [x] `PropertyContentService` unit test: `getProperty()` shape, and
      `getSectionRoute()` resolving all 4 example cards' `targetSectionId`
      values (including the two that both resolve to `info`) plus throwing
      for an unknown id — _REQ-005, REQ-006, REQ-019_
- [x] `SidebarComponent` render + active-state test: 5 `menuItems` + fixed
      `settings` item render from injected config, correct item active per
      route — _REQ-007, REQ-017, REQ-018_
- [x] `SidebarComponent` logo-slot-from-config test — _REQ-011_
- [x] `ShellComponent` test: background + Sidebar identical across two
      different child routes; router-outlet content differs — _REQ-004,
      REQ-020_
- [x] `HeadingBlockComponent` content test — _REQ-008_
- [x] `ActionCardComponent` render + click/keyboard navigation test,
      asserting navigation targets the route resolved from
      `targetSectionId` — _REQ-009, REQ-019_
- [x] `HeroArtworkComponent` no-artwork-fallback test — _REQ-010_
- [x] `SectionPlaceholderComponent` test: renders the correct section label
      for a mocked `sectionId`, for at least two different sections — _REQ-021_
- [x] Routing test: Home resolves at `''`; each of the 5 non-Home routes
      resolves and renders its section's label; all rendered inside
      `ShellComponent` — _REQ-012_
- [x] `HomeContentComponent` layout test: an empty `flex: 1` widget-column
      element is present (not absent) — _REQ-014_
- [x] Full suite run confirms all of the above are green — _REQ-015_

## Cleanup

- [x] Confirm no external runtime requests anywhere in the build (font,
      icon, image) — re-check after all steps, not just after REQ-002's step
- [x] Confirm no direct import of `example-property.data.ts` outside
      `PropertyContentService` (constitution principle 2)
- [x] Confirm no card's `targetSectionId` and no route wired in
      `app.routes.ts` is orphaned — every `menuItems`/`settings` id has a
      matching child route, and every card's `targetSectionId` resolves
      (already covered by the `PropertyContentService` test above; this is a
      final cross-check, not a new test)
- [x] Propose (do not write) a `frontend/CLAUDE.md` entry capturing the
      conventions established here (standalone components, token layer
      location, content-service pattern, shared app-shell + section-
      placeholder pattern, card-target-section resolution) for the user to
      confirm — per `.claude/agents/dev.md` → Convention Capture
- [x] Update `docs/features/home-screen-prototype/03-todo.md` checkboxes and
      hand off to `reviewer`

---

## Round 3 (2026-08-18): visual-defect correction

> **This section is the whole of the remaining work.** Everything above is
> done; do not re-run it. Each task traces to a `REQ-ID`, touches few files,
> and is independently verifiable — **build, test, and *look at the rendered
> screen* after each one**. The last part is not optional: every defect in this
> round survived a green build, a green 29-test suite and a PASS review,
> because nothing in that chain rendered a pixel.
>
> **Ground truth for this round is `docs/design/assets/home-no-artwork.png`**
> and the measured numbers in `01-prd.md` → Acceptance criteria. Do **not**
> query Figma or any design MCP server — metered, and forbidden outside
> `/design-import` (`docs/constitution.md`).
>
> **Environment first:** `npm install` and `npx playwright install chromium`
> have not necessarily been run since `playwright` was added to
> `package.json`. `npm run build` and `npm test` have not run since the
> out-of-band fixes of 2026-08-18 (mesh sizing, action-card `flex-shrink`, icon
> sizes, background saturation). **Establish a green baseline before touching
> anything**, so a failure later is attributable.
>
> **R7 was blocked** on an approval-gated decision until the user approved the
> scaling mechanism on 2026-08-18 (uncapped viewport-proportional root font
> size); `docs/constitution.md` v1.2.0 records it. All of R0–R8 are done.

### R0 — baseline

- [x] Install and verify the existing state before any change: `npm install`,
      `npx playwright install chromium` (once), `npm run build`, `npm test`.
      Record the result. If the suite is not green *before* round 3 starts,
      stop and report — the out-of-band fixes were never verified
      — _no REQ-ID (process)_
  - Files: none
  - Validate: build green; tests green (expected: 10 files / 29 tests)

### R1 — the `:host` layout convention

- [x] Apply the host-participation convention from `02-design.md` → "The host
      element is the layout, not the template root": `:host { display:
      contents; }` on `SidebarComponent` and `HomeContentComponent`;
      `:host { display: none; }` on `HeroArtworkComponent`.
      `ActionCardComponent` already has it — leave it, and note in the commit
      that it is now a codebase convention rather than a one-off
      — _REQ-022, REQ-014, REQ-010_
  - Files: `frontend/src/app/layout/sidebar/sidebar.component.css`,
    `frontend/src/app/features/home/home-content.component.css`,
    `frontend/src/app/features/home/hero-artwork/hero-artwork.component.ts`
    (or a new `.css` for it)
  - Validate: build + tests green. Measure in the browser: the card row's left
    edge moves from `x = 204` to `x = 172` (that is D7 fixed by the
    `HeroArtworkComponent` change alone). Sidebar/home heights do not change
    yet — R2/R3 do that; this task only makes the boxes reachable

### R2 — full-height sidebar (D1)

- [x] `.shell`: `align-items: center` → `stretch`. `.sidebar-item`: replace the
      fixed `width/height: var(--size-sidebar-item)` with `width: 100%;
      aspect-ratio: 1; height: auto` and give `.sidebar__menu-items`
      `width: 100%`, per `02-design.md` → "Full-height sidebar and group
      distribution" — _REQ-022_
  - Files: `frontend/src/app/layout/shell/shell.component.css`,
    `frontend/src/app/layout/sidebar/sidebar.component.css`
  - Validate: measured sidebar box is `x 32, y 24, w 100, h 784` (±2px) at
    1280×832; the gap between the logo and the first nav item, and between the
    last nav item and Settings, is each > 60px; the active pill measures
    84 × 84. Compare against the reference render side by side

### R3 — heading top, cards bottom (D2)

- [x] `.home-content`: `align-items: flex-end` → `stretch`, `min-height: 100%`
      → `height: 100%`. Remove the now-inert `gap: var(--space-6)` from
      `.home-content__screen` (its `justify-content: space-between` and
      `padding: var(--space-9) 0` stay and now do the work) — _REQ-014_
  - Files: `frontend/src/app/features/home/home-content.component.css`
  - Validate: heading block top edge ≈ `y = 88`, card row bottom edge
    `y = 744`, at 1280×832; the space between them is empty; the reserved
    widget column is still present (existing REQ-014 test must stay green)

### R4 — logo asset (D4)

- [x] Root-cause fix: rewrite `logo-placeholder.svg`'s comment so it contains
      no `--` sequence (illegal inside an XML comment — this is why the file is
      rejected as malformed and renders as the broken-image placeholder), and
      add explicit `width`/`height` attributes alongside the `viewBox` so the
      asset has an intrinsic size — _REQ-011_
  - Files: `frontend/src/assets/branding/logo-placeholder.svg`
  - Validate: the logo renders in the pink tile (screenshot it); in the
    browser, `img.complete === true` **and** `img.naturalWidth > 0`, and the
    `error` event does not fire
- [x] Add a regression test that the shipped SVG **parses**:
      `DOMParser.parseFromString(<file contents>, 'image/svg+xml')` yields no
      `<parsererror>` element. This is the one part of D4 that jsdom can
      actually catch, and the existing REQ-011 test (which only asserts the
      `src` came from config) demonstrably cannot — _REQ-011, REQ-015_
  - Files: a spec next to the sidebar's existing tests
  - Validate: the new test fails against the pre-R4 file and passes after

### R5 — no text wrapping (D5)

- [x] `white-space: nowrap` on `.sidebar-item__label`; on
      `.action-card__header` add `white-space: nowrap; overflow: hidden;
      text-overflow: ellipsis`; pin the card's description row to the bottom of
      the text block (`justify-content: flex-end` on
      `.action-card__text-block`) so a long title can never desynchronise the
      four cards again — per `02-design.md` → "Text must not wrap".
      R2 must land first: the sidebar label only *fits* once the item is 84px
      wide — _REQ-007, REQ-009_
  - Files: `frontend/src/app/layout/sidebar/sidebar.component.css`,
    `frontend/src/app/features/home/action-card/action-card.component.css`
  - Validate: "Smart Home" and "Einstellungen" each render on one line;
    "Haus & Funktionen" renders on one line; the four cards' title,
    description and arrow y-positions are pairwise identical (measure, do not
    eyeball)

### R6 — gradient-filled active icon (D3)

- [x] Add the tokens: primitive layer unchanged (the gradient's end stop is the
      **existing** `--brand-secondary`, not a new colour — see
      `docs/design/tokens.md` → Gradients for why); semantic layer gains
      `--color-icon-active-start: var(--brand-primary)`,
      `--color-icon-active-end: var(--brand-secondary)` and, as the canonical
      specification of what they compose into,
      `--gradient-icon-active: linear-gradient(139deg, var(--color-icon-active-start), var(--color-icon-active-end))`
      — _REQ-023, REQ-003_
  - Files: `frontend/src/styles/tokens.css`
  - Validate: build green; no component references a primitive directly
- [x] Add the SVG gradient definition to `ShellComponent`'s template (inline,
      zero-size `<svg><defs><linearGradient id="icon-gradient-active"
      gradientTransform="rotate(49 0.5 0.5)">` with two class-carrying
      `<stop>`s) and style the stops from `shell.component.css` using the two
      semantic roles — per `02-design.md` → "Gradient-filled icon glyph"
      — _REQ-023_
  - Files: `frontend/src/app/layout/shell/shell.component.html`,
    `frontend/src/app/layout/shell/shell.component.css`
  - Validate: the `<linearGradient>` is in the DOM on every route; its stops'
    computed `stop-color` resolves to the brand colours
- [x] Add the one global rule `.icon-gradient-active svg { fill:
      url('#icon-gradient-active') currentColor; }` to `global.css` — it
      **cannot** live in `sidebar.component.css`, because `@ng-icons` appends
      its `<svg>` via `Renderer2` outside the template, so the node carries no
      `_ngcontent-*` attribute and no scoped selector can match it — and apply
      `[class.icon-gradient-active]="isActive(item)"` to the sidebar's
      `<ng-icon>`s — _REQ-023_
  - Files: `frontend/src/styles/global.css`,
    `frontend/src/app/layout/sidebar/sidebar.component.html`
  - Validate: the active glyph renders pink→violet, inactive glyphs stay flat
    `--color-text-primary`; **check a non-Home route too** (the document has
    `<base href="/">`, which historically broke bare `url(#id)` fragment
    references on non-root URLs); overriding `--brand-primary` at runtime
    changes the glyph. Unit test: the active item's `<ng-icon>` carries the
    class and no inactive one does

### R7 — viewport fit and scaling (D6) — unblocked 2026-08-18

- [x] **Approved by the user 2026-08-18** — viewport-proportional root font
      size, explicitly **uncapped** at the top end, accessibility trade
      accepted for this kiosk device; `docs/constitution.md` amended to v1.2.0
      accordingly. Applied the agreed root
      font-size rule, change `.shell` from `min-height: 100dvh` to
      `height: 100dvh`, and move the guard rail from `min-width: 1280px` to
      `min-width: 1024px` — _REQ-024, REQ-025, REQ-016_
  - Files: `frontend/src/styles/global.css`,
    `frontend/src/app/layout/shell/shell.component.css`
  - Validate: at 1024×768, 1280×832 and 1440×900, on **every** route,
    `scrollWidth ≤ clientWidth` and `scrollHeight ≤ clientHeight`; the DOM
    structure at 1024×768 is identical to 1280×832 and nothing that was one
    line has become two; no unconditional `overflow: hidden` was used to get
    there

### R8 — verify and close

- [x] Full visual check against `docs/design/assets/home-no-artwork.png` at
      1280×832 **and** at 1024×768 (the latter only meaningful once R7 lands),
      plus every measurement listed in `01-prd.md` → Acceptance criteria for
      REQ-007, REQ-009, REQ-010, REQ-011, REQ-014, REQ-022, REQ-023, REQ-024,
      REQ-025. Record the numbers, not "looks right" — _REQ-015_
  - Files: none
  - Validate: build green, tests green, every measured value inside tolerance
- [x] Re-check the three unrelated defects fixed out-of-band on 2026-08-18
      (mesh gradient sizing, background saturation, icon sizes, action-card
      `flex-shrink`) are still correct after these layout changes — none has
      ever been build/test verified — _no REQ-ID (process)_
- [x] Update this file's checkboxes and hand back to `reviewer`. The review
      must include the visual-check step this time — the previous PASS predates
      it, which is the entire reason this round exists

## Round 4 — round-3 review fixes (2026-08-18)

> Source: `06-review.md` → "Round 3 review" (🔴 0 · 🟠 4 · 🟡 5 · 🟢 8). The
> three design calls in it were ruled by the user before this round started:
> rename card 2's copy rather than change its padding (#1), take the 390px
> `--size-heading` (#2), give the sparkle the gradient (#3). `playwright` was
> approved as a *verification tool* in `docs/constitution.md` v1.3.0, which
> resolves #8 at the governance level without deciding the E2E framework.

- [x] **#1 — card 2's title no longer truncates.** Renamed the configured copy
      "Haus & Funktionen" → **"Smart Home"** (and its `id` to
      `smart-home-karte`, matching the `umgebung-karte` precedent);
      `targetSectionId` still `smart-home`. Card padding deliberately
      unchanged at `--space-5`. Doc table in `docs/design/screens/home.md`
      updated the same way the "Entdecken"→"Umgebung" rename was
      — _REQ-009, REQ-006, REQ-008_
  - Files: `frontend/src/app/core/content/example-property.data.ts`,
    `frontend/src/app/features/home/action-card/action-card.component.css`
    (comments only), `docs/design/screens/home.md`
  - Validate: measured in Chromium — title ink **111px** in the **162px** box
    (was 167px); 0 of 4 titles truncated at 1024 and 1280; the ellipsis
    fallback still fires for over-long copy (probed with the old string:
    167px → `TRUNCATED`)
- [x] **#2 — `--size-heading` corrected from 780px to 390px.** 780 is the raw
      Figma value on the 2× canvas; it was the only number in
      `home.md` → Components never halved — _REQ-014_
  - Files: `frontend/src/styles/tokens.css`, `docs/design/tokens.md`,
    `docs/design/screens/home.md`, `01-prd.md` (REQ-014), `02-design.md`
  - Validate: heading block measures **390px**; the headline now wraps to
    **two** lines (359 + 375px) and the paragraph to two (347 + 150px),
    against the reference render's 356 + 369 and 348 + 150.4; ink band of
    headline line 1 is **y 130–166 in both**; no layout side effects (card row
    still `x 172` / bottom `744`, widget column still 224px, no scroll)
- [x] **#3 — the heading sparkle is filled and gradient-painted.** Switched to
      Phosphor's `fill` weight (`phosphorSparkleFill`) and the existing
      `.icon-gradient-active` paint server — _REQ-023 (extended)_
  - Files: `frontend/src/app/features/home/heading-block/heading-block.component.html`,
    `frontend/src/app/app.config.ts`,
    `frontend/src/app/features/home/heading-block/heading-block.component.spec.ts`,
    `docs/design/screens/home.md`, `docs/design/design-sheet.md`, `01-prd.md`
  - Validate: **rendered pixels**, not computed style — glyph ink runs
    `(201,91,199)` top → `(173,92,220)` bottom with **0 flat-navy pixels**,
    against the reference's `(220,96,185)` → `(179,95,216)`; a headline control
    sample in the same scan stays flat `(44,31,74)` in both. Unit test asserts
    the requested glyph name and the class
- [x] **#4 / #7 / #9 — `frontend/CLAUDE.md` rewording.** Held at the approval
      gate through round 4 by design (`docs/constitution.md` → Decisions
      Requiring Your Approval: the exact text of a nested `CLAUDE.md` entry is
      confirmed before it is written, and #9 is precisely the finding that this
      gate had previously been satisfied opt-out rather than opt-in). The user
      approved the exact wording; **written in round 5** — see below —
      _no REQ-ID (conventions)_
- [x] **#5 — Node ambients scoped out of the app compilation** — _REQ-001_
  - Files: `frontend/tsconfig.app.json`, `frontend/src/node-builtins.d.ts`
  - Validate: a throwaway probe under `src/app/` importing `node:fs` and
    calling `process.cwd()` now fails `tsc -p tsconfig.app.json --noEmit`
    with TS2591 on both (probe deleted); tests, which do need the ambients,
    stay green
- [x] **#6 — `02-design.md`'s "the shipped copy fits" corrected**, and the
      164px title box corrected to 162px there and in
      `action-card.component.css` — _no REQ-ID (docs)_
- [x] **#7 — one committed measurement instrument**: `frontend/tools/measure.mjs`
      (no-scroll on 6 routes × 3 viewports, REQ-022/REQ-014 boxes, wrapping and
      truncation, logo decode). Reports numbers and asserts nothing, always
      exits 0, is not matched by `tsconfig.spec.json`'s spec pattern and so is
      not picked up by `npm test`; run as `node tools/measure.mjs`. No
      `package.json` edit — _REQ-015, REQ-024_
- [x] **#12 / #13 / #14 / #15 (Info) applied**: the two unreferenced tokens now
      say at their definition site why they exist and what can drift;
      `docs/design/tokens.md`'s Sizing table drops the deleted
      `--size-widget-col` and gains `--size-heading`; `design-sheet.md`'s
      "Glyph-Box 12 px" is corrected to point at `tokens.md`; `home.md`'s
      "AirBnb" becomes "Airbnb" so REQ-008's "matches the doc exactly" is true
      of the shipped copy — _REQ-008_

## Round 5 — approved wording + the stale glyph claims (2026-08-18)

> Two doc-only tasks closing round 3. No application code, no token *value* and
> no dependency-manifest change; the code was already correct in both cases.

- [x] **#4 / #7 / #9 — the three approved replacements written into
      `frontend/CLAUDE.md`** (verbatim as confirmed by the user): the
      documented `--mesh-1…16` exception to "never a primitive"; the
      `property-content.service.spec.ts` exception to "nothing outside that
      service imports the data file"; and `node tools/measure.mjs` named in the
      Tests section as the browser-measurement instrument, with the note that
      `playwright` is approved as review-time tooling only and the E2E-framework
      decision is still open — _no REQ-ID (conventions)_
  - Files: `frontend/CLAUDE.md`
  - Validate: both new rules re-checked against the shipped code rather than
    assumed. `grep` for `var(--<primitive>)` across `src/` returns **only**
    `shell.component.css` (17 hits: `--mesh-1…16` plus `--mesh-6` as
    `background-color`) — no other primitive reference exists. `grep` for
    `example-property.data` returns only `property-content.service.ts` and
    `property-content.service.spec.ts`. Both rules are true as written
- [x] **The `aspect-[24/24]` misread purged from
      `docs/design/screens/home.md`.** It was corrected in `tokens.md` after
      round 2 but never here, so this document still taught the bug that once
      made every icon render at a third of its size. Four sites repointed at
      the corrected token layer — sidebar nav → `--size-icon-md` (26px CSS),
      card tile glyph → `--size-icon-lg` (36px CSS), card arrow and heading
      sparkle → `--size-icon-sm` (24px CSS) — plus a warning block under
      `## Components` recording the trap itself, since this is the second
      defect round it has cost — _no REQ-ID (docs)_
  - Files: `docs/design/screens/home.md`
  - Validate: the four values match `tokens.css`'s call sites exactly
    (`sidebar.component.html` ×2 `--size-icon-md`, `action-card.component.html`
    `--size-icon-lg` + `--size-icon-sm`, `heading-block.component.html`
    `--size-icon-sm`). `design-sheet.md` needed no change — Info #14's
    "Glyph-Box 12 px" was already corrected in round 4. Build green, tests
    11 files / 35 passed / 0 failed
