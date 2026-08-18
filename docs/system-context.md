# System Context

High-level map of existing components and cross-cutting concerns. Read this
before planning a new feature to spot related or conflicting work.

> **Status:** no code exists yet. This records decisions already taken, so the
> first build starts from them rather than re-deciding.

## Deployment model

**Self-hosted, one instance per property.** Every apartment or hotel runs its
own server inside the building, serving the app to a touchscreen on site. There
is no central multi-tenant service.

What this buys us, and what it costs:

| | |
|---|---|
| ✅ **Tenant isolation is physical** | One instance holds one property's data. No cross-tenant leakage is even possible, and no tenant-scoping logic is needed in queries |
| ✅ **No internet dependency** | The app keeps working when the property's connection is down — which is exactly when a guest needs the WiFi password |
| ⚠️ **No central patching** | A vulnerability persists until someone updates that box. This is the dominant long-term risk and the reason to keep the dependency count low |
| ⚠️ **Shared network** | The server sits on the same LAN as guest devices |
| ⚠️ **Physical access** | The touchscreen stands in a guest-accessible space |

Per-property configuration remains **data, not code** (constitution principle 1)
even though each property has its own instance — one build artifact, configured
per site. Building a bespoke image per customer would defeat reproducible
deployments and make patching harder, which is the thing this model is already
weakest at.

### Open security questions

These need answering before content that matters is served. None block the first
build, but they shape it.

- `[NEEDS DECISION: does the app authenticate the guest at all?` Today's plan
  serves static JSON from a local server. Door codes, key-box codes and WiFi
  passwords would therefore be retrievable by **any device on the LAN**, without
  authentication, simply by requesting the file — including a neighbour in range
  or a guest whose stay has ended. "It's only on the local network" is a weak
  boundary for a door code. Options range from accepting it (the touchscreen is
  in the flat anyway) through a per-stay PIN to a short-lived token. The answer
  determines whether sensitive content may live in the client bundle at all.`]`
- `[NEEDS DECISION: HTTPS on the local network?` Plain HTTP exposes everything to
  passive sniffing on shared WiFi. Certificates for a LAN device are awkward but
  solvable (self-signed with a provisioning step, or a real cert on a local
  domain).`]`
- `[NEEDS DECISION: how does the touchscreen get locked down?` Kiosk mode, no
  browser chrome, no navigating away, no developer tools. This is device
  provisioning rather than application code, but it is part of the security
  story and nobody else will do it.`]`
- `[NEEDS DECISION: how do instances get updated,` and who is responsible for
  it? See the patching risk above.`]`

## Stack

Per `README.md` → "Erste Technische Richtung":

- **Angular + TypeScript**, standalone components
- **Angular Router** for the main areas
- **Signals** for local UI state
- Content initially **static JSON / TypeScript files** — no backend, no auth,
  no admin area in the first step
- Target: **touch-optimised web app** for tablet and touchscreen

**Planned backend: Java + Quarkus**, after the MVP. The static JSON stage is
explicitly temporary. Two things follow:

- Constitution principle 2 (*content is reached through a service, never
  imported into a component*) is what makes this swap cheap. Components must
  never import a JSON file directly, however tempting it looks while the data is
  static — that shortcut is the one thing that would turn the backend migration
  into a rewrite of every component.
- Quarkus suits the on-premise model well: compiled to a native image it starts
  fast and holds a small memory footprint, which matters when the target is a
  modest box in a building rather than a cloud instance.

A backend also opens the natural answer to the open authentication question
below — access control can live server-side instead of relying on the LAN
boundary. It does not answer it for the MVP, which has no server-side logic.

## Components

Introduced by `docs/features/home-screen-prototype/` (see that feature's
`02-design.md` for the full rationale). **Revised 2026-08-18**: the sidebar
and background moved into a shared, route-agnostic `layout/` area (they are
no longer Home-specific), and the generic placeholder was replaced by a
section-aware one:

| Component | Location | Responsibility |
|-----------|----------|-----------------|
| `PropertyContentService` | `frontend/src/app/core/content/` | Supplies per-property/guest content via DI (constitution principle 2), including sidebar `menuItems`/`settings` and each card's `targetSectionId` → route resolution (`getSectionRoute()`); backed by static TS today, swappable for the future Quarkus backend |
| `ShellComponent` | `frontend/src/app/layout/shell/` | App-wide shell rendered on every route: mesh-gradient background + `SidebarComponent` + `<router-outlet>` for the routed main content |
| `SidebarComponent` | `frontend/src/app/layout/sidebar/` | Config-driven nav — 5 `menuItems` (Home, Info, Umgebung, Smart Home, Services) + 1 structurally-fixed `settings` entry (`MenuIcons`), active state from current route |
| `HomeContentComponent` | `frontend/src/app/features/home/` | Home's main-content region only (heading block, hero artwork, 4 action cards, reserved empty widget-column space) — composed inside `ShellComponent`'s router-outlet, not the sidebar/background |
| `HeroArtworkComponent` | `frontend/src/app/features/home/hero-artwork/` | Renders the no-artwork fallback (no image; background now lives in `ShellComponent`) |
| `HeadingBlockComponent` | `frontend/src/app/features/home/heading-block/` | Subtitle, headline, paragraph — from `PropertyContentService`; property-level content, not per-guest |
| `ActionCardComponent` | `frontend/src/app/features/home/action-card/` | `GlassPanel` action card; resolves its destination from `targetSectionId` via `PropertyContentService.getSectionRoute()`, keyboard- and touch-activatable |
| `SectionPlaceholderComponent` | `frontend/src/app/features/section-placeholder/` | Shared main-content stub, rendered inside `ShellComponent`, for the 5 sections with no design yet (Info, Umgebung, Smart Home, Services, Einstellungen) |

Still designed but not implemented: nothing — the Home screen's designed
components are now all covered by the row above. The **widget column**
remains undesigned (no content built), but its layout space is reserved
(`flex: 1`, empty) rather than collapsed — see "Open Structural Questions"
below.

## Cross-Cutting Concerns

- **Design tokens:** CSS custom properties in two layers (primitive → semantic).
  Components reference semantic roles only. Full rationale and shape in
  `docs/design/tokens.md` → "Implementation". No Tailwind. As of
  `home-screen-prototype`, this moved from spec to real code:
  `frontend/src/styles/tokens.css`.
- **Theming:** two independent axes.
  - *Appearance* (`data-theme` on `<html>`) overrides **semantic** roles.
  - *Property branding* overrides **primitives**, written at runtime from a
    per-property config. Adding a customer must never require a code change.
- **Icons:** Phosphor Icons via `@ng-icons/core` + `@ng-icons/phosphor-icons`.
  Figma glyph names already match the set, so icons are not exported
  individually. Note there is no Angular-specific icon set — `mat-icon` renders
  Google's Material Symbols, which is a style choice, not a framework fit.
  `@ng-icons` is a multi-set wrapper, so switching sets later does not change
  component code.
- **Overlay behaviour:** `@angular/cdk` — Overlay, Dialog, Menu, FocusTrap,
  Portal, A11y. Unstyled; all visuals come from the project's own CSS.
- **External runtime dependencies: none permitted.** Fonts are self-hosted,
  icons bundled, images embedded. The most likely accidental breach is a
  Google Fonts `<link>` for Plus Jakarta Sans — check for it in the very first
  scaffold. Features needing internet (weather, maps, live recommendations) must
  be designed to degrade when it is absent.
- **Testing:** unit tests on **Vitest + jsdom** — the Angular CLI default since
  v21, so `ng new` sets it up and no separate "install a test runner" TODO step
  is needed. Karma/Jasmine are deliberately not used. **No end-to-end framework
  is chosen** (Angular ships no default; Playwright and Cypress are the usual
  candidates) — an open, approval-gated decision for when one is first needed.
- **Logging:** `<not yet decided>`
- **Persistence:** none in the first step — static JSON / TS content. A Java +
  Quarkus backend is planned after the MVP; the datastore behind it is not yet
  chosen and is an approval-gated decision.
- **Messaging / async:** none.
- **Auth:** none in the first step.

## Integration Points

| System | Direction | Notes |
|--------|-----------|-------|
| Figma (`AirBnB-Screen`) | inbound, manual | Design source. Only the `design-import` skill may query it, and the plan is metered — see `docs/design/extraction-log.md` |

Potential future integrations implied by the design's widget column (weather,
nearby recommendations) would each be an approval-gated infrastructure decision.
None are committed.

## Open Structural Questions

- ~~Viewport contradiction~~ — **resolved 2026-08-17.** The MVP targets large
  touchscreens only, rendering the fixed 1280 × 832 layout as designed. No
  breakpoints, no responsive work. Phone support is deferred and will be a
  layout project, not a token change. Open sub-questions (minimum supported
  width, and whether type sizes need scaling for viewing distance) are tracked
  in `docs/design/tokens.md` → "Breakpoints".
- **Widget column** (`docs/design/screens/home.md`) is intentionally undesigned;
  `README.md` treats the widgets as hypotheses to be validated or dropped.
  **Planning resolution (`home-screen-prototype`, 2026-08-18):** no *content*
  built in the first prototype — no Figma design exists for any widget, and
  weather/recommendations would each need an external data source (an
  infrastructure decision gated under `docs/constitution.md`). **User
  confirmed 2026-08-18:** unlike the content, the column's *space* is
  reserved (`flex: 1`, empty) in the Home layout, matching the designed
  proportions, so this stays a content/future-feature decision rather than
  a layout one to revisit later. Selecting and designing the widget set
  itself remains deferred to its own future feature.
