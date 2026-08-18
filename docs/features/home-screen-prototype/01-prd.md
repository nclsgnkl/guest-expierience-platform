# PRD: Home Screen Prototype
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** In Review

## Problem statement

Nothing is implemented yet — there is no Angular project, no `src/`, no
`angular.json`. The user asked for "a first prototype of this application
based on the design documents." The only screen with an actual design spec is
the Home/Welcome screen (`docs/design/screens/home.md`); the other five
`README.md` modules (Ankommen, Wohnen, Hilfe, Umgebung, Abreise,
Einstellungen — detail pages) are product vision, not yet designed in Figma.

This feature scaffolds the Angular project **and** implements the Home screen
end to end, so that:
1. A guest opening the app sees a real, on-brand welcome screen, not a
   generic Angular starter page.
2. Every future feature (WLAN detail, house rules, …) has a project skeleton,
   a token layer, and a content-service pattern to build on, rather than
   re-deciding those basics each time.

## Goals / Non-goals

**Goals:**
- Stand up the Angular project (`frontend/`) per `docs/constitution.md`:
  strict TypeScript, standalone components, Angular Router, Vitest + jsdom,
  self-hosted Plus Jakarta Sans, `@ng-icons` + Phosphor, `@angular/cdk`.
- Implement the design-token CSS layer (primitive → semantic) from
  `docs/design/tokens.md`, including the `data-theme` and per-property
  branding runtime hooks — even though only one theme/property exists today.
- Implement the Home/Welcome screen per `docs/design/screens/home.md`,
  **no-artwork variant**, sourced entirely from a property content service
  (constitution principles 1 & 2) — not hardcoded in components.
- Give every visible interactive control (6 sidebar items, 4 action cards) a
  real, working destination, even where that destination has no design yet —
  no dead-looking or silently-inert controls on a touch-first kiosk screen.
- Cover the above with Vitest component/unit tests (constitution principle 6).

**Non-goals** (explicitly out of scope for this prototype):
- The right-hand **widget column**'s *content* (weather / host contact /
  recommendations). Nothing is designed for it in Figma; `README.md` itself
  frames these as *hypotheses* to validate later, not requirements. Weather
  and recommendations would also each pull in an external data source, which
  is an infrastructure decision gated under `docs/constitution.md` and well
  beyond "first prototype." Treat "select + design + build the widget
  column" as its own future feature.
  **User decision (2026-08-18):** unlike the rest of this non-goal, the
  column's **space is not absent** — the layout reserves it (`flex: 1`,
  empty, per `docs/design/screens/home.md` → Layout) so the Home screen's
  proportions already match the designed composition and adding widgets
  later is a content change, not a layout one. See REQ-014.
- **Brand-tinted background gradient.** `docs/design/tokens.md` already
  specifies the mesh gradient's *default* as a fixed 16-colour palette, with
  per-property tinting explicitly "prepared, not built" (no derivation rule
  from `--brand-primary` exists yet). This prototype implements the fixed
  default only, structured so tinting can be added later without touching the
  gradient's shape. **Flagged for your confirmation** — see Risks.
- Real 3D property renderings, the loading state for one, and any per-property
  artwork upload pipeline. Only the no-artwork fallback is built.
- A final logo asset. A swappable placeholder ships instead.
- Dark mode values (the `data-theme` mechanism is wired, but no dark palette
  exists in Figma yet — light only).
- Detail/content pages for WLAN, house rules, devices, checkout, environment
  (the six full modules from `README.md`'s "Nächster Schritt"). Their nav/card
  entry points exist and route somewhere (see REQ-012), but the destination
  screens themselves are minimal placeholders, not real designs.
- i18n / multi-language content, backend integration, authentication, admin
  area, ~~responsive/phone layouts~~, an E2E test framework.
  **Amended 2026-08-18 (round 3):** *phone* and *portrait* layouts stay out of
  scope, and there are still no breakpoints and no second layout. What is now
  **in** scope is proportional scaling across a supported landscape range
  (1024×768 → 1280×832 → larger) with no reflow and no scrollbars — see
  REQ-024/REQ-025. That is a scaling contract, not a responsive layout.

## Assumptions

- **Project location:** the Angular app scaffolds into `frontend/`, sibling to
  a future `backend/` — `CLAUDE.md` itself already anticipates this split
  (`backend/CLAUDE.md`, `frontend/CLAUDE.md`).
- **Content correction:** the headline uses the correct trademark spelling
  **"Airbnb"** (not "AirBnb"). `docs/design/design-sheet.md` explicitly flags
  the Figma spelling as wrong; since this is config data, not a code change,
  correcting it costs nothing and avoids shipping a known typo.
- **Guest name:** "Lisa" (Figma is authoritative over the PNG's "Anna" per
  `docs/design/screens/home.md`'s resolution table).
- **Heading content is property-level configuration, not per-guest
  personalisation — confirmed 2026-08-18.** The subtitle ("Schön, dass ihr da
  seid"), headline ("Willkommen im Airbnb von {guestName}!") and paragraph
  are authored by the **property owner/host at configuration time**, exactly
  like the card texts and branding colours — they are not generated,
  templated, or looked up per individual guest or per stay. This matters to
  state explicitly because the headline happens to contain a name, which
  could be mistaken for guest-level personalisation; it is not. There is no
  guest-level or stay-level data model, and no authentication, anywhere in
  this MVP (constitution constraints; `docs/system-context.md` → Stack), so
  no such mechanism is implied by REQ-006/REQ-008 — "guestName" is one more
  field on the single property config, changed by editing that config, the
  same way the card copy is.
- **Card copy:** uses the confirmed Figma text overrides already recorded in
  `docs/design/screens/home.md` (Check-Out / Haus & Funktionen / Entdecken /
  Wichtige Infos) — no further invention needed.
- **Sidebar nav structure is config-driven, not a code constant — revised
  2026-08-18.** The original plan below (superseded) treated the 6 sidebar
  items as app-wide product structure living in a code file. The user
  corrected this: labels, icons and routes for the sidebar's `menuItems`
  (Home, Info, Umgebung, Smart Home, Services — REQ-017) come from
  `PropertyContentService` like every other guest-facing text, per
  constitution principle 1. The one exception is **Settings**, whose
  *position* (always present, always last, its own group) stays structurally
  fixed in code — matching Figma's separate "Settings" group, distinct from
  the five `MenuIcons` in "MenuItems" — while its label/icon/route content
  still comes from config for consistency (REQ-018). Full rationale in
  `02-design.md` → "Sidebar config & the Settings decision".
  ~~Superseded assumption: sidebar nav structure lives as a small code
  constant because it doesn't vary per property.~~ That reasoning held for
  *whether the module exists*, which genuinely doesn't vary yet, but not for
  *how it's labelled/iconed/routed* — which is exactly the kind of
  property-facing text principle 1 already covers, and the user asked for it
  explicitly.
- **Undesigned destinations, and how cards reach them — revised 2026-08-18.**
  Originally, all 9 undesigned destinations (5 nav items + 4 cards) each got
  their own route to one generic placeholder. **User decision:** the 4 action
  cards do not have independent destinations at all — each card targets a
  specific sidebar section via a config field (`ActionCard.targetSectionId`,
  REQ-019), so tapping a card is equivalent to tapping that section's sidebar
  item: same route, same section becomes active in the sidebar. Confirmed
  mapping: "Check-Out" → Info, "Haus & Funktionen" → Smart Home, "Entdecken"
  → Umgebung, "Wichtige Infos" → Info. The last two intentionally converge on
  the same destination — confirmed by the user, not an error. This drops the
  undesigned-destination count from 9 routes to 5 (`menuItems`' 4 non-Home
  entries + Settings); every one of those 5 renders the same reusable
  `SectionPlaceholderComponent` (REQ-021), but now inside the **full app
  shell** — same background, same functional sidebar with the correct item
  active — not an isolated stub page (REQ-020). Per this agent's own
  operating rules, a screen with no spec under `docs/design/screens/` is
  still flagged here with a recommendation to run `/design-import` once it
  exists in Figma; nothing about a section's real content is guessed here.
- **Interaction states:** Figma defines no hover/focus/pressed/disabled state
  for `MenuIcons` or `GlassPanel`, despite both being interactive controls on
  a touch-first kiosk screen. This prototype adds a minimal `:focus-visible`
  ring and a subtle `:active` press feedback, built from existing tokens
  (e.g. `--color-text-primary`, the 180ms duration from
  `docs/design/design-sheet.md` → Motion) — explicitly called out as an
  assumption filling a design gap, not a value taken from Figma.
- **Property content service** returns data synchronously today (backed by a
  static TS object) but is designed so swapping it for an async call to the
  future Java/Quarkus backend touches only the service, never a component
  (constitution principle 2).

## User stories

- As a guest opening the app for the first time, I see a warm, personalised
  welcome (my name, the property's copy) within seconds, so I feel oriented.
- As a guest, I can tap any sidebar icon or any of the 4 action cards and
  always get a response — even "coming soon" — so nothing on a touchscreen
  feels broken or dead.
- As the property owner, I can change the guest name, card copy, or which
  section a card links to by editing configuration data, with no code change
  and no redeploy.
- As the developer building the next screen (e.g. WLAN detail), I reuse the
  token layer, the content-service pattern, and the shared app-shell/routing
  scaffold this feature establishes, instead of re-deciding them.

## Requirements

> Revised 2026-08-18 to reflect user decisions on the placeholder
> architecture, the widget-column space, and the property-level nature of
> heading content. REQ-001…REQ-011, REQ-013, REQ-015, REQ-016 keep their
> original IDs (text amended where the requirement itself changed);
> REQ-012/REQ-014 are substantially rewritten; REQ-017…REQ-021 are new.
>
> **Revised again 2026-08-18 — round 3, visual-defect correction.** The screen
> was implemented, reviewed PASS, and then *run and looked at*. Six visual
> defects surfaced (a seventh was found while verifying them); see "Defect
> trace (round 3)" below for each one's verified root cause. Consequences for
> this table:
> - **REQ-022…REQ-025 are new** — full-height sidebar, active-icon gradient,
>   no-scrollbars, and the responsive scaling contract. None of them existed as
>   a requirement, which is why nothing caught their absence.
> - **REQ-014 and REQ-016 are amended in place.** Their previous wording is
>   part of what *caused* two of the defects, so leaving it would re-cause them.
> - **REQ-007, REQ-009, REQ-010, REQ-011 are unchanged in intent** but pick up
>   sharper acceptance criteria: the defects traced to them are plain bugs
>   against requirements that already existed, not missing requirements.

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-001 | Angular project scaffolded under `frontend/`: TypeScript strict mode, standalone components, Angular Router, Vitest + jsdom (CLI default) | Must |
| REQ-002 | Plus Jakarta Sans self-hosted inside the build; zero external runtime requests (no Google Fonts `<link>`, no CDN) | Must |
| REQ-003 | Design token CSS layer implemented per `docs/design/tokens.md`: primitive + semantic custom properties, `data-theme` attribute hook on `<html>` (light values only), per-property branding hook overriding primitives at runtime | Must |
| REQ-004 | Mesh-gradient background implemented as 16 stacked `radial-gradient` layers, colour stops read from `--mesh-1`…`--mesh-16` custom properties, fixed default palette (no brand-derivation logic); rendered once at the shared app shell (REQ-020) so **every** route — Home and every section placeholder — shows the identical background, not just Home | Must |
| REQ-005 | A `PropertyContentService` (or equivalent) supplies property/guest content via Angular DI; no component imports config/JSON directly | Must |
| REQ-006 | Example property config exists — guest name "Lisa", corrected "Airbnb" spelling, headline/subtitle/paragraph (property-level content, not per-guest — see Assumptions), 5 `menuItems` + 1 `settings` sidebar entries (REQ-017/REQ-018), 4 card texts each with a `targetSectionId` (REQ-019), logo asset reference, branding primitives — matching `docs/design/screens/home.md` | Must |
| REQ-007 | Sidebar component renders the 5 `menuItems` (Home, Info, Umgebung, Smart Home, Services) plus the structurally-fixed Settings entry as 6 `MenuIcons`, all sourced from the content service (REQ-017/REQ-018), using semantic tokens and Phosphor glyphs via `@ng-icons`; active state reflects the current route and is visible from within the shared shell on every screen, not just Home | Must |
| REQ-008 | Heading block renders the sparkle+subtitle row, the headline (guest name interpolated), and the paragraph, sourced from the content service; this content is property-level configuration authored by the host, not per-guest/per-stay personalisation (see Assumptions) | Must |
| REQ-009 | 4 `GlassPanel` action cards render icon/header/description/arrow from the content service and are each keyboard- and touch-activatable; each card's destination is resolved from its `targetSectionId` (REQ-019), not an independent per-card route | Must |
| REQ-010 | Hero artwork area renders **only** the no-artwork fallback (no scrim, no `<img>`) — the mesh-gradient background it used to paint itself now comes from the shared shell (REQ-004/REQ-020), so its absence simply lets that shared background show through | Must |
| REQ-011 | Logo renders as a swappable single-asset slot sourced from property config (placeholder SVG, not `AngularLogo`) | Must |
| REQ-012 | Routing: `''` renders Home's content inside the shared shell; the `menuItems`' 4 non-Home routes (Info, Umgebung, Smart Home, Services) and the fixed Settings route each render the shared section-placeholder content (REQ-021), also inside the shared shell (REQ-020) — no independent routes exist for the 4 action cards; they navigate via their resolved target section's route (REQ-019) | Must |
| REQ-013 | Sidebar items and cards get a minimal `:focus-visible` ring and `:active` press feedback, built from existing tokens | Should |
| REQ-014 | **Amended 2026-08-18 (round 3).** Home's content column spans the **full height** of the shell's content area, with the heading block anchored at its **top** and the 4-card row anchored at its **bottom** — inset `--space-9` from each — and the vertical space between them left empty. The right-hand widget column's space is reserved as an empty `flex: 1` element. Nothing renders in either empty region, but the layout's proportions match `docs/design/screens/home.md` → Layout instead of collapsing that space away. ~~"heading block + 4-card row, bottom-aligned"~~ — that phrasing was read as bottom-aligning the *whole* column, which glued the heading onto the cards in mid-screen (defect D2) | Must |
| REQ-015 | Vitest component/unit tests cover: the content service (including target-section resolution), the shared shell (background + sidebar + routed content), sidebar rendering + active state, heading block content, card rendering + navigation to its resolved section, hero fallback rendering, the section-placeholder component, and routing for Home + every section | Must |
| REQ-016 | **Superseded 2026-08-18 (round 3) by REQ-025 — kept for traceability.** ~~Layout is fixed at the 1280×832 logical viewport; no responsive breakpoints; a `min-width` guard rail per `docs/design/tokens.md` → Breakpoints.~~ 1280×832 remains the *design* size and there are still no breakpoints, but the layout must now scale down to a 1024×768 floor; the `min-width` guard rail moves from 1280px to 1024px | Should |
| REQ-017 | Property config's `menuItems`: an array of exactly 5 entries (Home, Info, Umgebung, Smart Home, Services), each `{ id, label, icon, route }`, sourced via `PropertyContentService` — replaces the previously-planned app-wide code constant for these entries (constitution principle 1) | Must |
| REQ-018 | Property config's `settings` entry: exactly 1 `{ id, label, icon, route }` object, sourced via `PropertyContentService` for its **content**, but its **position** (always present, always rendered last, in its own group, separate from `menuItems`) is structurally fixed in the Sidebar component — not something a property config can omit or reorder. Rationale documented in `02-design.md` → "Sidebar config & the Settings decision" | Must |
| REQ-019 | Each `ActionCard` config entry carries a `targetSectionId` that must resolve to an existing `menuItems`/`settings` id; activating a card navigates to that section's route — the exact same destination reached by tapping the corresponding sidebar item — and the sidebar highlights that section as active | Must |
| REQ-020 | A shared app-shell component renders the mesh-gradient background (REQ-004) and the functional Sidebar (REQ-007) for **every** route — Home and every section placeholder alike; only the main-content area changes per route. Placeholder destinations are therefore never isolated stub pages | Must |
| REQ-021 | A shared section-placeholder component renders minimal stub main content — showing the section's configured label — for every `menuItems`/`settings` destination without a real design yet (Info, Umgebung, Smart Home, Services, Einstellungen), rendered inside the shared shell (REQ-020) | Must |
| REQ-022 | **New 2026-08-18 (round 3).** The Sidebar spans the **full height** of the shell's content box (viewport height minus the shell's own vertical padding) on **every** route, and distributes its three groups — logo / `menuItems` / `settings` — with `space-between`, so the logo sits at the top extreme, Settings at the bottom extreme, and the nav group carries generous empty space above and below it. A sidebar item's size is **derived** from the sidebar's own width minus its horizontal padding (square), not from a separate fixed size token | Must |
| REQ-023 | **New 2026-08-18 (round 3).** The **active** sidebar item's icon glyph is filled with `--gradient-icon-active` (139°, `--brand-primary` → `--brand-secondary`, per `docs/design/tokens.md` → Gradients), not a flat colour. The gradient comes from the token layer, so a property's runtime branding override changes it without a rebuild (REQ-003). Inactive glyphs stay `--color-text-primary`. This deliberately overrides `docs/design/screens/home.md`'s 2026-08-17 decision that the icon does not participate in the active/inactive distinction — user decision, recorded in both design files. **Extended 2026-08-18 (round-3 review finding #3, same user ruling):** the Heading block's **Sparkle** glyph carries the same gradient, and additionally switches to Phosphor's `fill` weight, because the reference render shows it solid rather than outlined. It reuses the identical paint-server mechanism, so it inherits the runtime-branding property above; the `regular`-throughout weight rule keeps holding for every other glyph, including the active sidebar one | Must |
| REQ-024 | **New 2026-08-18 (round 3). Project-wide, not Home-specific.** No screen scrolls. Within the supported viewport range (REQ-025) the rendered document never overflows its viewport on either axis — `scrollWidth ≤ clientWidth` **and** `scrollHeight ≤ clientHeight` — so no scrollbar ever appears. User decision 2026-08-18, explicitly extended to every future screen (*"das gilt auch für jeden zukünftigen Screen"*). It must be met **by construction** (nothing is allowed to overflow), not by hiding overflow, so that a layout regression stays visible instead of being silently clipped | Must |
| REQ-025 | **New 2026-08-18 (round 3).** Responsive scaling contract. The design size stays 1280×832 logical px. Between the **minimum supported size of 1024×768 (landscape)** and the design size, the whole UI scales **proportionally**: identical composition, no reflow, no element wrapping that was not already there, no breakpoints, no scrollbars (REQ-024). Above the design size proportions are kept and everything grows. **Portrait orientation is explicitly out of scope** — the design is unambiguously landscape, and a portrait viewport is treated exactly like a below-floor one. Below the floor nothing is guaranteed: the `min-width` guard rail (REQ-016, moved to 1024px) applies and scrolling there is acceptable | Must |

## Acceptance criteria (testable)

- REQ-001: Given a clean checkout, when `npm install && npm start` runs
  inside `frontend/`, then the dev server serves the app and `npm test` runs
  the Vitest suite, both without error.
- REQ-002: Given the built app, when the network tab is inspected, then no
  request leaves the origin for fonts, icons, or images.
- REQ-003: Given `[data-theme]` is unset on `<html>`, when the page renders,
  then it uses the light semantic role values; given a property's branding
  config is applied at runtime, then the primitives it overrides (e.g.
  `--brand-primary`) change and every derived token (e.g. `--gradient-icon`)
  updates without a rebuild.
- REQ-004: Given no property branding override, when **any** route renders
  (Home or a section placeholder), then the shared shell's background shows
  the identical 16-stop pink/peach/violet mesh, not a flat colour and not a
  Home-only effect.
- REQ-005: Given any component that displays guest/property content, when its
  source is inspected, then it injects the content service and contains no
  direct `import` of a JSON/data file.
- REQ-006: Given the example property config, when the Home screen renders,
  then the headline reads "Willkommen im Airbnb von Lisa!", all 4 cards show
  the confirmed Figma copy, and each card's `targetSectionId` resolves to a
  real `menuItems`/`settings` id (no dangling reference).
- REQ-007: **Acceptance strengthened 2026-08-18 (defect D5):** every sidebar
  label renders on **exactly one line** at every supported viewport size — no
  label wraps ("Smart Home" wrapped to two lines in the built app). Given the
  Home route is active, when the sidebar renders, then
  exactly the Home item shows the active gradient background and
  `--text-label-active` weight, the other 5 (4 `menuItems` + Settings) show
  the inactive style, and every label/icon/route rendered traces back to the
  content service, not a literal string in the component; given a section
  placeholder route is active instead, then that section's item is the one
  shown active, from the same shell.
- REQ-008: Given the content service returns the example property, when Home
  renders, then the subtitle, headline, and paragraph text match
  `docs/design/screens/home.md` exactly, and changing only the config (no
  code, no rebuild trigger beyond a normal reload) changes what's displayed —
  demonstrating the content is property-level data, not baked into the
  component.
- REQ-009: Given a card is focused, when Enter/Space is pressed (or it is
  tapped), then the router navigates to the route resolved from that card's
  `targetSectionId` — the same route reached by activating the corresponding
  sidebar item.
  **Acceptance strengthened 2026-08-18 (defect D5):** every card title renders
  on **exactly one line** at every supported viewport size, **and is not
  ellipsized** — the title's advance width fits the card's 162px content box
  ("Haus & Funktionen" first wrapped to two lines, then truncated once `nowrap`
  landed; the user renamed that card to "Smart Home" on 2026-08-18, measured at
  111px, rather than widening the box) — and the four cards' titles,
  descriptions and arrows share identical y positions — a longer title must
  never desynchronise the row's text baselines.
- REQ-010: Given the property has no artwork asset configured, when Home
  renders, then no `<img>`/background-image for hero artwork is present —
  only the shared shell's mesh gradient shows through — **and the component
  occupies no layout space at all**: it contributes neither width, height, nor
  a flex gap to the content row (added 2026-08-18 after defect D7, where the
  empty component's host element still consumed a 32px gap and pushed the whole
  content column right).
- REQ-011: Given the property config's `logoAsset` path, when the sidebar
  renders, then the logo tile renders that asset, not a hardcoded reference —
  **and the asset actually decodes**: the `<img>` fires `load` (not `error`),
  `naturalWidth`/`naturalHeight` are non-zero, and no broken-image placeholder
  is shown (added 2026-08-18 after defect D4 — the original criterion was
  satisfied by an `<img>` whose `src` came from config but whose file the
  browser refused to parse).
- REQ-012: Given any of the 4 non-Home `menuItems`, the Settings item, or any
  of the 4 cards is activated, when navigation completes, then the shared
  shell stays rendered (background + sidebar), the section-placeholder
  content renders showing that section's configured label, the sidebar's
  active item matches the destination, and the URL reflects a distinct route
  — one route per section (5 total, not 9).
- REQ-013: Given a card or sidebar item receives keyboard focus, when
  inspected, then a visible focus ring renders using a token colour, not a
  browser default silently overridden to `none`.
- REQ-014: **Amended 2026-08-18; width clause corrected 2026-08-18 (round-3
  review finding #2).** Given the rendered DOM on the Home route at
  the design viewport, when measured against
  `docs/design/assets/home-no-artwork.png`, then the content column fills the
  content area's full height; the heading block's top edge sits ≈`--space-9`
  below the content area's top (measured reference: y ≈ 88 at 1280×832); the
  card row's bottom edge sits ≈`--space-9` above its bottom (reference: y =
  744); the space between them is empty; the card row's left edge is at x = 172
  — i.e. immediately after the sidebar plus one `--space-8` gap, with no
  additional offset; the heading block keeps its **390px** width — `780px` is the
  raw Figma value on the 2× canvas and had to be halved like every other number
  in `docs/design/screens/home.md` → Components, and at 390px the headline wraps
  to two lines exactly as the reference render does; **and** an empty
  `flex: 1` widget-column element exists and takes up the remaining horizontal
  space — present but visibly empty, not absent.
- REQ-015: Given `npm test`, when the suite runs, then all listed behaviors
  have at least one passing test and the run is green.
- REQ-016: **Superseded by REQ-025's criteria.** Given the viewport is narrower
  than **1024px** (the new floor), when the page loads, then the layout does not
  collapse into an unreadable state (horizontal scroll via `min-width` is
  acceptable there; no crash, no broken overlap).
- REQ-017: Given the property config, when inspected, then `menuItems` has
  exactly 5 entries in Figma's order (Home, Info, Umgebung, Smart Home,
  Services), each with a non-empty `id`, `label`, `icon`, and `route`.
- REQ-018: Given the property config, when inspected, then `settings` is a
  single object (not part of the `menuItems` array) with `id`, `label`,
  `icon`, `route`; given the Sidebar renders, then Settings always appears,
  always last, in its own group, regardless of what `menuItems` contains.
- REQ-019: Given a card's `targetSectionId`, when the app resolves it, then
  it matches exactly one `menuItems`/`settings` id and yields that item's
  `route`; given two cards share the same `targetSectionId` (Check-Out and
  Wichtige Infos both → Info), then both navigate to the identical route and
  both leave the Info sidebar item active — this is expected, not a bug.
- REQ-020: Given any route in the app resolves, when the DOM is inspected,
  then the background and the Sidebar are present and identical regardless
  of which route it is — only the main-content region differs.
- REQ-021: Given a section-placeholder route is active, when it renders, then
  the displayed title matches that section's `label` from the content
  service (not a hardcoded string per route), and it renders inside the
  shared shell, not as a standalone page.
- REQ-022: Given any route at 1280×832, when the sidebar's bounding box is
  measured, then it is `x = 32, y = 24, w = 100, h = 784` (± 2px) — i.e. the
  full viewport height minus the shell's `--space-6` vertical padding, matching
  the reference render — and the vertical gap between the logo group and the
  first nav item, and between the last nav item and Settings, is each > 60px at
  that size. Given an item's box is measured, then it is square and its width
  equals `--size-sidebar` minus twice `--space-3`.
- REQ-023: Given the Home route, when the active item's injected `<svg>` is
  inspected, then its resolved `fill` is a gradient paint reference (not a flat
  colour), the referenced gradient exists in the document, and its stops resolve
  to `--brand-primary` / `--brand-secondary`; given `--brand-primary` is
  overridden at runtime, then the rendered glyph changes with it. Given an
  **inactive** item, then its glyph is flat `--color-text-primary`.
- REQ-024: Given the app at 1024×768, 1280×832 and 1440×900, when the document
  is measured after load on **every** route (Home and all 5 section
  placeholders), then `documentElement.scrollWidth ≤ clientWidth` and
  `scrollHeight ≤ clientHeight` in each case. Given the page source is
  inspected, then this is achieved without an unconditional `overflow: hidden`
  on `html`/`body`.
- REQ-025: Given the app at 1024×768, when the DOM is compared with the same
  page at 1280×832, then the element order and structure are identical, every
  text node that occupies one line at the design size still occupies one line,
  and no element has moved to a different row/column. Given a portrait viewport
  (e.g. 768×1024), then no guarantee is claimed — the guard rail's behaviour is
  acceptable and this is documented, not a defect.

## Defect trace (round 3, 2026-08-18)

The user ran the built app and found six visual defects. The `06_review` PASS
that precedes them was given **before** the `code-review` skill had a
visual-check step at all, so process never looked at a rendered pixel — every
one of these was invisible to a source-only review and to a unit-test suite
that asserts presence, not geometry.

Each root cause below was verified against the running app at 1280×832 (DOM
geometry measured in a headless browser) and against the reference render
`docs/design/assets/home-no-artwork.png` (edge-scanned for ground-truth
coordinates), not inferred from reading the CSS.

| # | Defect | Verified root cause | Requirement |
|---|--------|---------------------|-------------|
| **D1** | Sidebar is too short (measured `y = 148, h = 536` in an 832px viewport — 64% of the height, vertically centred) and logo/Settings sit bunched against the nav group | `.shell` uses `align-items: center`, so its flex items do not stretch. `.sidebar` *does* carry `align-self: stretch`, but it is not the flex item — with Angular's default emulated encapsulation the **unstyled `<app-sidebar>` host** is, and `.sidebar` is a plain block child of it. The `align-self` therefore applies to an element whose parent is not a flex container, and does nothing. Height collapses to content, and `justify-content: space-between` has nothing to distribute | **REQ-022** (new) |
| **D2** | Heading block sits at `y = 360`, glued on top of the cards, mid-screen | `.home-content` uses `align-items: flex-end`, bottom-aligning the whole `.home-content__screen` column; the column is then content-height, so its own `justify-content: space-between` and `padding: --space-9 0` are no-ops. `docs/design/screens/home.md` → Layout says both "`align: end`" and "space-between + 128px padding" — the two only make sense together (the column is full height, its *contents* are pushed apart); reading them as separate instructions produces exactly this | **REQ-014** (amended — the old wording is part of the cause) |
| **D3** | Active sidebar icon glyph renders flat dark navy, no gradient | Never specified. `docs/design/screens/home.md` explicitly decided the glyph does *not* participate in the active state, so the implementation was faithful to the spec and the spec was wrong about the render. User override, 2026-08-18 | **REQ-023** (new) |
| **D4** | Logo tile shows the browser's broken-image placeholder | **The SVG file is not well-formed XML.** `frontend/src/assets/branding/logo-placeholder.svg` contains `<!-- Placeholder mark -- swappable single-asset slot … -->`; a `--` sequence is illegal *inside* an XML comment. Chromium's SVG parser rejects the document, the `<img>` fires `error`, and the broken-image glyph renders. Verified: the file serves `200 image/svg+xml` (not a 404, not an asset-pipeline problem) and `new Image()` fires `error` on it, while the byte-identical file with the `--` removed loads fine | **REQ-011** (existing, violated) |
| **D5** | "Smart Home" wraps to two lines in the sidebar; "Haus & Funktionen" wraps to two lines on its card — desynchronising all four cards' title/description baselines | Two independent near-misses, both from taking a spec number literally. Sidebar: `--size-sidebar-item` is 72px (the Figma *component's* box), leaving a 56px label box; the reference render's item is **84px** — the sidebar's inner width — and the label needs ~71px. Card: the title box is 162px (196 − 2 × 1px glass border − 2 × 16px padding — the "164px" first recorded here forgot the border) and "Haus & Funktionen" at `--text-title` measures 167px — it fits in Figma, which does not re-wrap a fixed text frame, and does not fit in a browser | **REQ-007 / REQ-009** (existing; acceptance criteria strengthened — no prior requirement said "must not wrap", which is why nothing caught it) |
| **D6** | Horizontal scrollbar below 1280px; no defined behaviour for smaller screens | `html, body { min-width: 1280px }` (REQ-016's guard rail) forces overflow at anything narrower. Verified: at 1024×768 `scrollWidth = 1280` vs `clientWidth = 1024`; at 1280×832 and 1366×768 there is no overflow today | **REQ-024 + REQ-025** (new) |
| **D7** | *(found while verifying the others, not reported by the user)* The whole content column sits **32px too far right** — card row at `x = 204` where the reference has `x = 172` | `HeroArtworkComponent` renders nothing, but its host element is still a flex item of `.home-content`, and that row has `gap: var(--space-7)` (32px). A zero-width item still gets a gap after it. The empty component costs exactly one gutter | **REQ-010 / REQ-014** (existing, violated) |

**D1, D2 and D7 are one root-cause class**, not three coincidences: an Angular
component's host element is an unstyled `display: inline` box that sits between
a parent's layout and the child's, and silently absorbs `flex`, `align-self`
and percentage-height chains. The same class already bit this feature once
before (the action-card `flex-shrink` fix on 2026-08-18, see
`handoff.md`). It is addressed as a convention in `02-design.md` → "The host
element is the layout, not the template root", not as three separate patches.

## Technical constraints

Per `docs/constitution.md`: Angular + TypeScript strict, standalone
components, Angular Router, signals for local UI state, no backend/auth/admin
area, plain CSS with custom properties (no Tailwind/SCSS), sizes in `rem`
(hairlines/blur stay `px`), approved dependencies only (`@angular/cdk`,
`@ng-icons/core` + `@ng-icons/phosphor-icons`, `vitest` + `jsdom`), no
external runtime requests, no dependency-manifest edits without approval,
branch discipline (this work happens on `feature/home-screen-prototype`).

No new dependency, datastore/infra choice, or token-mechanism change is
introduced by this feature — everything above is already pre-approved by the
constitution's constraints, so nothing here requires a fresh approval gate.
The 2026-08-18 revision's shared-shell/child-route restructuring (REQ-020) is
Angular Router used in its ordinary parent/child-route form — not a new
architectural pattern under `docs/constitution.md` → "Decisions Requiring
Your Approval", so it doesn't reopen that gate either.

## Risks & open questions

- `[NEEDS CLARIFICATION: widget column scope — resolved for this feature as
  a Non-goal for its *content* (see above), because nothing is designed for
  it and weather/recommendations would pull in an external data source
  (approval-gated infra). **User confirmed 2026-08-18:** the column's
  *space*, however, is reserved (REQ-014) rather than omitted — matches the
  designed proportions and doesn't foreclose building the widget content as
  its own future feature.]`
- `[NEEDS CLARIFICATION: background gradient tint source — resolved for this
  feature as "fixed default mesh only" per docs/design/tokens.md, which
  already states tinting is "prepared, not built." No derivation rule from
  brand primitives is implemented here. **User confirmed 2026-08-18.**]`
- `[NEEDS CLARIFICATION: the 5 section-placeholder destinations (menuItems'
  4 non-Home entries + Settings) have no design spec under
  docs/design/screens/. Recommendation unchanged: once any of them is ready
  to become a real screen, run /design-import for it rather than this
  pipeline inventing its design. Non-blocking — the shared placeholder keeps
  every control interactive, inside the real shell, in the meantime. Note
  this count dropped from 9 to 5 in the 2026-08-18 revision, since the 4
  action cards no longer have independent destinations — see Assumptions.]`
- **Resolved 2026-08-18, not a `[NEEDS CLARIFICATION]` marker:** whether
  Settings should be hardcoded or config-driven was explicitly left to this
  agent's judgement by the user (not on the constitution's approval-gated
  list). Decision: structurally fixed position, config-sourced content — see
  REQ-018 and `02-design.md` → "Sidebar config & the Settings decision" for
  the full rationale.
- `[NEEDS CLARIFICATION: final logo asset — pending. Non-blocking; a
  placeholder SVG ships behind the same swappable slot the real asset will
  use.]`
- `[NEEDS CLARIFICATION: dark-mode palette — none exists in Figma yet. The
  data-theme mechanism is wired per REQ-003 but only light values ship.
  Non-blocking; revisit when Einstellungen → Darstellung is built.]`
- `[NEEDS CLARIFICATION: docs/design/design-sheet.md flags 14px card body
  text / 12px sidebar labels as "at the lower edge" for an 11–13" touchscreen
  and recommends validating on real hardware before it ships broadly.
  Non-blocking for this prototype — implemented as specified; worth a
  hardware check before this becomes the shipped default.` **Sharpened
  2026-08-18 by REQ-025:** at the 1024×768 floor everything is 0.8×, so card
  body text lands at 11.2px and sidebar labels at 9.6px — below the edge that
  entry already warns about. The floor is a *supported* size, not the *target*
  (1280×832 stays the target), so this does not block REQ-025; it does mean the
  hardware check must be done at the floor, not only at the design size.
  **Measured after implementation (2026-08-18, real browser):** the root font
  size resolves to 12.8px at 1024×768 and 16px at 1280×832, so the 12px
  sidebar-label token renders at exactly **9.6px** and the 14px card-body token
  at **11.2px** at the floor. Not compensated for in code on purpose — bending
  the type scale at small sizes would hide the problem the hardware check
  exists to answer.`]`

**New in round 3 (2026-08-18):**

> **Both `[APPROVAL REQUIRED]` items below were resolved by the user on
> 2026-08-18 and are kept only for traceability.** The scaling mechanism was
> approved as recommended and explicitly **uncapped** at the top end (a larger
> display renders a proportionally larger UI), with the accessibility trade —
> a viewport-derived root overrides the browser's font-size preference —
> raised and accepted for this kiosk device. The no-scrollbar rule was ratified
> in `docs/constitution.md` v1.2.0, which also rewrote the `rem`/root-size
> constraint. Both are implemented (R7).

- `[APPROVAL REQUIRED — blocks REQ-025's implementation task only: the responsive
  scaling **mechanism** is a new cross-cutting convention under
  docs/constitution.md → "Decisions Requiring Your Approval".` The recommendation
  is a viewport-proportional root font size; the constitution currently mandates
  `rem` against a **16px** root, so changing what the root means is precisely the
  kind of project-wide change reserved for the user. Full rationale, the exact
  CSS, and the alternatives considered are in `02-design.md` → "Responsive
  scaling (REQ-025) — approval-gated". Every other round-3 task is unblocked by
  this.`]`
- `[APPROVAL REQUIRED — the no-scrollbar rule (REQ-024) is project-wide by the
  user's own instruction, which makes it constitution material rather than a
  feature requirement.` The user has approved *that* it becomes a constraint and
  that the constitution bumps 1.1.0 → 1.2.0; the exact wording is proposed
  separately and must be confirmed before `docs/constitution.md` is edited. Until
  it is ratified there, REQ-024 binds this feature only — but plan every new
  screen as if it were already in force.`]`
- **Not a `[NEEDS CLARIFICATION]`, but do not lose it:** `--gradient-icon-active`
  (REQ-023) is the **only** value in `docs/design/tokens.md` that was measured
  from a render rather than extracted from Figma, and the Figma component still
  does not contain it. A future `/design-import` will not reproduce it. Both
  `tokens.md` and `screens/home.md` carry the provenance note; the durable fix is
  to add the gradient to the Figma `MenuIcons` component.
