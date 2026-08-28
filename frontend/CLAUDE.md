# frontend/ — Angular conventions

Stack-specific rules for this directory. `docs/constitution.md` still governs;
this file only adds what it does not already say.

## Structure

- Standalone components only, no NgModules. `core/content/` holds the content
  layer, `layout/` the app-wide shell (`ShellComponent`, `SidebarComponent`),
  `features/<feature>/` everything route-specific.
- Design tokens live in `src/styles/tokens.css` (primitives → semantic roles)
  and are imported once globally; `src/styles/global.css` holds resets, fonts
  and the few genuinely global rules. Components reference semantic tokens
  only — never a raw hex or px. **Nothing in `src/` may *read* a primitive —
  there is no exception.** The mesh-gradient background in
  `shell.component.css` used to be one; since `appearance-and-language`
  (REQ-103) it composes the semantic `--color-mesh-1…16` roles instead,
  because the theme axis may only override roles and an inline per-property
  primitive override would otherwise win over `[data-theme='dark']` and
  silently show a light mesh in dark mode. **Writing** a primitive is a
  different thing and is allowed in exactly one place: `app.component.ts`'s
  `applyBranding()` sets `--brand-primary`, `--brand-primary-deep` and
  `--brand-secondary` on `:root` at startup, and `app.component.spec.ts`
  asserts that it did. That is the per-property branding hook *defining* the
  primitive layer (REQ-003, `docs/design/tokens.md` → Implementation), not a
  component consuming a raw value.
- Property content arrives from the guest API: `core/guest/` owns all content
  wire I/O (`GuestDataService`), and only `core/guest/` and `core/api/` may
  import `@gxp/api-client` models — components keep reading
  `PropertyContentService`. There is no static content fallback; a spec that
  needs content builds a config double.
- **Routes come from the wire, not from a lookup.** Every menu-shaped thing —
  a sidebar entry, an overflow tile, an action card — navigates the
  server-resolved `route` the payload delivers, because enablement and
  ceiling resolution live on the server. For the reverse direction, from the
  current URL back to the item that claims it, use
  `layout/sidebar/menu-split.ts`'s `routeMatches(item, url)` /
  `itemForRoute(items, url)`; `SidebarComponent` and
  `SectionPlaceholderComponent` share them, so a route normalises identically
  in both. There is no id→route lookup service —
  `PropertyContentService.getSectionRoute(id)` was retired in 2026-08-27 once
  nothing called it.
- Every in-app route renders inside `ShellComponent`; a section without a real
  design yet gets `SectionPlaceholderComponent`, never a standalone stub page.
  The three kiosk **state** screens — `/pairing`, `/standby`, `/connection` —
  are the deliberate exception: they render outside the shell (no sidebar, no
  chrome), are entered only by the state guards in `core/guest/`, and read no
  property content that the guards have not already guaranteed.

## Layout

- **The host is the flex item, not your template root.** With emulated
  encapsulation, `<app-x>` — not `.x` — is what the parent lays out, and it is
  unstyled. A component that participates in its parent's layout must declare
  it on `:host`: `display: contents` when the template root should be the real
  item, `display: none` when the component renders nothing (an empty host still
  consumes a flex `gap`). This caused four separate layout bugs before it was
  written down.
- **Nothing scrolls, and it is verified by measurement, not by eye**
  (`docs/constitution.md` → Constraints). On every route, at 1024×768 and at
  1280×832, assert `scrollWidth <= clientWidth` and `scrollHeight <=
  clientHeight` in a real browser. Never reach for `overflow: hidden` — it
  hides the regression instead of fixing it.
- Sizes are `rem`; the root font size is viewport-proportional
  (`global.css`), so the whole UI scales without breakpoints. Do not add a
  media query to compensate for something being the wrong size.

## Icons

`@ng-icons` injects its `<svg>` with `Renderer2`, outside the template, so that
node carries no `_ngcontent-*` attribute and **component-scoped CSS silently
cannot style it**. Rules targeting an injected glyph go in `global.css` behind
a purpose-named class (see `.icon-gradient-active`), not in the component's
stylesheet and not via `::ng-deep`.

## Appearance & language

- **No user-visible string is written in a component or template** — not a
  label, not an `aria-label`, not `alt` text, not an empty state. App chrome
  resolves through `TranslationService` (`core/i18n/`); property-authored text
  comes from `PropertyContentService` as a per-language map. If you are typing
  German into a `.html` file, you are in the wrong file.
- **`<html>`'s `data-theme` and `lang` are written in exactly one place**,
  `app.component.ts`, beside `applyBranding()`. That is the only code in
  `src/` allowed to touch `document.documentElement`.
- **The theme axis overrides semantic roles only.** `[data-theme='dark']`
  never redefines a primitive: primitives are the per-property branding axis
  and are written inline on `:root` at runtime, which beats any stylesheet
  rule. Dark raw values are *new* primitives (`--ink-050`, `--ink-300`,
  `--night-1…16`, `--surface-*-dark-raw`), added, never overridden.
- **The top-right corner of the content area is reserved** for
  `StatusBarComponent` (~13rem × 2.75rem, inset by the shell's own padding).
  It is an overlay and takes no flow space, so a new screen will not be pushed
  out of its way — check it does not collide.

## Tests

Vitest + `TestBed`, zoneless (`provideZonelessChangeDetection()` in every
component test). Components under test get a `PropertyContentService` test
double through DI — if a test has to import the data file to assert something,
that is a design smell, not a test convenience.

`tools/measure-states.mjs` is the review instrument for the three full-screen
states and for the staleness chrome — the discreet pill and its escalated
banner, the latter driven by ageing the cached record's `storedAt` past the
threshold; it needs the dev backend and drives its state via the manager API
(and restores it).

**`src/test-setup.ts` runs before every spec** (`setupFiles` on the `test`
target). It supplies `localStorage`/`sessionStorage`, because Node ≥ 26 defines
those names on `globalThis` itself and Vitest then skips jsdom's — it installs
only what is missing, so a future runtime that fixes this upstream silently
wins. No other global state belongs in that file: a test double lives in the
spec that needs it.

**A service's collaborator is a DI double built from writable signals**
(`{ provide: PreferencesService, useValue: { language } }`), not the real
service with a stand-in gateway underneath it. `vi.mock` does not work here —
the Angular unit-test system rejects it for relative imports — so anything that
needs a *broken* config tests the exported pure function instead.

**A test double for an injected transport lives inside the spec that needs
it** — never in `src/` as a shared helper, and never imported from another
spec: `FakePreferenceGateway` sits in `preferences.persistence.spec.ts`, and
`installMatchMedia` is copied into each spec that needs it.

jsdom computes no layout and decodes no images: geometry, wrapping and asset
rendering are checked in a real browser during review, never asserted in a unit
test. The instrument for that is `node tools/measure.mjs` — no-scroll on every
route × theme × language × viewport, the REQ-022/REQ-014 boxes,
wrapping/truncation and logo decode. It reports numbers and asserts nothing:
`docs/constitution.md` v1.3.0 approves `playwright` as review-time tooling
only, and the end-to-end-framework decision is still open.
