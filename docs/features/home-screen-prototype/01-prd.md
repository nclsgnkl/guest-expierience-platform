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
  area, responsive/phone layouts, an E2E test framework.

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
| REQ-014 | Home's content layout matches the no-artwork Figma composition (heading block + 4-card row, bottom-aligned, per `docs/design/screens/home.md` → Layout) **and reserves the right-hand widget column's space** as an empty `flex: 1` element — nothing renders inside it, but the layout's proportions match the design instead of collapsing that space away | Must |
| REQ-015 | Vitest component/unit tests cover: the content service (including target-section resolution), the shared shell (background + sidebar + routed content), sidebar rendering + active state, heading block content, card rendering + navigation to its resolved section, hero fallback rendering, the section-placeholder component, and routing for Home + every section | Must |
| REQ-016 | Layout is fixed at the 1280×832 logical viewport; no responsive breakpoints; a `min-width` guard rail per `docs/design/tokens.md` → Breakpoints | Should |
| REQ-017 | Property config's `menuItems`: an array of exactly 5 entries (Home, Info, Umgebung, Smart Home, Services), each `{ id, label, icon, route }`, sourced via `PropertyContentService` — replaces the previously-planned app-wide code constant for these entries (constitution principle 1) | Must |
| REQ-018 | Property config's `settings` entry: exactly 1 `{ id, label, icon, route }` object, sourced via `PropertyContentService` for its **content**, but its **position** (always present, always rendered last, in its own group, separate from `menuItems`) is structurally fixed in the Sidebar component — not something a property config can omit or reorder. Rationale documented in `02-design.md` → "Sidebar config & the Settings decision" | Must |
| REQ-019 | Each `ActionCard` config entry carries a `targetSectionId` that must resolve to an existing `menuItems`/`settings` id; activating a card navigates to that section's route — the exact same destination reached by tapping the corresponding sidebar item — and the sidebar highlights that section as active | Must |
| REQ-020 | A shared app-shell component renders the mesh-gradient background (REQ-004) and the functional Sidebar (REQ-007) for **every** route — Home and every section placeholder alike; only the main-content area changes per route. Placeholder destinations are therefore never isolated stub pages | Must |
| REQ-021 | A shared section-placeholder component renders minimal stub main content — showing the section's configured label — for every `menuItems`/`settings` destination without a real design yet (Info, Umgebung, Smart Home, Services, Einstellungen), rendered inside the shared shell (REQ-020) | Must |

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
- REQ-007: Given the Home route is active, when the sidebar renders, then
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
- REQ-010: Given the property has no artwork asset configured, when Home
  renders, then no `<img>`/background-image for hero artwork is present —
  only the shared shell's mesh gradient shows through.
- REQ-011: Given the property config's `logoAsset` path, when the sidebar
  renders, then the logo tile renders that asset, not a hardcoded reference.
- REQ-012: Given any of the 4 non-Home `menuItems`, the Settings item, or any
  of the 4 cards is activated, when navigation completes, then the shared
  shell stays rendered (background + sidebar), the section-placeholder
  content renders showing that section's configured label, the sidebar's
  active item matches the destination, and the URL reflects a distinct route
  — one route per section (5 total, not 9).
- REQ-013: Given a card or sidebar item receives keyboard focus, when
  inspected, then a visible focus ring renders using a token colour, not a
  browser default silently overridden to `none`.
- REQ-014: Given the rendered DOM on the Home route, when compared to
  `docs/design/screens/home.md`'s layout section, then the content column
  keeps its 780px heading width and bottom alignment, **and** an empty
  `flex: 1` widget-column element exists and takes up the remaining
  horizontal space — present but visibly empty, not absent.
- REQ-015: Given `npm test`, when the suite runs, then all listed behaviors
  have at least one passing test and the run is green.
- REQ-016: Given the viewport is narrower than 1280px, when the page loads,
  then the layout does not collapse into an unreadable state (horizontal
  scroll via `min-width` is acceptable; no crash, no broken overlap).
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
  hardware check before this becomes the shipped default.]`
