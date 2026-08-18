# Review: Home Screen Prototype
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md) | **Analyze:** [04-analyze.md](04-analyze.md)
**Result:** PASS ✅ (findings #1–#5 fixed and spot-verified; #6–#8 remain as non-blocking Info notes for future work)
**Reviewed:** 2026-08-18 · **Re-verified after fixes:** 2026-08-18

> First code review in this repo — `frontend/` scaffold + Home screen, reviewed
> in full (not a diff review). Build run via `npm run build`, tests run via
> `npm test`, both independently confirmed green (10 files / 29 tests) before
> and after this review's own investigation. All findings below were verified
> by reading the actual source, not by trusting `03-todo.md`'s checkmarks or
> `04-analyze.md`'s PASS.

## Findings

| # | Severity | Category | File | Finding | Fix |
|---|----------|----------|------|---------|-----|
| 1 | 🟠 Major | Conventions/Constraint | `frontend/tsconfig.json` | `"strict": true` is **not present** in the effective TypeScript compiler config, despite REQ-001 ("TypeScript strict mode") being a Must requirement, `03-todo.md`'s scaffold step being checked off as validated, and the constitution's constraint "Angular + TypeScript (strict mode)". Verified with `npx tsc -p tsconfig.app.json --showConfig` — `strict` does not appear anywhere in the resolved `compilerOptions`. The `ng new --strict` flag dev reported passing did not land in the committed `tsconfig.json`. | Add `"strict": true` to `tsconfig.json` → `compilerOptions`. Verified this is a low-risk, zero-fallout fix: temporarily enabling it and re-running `tsc --noEmit` and `ng build` produced **zero new errors** — the codebase is already strict-clean, just not declared as such. |
| 2 | 🟠 Major | Conventions | `frontend/src/app/app.config.ts` | `provideZonelessChangeDetection()` is missing from the production app config, even though this is a zoneless scaffold (no `zone.js` dependency, no `polyfills` entry in `angular.json`) and **every one of the 9 spec files** explicitly adds it to `TestBed`. Empirically verified with a throwaway diagnostic test (bootstrapped the real, unmodified `App` + `appConfig`, clicked a sidebar button with no manual `fixture.detectChanges()` call): the DOM **did** update and the router **did** navigate, because Angular 22's default notification-based scheduler happens to trigger change detection on DOM events even without either zone.js or the explicit provider. So this is not currently a functional bug — but it means the app runs in an undeclared, implicit scheduling mode in production while every test explicitly opts into the real one, which is exactly the "zone.js vs. zoneless consistency" check this review was asked to make. A future Angular version could change that default. | Add `provideZonelessChangeDetection()` to `appConfig.providers` in `app.config.ts`, matching what every spec file already does. |
| 3 | 🟡 Minor | Correctness/Code quality | `frontend/src/app/features/home/action-card/action-card.component.html` | `(keydown.enter)` and `(keydown.space)` handlers are added alongside `(click)` on a native `<button>`. Browsers already synthesize a `click` event for Enter (on keydown) and Space (on keyup) on `<button>` elements, so a real keyboard activation fires `activate()` — and therefore `router.navigateByUrl()` — **twice**. Harmless today (navigating to the same URL twice is a no-op), but sloppy, and would double-fire any future side effect (e.g. an analytics call) added to `activate()`. `SidebarComponent`'s buttons correctly rely on native activation with only a `(click)` handler — this component should match that pattern. | Remove the `(keydown.enter)`/`(keydown.space)` bindings; the native `<button>` already handles both via its synthesized `click`. |
| 4 | 🟡 Minor | Conventions | `frontend/src/styles/tokens.css`, `frontend/src/styles/global.css` | `--font-family-base` and `--size-widget-col` are defined in the semantic token layer but never referenced by any component. `global.css`'s `body` rule hardcodes the literal `'Plus Jakarta Sans', sans-serif` instead of `var(--font-family-base)`, duplicating the value the token exists to centralize (not a hex/px violation of principle 5, but it defeats the point of having the token). `--size-widget-col` is unused because the widget column correctly uses `flex: 1` per REQ-014/Figma, not a fixed width — the token itself may just be dead weight worth removing or explicitly marking as reserved-for-later. | Reference `var(--font-family-base)` from `body`'s `font-family` in `global.css`; either use or remove `--size-widget-col`. |
| 5 | 🟡 Minor | Conventions | `frontend/src/app/layout/sidebar/sidebar.component.ts` | Uses constructor-parameter DI (`constructor(private readonly router: Router) {}`) while every other component in this codebase (`ShellComponent`, `ActionCardComponent`, `HomeContentComponent`, `HeadingBlockComponent`, `App`, `SectionPlaceholderComponent`) consistently uses `inject()`. Since this is the pattern-setting first feature in the repo, worth normalizing now before it's copied. | Change to `private readonly router = inject(Router);` for consistency. |
| 6 | 🟢 Info | Design fidelity | `frontend/src/app/features/home/home-content.component.html/.css` | `<app-hero-artwork />` is rendered as an ordinary flex sibling inside `.home-content`'s flex row (sharing the `--space-7` gap with the Screen/Widgets columns), whereas `docs/design/screens/home.md` describes hero artwork as **absolutely positioned** ("sitting above the background and below the layout"). Invisible today since the no-artwork variant renders an empty template, but the component's own doc comment says it's "kept as its own component so a future artwork-present variant has a clear seam to slot into" — whoever builds that variant will need to change this from a flex item to `position: absolute` to avoid disturbing the row's gap/alignment. Worth a comment now so it isn't a surprise later. | No action required for this feature; leave a code comment noting the future `position: absolute` change, or flag in the next feature's design doc. |
| 7 | 🟢 Info | Testing | `frontend/src/app/layout/shell/shell.component.spec.ts`, `sidebar.component.spec.ts` | REQ-007's acceptance criterion ("given a section placeholder route is active instead, then that section's item is the one shown active, from the same shell") is only unit-tested in isolation — `SidebarComponent`'s spec feeds `activeRoute` directly as an input, and `ShellComponent`'s spec checks routed *content* differs across two routes but never asserts which sidebar item carries `.sidebar-item--active` after a real navigation. No test exercises the full `Router → NavigationEnd → ShellComponent.activeRoute signal → SidebarComponent` chain end-to-end. | Add one `ShellComponent` test asserting the correct item is `.sidebar-item--active` after navigating to a non-Home route through the real router. |
| 8 | 🟢 Info | Dependencies/Scope | `frontend/package.json` | `@angular/cdk` was added (pre-approved by name, so no approval-gate violation) but is not imported anywhere in this feature's source — grep confirms zero usage. Pre-approved does not mean "add before needed"; constitution principle 4 favors minimizing what ships to an unpatched, self-hosted box. Low current impact since it's tree-shaken out of the 233 kB bundle, but worth deferring actual installation to the feature that first uses Overlay/Dialog/FocusTrap. | No action required now; note for future features that add the first CDK-dependent UI. |

## REQ-ID coverage confirmation

All 21 requirements were checked against the actual running code (not just the docs), independent of `04-analyze.md`'s matrix:

| REQ-ID | Verified how | Status |
|---|---|---|
| REQ-001 | `ng new` scaffold present; strict mode **not** actually enabled (see #1) | ⚠️ Partial — code/tooling otherwise correct |
| REQ-002 | `grep` for external URLs (none); font files present; build output inspected | ✅ |
| REQ-003 | `tokens.css` two-layer structure; `data-theme` hook; `applyBranding()` wired + tested | ✅ |
| REQ-004 | `shell.component.css` 16-layer mesh on shared shell; `shell.component.spec.ts` asserts identical background across two routes | ✅ |
| REQ-005 | `grep` confirms only `property-content.service.ts` imports `example-property.data.ts` | ✅ |
| REQ-006 | `example-property.data.ts` matches PRD content verbatim (guest name, "Airbnb" spelling, card copy, `targetSectionId` mapping) | ✅ |
| REQ-007 | `sidebar.component.ts/html` renders 5+1 items from inputs, active state from `activeRoute`; tested | ✅ (see #7 for an integration-test gap, non-blocking) |
| REQ-008 | `heading-block.component.ts` interpolates `{guestName}`; tested | ✅ |
| REQ-009 | `action-card.component.ts` resolves via `getSectionRoute()`; click + Enter tested (see #3 for a redundancy note) | ✅ |
| REQ-010 | `hero-artwork.component.ts` renders empty template; tested for no `<img>`/background-image | ✅ |
| REQ-011 | `sidebar.component.html` binds `[src]="logoAsset()"`; tested | ✅ |
| REQ-012 | `app.routes.ts` — 1 shell + 6 children (Home + 5 sections); `app.routes.spec.ts` navigates all 6 | ✅ |
| REQ-013 | `:focus-visible`/`:active` rules present in `sidebar.component.css` and `action-card.component.css`, token-driven | ✅ |
| REQ-014 | `home-content.component.css` — `--size-heading` (780px), `.home-content__widgets { flex: 1 }`; tested for presence + `flexGrow: 1` | ✅ |
| REQ-015 | `npm test` → 10 files / 29 tests, all green, independently re-run | ✅ |
| REQ-016 | `global.css` — `html, body { min-width: 1280px; }` | ✅ |
| REQ-017 | `example-property.data.ts` — exactly 5 `menuItems`, correct order | ✅ |
| REQ-018 | `sidebar.component.html` — `settings` rendered in its own fixed group, content from input | ✅ |
| REQ-019 | `property-content.service.ts` — `getSectionRoute()` resolves + throws; tested including the shared-`info` case | ✅ |
| REQ-020 | `shell.component.ts/html` — background + sidebar + `<router-outlet>`; tested across routes | ✅ |
| REQ-021 | `section-placeholder.component.ts` — reads `sectionId`, resolves label via service; tested for 3 sections | ✅ |

No REQ-ID is unimplemented. The two Major findings are both cross-cutting technical-constraint gaps (tsconfig, app bootstrap config), not missing functionality — every acceptance criterion in `01-prd.md` is demonstrably met by the running app and its test suite.

## Constitution compliance

- **Principle 1** (property data, not code) — held. `menuItems`, `settings`, card copy, `targetSectionId`, branding, guest name all come from `example-property.data.ts` via the service; the only structurally-fixed thing in code is Settings' *position*, which is a layout decision, not content, matching `02-design.md`'s own reasoning.
- **Principle 2** (content via service, never imported) — held. Confirmed by `grep`: `example-property.data.ts` is imported nowhere except `property-content.service.ts` (test files importing it directly for fixture setup are fine — the rule targets components).
- **Principle 3** (no sensitive data logged) — not applicable to this feature's scope; no guest-sensitive fields (door/key codes, WiFi passwords) exist yet, and nothing here sets a bad precedent (no `console.log` of any content object, only `main.ts`'s generic bootstrap-error catch).
- **Principle 4** (self-hosted, no internet dependency) — held. No external URLs in source; fonts/icons/images all bundled. Finding #8 (`@angular/cdk` unused) is a soft nudge toward this principle's "prefer fewer dependencies" spirit, not a violation — it's pre-approved by name.
- **Principle 5** (semantic tokens only) — held. Every component CSS file reviewed references only `var(--...)` tokens; the only raw values anywhere are inside `tokens.css` itself (where principle 5 says raw values belong) and the two constitution-sanctioned exceptions (`1px` hairline border, `8px` blur radius).
- **Principle 6** (tests accompany code) — held, with the two Minor test-coverage notes above (#7) — not a gap in "does a test exist," but in "does it test the exact integration path," which is why it's Info/Minor rather than blocking.
- **Principle 7** (traceability) — held. Every TODO task and every component doc-comment cites its REQ-ID; see coverage table above.
- **Constraints** — Angular strict/standalone/Router/signals: standalone ✅, Router ✅, signals used for local UI state (`ShellComponent.activeRoute`) ✅, **strict mode not actually on (finding #1)**. Plain CSS custom properties, `rem` units, hairline/blur `px` exceptions: held throughout. Approved dependencies only: held (`@angular/cdk`, `@ng-icons/core`+`@ng-icons/phosphor-icons`, `vitest`+`jsdom` — exact versions, nothing extra). No dependency-manifest edits beyond the approved list: held. No external runtime requests: held (verified by grep and by the build/network reasoning in `handoff.md`). Branch discipline: held — all work is on `feature/home-screen-prototype`, nothing on `main`.
- **Decisions Requiring Your Approval** — nothing in the implemented code required approval that wasn't already granted; the one self-resolved decision (`{guestName}` placeholder substitution) is correctly not on that list (an implementation detail of content interpolation, not a new dependency/infra/token-mechanism/architectural-pattern/CLAUDE.md change).

## Build & test verification (run independently by this review, not trusted from the report)

```
npm run build   → Application bundle generation complete. Initial total 235.89 kB / 64.37 kB transfer.
npm test        → Test Files 10 passed (10) · Tests 29 passed (29)
```

Both re-confirmed after this review's own investigation (which included a temporary, since-deleted diagnostic spec and a temporary, since-reverted `tsconfig.json` edit — repo left exactly as handed off, verified via `git status`).

## Overall verdict against the constitution's "Quality bar (done)"

- Builds and all tests green — ✅ yes.
- Conventions from nested `CLAUDE.md` files followed — no nested `frontend/CLAUDE.md` exists yet (correctly still only *proposed*, not written, per `handoff.md` — writing it requires your confirmation first).
- No sensitive data in logs — ✅ yes, not applicable to this feature's content but no bad precedent set.
- `06-review.md` result is PASS with every `REQ-ID` implemented — **not yet**: every REQ-ID **is** implemented (see coverage table), but this review's result is **NEEDS CHANGES** because of findings #1 and #2, both 🟠 Major, both cross-cutting (not tied to a single REQ-ID) and both trivial, low-risk fixes (verified: enabling `strict` introduces zero new errors; adding `provideZonelessChangeDetection()` just makes the already-working behavior explicit).

**PASS ✅** — findings #1–#5 were fixed by `dev` and spot-verified against the diffs directly (not re-delegated to a full re-review, per this review's own recommendation that a spot-check was sufficient):

- `frontend/tsconfig.json` — `"strict": true` confirmed present in `compilerOptions`.
- `frontend/src/app/app.config.ts` — `provideZonelessChangeDetection()` confirmed present in `appConfig.providers`.
- `frontend/src/app/features/home/action-card/action-card.component.html` — redundant keydown handlers removed; the one test that exercised them was correctly removed too (jsdom doesn't synthesize `click` from `keydown` the way real browsers do, so it couldn't otherwise pass).
- `frontend/src/styles/global.css` — `body` font-family now references `var(--font-family-base)`.
- `frontend/src/styles/tokens.css` — unused `--size-widget-col` removed (the widget column correctly uses `flex: 1`, not a fixed width).
- `frontend/src/app/layout/sidebar/sidebar.component.ts` — switched to `inject(Router)`, consistent with every other component.

`npm run build` and `npm test` re-run independently after the fixes: build green (235.75 kB initial / 64.42 kB transfer), **28/28 tests passing** (10 files) — one fewer than the original 29 baseline, accounted for entirely by the intentional removal of the now-invalid keydown test.

Findings #6–#8 (🟢 Info) remain open by design — none were in scope for this fix pass, all are non-blocking, and are noted here for whoever picks up the future artwork-present variant, the router-integration test, or the first CDK-dependent feature.

Counts (original): 🔴 0 · 🟠 2 · 🟡 3 · 🟢 3 — all 🟠/🟡 findings resolved; 🟢 3 remain as deferred notes.
