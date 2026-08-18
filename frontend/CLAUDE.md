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
  only — never a raw hex or px. One documented exception: the mesh-gradient
  background in `shell.component.css` composes the `--mesh-1…16` primitives
  directly, because those are gradient **layers**, not a colour role — see
  `docs/design/tokens.md` → "Background implementation". Nothing else in `src/`
  may *read* a primitive. **Writing** one is a different thing and is allowed
  in exactly one place: `app.component.ts`'s `applyBranding()` sets
  `--brand-primary`, `--brand-primary-deep` and `--brand-secondary` on `:root`
  at startup, and `app.component.spec.ts` asserts that it did. That is the
  per-property branding hook *defining* the primitive layer (REQ-003,
  `docs/design/tokens.md` → Implementation), not a component consuming a raw
  value.
- Property content is reached through `PropertyContentService` via DI. Nothing
  outside that service imports `example-property.data.ts` — except
  `property-content.service.spec.ts`, which reads it deliberately to check the
  service against the real shipped config. A component that does this is a bug;
  any other spec that does it is the design smell the Tests section describes.
  A component that needs a route for a card resolves it with
  `getSectionRoute(targetSectionId)` instead of carrying a route of its own.
- Every route renders inside `ShellComponent`; a section without a real design
  yet gets `SectionPlaceholderComponent`, never a standalone stub page.

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

## Tests

Vitest + `TestBed`, zoneless (`provideZonelessChangeDetection()` in every
component test). Components under test get a `PropertyContentService` test
double through DI — if a test has to import the data file to assert something,
that is a design smell, not a test convenience. jsdom computes no layout and
decodes no images: geometry, wrapping and asset rendering are checked in a real
browser during review, never asserted in a unit test. The instrument for that
is `node tools/measure.mjs` — no-scroll on every route × viewport, the
REQ-022/REQ-014 boxes, wrapping/truncation and logo decode. It reports numbers
and asserts nothing: `docs/constitution.md` v1.3.0 approves `playwright` as
review-time tooling only, and the end-to-end-framework decision is still open.
