# TODO: Home Screen Prototype
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** Implemented (2026-08-18) — all tasks complete, build and test suite green. Handed off to `reviewer`.

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
