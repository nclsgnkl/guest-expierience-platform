# System Context

High-level map of existing components and cross-cutting concerns. Read this
before planning a new feature to spot related or conflicting work.

> **Status:** `frontend/` exists and is implemented — the
> `home-screen-prototype` and `appearance-and-language` features; everything
> below reflects real code, not only intent. Updated 2026-08-19 with
> `appearance-and-language`'s components, its two runtime axes and the one
> thing the app now persists.

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
- `[NEEDS DECISION: how is the guest-preference endpoint secured?`
  **Raised 2026-08-18 by `appearance-and-language`, not answered by it.** Once
  the preference is stored server-side (Cross-Cutting Concerns → Persistence),
  the endpoint that stores it is the product's **first unauthenticated *write*
  surface** on a LAN shared with guest devices and possibly neighbours.
  *Where* the server keeps it is a separate, still-unmade and approval-gated
  decision; this question stands whatever that answer is. The impact is nuisance
  rather than disclosure — someone could change what the screen shows, not read
  a door code — but it is a mutation reachable by anyone in range, and it
  belongs to the backend feature's design. Note the same feature deliberately
  does **not** pre-empt the authentication question above: the stored record is
  scoped to *the screen* and carries **no identifier of any kind** (no guest,
  stay, session or device id), so whatever that decision lands on, scoping the
  record later is a change inside the gateway and the backend only.`]`

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

**Two seams already exist for it (added 2026-08-18).** Appearance and language
are the first two settings deliberately shaped for that backend, and they mark
where it plugs in:

| Seam | Direction | What crosses it |
|---|---|---|
| `PropertyContentService` → `PreferencesService.applyPropertyDefaults()` | read | The property's configuration: which options are offered and what the defaults are. Static TS today; one service changes when it comes over the wire |
| `PreferenceGateway` (`core/preferences/`) | read **and** write | The guest's own choice. One token, one provider line, `sessionStorage` behind it today, an HTTP implementation later |

Nothing else in the app is a candidate for a server call, which is what keeps
the migration small.

## Components

Introduced by `docs/features/home-screen-prototype/` (see that feature's
`02-design.md` for the full rationale). **Revised 2026-08-18**: the sidebar
and background moved into a shared, route-agnostic `layout/` area (they are
no longer Home-specific), and the generic placeholder was replaced by a
section-aware one:

| Component | Location | Responsibility |
|-----------|----------|-----------------|
| `PropertyContentService` | `frontend/src/app/core/content/` | Supplies per-property/guest content via DI (constitution principle 2), including sidebar `menuItems`/`settings` (the Einstellungen nav entry — **not** the settings *config*, which is the separate `settingsConfig` below) and each card's `targetSectionId` → route resolution (`getSectionRoute()`); backed by static TS today, swappable for the future Quarkus backend. **Two signals:** `property()` resolves every localized field into the active language (falling back to the property's `defaultLanguage`), and `settingsConfig()` exposes the language-independent settings slice — the offered options and the property's defaults — which is what `PreferencesService.applyPropertyDefaults()` consumes. Validates the raw config at construction |
| `ShellComponent` | `frontend/src/app/layout/shell/` | App-wide shell rendered on every route: mesh-gradient background + `SidebarComponent` + `<router-outlet>` for the routed main content |
| `SidebarComponent` | `frontend/src/app/layout/sidebar/` | Config-driven nav — 5 `menuItems` (Home, Info, Umgebung, Smart Home, Services) + 1 structurally-fixed `settings` entry (`MenuIcons`), active state from current route |
| `HomeContentComponent` | `frontend/src/app/features/home/` | Home's main-content region only (heading block, hero artwork, 4 action cards, reserved empty widget-column space) — composed inside `ShellComponent`'s router-outlet, not the sidebar/background |
| `HeroArtworkComponent` | `frontend/src/app/features/home/hero-artwork/` | Renders the no-artwork fallback (no image; background now lives in `ShellComponent`) |
| `HeadingBlockComponent` | `frontend/src/app/features/home/heading-block/` | Subtitle, headline, paragraph — from `PropertyContentService`; property-level content, not per-guest |
| `ActionCardComponent` | `frontend/src/app/features/home/action-card/` | `GlassPanel` action card; resolves its destination from `targetSectionId` via `PropertyContentService.getSectionRoute()`, keyboard- and touch-activatable |
| `SectionPlaceholderComponent` | `frontend/src/app/features/section-placeholder/` | Shared main-content stub, rendered inside `ShellComponent`, for the **4** sections with no design yet (Info, Umgebung, Smart Home, Services). Einstellungen left this set on 2026-08-18 — it has a real screen now |

Added by `docs/features/appearance-and-language/` (2026-08-18). Same rule as
above: these are real files under `frontend/src/`, not intent.

| Component | Location | Responsibility |
|-----------|----------|-----------------|
| `TranslationService` | `frontend/src/app/core/i18n/` | Signal-based translation. `t(key, params?)` is an arrow-function property, so templates may hold it detached; keys are typed from the German catalogue, so a missing English string fails the build. Both catalogues are **compiled into the bundle** — nothing is fetched to render a language (constitution principle 4). Unknown key → default catalogue → the key itself |
| `PreferencesService` | `frontend/src/app/core/preferences/` | The **policy** layer for appearance and language: the property's defaults ⊕ the guest's override. Exposes `language()`, `appearance()` and `resolvedTheme()` (`'light' \| 'dark'`, the only place `prefers-color-scheme` is ever read, and only when a property configures the `system` option); `applyPropertyDefaults()`, `hydrate()`, `setLanguage()`, `setAppearance()`, `reset()`. Policy lives here and not in the transport, which is what makes swapping the transport a no-behaviour-change operation |
| `PreferenceGateway` (+ `SessionPreferenceGateway`) | `frontend/src/app/core/preferences/` | The **transport** contract for the guest's stored preference — `load()` / `save()` / `clear()`, all `Promise`-returning, whole-record writes, behind one injection token. `SessionPreferenceGateway` is the `sessionStorage` **stand-in** until the backend endpoint exists; it is *deleted*, not adapted, when the HTTP one lands. It is a **DI leaf**: it injects nothing, and must never reach for `PropertyContentService` |
| `ClockService` | `frontend/src/app/core/clock/` | A `now` signal that ticks on the **minute boundary** (a `setTimeout` to the boundary, then a `setInterval`), torn down via `DestroyRef`. Zoneless change detection cannot see a bare `setInterval`, so the clock has to be a signal |
| `StatusBarComponent` | `frontend/src/app/layout/status-bar/` | App chrome on **every** route: language trigger, appearance trigger (sun/moon by resolved theme), hairline divider, clock as `<time [attr.datetime]>`. Positioned absolutely in the content area's top-right corner rather than in the flow, so Home's measured geometry does not move. `:host { display: contents }`; deliberately **no** `role="toolbar"` — that role promises roving `tabindex` and arrow traversal, which this does not implement |
| `StatusBarMenuComponent` | `frontend/src/app/layout/status-bar/status-bar-menu/` | One generic CDK dropdown (`CdkMenuTrigger`/`CdkMenu`/`CdkMenuItem`) used by both status-bar triggers: takes options, the selected value and the trigger content, emits a selection. Renders into the CDK **overlay container** on `document.body`, not inside the shell |
| `SettingsComponent` | `frontend/src/app/features/settings/` | The `/einstellungen` screen (took that route from `SectionPlaceholderComponent`): screen title plus two glass panels, *Sprache* and *Darstellung*. Reads no route data |
| `SettingChoiceComponent` | `frontend/src/app/features/settings/setting-choice/` | One setting: label, hint and a row of **all-visible** option buttons, each at least the minimum touch target. Two controls per setting is intentional — compact in the status bar, everything visible in Settings, both bound to the same signals |

Full rationale for the eight rows above:
`docs/features/appearance-and-language/02-design.md`.

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
  - **Why the separation is load-bearing, not tidiness:** branding primitives
    are written **inline on `:root`** at runtime, and an inline style beats any
    `[data-theme='dark']` stylesheet rule. So the moment the theme axis
    touches a primitive, a *branded* property silently gets the wrong value —
    a defect visible only to paying customers.
  - **The mesh gradient is therefore a semantic role, not a primitive
    (2026-08-18).** `--mesh-*` became `--color-mesh-1…16` + `--color-mesh-base`,
    and `shell.component.css` reads only the roles. That removed the last
    "components may read a primitive" exception, so the rule is now absolute:
    `grep` for `var(--mesh-|--ink-|--brand-|--night-` in `frontend/src` matches
    **only** `styles/tokens.css`.
  - **A dark palette exists as of 2026-08-18** — designed in-house, no Figma
    counterpart, documented in **`docs/design/dark-theme.md`** and realised in
    `frontend/src/styles/tokens.css`. It is deliberately **not** in
    `docs/design/tokens.md`, which records only what exists in Figma, nor in
    `docs/design/design-sheet.md`. A Figma re-import will not reproduce it;
    its absence from a fresh extraction is not evidence that it is wrong. It
    changes **colour only** — same roles, same geometry, same glassmorphism
    (the surfaces stay translucent and blurred).
- **Appearance & language (added 2026-08-18).** Both are switchable at
  runtime, from two places each: the status-bar dropdowns and the Settings
  screen, all bound to the same signals. Three points bind future work:
  - **Default ⊕ choice.** The *property* configures which options are offered
    and what the defaults are (`settingsConfig()`); the *guest* may override
    per setting. An override that is `null` means "still following the
    property". A value outside the configured option list is discarded, never
    applied — including one that arrives from storage.
  - **`<html>` always carries a resolved value.** `data-theme` is `light` or
    `dark`, never absent, and `lang` is always set. Exactly one place in
    `src/` writes them: the effects in `App` (`app.component.ts`). `prefers-color-scheme` is
    read **only** when a property configures the `system` option — it is never
    the default.
  - **Nothing is fetched to render either one.** Both catalogues ship in the
    bundle and the theme is CSS custom properties, so switching is a signal
    write and a repaint — no request, no reload, no route change. The one
    request in the area is the *persistence* write below, which is outside the
    render path.
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
  **`playwright` is nonetheless an approved devDependency since
  `docs/constitution.md` v1.3.0 — as a *verification tool only*:** it drives a
  real browser at review time to measure geometry, wrapping and scroll
  dimensions, which jsdom cannot compute. `frontend/tools/measure.mjs` is that
  instrument; it reports numbers and asserts nothing. Approving it explicitly
  did **not** decide the E2E question above — committed specs, a browser test
  runner or anything wired into `npm test` still needs that separate approval.
- **Viewport & scaling (added 2026-08-18).** Design size **1280 × 832**
  logical px; supported floor **1024 × 768, landscape**; portrait explicitly
  out of scope. Between floor and design size the UI **scales proportionally**
  — no breakpoints, no second layout, no reflow. **No screen ever shows a
  scrollbar**, on any axis, on any route: the user extended this to every
  future screen, so treat it as binding when planning a new one. It is met by
  construction (nothing overflows) and verified by measuring
  `scrollWidth`/`scrollHeight` against `clientWidth`/`clientHeight` — never by
  `overflow: hidden`, which would hide regressions instead of preventing them.
  Full contract in `docs/design/tokens.md` → "Minimum supported width".
  The scaling **mechanism** — an uncapped viewport-proportional root font size,
  `html { font-size: min(1.25vw, 1.9231vh) }` — was **approved by the user on
  2026-08-18** and is recorded in `docs/constitution.md` v1.2.0.

  **The content area's top-right corner is reserved (added 2026-08-18).**
  `StatusBarComponent` overlays it on every route, absolutely positioned at the
  shell's own padding offsets rather than in the flow — a header row would have
  pushed Home's signed-off geometry down. Anything placed there in future has
  to clear it, and the margin is not generous: at 1280 × 832 the clearance to
  Home's card row is 48px horizontally and 20px vertically to the heading; at
  the 1024 × 768 floor it is ~38px and 16px. Verify by measuring, not by
  looking.
- **Component host elements are part of the layout (added 2026-08-18).** With
  Angular's default emulated encapsulation, `<app-foo>` — not the template's
  root element — is what the parent lays out, and it is unstyled
  (`display: inline`, `flex-shrink: 1`, `height: auto`). A component that takes
  part in its parent's flex/grid/height chain must declare it on its own
  `:host` (`display: contents` when the template root should be the item;
  `display: none` when the component renders nothing). Four separate visual
  bugs in the first feature came from this one gap. Not applicable to hosts
  that carry a role/label/landmark, since `display: contents` removes them from
  the accessibility tree.
- **Icon glyphs cannot be styled from a component stylesheet.** `@ng-icons`
  appends its `<svg>` via `Renderer2`, outside the Angular template, so the
  node carries no `_ngcontent-*` attribute and **no scoped selector can ever
  match it**. Anything that must reach the glyph itself (e.g. the
  gradient-filled active nav icon) needs a document-level rule in
  `global.css`, keyed on a purpose-named class. Inheritable properties
  (`color` → `currentColor`) still work normally and should be preferred.
- **Logging:** `<not yet decided>`
- **Persistence:** **exactly one thing is persisted — the guest's language and
  appearance preference.** All *content* is still static JSON / TS and is not
  written to by anything.
  - **It is server-owned state, by the user's decision of 2026-08-18** — not
    UI state, and not a client-side cache. It is written when the guest
    changes it, delivered back on load, and the server always supplies the
    defaults (`light`, `de` for the example property).
  - **The app reaches it through one contract**, `PreferenceGateway`
    (`load`/`save`/`clear`, all async, whole-record writes) behind a single
    injection token, with all policy above it in `PreferencesService`. Today
    that token is bound to `SessionPreferenceGateway`, a **`sessionStorage`
    stand-in**, which is *deleted* — not adapted — when the real endpoint
    lands. Swapping it is one provider line and, by design, changes no
    behaviour.
  - **The record carries no identifier of any kind** — no guest, stay, session
    or device id. It is scoped to *the screen*. This deliberately does not
    pre-empt the open guest-authentication question above; scoping the record
    later is a change inside the gateway and the server, nowhere else.
  - **What is still open and approval-gated:** where the server keeps it. A
    Java + Quarkus backend is planned after the MVP; **nothing behind it has
    been chosen**, and this entry records a fact about the app, not that
    choice. The endpoint's shape, its server-side validation, its security
    (see the open question above) and what clears the record at check-out all
    belong to that feature —
    `docs/features/appearance-and-language/02-design.md` §6.7.
- **Messaging / async:** two boundaries, both about the preference above and
  nothing else. Everything else in the app is synchronous.
  - **One bootstrap-gated read.** An app initializer applies the property
    defaults and then `await`s the stored preference behind a **bounded
    timeout** before the first paint, so the screen never renders light-then-
    dark or German-then-English. The read never rejects: a failure, a timeout
    and "nothing stored" are indistinguishable and all mean "use the
    defaults". A late answer only fills a setting whose override is still
    unset, so it can never overwrite a choice the guest just made.
  - **One fire-and-forget write.** A change updates the UI optimistically and
    schedules a write that **coalesces** (rapid changes collapse to one) and
    retries a bounded number of times, then stops. Failure is normal, not
    exceptional: the guest's choice stays on screen, no error UI appears, and
    **nothing is logged** — the app has no logging convention yet and a failed
    preference write is not the place to invent one (constitution principle 3
    also applies: none of this may reach a log).
- **Auth:** none in the first step.

## Integration Points

| System | Direction | Notes |
|--------|-----------|-------|
| Figma (`AirBnB-Screen`) | inbound, manual | Design source. Only the `design-import` skill may query it, and the plan is metered — see `docs/design/extraction-log.md` |

Potential future integrations implied by the design's widget column (weather,
nearby recommendations) would each be an approval-gated infrastructure decision.
None are committed.

## Open Structural Questions

- ~~Viewport contradiction~~ — **resolved 2026-08-17, revised 2026-08-18.** The
  MVP still targets large touchscreens and still has no breakpoints and no
  second layout, but "the fixed 1280 × 832 layout" is no longer accurate: a
  supported range now exists (1024 × 768 landscape → 1280 × 832 → larger),
  covered by proportional scaling, with a hard no-scrollbar rule. See
  "Cross-Cutting Concerns → Viewport & scaling" above and
  `docs/design/tokens.md` → "Minimum supported width". Phone support is still
  deferred and still a layout project. The type-size-at-viewing-distance
  sub-question is now sharper, not resolved: at the floor, body text renders at
  11.2px and sidebar labels at 9.6px, below the edge `design-sheet.md` already
  warns about — the pending hardware check must happen at the floor size, not
  only at the design size.
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
