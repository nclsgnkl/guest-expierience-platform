# Review: Home Screen Prototype
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md) | **Analyze:** [04-analyze.md](04-analyze.md)
**Result:** **PASS ✅** (sidebar-rail review, pass 3 — see
"Sidebar-rail review — pass 3" at the bottom of this file, which supersedes
every earlier result for status purposes: 🔴 0 · 🟠 0 · 🟡 4 · 🟢 3, against
constitution **v1.10.0**)
**Reviewed:** 2026-08-18 (round 1) · **Re-verified after fixes:** 2026-08-18 ·
**Round 3 (visual-defect correction, first review with the visual check):** 2026-08-18 ·
**Round 3 final verification (rounds 4 + 5 fixes re-measured):** 2026-08-18 ·
**Sidebar-rail review, pass 1 (the four UI rounds of 2026-08-28/29):** 2026-08-29 ·
**Sidebar-rail review, pass 2 (verification of the pass-1 fixes):** 2026-08-29 ·
**Sidebar-rail review, pass 3 (final verification of the pass-2 fixes):** 2026-08-29

> **A note on numbering, because this file now has two kinds of "round".**
> *Rounds 1–6* elsewhere in this file and in `handoff.md` are **implementation**
> rounds (`dev`); *round 1 / round 3 / round 3 final verification* above are
> **review** passes. The three 2026-08-29 sections at the bottom are review
> passes over the sidebar-rail work and are deliberately named by subject
> rather than given a number that would collide with either sequence.

> ⚠️ **The 2026-08-18 PASS below is history, not the current status.**
> Four owner-requested UI rounds landed on the sidebar rail after it (see
> `handoff.md`), and the three sidebar-rail passes at the bottom of this file
> are about those.
> **Updated 2026-08-29 by pass 3:** pass 2's three 🟠 Major findings are all
> **closed and independently re-measured**; nothing 🔴 or 🟠 is open against
> this feature. The rail stack is still **uncommitted** on
> `refactor/bce-subpackages-and-boundary-security` (**15** modified files, 0
> untracked — 13 was pass 2's count, before the pass-2 fix run touched
> `hero-artwork.component.ts` and `06-review.md`). The owner commits; no agent
> does.

> Round 1's result below was **PASS ✅** (findings #1–#5 fixed and spot-verified;
> #6–#8 remain as non-blocking Info notes). It is kept verbatim as history.

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

---

# Round 3 review (2026-08-18, `reviewer`) — visual-defect correction

**Result: NEEDS CHANGES ❌** · 🔴 0 · 🟠 4 · 🟡 5 · 🟢 8
> **Superseded 2026-08-18** by "Round 3 — final verification" at the bottom of
> this file, which flips the result to **PASS ✅**. Kept verbatim as the record
> of what was found and why.

> **Scope:** round 3 (`03-todo.md` → R0–R8) against `docs/constitution.md`
> **v1.2.0**, the amended planning docs, `docs/design/tokens.md`,
> `docs/design/screens/home.md`, the reference render
> `docs/design/assets/home-no-artwork.png`, and the new `frontend/CLAUDE.md`.
>
> **This is the first review to run the `code-review` skill's step 0 / category
> 9 (visual check) for real.** It paid for itself: two of the four 🟠 Major
> findings below (#2 heading-width token, #3 sparkle glyph) are *only* visible
> by comparing rendered pixels against the reference — every source-level
> category, the green build, the 34 green tests and `04-analyze.md`'s PASS all
> let them through, exactly as they let round 2's defects through.
>
> **Nothing was taken on faith from the implementation report.** Build and
> tests re-run independently; all geometry, colour, scroll and wrapping numbers
> below were re-measured in Chromium against the running dev server (port 4300,
> reused, not restarted) and re-scanned off the reference PNG with an
> independent decoder. Where `dev`'s diagnosis and the pixels disagreed, the
> pixels won — see finding #2.

## Verification performed (independent)

```
npm run build   → green. Initial total 237.35 kB / 64.71 kB transfer.
npm test        → Test Files 11 passed (11) · Tests 34 passed (34) · 0 failed
```

**Baseline test count — `dev` is right, 28 not 29.** Counted at `HEAD`:
24 literal `it(` blocks + 4 extra runtime cases from the 5-way loop in
`app.routes.spec.ts` = **28**. Working tree: 30 literal + 4 = **34**. Round-3
delta **+6** (3 × `logo-asset.spec.ts`, 2 × sidebar gradient-flag, 1 × shell
paint-server). The `29` in `03-todo.md`:283 is *historically* correct rather
than wrong — 29 was the count before the redundant-keydown test was removed in
the round-1 fix pass (`06-review.md` above records that removal). It is stale
as a round-3 baseline. `02-design.md`:798 carries the same stale 29.

**No-scroll (REQ-024), re-run — 18/18 pass**, `documentElement.scrollWidth ≤
clientWidth && scrollHeight ≤ clientHeight` on all 6 routes × 3 viewports:

| viewport | root font size | routes passing | `html`/`body` computed `overflow` |
|---|---|---|---|
| 1024 × 768 | 12.8px | 6 / 6 | `visible` / `visible` |
| 1280 × 832 | 16px | 6 / 6 | `visible` / `visible` |
| 1440 × 900 | 17.3079px | 6 / 6 | `visible` / `visible` |

Achieved **by construction**, not by hiding overflow: the only `overflow:
hidden` in the whole of `src/` is on `.action-card__header`, where it serves
`text-overflow: ellipsis` on a single line of text and is not a scroll
suppressor. `html`/`body` carry no `overflow` declaration at all. ✅ REQ-024's
second acceptance criterion holds.

**Geometry at 1280 × 832, re-measured (targets from `01-prd.md` → REQ-022 /
REQ-014 and the reference render):**

| | measured | target | |
|---|---|---|---|
| sidebar box | `x 32, y 24, w 100, h 784` | `32 / 24 / 100 / 784` (± 2) | ✅ |
| nav item / active pill | `84 × 84` at `x 40` | `84 × 84`, = `--size-sidebar` − 2 × `--space-3` | ✅ |
| logo→first nav / last nav→Settings | `88` / `88` | `> 60` each | ✅ |
| heading block | `x 172, y 88, w 780` | `x 172`, `y ≈ 88` | ✅ |
| card row | `x 172`, bottom `y 744` | `172` / `744` | ✅ |
| card widths / gaps | `196` × 4, gaps `12`, row `172 → 992` | reference edge-scan: identical | ✅ |
| logo asset | `complete: true`, `naturalWidth: 24`, no `error` | decodes | ✅ |
| active glyph fill | `url("#icon-gradient-active") rgb(44,31,74)` | gradient paint + fallback | ✅ |
| gradient stops | `rgb(244,90,168)` → `rgb(138,92,246)` | `--brand-primary` → `--brand-secondary` | ✅ |
| inactive glyph fill | `rgb(44,31,74)` flat | `--color-text-primary` | ✅ |

At 1024 × 768 the composition is identical, uniformly 0.8×, with no reflow and
nothing newly wrapped (✅ REQ-025). Sidebar labels: 6 / 6 on one line at both
sizes. Card titles: `white-space: nowrap`, box height 19px @1024 / 23px @1280 =
one line in all four cards; title / description / arrow y-positions pairwise
identical across the four cards (✅ REQ-007, REQ-009's strengthened criteria).

**Gradient glyph, re-verified by rendered pixels (not by computed style):**
on `/` the active glyph runs `(239,135,195)` pink at the top to `(166,125,243)`
violet at the bottom, 0 flat-ink pixels; on **`/smart-home`** — a non-root URL
under `<base href="/">` — `(239,112,183)` → `(141,92,245)`, again 0 flat-ink
pixels. **The `url(#…)` fragment reference survives `<base>` in Chromium; `dev`'s
claim reproduces.** Overriding `--brand-primary` to `#00b894` at runtime
repaints the glyph green→violet without a rebuild (✅ REQ-023 + REQ-003). Only
one element in the document carries `.icon-gradient-active`, and it is the
active item's `<ng-icon>` — the global rule does not leak.

## Findings

| # | Severity | Category | File | Finding | Fix |
|---|----------|----------|------|---------|-----|
| 1 | 🟠 Major | Visual fidelity / Design | `frontend/src/app/features/home/action-card/action-card.component.css` | **Card title truncates: "Haus & Funktion…"** where the reference render shows "Haus & Funktionen" in full. Measured: the title needs **167px** of advance width in a **162px** content box (`--size-panel` 196 − 2 × `--border-glass` 1px − 2 × `--space-5` 16px). R5's literal criterion ("renders on one line") is met — this is a *design* deviation, not a broken rule. Root cause is not the implementation: "Check-Out" measures 102px of ink in the build and 101.6px in the reference (identical font rendering), and in the reference the title's own ink (161.6px) already fills its 160.8px inset box — the Figma frame is ink-measured and over-runs its padding by a hair, the browser is advance-measured and cannot. The design's copy is simply 3–5px too long for a 196px card at 18px ExtraBold in a real browser. | User's design call. Cheapest faithful fix: horizontal padding `--space-5` (16px) → `--space-4` (12px) on `.action-card`, which yields a 170px box (3px headroom; the arithmetic limit is ≤ 13.5px of padding). Alternative: shorten the configured copy. Do **not** shrink `--text-title` — it is verified correct against the render. |
| 2 | 🟠 Major | Visual fidelity / Spec derivation | `frontend/src/styles/tokens.css` (`--size-heading`), `docs/design/screens/home.md`, `01-prd.md` → REQ-014 | **The headline gap is *not* a type-size error — `--text-display: 3rem` is verified correct — it is `--size-heading` being wrong by exactly 2×, and it *is* the same family as the `aspect-[24/24]` bug.** Evidence, all measured: headline ink height reference **36.8px** vs build **37px**; total advance reference **≈738px** vs build **736px**; subtitle width reference **188.8px** vs build **188px** — the display type is the right size. The reference wraps because its text column is ≈390px, not 780px: `docs/design/screens/home.md` → "Heading block — Fixed `780px` wide" sits in the Components section, where *every* number is the raw Figma value at 2× (sidebar `199` → 100 CSS, logo tile `112` → 56, Glass Panel `392` → 196, widget column `443` → 220, `--text-display` `96` → 48). 780 raw = **390 CSS**; it is the only size in the file that was never halved. **Proven at runtime:** setting `--size-heading: 24.375rem` (390px) reproduces the reference's line breaks exactly — headline 359 + 375 (reference ink 356 + 369), paragraph 347 + 150 (reference ink 348 + 150.4) — with zero layout side effects (card row still `x 172`, widget column still 224px, still no scroll). | User's design call (it changes a token and three docs). `--size-heading: 24.375rem`; correct `home.md`'s Components entry **and** its Layout table's `780` width; amend REQ-014's "the heading block keeps its 780px width" criterion. Expect the two headline lines to sit tighter than the reference — that is the already-accepted `--text-display` line-height decision (see "Explicitly cleared" below), not a new defect. |
| 3 | 🟠 Major | Visual fidelity | `frontend/src/app/features/home/heading-block/heading-block.component.html` | **The heading sparkle deviates from the reference on two axes at once, and was not reported.** Reference: a **filled** glyph with a pink→violet gradient (sampled `(192,91,206)`, `(174,92,220)`, `(239,129,171)`). Build: an **outline** `phosphorSparkle` in flat `--color-text-primary` `(44,31,74)` — dominant colour confirmed by pixel scan, 22 × 22px box. Same defect class as D3 (the active nav glyph), which the user has already ruled on once this round. The implementation is faithful to the written spec (`home.md`: "All four use `--color-text-primary`"; `design-sheet.md`: "Gewicht `regular` — in **allen** Zuständen") — as with D3, the spec is wrong about the render. | User's design call. If ruled the same way as D3: reuse the mechanism just built — add `class="icon-gradient-active"` to the sparkle `<ng-icon>` and switch to the Phosphor `fill` weight — then update `home.md` and `design-sheet.md` the way D3's override was recorded. |
| 4 | 🟠 Major | Conventions | `frontend/CLAUDE.md` | **The new nested `CLAUDE.md` states a rule that its own codebase violates on day one:** "Components reference semantic tokens only — never a primitive". `shell.component.css` references **17** primitives directly (`--mesh-1` … `--mesh-16` in the 16 gradient layers, plus `--mesh-6` as `background-color`) — correctly so, per `docs/design/tokens.md` → "Background implementation". A nested `CLAUDE.md` loads into every future session in that directory, so this line will either drive a pointless "fix" of the shell or teach the next agent that the file's rules are approximate. Same shape, milder: "Nothing outside that service imports `example-property.data.ts`" — `property-content.service.spec.ts` legitimately does, and the file's own Tests section calls that "a design smell". | Carve out the documented exception in both rules, e.g. "…never a raw hex or px. One documented exception: the mesh-gradient background in `shell.component.css` composes the `--mesh-*` primitives directly (`docs/design/tokens.md` → Background implementation)" and "…outside that service and its own spec". |
| 5 | 🟡 Minor | Conventions / Type safety | `frontend/src/node-builtins.d.ts`, `frontend/tsconfig.app.json` | **The hand-rolled Node ambients are in the *application* compilation, not just the spec one** — the opposite of what the file's own comment claims ("Picked up by `tsconfig.spec.json`'s d.ts include pattern"). `tsconfig.app.json`'s `include: ["src/**/*.ts"]` matches `.d.ts` files; confirmed with `tsc -p tsconfig.app.json --listFiles`, and demonstrated with a throwaway probe under `src/app/` that imports `node:fs` and calls `process.cwd()` — it type-checks clean (probe deleted, `git status` verified). So app code can now reference Node built-ins and only fail at bundle/runtime. The *test* that needs them is justified: D4 was invisible to every other check, and `logo-asset.spec.ts` even guards its own guard with a negative case. The scoping is what is wrong, not the idea. | One line, no manifest change: add `"src/node-builtins.d.ts"` to `tsconfig.app.json` → `exclude`. Cleaner still, and it deletes two thirds of the file: resolve the asset with `new URL('../../assets/branding/logo-placeholder.svg', import.meta.url)` — standard ESM + the DOM `URL` type — which removes the need for `node:path` and the global `process` shim entirely, leaving only `node:fs`. |
| 6 | 🟡 Minor | Docs vs code | `docs/features/home-screen-prototype/02-design.md` | "**The shipped copy fits**" (Round-3 risks) is **false** — verified 167px in a 162px box, see finding #1. The same passage's "≤ ~164px" and `action-card.component.css`'s "the title box is 164px" both miss the 1px glass border on each side; the real content box is **162px**. Trust the measurement. | Correct the sentence to record that the shipped copy does **not** fit and is currently ellipsized; correct 164 → 162 in both places. |
| 7 | 🟡 Minor | Testing / Process | `frontend/CLAUDE.md`, `frontend/.measure.js`, `.scroll.js`, `.shot.js` | `frontend/CLAUDE.md` now *mandates* browser-measured verification ("geometry, wrapping and asset rendering are checked in a real browser during review, never asserted in a unit test") but the repo carries **no instrument** for it — all three helpers are untracked throwaways `dev` invites the next person to delete. The next session will rewrite them from scratch and measure something subtly different. | Commit one small script (e.g. `frontend/tools/measure.mjs`) covering the no-scroll assertion and the REQ-022/REQ-014 boxes. Note an npm-script entry would be a `package.json` edit — approval-gated — so run it as `node tools/measure.mjs` until the E2E-framework decision is taken. |
| 8 | 🟡 Minor | Dependencies | `frontend/package.json`, `frontend/package-lock.json`, `docs/constitution.md` | `playwright@^1.48.0` (resolved 1.62.1) is a devDependency but is **not** on the constitution's "Approved dependencies" list, and the constraint "No end-to-end framework is chosen yet — that is a separate decision, still open" now reads as stale next to it. **Not a round-3 violation** — it was added out-of-band in an earlier session and round 3 only regenerated `package-lock.json` (the sole manifest diff, 48 generated lines). | User's call: either add it to the approved list explicitly scoped as *review-time browser tooling, not the E2E-framework decision*, or remove it and treat the visual check as an ad-hoc local tool. |
| 9 | 🟡 Minor | Process / Approval gate | `frontend/CLAUDE.md` | The constitution's gate reads "the exact text is proposed first, written only after you confirm it". The record (`handoff.md`) is "approved in principle" plus "its exact wording was reproduced in this session's report for the user to object to" — opt-out, not the opt-in the gate specifies. Low harm (a real approval is on record, and the file is in front of you now), but worth closing cleanly, especially since finding #4 requires an edit anyway. | Confirm the final wording explicitly once #4 is applied. |
| 10 | 🟢 Info | Visual fidelity | `frontend/src/app/layout/shell/shell.component.css` | Mesh background, 8 × 6 grid of mean colours vs the reference: typical Δ 10–30 / 255, worst **Δ 40** in the centre-right (reference `(187,138,146)` vs build `(223,169,174)`) — the build reads lighter and pinker through the middle, and the reference's violet lower-left region is broader. Much improved over round 2 and inherent to approximating a Figma mesh with 16 CSS radial layers; recorded, not worth chasing unless you want it closer. |
| 11 | 🟢 Info | Correctness | `frontend/src/app/layout/shell/shell.component.html` | `gradientTransform="rotate(49 0.5 0.5)"` runs under the default `gradientUnits="objectBoundingBox"`, so the axis is rotated in a *normalised* unit square — the rendered angle therefore depends on each glyph's bounding-box aspect ratio. That is the likely explanation for the 137° best-fit you measured against a specified 139°; harmless for the near-square `House`, larger for a markedly non-square glyph. `gradientUnits="userSpaceOnUse"` with explicit coordinates would pin it, at the cost of hardcoding a size. |
| 12 | 🟢 Info | Conventions | `frontend/src/styles/tokens.css` | Two tokens are defined and referenced by nothing: `--size-sidebar-item` (4.5rem — superseded by the derived 84px, deliberately kept for Figma traceability) and `--gradient-icon-active` (documentation-only; the glyph is actually painted by the SVG paint server reading `--color-icon-active-start/end`). Both are documented as deliberate. The second can silently drift from the SVG's real axis — nothing binds them. |
| 13 | 🟢 Info | Docs vs code | `docs/design/tokens.md` | The Sizing table still lists `--size-widget-col` (13.75rem), which was removed from `tokens.css` in the round-1 fix pass (the widget column correctly uses `flex: 1`). Doc documents a token the code no longer defines. |
| 14 | 🟢 Info | Docs | `docs/design/design-sheet.md`:212 | Still carries "Glyph-Box \| 12 px (1:1)" — the exact `aspect-[24/24]` misread that was corrected in `tokens.md` after round 2. `tokens.md` wins per the constitution, but this is the file the next feature's designer-facing read starts from. |
| 15 | 🟢 Info | Docs vs code | `frontend/src/app/core/content/example-property.data.ts`, `docs/design/screens/home.md` | The shipped headline is "Willkommen im **Airbnb** von Lisa!"; `home.md` and the reference render say "**AirBnb**". REQ-008 requires the text to "match `docs/design/screens/home.md` exactly", so the letter of that criterion currently fails. Trust the code — "Airbnb" is the correct brand spelling — and fix the doc. |
| 16 | 🟢 Info | Architecture | `frontend/src/app/features/home/hero-artwork/hero-artwork.component.ts` | `:host { display: none }` does **not** foreclose the component's future. The space REQ-014 reserves is a different element (`.home-content__widgets`, `flex: 1`) and is present and correctly sized — measured **224px** wide at 1280 × 832, which independently matches the documented `--size-widget-col` 220px. And `home.md` specifies the artwork as *absolutely positioned* (`1550 × 999` at `x793/y155`, above the background, below the layout) — so the future variant must not become a flex sibling again; `display: none` is the right shape for the seam, and the rule sits in the component's own `styles`, one line from where a variant would make it conditional. |
| 17 | 🟢 Info | Robustness | `frontend/src/styles/global.css` | `html, body { min-width: 1024px }` plus `.shell { height: 100dvh }` is knife-edge at exactly the 1024px floor: any future change that produced 1px of vertical overflow there would add a vertical scrollbar, drop `clientWidth` below 1024, and trip the guard rail into a horizontal scrollbar too. Not a defect today (18/18 pass with margin); worth knowing when the floor is next stress-tested. |

## Explicitly cleared (checked, not defects)

- **Line heights read tighter in the reference than in the build** — measured
  card description leading 17.6px (reference) vs 21px (build); display 60px vs
  52.8px. This is the **documented, accepted decision** of 2026-08-17
  (`docs/design/tokens.md` → Implementation): every Figma text layer is on
  "Auto" (≈1.26 for Plus Jakarta Sans — my measurements confirm that number
  exactly), and explicit `1.1 / 1.3 / 1.5` were chosen to live in the token
  layer instead, with "headings slightly tighter, body text slightly looser"
  accepted in writing. Verified as intentional, not reported as a finding.
- **Logo mark differs from the reference** (house placeholder vs Figma's
  `AngularLogo`) — both are placeholders; `home.md` records `AngularLogo` as
  "picked arbitrarily" behind a swappable slot. Not a deviation.
- **`overflow: hidden` on `.action-card__header`** — not the constitution's
  forbidden use. It clips one line of text to render an ellipsis; it is not
  suppressing document scroll, and the no-scroll rule is met by construction
  everywhere (see the table above).
- **Security** (constitution principle 3/4 and the review's own category 3) —
  no secrets, no guest data logged (the only logging is the CLI's default
  `console.error` on bootstrap failure in `main.ts`), no new endpoints or
  handlers, no user input reaching a query. Production `dist/` contains no
  external URL other than XML namespace URIs and a static documentation link
  inside Angular's own bundle — no runtime request leaves the box. The new
  paint-server `<svg>` is `aria-hidden`, `focusable="false"`, zero-sized and
  absolutely positioned: no layout, focus-order or exposure impact.
- **Branch discipline** — all work is on `feature/home-screen-prototype`;
  `main` untouched. (Round 3 is currently **uncommitted** in the working tree —
  30 modified/untracked paths. Not a finding, just the state I reviewed.)

## REQ-ID coverage — round 3

| REQ-ID | Verified how | Status |
|---|---|---|
| REQ-022 (full-height sidebar) | Measured `32 / 24 / 100 / 784` at 1280 × 832; item `84 × 84` square = `--size-sidebar` − 2 × `--space-3`, derived not tokenised; group gaps 88 / 88 > 60 | ✅ |
| REQ-023 (gradient active glyph) | Rendered pixels pink→violet on `/` **and** `/smart-home`; stops resolve to `--brand-primary`/`--brand-secondary`; runtime `--brand-primary` override repaints it; inactive glyphs flat `#2c1f4a`; exactly one element carries the class | ✅ |
| REQ-024 (nothing scrolls) | 18/18 route × viewport assertions; `overflow: visible` on `html` and `body`; met by construction | ✅ |
| REQ-025 (scaling contract) | 1024 × 768 vs 1280 × 832: identical structure, uniform 0.8×, nothing newly wrapped, no scrollbars; 1440 × 900 also clean; guard rail at 1024px | ✅ |
| REQ-014 (amended: full-height column, heading top / cards bottom, reserved widget space) | Heading `y 88`, card row bottom `y 744`, `x 172`, empty middle, widget column present at 224px | ✅ (its "780px heading width" clause is what finding #2 disputes) |
| REQ-016 (amended: guard rail 1280 → 1024) | `min-width: 1024px` in `global.css`; below-floor behaviour documented as out of scope | ✅ |
| REQ-007 (no label wraps) | 6/6 labels one line at 1024 and 1280 | ✅ |
| REQ-009 (no title wraps, baselines aligned) | `nowrap`, one-line box height in all four cards at both sizes; title/description/arrow y-positions pairwise identical | ✅ literal criterion; see finding #1 for the design deviation |
| REQ-010 (artwork occupies nothing) | `:host { display: none }`; card row at `x 172`, i.e. the 32px phantom gap (D7) is gone | ✅ |
| REQ-011 (logo decodes) | `complete: true`, `naturalWidth: 24`, no `error`; plus a real regression test on XML well-formedness that fails against the pre-R4 file | ✅ |
| REQ-015 (tests green) | 11 files / 34 tests, independently re-run | ✅ |
| REQ-003 (runtime branding) | Overriding `--brand-primary` recolours the active glyph without a rebuild | ✅ |
| REQ-008 (copy matches `home.md`) | Matches except "Airbnb"/"AirBnb" — see finding #15; trust the code | ⚠️ doc |

**All of REQ-001…REQ-025 remain implemented.** No requirement is unmet by the
code; the blocking findings are quality/fidelity items, two of which need a
design decision from you rather than a code fix.

## Verdict against the constitution's quality bar

- Builds and all tests green — ✅ (re-run independently: 11 files / 34 tests).
- Conventions from nested `CLAUDE.md` followed — ⚠️ the new `frontend/CLAUDE.md`
  states a rule the shipped `shell.component.css` contradicts (finding #4).
- No sensitive data in logs — ✅.
- `06-review.md` result is PASS with every `REQ-ID` implemented — every REQ-ID
  **is** implemented and independently verified, but the result is
  **NEEDS CHANGES ❌** on four 🟠 Major findings.

**Round 3's own task list (R0–R8) passed every acceptance criterion it set
itself** — measured, not asserted. Of the four Majors, exactly **one (#4) is a
plain fix-before-merge with no decision attached**; #1, #2 and #3 are design
calls that are yours, not `dev`'s and not this review's. Once you rule on them
and #4 is reworded, this flips to PASS with a build/test re-run and a
re-screenshot of the heading and card row — no full re-review needed.

### Top 3 recommendations

1. **Rule on the two fidelity gaps together — they are one decision about how
   literally the reference render binds.** Recommended: fix `--size-heading` to
   390px (#2 — a proven 2× mis-derivation, and leaving it in the token layer
   guarantees the next screen inherits it) and take the 12px card padding for
   the title (#1). Both are one-line changes with measured, side-effect-free
   outcomes.
2. **Reword `frontend/CLAUDE.md`'s "never a primitive" rule (#4) and confirm
   the final text (#9)** before anything else lands in that directory — every
   future session in `frontend/` reads it.
3. **Scope the Node ambients out of the app compilation (#5)** with one
   `exclude` entry, and decide whether the visual-check instrument becomes a
   committed script (#7) — otherwise the rule `CLAUDE.md` just introduced has
   no repeatable way to be obeyed.

---

# Round 3 — final verification (2026-08-18, `reviewer`)

**Result: PASS ✅** · 🔴 0 · 🟠 0 · 🟡 1 · 🟢 4 — **all findings now closed:
the single 🟡 (finding 1) was approved verbatim by the user and written on
2026-08-18; see "Proposed `frontend/CLAUDE.md` amendment (finding 1) —
WRITTEN" below. Nothing is open against this feature.**
> **Superseded for status purposes 2026-08-29** by the two sidebar-rail review
> passes at the bottom of this file. *"Nothing is open against this feature"* was
> true of the code as it stood on 2026-08-18; four owner-requested UI rounds have
> landed on the sidebar rail since, and pass 2 leaves three 🟠 Major open. Everything
> in this section is kept verbatim — it is still an accurate record of what was
> verified on 2026-08-18, and none of it was contradicted by the later rounds.

> **Scope:** verification that rounds 4 and 5 closed the round-3 findings above,
> against `docs/constitution.md` **v1.3.0** (re-read in full — v1.2.0's
> "nothing scrolls" + uncapped viewport-proportional root, and v1.3.0's
> `playwright`-as-verification-tool amendment), `frontend/CLAUDE.md`,
> `docs/design/tokens.md`, `docs/design/screens/home.md` and the reference
> render `docs/design/assets/home-no-artwork.png`.
>
> **Nothing was taken from the implementation report.** Build and tests re-run;
> every geometry, wrapping, scroll, colour and truncation number below was
> re-measured in Chromium against the running dev server (port 4300, reused),
> and the rendered pixels were re-scanned against the reference with an
> independent canvas decoder. Where round 4's report and the pixels could have
> disagreed, the pixels were the authority — they agreed.

## Verification performed (independent)

```
npm run build   → green. Initial total 237.20 kB / 64.71 kB transfer.
npm test        → Test Files 11 passed (11) · Tests 35 passed (35) · 0 failed
```

Matches the claimed 11 / 35 exactly. The +1 over round 3's 34 is
`heading-block.component.spec.ts` → "paints the sparkle with the shared
gradient paint server, in the filled weight", which is the finding-#3 fix's own
test — so the count moved for a stated reason, not a drifting one.

**No-scroll (REQ-024) — 18/18, re-run**, plus **9 further viewports** because
finding #2 moved layout and the constitution's range is now explicitly
uncapped at the top end. All 27 combinations report
`scrollWidth ≤ clientWidth && scrollHeight ≤ clientHeight`, `overflow: visible`
on both `html` and `body`:

| viewport | root font size | result | headline | card titles truncated |
|---|---|---|---|---|
| 1024 × 768 (floor) | 12.8px | 6/6 routes | 2 lines | 0 / 4 |
| 1152 × 864 | 14.4px | ✅ | 2 lines | 0 / 4 |
| 1280 × 832 (design) | 16px | 6/6 routes | 2 lines | 0 / 4 |
| 1366 × 768 | 14.7694px | ✅ | 2 lines | 0 / 4 |
| 1440 × 900 | 17.3079px | 6/6 routes | 2 lines | 0 / 4 |
| 1600 × 900 | 17.3079px | ✅ | 2 lines | 0 / 4 |
| 1920 × 1080 | 20.7695px | ✅ | 2 lines | 0 / 4 |
| 2560 × 1080 (ultrawide) | 20.7695px | ✅ | 2 lines | 0 / 4 |
| 2560 × 1440 | 27.6926px | ✅ | 2 lines | 0 / 4 |
| 3840 × 2160 | 41.539px | ✅ | 2 lines | 0 / 4 |
| 1024 × 1000 (tall landscape) | 12.8px | ✅ | 2 lines | 0 / 4 |

The uncapped root (constitution v1.2.0) demonstrably works: at 3840 × 2160 the
root resolves to 41.5px and the whole composition is a uniform 2.6× of the
design size, not the design size adrift in empty space.

**Geometry at 1280 × 832 — every claimed number reproduced:**

| | measured | claimed / target | |
|---|---|---|---|
| sidebar box | `x 32, y 24, w 100, h 784` | `32 / 24 / 100 × 784` | ✅ |
| nav item | `84 × 84` at `x 40` | `84 × 84` | ✅ |
| group gaps (logo→nav, nav→settings) | `88` / `88` | `> 60` | ✅ |
| heading block | `x 172, y 88, w 390` | `x 172 y 88 w 390` | ✅ |
| card row | `x 172`, bottom `744` | `x 172` / `744` | ✅ |
| widget column | `224px` | reserved, `flex: 1` | ✅ |
| card title ink | `104 / 111 / 99 / 129` in a `162px` box | `104 / 111 / 99 / 129` | ✅ |
| card title/desc/arrow y | `650 / 677 / 695`, identical in all 4 | pairwise identical | ✅ |
| logo | `complete: true, naturalWidth: 24`, no error | decodes | ✅ |

## Round-3 findings — verification

| # | Sev | Claim | Verified how | Status |
|---|---|---|---|---|
| 1 | 🟠 | Card title truncates | Card 2 is `Smart Home` (`id: smart-home-karte`, `targetSectionId: 'smart-home'` untouched), padding still `--space-5`. **0 of 4 titles truncated at all 11 viewports measured.** Widest title is "Wichtige Infos" at 129px in the 162px box — 33px of headroom, and the ratio is scale-invariant (129/162 at 1280 = 103/129 at 1024). **Ellipsis fallback re-probed by injection**: the old `"Haus & Funktionen"` string goes straight back to 167px ink in the 162px box and reports `TRUNCATED`; a deliberately absurd 409px string likewise. `white-space: nowrap` / `overflow: hidden` / `text-overflow: ellipsis` all still resolve on `.action-card__header`. The fallback is intact for future property-authored copy | **Fixed ✅** |
| 2 | 🟠 | `--size-heading` wrong by 2× | `tokens.css` → `--size-heading: 24.375rem`; `--text-display: 3rem` untouched, as instructed. Measured width **390px** at the design size. **Headline wraps to 2 lines at every one of the 11 viewports** — never 1, never 3. Corrected in `docs/design/tokens.md` (Sizing table + a halving-correction note), `docs/design/screens/home.md` (Components entry *and* the Layout table, both flagged as raw-2× values) and REQ-014's width clause | **Fixed ✅** |
| 3 | 🟠 | Sparkle outline + flat navy | `phosphorSparkleFill` + `class="icon-gradient-active"`, registered in `app.config.ts` and in all four affected specs. **Rendered-pixel scan**: build ink `(177, 92, 217)` vs reference `(180, 89, 216)` — within 3/255 per channel — with **0 flat-navy pixels** in a 24 × 24 box. Computed `fill` is `url("#icon-gradient-active") rgb(44,31,74)`, i.e. paint server + the documented fallback | **Fixed ✅** |
| 4 · 7 · 9 | 🟠 / 🟡 | `frontend/CLAUDE.md` rules, instrument, approval gate | All three replacement sentences present, and the approval sequence is now the opt-in the constitution specifies (exact text confirmed *before* writing). Both rules re-verified against the shipped code by exhaustive grep, not spot-check — see the residual note below | **Fixed ✅** (one 🟡 residual) |
| 5 | 🟡 | Node ambients in the app compilation | `tsconfig.app.json` → `exclude` now carries `"src/node-builtins.d.ts"`. `tsc -p tsconfig.app.json --listFiles` no longer lists it; `tsc -p tsconfig.spec.json --listFiles` still does. **Re-probed**: a throwaway `src/app/__probe.ts` importing `node:fs` and calling `process.cwd()` now fails `TS2591` on both (probe deleted) | **Fixed ✅** |
| 6 | 🟡 | `02-design.md` "the shipped copy fits" | Now records that it did **not** fit, that it rendered ellipsized for all of round 3, and how it was resolved. `164px` → `162px` corrected in `02-design.md`, `01-prd.md` → D5 and `action-card.component.css` | **Fixed ✅** |
| 8 | 🟡 | `playwright` unapproved | Resolved at governance level by constitution **v1.3.0**. Verified honest in both directions: `package.json` is **byte-identical to `HEAD`** (the only manifest diff is 48 generated `package-lock.json` lines resolving playwright 1.62.1); no spec imports playwright; `scripts.test` is still plain `ng test`; `tools/measure.mjs` carries no assertion library and always exits 0. `system-context.md`, `02-design.md` and `04-analyze.md` each state the approval **and** that the E2E-framework decision is still open — no doc overstates it in either direction | **Fixed ✅** |
| 12–15 | 🟢 | Doc/token notes | #12 both unreferenced tokens now explain themselves at the definition site (including that *nothing* binds `--gradient-icon-active` to the SVG paint server); #13 `--size-widget-col` removed from `tokens.md`'s Sizing table with a note; #14 `design-sheet.md`'s "Glyph-Box 12 px" corrected; #15 "Airbnb" spelling fixed in `home.md` and `design-sheet.md`, so REQ-008's "matches this document exactly" is now literally true | **Fixed ✅** |
| 10 · 11 · 16 · 17 | 🟢 | Skipped by `dev` | All four skips **defensible** — reasoning below | **Accepted** |

### Beyond the findings list: the `24px` glyph corrections in `home.md`

Not requested by this review; done at the user's instruction. Verified all four
against the code, not against the report:

| Site in `home.md` | Now says | Code | |
|---|---|---|---|
| `MenuIcons` states (l.156) | `--size-icon-md` (1.625rem / 26px CSS, ~52 raw) | `sidebar.component.html` ×2 `size="var(--size-icon-md)"` | ✅ |
| Glass Panel icon block (l.177) | `--size-icon-lg` (2.25rem / 36px CSS, 72 raw) | `action-card.component.html:8` | ✅ |
| Glass Panel action column (l.184) | `--size-icon-sm` (1.5rem / 24px CSS, 48 raw) | `action-card.component.html:16` | ✅ |
| Heading subtitle row (l.226) | `--size-icon-sm` (1.5rem / 24px CSS, 48 raw) | `heading-block.component.html:12` | ✅ |

The warning block under `## Components` is the right shape: it states the
misread, states that `--size-icon-sm` genuinely *is* 24px so the last two lines
"were numerically right for the wrong reason", and closes by naming the **one**
remaining `aspect-[…]`-adjacent claim — the `NEEDS CLARIFICATION` about card
4's second empty `24px` "Info" node (l.219) — as **unverified**. That is the
correct call: confirming it needs a Figma query, which is metered and
forbidden here, so leaving it as a flagged record of what the design file
appeared to show beats silently "correcting" a record. Both the card-2 rename
and card-3's earlier "Umgebung" rename are recorded in `home.md`'s card table
with Figma's disagreement stated and the shipped config named as the authority.

### Are `dev`'s four skips defensible?

- **#10 (mesh Δ)** — yes. Re-measured on an 8 × 6 grid of mean colours: worst
  cell Δ **37**, mean Δ **20** (round 3 measured 40). Unchanged, marginally
  better, not regressed. My own finding said "inherent to approximating a Figma
  mesh with 16 CSS radial layers … not worth chasing unless you want it
  closer". Closing it further means more layers or a raster background — a
  design call, not a fix.
- **#11 (`gradientUnits`)** — yes, and the reasoning is technically right, not
  an excuse. `userSpaceOnUse` requires literal `x1/y1/x2/y2` in user units;
  the two glyphs that carry this gradient are sized from `var(--size-icon-md)`
  and `var(--size-icon-sm)`, both `rem`, both scaling with the viewport — so a
  pinned coordinate pair would be correct at exactly one viewport and wrong at
  every other, which is a straight collision with the constitution's
  viewport-proportional-root constraint. Impact today is nil: both glyphs
  measure square (sparkle **24 × 24**), so the bounding-box distortion is zero,
  and the rendered gradient matches the reference to within 3/255. Correctly
  identified as a decision rather than a cheap fix.
- **#16 (hero artwork)** — yes; the finding was explicitly a *checked, not a
  defect* note that concluded `display: none` is the right shape for the seam.
  There was nothing to do.
- **#17 (1024px knife edge)** — yes; no action was proposed and none is needed.
  Re-measured with margin: at 1024 × 768 the document is exactly 1024 × 768,
  and a 1024 × 1000 landscape variant is also clean.

## Findings

| # | Severity | Category | File | Finding | Fix |
|---|----------|----------|------|---------|-----|
| 1 | 🟡 Minor | Conventions / Docs vs code | `frontend/CLAUDE.md` | **"Nothing else in `src/` may reference a primitive" is true in spirit and false to the letter — and the letter is what the next session will grep.** An exhaustive scan (all 25 layer-1 primitives, every `.ts`/`.html`/`.css` under `src/`, `tokens.css` excluded) returns **three** files, not one: `shell.component.css` (17 `--mesh-*` reads — the carved-out exception ✅), `app.component.ts:14–16` (three `setProperty()` **writes** of `--brand-primary/-deep/-secondary`), and `app.component.spec.ts:24` (a **read**, `getPropertyValue('--brand-primary')`, asserting the branding hook fired). `property.model.ts:8–10` names them in comments. `dev` honestly flagged the first of these; the spec read was not flagged, and it is the harder case, since "writes are a different thing" does not cover it. Substantively both are correct — `applyBranding()` *defines* the primitive layer at runtime, which is exactly what REQ-003 and `docs/design/tokens.md` → Implementation prescribe, and the spec asserts that it did — but this is precisely the failure mode finding #4 existed to prevent: a future session greps, finds the branding hook, and either "fixes" it (breaking REQ-003) or concludes the file's rules are approximate. | **RESOLVED 2026-08-18 (`dev`).** The user approved the exact wording below and it is written: `frontend/CLAUDE.md` → *Structure* bullet 2 now says "may *read* a primitive" and names `app.component.ts`'s `applyBranding()` write + `app.component.spec.ts`'s assertion as the one allowed place, with the REQ-003 rationale. Line-wrapping was the only change to the approved text. The scan was re-run after the edit and the new rule is true to the letter — see the resolution note below. |
| 2 | 🟢 Info | Docs | `03-todo.md`:283, `02-design.md`:802 | Two stale "29 tests" survive (`expected: 10 files / 29 tests`; `the existing 29 tests stayed green`). The real pre-round-3 baseline was **28** — 29 predates the round-1 removal of the redundant keydown test. Both sit in superseded/historical sections and neither was a numbered finding, so this is not a missed fix. Current count is 35. | Correct opportunistically if either section is edited again. |
| 3 | 🟢 Info | Dependencies | `frontend/package.json`, `docs/constitution.md` | `prettier@^3.8.1` is a devDependency that is **not** named on the constitution's approved list — the same gap that finding #8 raised for `playwright`. Not a violation of this round: it is present unchanged at `HEAD`, arrived with the `ng new` scaffold exactly like `vitest`/`jsdom` (which the list *does* name, precisely because they were scaffold-installed), and ships nothing to the client. | Name it in the approved list next time the constitution is amended, for symmetry with how `playwright` was handled. |
| 4 | 🟢 Info | Visual fidelity | `shell.component.css` | Mesh background vs reference, 8 × 6 grid: worst Δ **37**, mean Δ **20**. Round 3 measured worst 40. Carried forward from round-3 Info #10, unchanged. | None. |
| 5 | 🟢 Info | Architecture | `hero-artwork.component.ts` | The component documents the *seam* but not the specific change a future artwork variant needs (`home.md` specifies the artwork as absolutely positioned at `1550 × 999`, so the variant must not become a flex sibling again). Round-1 Info #6 asked for exactly that note; `home.md` carries it, the component does not. | Add one sentence to the existing comment whenever that variant is built. |

## Explicitly cleared (checked this round, not defects)

- **The `--size-heading` change moved layout and nothing regressed
  downstream.** Widget column still present and `flex: 1` (224px at the design
  size, 179px at the floor, 1189px at 2560 × 1080); card row still `x 172`,
  bottom `744`; sidebar box unchanged; the 1024 floor is still a uniform 0.8×
  with no reflow. The two-line headline **collides with nothing** at any
  supported size: heading bottom vs card-row top is `247 / 541` at the floor,
  `309 / 548` at the design size, `801 / 1423` at 3840 × 2160. The gap is
  distributed by `.home-content__screen`'s `space-between`, so it grows rather
  than closing as the viewport changes shape.
- **REQ-025's "every text node that occupies one line at the design size still
  occupies one line"** — the headline is two lines at *every* supported size,
  so the contract holds by consistency, not by exception. All 6 sidebar labels
  are one line at all viewports measured.
- **REQ-023 end-to-end, re-verified.** Gradient stops resolve to
  `rgb(244,90,168)` → `rgb(138,92,246)` = `--brand-primary` → `--brand-secondary`;
  the `url(#…)` fragment survives `<base href="/">` on `/smart-home` as well as
  on `/`; overriding `--brand-primary` to `#00b894` at runtime repaints the
  stop to `rgb(0,184,148)` with no rebuild. Exactly two elements carry
  `.icon-gradient-active` — the active nav glyph and the sparkle — so the
  global rule still does not leak.
- **Security.** No sensitive data logged (the only `console.*` in non-test
  source is `main.ts`'s generic bootstrap `catch`). No new endpoint, handler,
  query or user input. Production `dist/` contains no external URL beyond XML
  namespace URIs and one documentation link inside Angular's own bundle — no
  runtime request leaves the box (constitution principle 4). The paint-server
  `<svg>` is `aria-hidden`, `focusable="false"` and zero-sized; the decorative
  logo carries `alt=""`; the nav carries `aria-label`. Nothing this round
  changes the exposure model, so no `architect` escalation is warranted.
- **Branch discipline** — all work is on `feature/home-screen-prototype`;
  `main` untouched. Rounds 3–5 remain **uncommitted** (32 modified + 7
  untracked paths). That is the state reviewed.
- **Repo left as handed off.** This review's own probes (a throwaway
  `src/app/__probe.ts`, and `.js` files emitted by an early `tsc --listFiles`
  run before `--noEmit` was added) were deleted and `git status` re-checked.

## Proposed `frontend/CLAUDE.md` amendment (finding 1) — WRITTEN 2026-08-18

The constitution requires the exact text of a nested `CLAUDE.md` entry to be
confirmed **before** it is written, so this review did not touch the file.
**The user then approved the text below verbatim, and `dev` wrote it on
2026-08-18** (only line-wrapping adjusted to the file's style; no rewording,
no grammatical connector needed). Finding 1 is closed. The replaced final
sentence of the *Structure* bullet 2 was —

> Nothing else in `src/` may reference a primitive.

— with:

> Nothing else in `src/` may *read* a primitive. **Writing** one is a different
> thing and is allowed in exactly one place: `app.component.ts`'s
> `applyBranding()` sets `--brand-primary`, `--brand-primary-deep` and
> `--brand-secondary` on `:root` at startup, and `app.component.spec.ts`
> asserts that it did. That is the per-property branding hook *defining* the
> primitive layer (REQ-003, `docs/design/tokens.md` → Implementation), not a
> component consuming a raw value.

**Re-verified after writing, which is the whole point of the rule.** The
exhaustive scan was re-run over all 25 layer-1 primitives across every
`.ts`/`.html`/`.css` under `frontend/src/` with `tokens.css` excluded (plus a
sweep for `setProperty` / `getPropertyValue` / `getComputedStyle`, and the one
`.svg` asset, in case a primitive was reached by an API rather than by `var()`).
Result: the only **reads** are `shell.component.css`'s 17 documented
`--mesh-1…16` hits and `app.component.spec.ts:24`; the only **writes** are
`app.component.ts:14–16`. `property.model.ts:8–10` still names three primitives
in end-of-line comments (`// --brand-primary override`) — a comment, neither a
read nor a write, so the rule as worded covers the file as written. No other
file in `src/` touches a primitive at all. Build green; 11 files / 35 tests, 0
failed (unchanged — a Markdown-only edit).

## REQ-ID coverage — round 3 final

| REQ-ID | Verified how (this round, independently) | Status |
|---|---|---|
| REQ-022 (full-height sidebar) | `32 / 24 / 100 × 784` at 1280 × 832, item `84 × 84` = `--size-sidebar` − 2 × `--space-3`, group gaps `88` / `88` > 60; identical composition at all 11 viewports | ✅ |
| REQ-023 (gradient active glyph **+ sparkle**) | Stops = `--brand-primary`/`--brand-secondary`; works on `/` and `/smart-home`; runtime override repaints; inactive glyphs flat `#2c1f4a`; sparkle ink `(177,92,217)` vs reference `(180,89,216)`, 0 flat-navy px; `phosphorSparkleFill` + `.icon-gradient-active` asserted by a unit test | ✅ |
| REQ-024 (nothing scrolls) | 18/18 route × viewport, plus 9 additional viewports up to 3840 × 2160; `overflow: visible` on `html`/`body`; the only `overflow: hidden` in `src/` serves the card title's ellipsis | ✅ |
| REQ-025 (scaling contract) | Uniform scaling verified from 1024 × 768 to 3840 × 2160 and at two off-design aspect ratios (1366 × 768, 2560 × 1080); no reflow, nothing newly wrapped, no breakpoints; guard rail `min-width: 1024px` | ✅ |
| REQ-014 (amended, **390px width clause**) | Heading `x 172, y 88, w 390`, 2 lines; card row bottom `744`; empty middle; widget column reserved `flex: 1` at 224px. Token, `tokens.md`, `home.md` (Components + Layout) and REQ-014 all agree on 390 | ✅ |
| REQ-016 (amended: guard rail 1024) | `global.css` `min-width: 1024px`; below-floor and portrait documented as out of scope | ✅ |
| REQ-007 (no label wraps) | 6/6 labels one line, 0 truncated, at all 11 viewports | ✅ |
| REQ-009 (no title wraps **or ellipsizes**) | **0 / 4 truncated at every viewport** (was 1 / 4 in round 3); title/desc/arrow y pairwise identical across the four cards; ellipsis fallback re-probed and intact | ✅ **now fully met** |
| REQ-010 (artwork occupies nothing) | `:host { display: none }`; card row at `x 172`, no phantom gap | ✅ |
| REQ-011 (logo decodes) | `complete: true`, `naturalWidth: 24`, no error, on every route × viewport; XML well-formedness regression test with a negative control | ✅ |
| REQ-015 (tests green) | 11 files / 35 tests, re-run | ✅ |
| REQ-003 (runtime branding) | `--brand-primary` → `#00b894` repaints the paint-server stop live | ✅ |
| REQ-008 (copy matches `home.md`) | Now matches exactly — `home.md` and `design-sheet.md` carry the corrected **"Airbnb"** spelling, and the card table records the "Smart Home" / "Umgebung" renames as the authority | ✅ **doc gap closed** |
| REQ-001…REQ-021 | Unchanged since round 1 / round 3; re-confirmed green build, strict mode, zoneless config, standalone components, service-only data access, no external requests | ✅ |

**All of REQ-001…REQ-025 are implemented and independently verified.** Two
requirements that were previously ⚠️ — REQ-009's ellipsis deviation and
REQ-008's spelling mismatch — are now fully met.

## Verdict against the constitution's quality bar (v1.3.0)

- Builds and all tests green — ✅ 11 files / 35 tests, re-run.
- Conventions from nested `CLAUDE.md` followed — ✅. The rule that round 3's
  finding #4 said the codebase violated is now carved out correctly and was
  re-verified against the code by exhaustive grep rather than assumed. The one
  🟡 wording residual (finding 1) is **now closed** — approved verbatim and
  written on 2026-08-18, then re-verified by re-running the same exhaustive
  scan against the new wording.
- No sensitive data in logs — ✅.
- `06-review.md` result is PASS with every `REQ-ID` implemented — ✅ both.

**PASS ✅.** Zero Critical, zero Major. Every round-3 Major is fixed and
independently re-measured; the four skipped Infos are defensible on their
merits; the approval gate that finding #9 raised was honoured properly this
time (exact wording confirmed before writing). **The feature is ready to
commit.** The one 🟡 — a nested-`CLAUDE.md` clause — was approved by the user
and written on 2026-08-18, so **no finding from any round remains open**.

### Top 3 recommendations

1. ~~**Approve (or amend) the one-clause `frontend/CLAUDE.md` addition
   above**~~ — **done 2026-08-18**: approved verbatim, written, and the rule
   now survives the grep it invites. **Commit rounds 3–5 together**; nothing
   else is outstanding.
2. **Commit `frontend/tools/measure.mjs` deliberately, and delete the three
   untracked throwaways** (`.measure.js`, `.scroll.js`, `.shot.js`) — they now
   duplicate a committed instrument that does the job better, and leaving them
   invites the next session to reach for the wrong one.
3. **Let the next feature trigger the two open decisions, not this one:** the
   end-to-end framework (the moment anyone wants `measure.mjs`'s numbers to
   *fail* a build) and the 12px-label legibility check at the 1024 floor, which
   the constitution itself records as able to raise the minimum viewport.

---

# Sidebar-rail review — pass 1 (2026-08-29, `reviewer`)

**Result: NEEDS CHANGES ❌** · 🔴 0 · 🟠 5 · 🟡 6 · 🟢 4

> ⚠️ **Reconstructed, not verbatim — read this before quoting anything below.**
> This pass was run by a **different `reviewer` session and its report was never
> written to this file**; the working tree carried no record of it at all, which
> is itself finding #8 of pass 2. The section below was reconstructed on
> 2026-08-29 by the pass-2 reviewer from a **condensed relay** of the findings,
> read against the diffs, `handoff.md` and the code. What is round-1-accurate:
> the **severities, the file:line targets, the counts and the verdict**. What is
> *not*: the wording — every finding text below is the pass-2 reviewer's
> paraphrase, so do not cite it as pass 1's own reasoning. Where pass 2 later
> measured a finding itself, that is recorded in the next section under
> "Pass-1 findings — verification"; where it could not, it says so.
>
> **A second 2026-08-29 review is missing too, and this file cannot reconstruct
> it.** `frontend/tools/measure.mjs` carries two comments citing *"review
> 2026-08-29, **Major 1**"* (the `truncated()` / `labelBoxes` rebuild) and
> *"review 2026-08-29, **Nit 13**"* (the overflow trigger's label being the only
> rail label still measured as `singleLine`). Neither matches #1 or #13 below,
> and that file's mtime (10:54) precedes this pass's fix run (12:22–13:09). So
> an **earlier** 2026-08-29 review exists, is also unrecorded, and its findings
> are not available. Recorded as a gap rather than smoothed over — and it is the
> reason `handoff.md`'s *"the review's 🟡 #12–#14 … are untouched"* does not line
> up with the list below, where #12–#14 are 🟢, not 🟡.

> **Scope:** the four owner-requested UI rounds that landed on the sidebar rail
> *after* the 2026-08-18 PASS above — 2026-08-28 (two label lines reserved per
> item; rail glyph `--size-icon-md` → `--size-icon-sm`; **committed in
> `63fcc6d`**), 2026-08-29 morning (the label box becomes the item's full width;
> the single line centred) and 2026-08-29 afternoon (`--size-sidebar` 6.25rem →
> **6.5rem**, new `--size-sidebar-label-inset`, and variant **B2**: the two-line
> reservation replaced by a `max-height` cap) — against `docs/constitution.md`
> **v1.10.0**, `docs/design/tokens.md`, `docs/design/screens/home.md` and
> `docs/design/screens-inhouse/`.
>
> **The measurement instruments could not be run in this pass.** Without the
> Quarkus backend the frontend boots to `/pairing` and the sidebar renders zero
> items, so `tools/measure.mjs` and `tools/measure-states.mjs` had nothing to
> measure; this pass fell back to a hand-rolled browser harness. That limitation
> is why several findings below are about numbers *recorded as measured* rather
> than about the rendering itself — and it is why finding #5 could be raised at
> all: the 24px glyph's only written justification was arithmetic that nobody
> had ever put in front of a browser.

## Findings

| # | Severity | Category | File | Finding | Fix |
|---|----------|----------|------|---------|-----|
| 1 | 🟠 Major | Docs vs. code | `docs/features/home-screen-prototype/01-prd.md:403` | **REQ-014 still pins the card row's left edge at `x = 172`, which the shipped code deliberately fails** — it measures **176** since `--size-sidebar` went to 6.5rem. REQ-007's box and REQ-022's `w = 100` were both rewritten for exactly this reason in the same session; REQ-014 was left standing, so the PRD now carries one criterion the implementation is knowingly outside of. A criterion the code deliberately fails is a broken criterion, not a deviation. | Rewrite `x = 172` → `x = 176` the way REQ-022's `w = 100` → `w = 104` was rewritten, and keep the *derivation* ("the sidebar plus one `--space-8` gap, with no additional offset"), which is the part that did not change. Record the Figma deviation once, in `docs/design/tokens.md` → Sizing. |
| 2 | 🟠 Major | Impact analysis | `frontend/src/app/layout/staleness-banner/staleness-banner.component.css:24` | **`--size-sidebar` has a third consumer and no document names it.** Every write-up lists `.sidebar` and `.sidebar-overflow__grid`; the escalated staleness banner bounds itself with `max-width: calc(100% - var(--size-sidebar) - var(--space-8) - 2 * var(--space-7))`. First order the bound is not binding, but the **second-order** effect is real and unassessed: the bar is right-anchored and did not move, while Home's headline column slid 4px towards it — i.e. the clearance recorded in `docs/features/guest-payload-consolidation/06-review.md:288–290` (the margin that review itself called *"four and five pixels of luck"*) got thinner, on a **manager-authored** headline. Changing a token without enumerating its consumers is the defect; the outcome happening to be safe is not the fix. | Enumerate the three consumers wherever the widening is recorded, and re-measure the banner-vs-headline clearance in the worst instrument cell (37-character unbroken display name) rather than reasoning about it. |
| 3 | 🟠 Major | As-built record | `docs/design/screens-inhouse/connection.md:118–119, 272–274`, `docs/design/screens-inhouse/info.md:449`, `docs/design/tokens.md:409` | **Four places still carry the pre-widening coordinates.** `connection.md`'s pill-vs-heading separation and its `.heading-block` / `.home-content__screen` / `.home-content__widgets` rows, `info.md`'s content-area left edge, and `tokens.md`'s `--size-widget-col` note ("measured: 224px") were all derived from a 100px rail and were not re-derived. These files are the **as-built** record — their whole value is that they say what the build does — so a stale number here is worse than no number. | Re-measure and update all four, marking which figures are fresh measurements and which are arithmetic on them. |
| 4 | 🟠 Major | Factual accuracy | `docs/features/home-screen-prototype/handoff.md:117, 233`, `docs/features/index.md:8` | **"All three rounds are uncommitted" is false.** The 2026-08-28 round — including the three rail glyphs stepping to `--size-icon-sm` — is **committed in `63fcc6d` ("frontend adjustments")**. That is not a bookkeeping detail: it decides whether putting `--size-icon-md` back is *repairing an uncommitted mistake* or *a new change on top of a commit*, and the two need different records. | Correct all three places and state the consequence explicitly where the glyph decision is written down. |
| 5 | 🟠 Major | Design fidelity | `docs/design/screens/home.md:203–215`, `frontend/src/app/layout/sidebar/sidebar.component.html:18, 39, 90` | **The rail glyph's step down to `--size-icon-sm` is now justified by an argument that no longer holds, and it was never measured.** The one recorded reason was arithmetic: `26 + 4 + 2 × 15.6 = 61.2px` against the *then* 84px item's 60px vertical content box. Both terms have since expired — the item is **88px** (a 64px content box) since `--size-sidebar` went to 6.5rem, and the two-line **reservation** the 24px was paid for became a `max-height` **cap** under variant B2. `--size-icon-md` is the extracted Figma value, so the build is sitting 2px below the design for a reason that is gone. | Owner's call ("measure, then decide"). Measure a 26px glyph in a live rail item at both reference viewports, active and inactive, one- and two-line, before changing anything — and if it holds the item square, put `--size-icon-md` back and close the 2026-08-28 deviation in `home.md` rather than restating it. |
| 6 | 🟡 Minor | Stale comment | `frontend/src/app/layout/sidebar/sidebar.component.css:212, 240` | Two comments still derive the popover track as `--size-sidebar − 2 × --space-3 = **5.25rem**`. The expression is unchanged and correct; the number it resolves to is **5.5rem** since the widening. A comment that states a resolved value has to be re-resolved when the input moves. | `5.25rem` → `5.5rem` in both, noting the previous value and the date. |
| 7 | 🟡 Minor | Consistency | `frontend/src/app/layout/sidebar/sidebar.component.css:245–255` | **The popover tile did not get the rail's two-line `max-height` cap.** The docs say the tile was "checked and deliberately left alone" — but that assessment covered the **centring** only; the cap was never considered for it. The tile carries the same `-webkit-line-clamp: 2`, and a clamp is not load-bearing on an engine that ignores it. | Give `.sidebar-overflow__tile-label` the same `max-height: calc(2 * var(--text-label-line-height) * 1em)`, and measure with the clamp neutralised so the claim is evidence rather than symmetry. |
| 8 | 🟡 Minor | Stated as measured | `docs/design/tokens.md:453`, `docs/design/screens/home.md:232` | **"`Einstellungen` is the longest label in the catalogue" is false.** `Hausordnung` measures wider. The superlative is load-bearing in the write-up — it is the reason `Einstellungen` is presented as *the* worst case — so it must be either measured or dropped. (The **decision** is unaffected: the cell it turned on is the 1024 × 768 floor, where `Einstellungen` active genuinely is the tightest.) | Correct the superlative wherever it was written, keep the decision, and state per viewport which label is actually tightest. |
| 9 | 🟡 Minor | In-place correction incomplete | `docs/features/home-screen-prototype/handoff.md:166, 170, 171, 173, 174` | The afternoon section opens with an explicit promise that every statement it overtook was "corrected in place, dated and pointed here, rather than left standing". Five figures in the morning section were left standing — the label box, two `in 84` box references, the icon top and the single-line ink pair — and one of them (*"icon top inside the item … unchanged"*) was already wrong when it was written. A handoff that promises in-place correction and does not deliver it is worse than one that promises nothing. | Annotate all five in place, dated, with re-measured values. |
| 10 | 🟡 Minor | Counting error | `docs/design/tokens.md:544` | "The other **six** are colourless and identical in both themes" — adding `--size-sidebar-label-inset` to the in-house table makes it **seven**. Same failure mode the constitution itself records twice (v1.6.1 (c), v1.7.1): a thing is added and the sentence counting the things is not touched. | Six → seven; also check the "nine rows / ten rows" sentence in the same section. |
| 11 | 🟡 Minor | Attribution | `docs/design/tokens.md`, `docs/features/home-screen-prototype/01-prd.md` → REQ-014, `02-design.md` | **"The content area is 4px narrower" is true of `.shell__content` and misleading about everything inside it** — and the owner rejected `+0.5rem` as *"too expensive for the content area"* on precisely that reading. The card row is sized by its four cards and the heading column by `--size-heading`; the only thing that can absorb the difference is the `flex: 1` widget column REQ-014 requires to exist **and to be empty**. | Measure both `--size-sidebar` values in one live render and record where the 4px actually lands. Record it as a fact about the cost — **not** as an argument to reopen a decision the owner took. |
| 12 | 🟢 Info | Instrument | `frontend/tools/measure.mjs` | The border-box switch and the new `labelBoxes` invariant are well built: the instrument now asks both halves of the question (does the ink leave the box that paints it; is the label still the size it should be relative to its item), and the expected delta is derived from `--size-sidebar-label-inset` rather than hard-coded. Two reading notes were attached for whoever touches it next. |
| 13 | 🟢 Info | Robustness | `frontend/src/app/layout/sidebar/sidebar.component.css` → `.sidebar-item__label` | The two-line cap is **exactly** two line boxes — swept 1024 → 3840, the slack between `max-height` and the rendered two-line height is **0.001–0.03px**. It holds at every size, and it holds with nothing to spare: any future change to `--text-label-line-height`'s rounding would clip the second line rather than degrade gracefully. Recorded, not a defect. |
| 14 | 🟢 Info | Usability | `frontend/src/styles/tokens.css` → `--size-sidebar-label-inset` | At the 1024 × 768 floor an ExtraBold `Einstellungen` has **1.41px** of total clearance per side. It fits and is not clipped, and the owner chose this explicitly over spending another 0.25rem of content width. Recorded so the next person does not read it as an accident. |
| 15 | 🟢 Info | Documentation decision | `docs/design/screens/home.md` → Layout | Not overwriting the reference-PNG edge-scan table, and instead putting the built app's numbers beside it as a dated deviation, was the right call: that table's value is that it says what the *reference render* contains, and a file that silently becomes a mixture of "what Figma has" and "what we shipped" stops being usable for either question. |

## Disposition

`dev` worked findings **#1–#11 and #15** in the fix run of 2026-08-29
(12:22–13:09) and left **#12–#14** open on purpose — all three are 🟢 Info and
none asks for a change. That fix run is what "Sidebar-rail review — pass 2"
below verifies. Note the numbering caveat in the reconstruction warning above:
`handoff.md` describes the deliberately-skipped items as "🟡 #12–#14", which
does not match this list; the most likely explanation is the missing earlier
2026-08-29 review, whose numbering `measure.mjs` also cites.

---

# Sidebar-rail review — pass 2 (2026-08-29, `reviewer`)

**Result: NEEDS CHANGES ❌** · 🔴 0 · 🟠 3 · 🟡 5 · 🟢 2 — verification of the
`dev` run that worked pass 1's #1–#11 and #15. **Pass 1's Majors are all
closed**; three *new* 🟠 are open, one of them a measured deviation from
`docs/design/screens/home.md` that the widening caused and that nobody had
looked for.

> **Scope:** the 13 uncommitted files on
> `refactor/bce-subpackages-and-boundary-security` (~1350 insertions), against
> `docs/constitution.md` **v1.10.0**, `frontend/CLAUDE.md`,
> `docs/design/tokens.md`, `docs/design/screens/home.md` and
> `docs/design/screens-inhouse/`. Two questions on top of "are pass 1's findings
> closed": did the 26px glyph break anything, and is the popover tile's new
> `max-height` cap — code that pass 1 only *proposed*, and that has never been
> reviewed — correct.
>
> **The instruments really ran this time, and nothing below is taken from the
> implementation report.** `./mvnw quarkus:dev` (Quarkus 3.33.3.1, Dev Services
> PostgreSQL 17.11) on :8080 behind the existing `ng serve --port 4300`, so
> `node tools/measure.mjs` and `GXP_SMOKE_ADMIN_PW=… node
> tools/measure-states.mjs` both ran end to end against a live instance.
> Everything else was measured with four throwaway Playwright probes written to
> `/tmp` and deleted afterwards — **never into the repo**; `git status
> --untracked-files=all` reports **0 untracked files**, which also verifies
> `dev`'s claim that his own probes were not left behind. The Quarkus process
> this pass started was stopped again; the `ng serve` (pid 5025, running since
> 12:20) was **not** started by this pass and was left as found.

## Verification performed (independent)

```
npm test        → Test Files 37 passed (37) · Tests 333 passed (333) · 0 failed
npm run build   → green. Initial total 476.94 kB / 108.44 kB transfer.
node tools/measure.mjs (seeded)
                → 168 route x theme x language x viewport combinations,
                  0 overflowing, 0 truncated, 0 clamped, 200 x NO-SCROLL OK,
                  0 findings
node tools/measure-states.mjs
                → all cells NO-SCROLL OK, no COLLISION in any banner cell,
                  4 findings — all of them `SPEC: artwork` (see finding #1)
```

**The 26px glyph, measured over 32 cells** — 1280 × 832 and 1024 × 768 ×
light/dark × de/en × active/inactive × one-line/two-line label, with the label
text injected into a live rail item:

| | 1280 × 832 | 1024 × 768 |
|---|---|---|
| item box, every one of the 32 cells | **88 × 88**, Δ 0.00 | **70.41 × 70.41**, Δ 0.00 |
| rendered glyph box | 26 × 26 | 20.8 × 20.8 |
| label box (item − 2 × inset) | 86 (Δ −2.00) | 68.81 (Δ −1.59) |
| glyph top in the item, one line / two | 21.20 / 13.41 | 16.97 / 10.73 |
| ink bottom above the item's bottom, one / two | 21.80 / 14.00 | 17.45 / 11.22 |
| pair offset from the item's centre | 0.295 px | 0.24 px |

Every figure `home.md`, `01-prd.md`, `02-design.md` and `handoff.md` record for
the 26px glyph is reproduced exactly. The arithmetic the 24px was bought with is
genuinely dead: at an 88px item the content box is 64px and `26 + 4 + 2 × 15.6 =
61.2 ≤ 64` (`20.8 + 3.2 + 2 × 12.48 = 48.96 ≤ 51.2` at the floor).

**Live geometry, from the grid run:** sidebar `x32 w104`, heading block
`x176 y88 w390 right566`, card row `x176 y548 w820 right996 bottom744`,
status-bar clearance **48** horizontal / **20** vertical, popover panel
**490 × 208** / **392 × 167**, tiles 88 / 70, grid does not scroll internally,
`heading.y 88` and `cardRow.bottom 744` unmoved.

**The 4px A/B, both `--size-sidebar` values compared in one live render:**
`.shell__content` 1076 → **1072** (860.81 → **857.63**),
`.home-content__screen` **820 → 820** (655.97 → 655.97), `--size-heading`
**390 → 390**, `.home-content__widgets` **224 → 220** (179.25 → **176.06**).
Confirms finding #11's attribution — and turned up the one element nobody
listed, which is finding #1 below.

**The popover tile's new cap, treated as fresh code.** With
`-webkit-line-clamp` neutralised on a five-line label and the `max-height`
removed, the tile leaves the square raster (88 → **135.56** at 1280, 70.39 →
**108.39** at 1024) — and so does the **rail item** (88 → **131.97**, 70.41 →
**105.52**). With the cap in place both stay square in both layout models. With
the clamp armed (Chromium 151 computes the label's `display` to `flow-root`) the
cap is inert and changes nothing rendered: across 9 tiles × 2 viewports × 2
languages, `scrollHeight === clientHeight` in every cell, label heights exactly
15.59 / 31.19 and 12.47 / 24.94, ink inside its box, tiles 88 / 70.39.
**Visually confirmed** on the open popover at 1280 × 832 — four two-line labels
render in full, descenders intact, nothing clipped.

**Screenshots taken and looked at** (`code-review` step 0, which this file's own
round-3 section records as the check that pays for itself): Home at 1280 × 832
light/de, Home at 1024 × 768 dark/de, and the open overflow popover. The rail
reads correctly at both sizes — glyphs visibly at 26px, every label on one line,
pairs optically centred, `Einstellungen` tight but clear of the pill at the
floor, which is what the owner accepted.

## Pass-1 findings — verification

| Pass-1 # | Status | How verified in this pass |
|---|---|---|
| #1 REQ-014 `x = 172` | ✅ **closed** | `01-prd.md` REQ-014 now reads `x = 176` with a dated owner-decision note that keeps the derivation. Live: card row `x176`. |
| #2 third `--size-sidebar` consumer | ✅ **closed** | The staleness banner is now named in `tokens.md`, `02-design.md` and `handoff.md`. Bound re-derived: 1076 → 1072 at 1280, 860.8 → 857.6 at 1024, against a bar ≤ 694px — not binding. `measure-states.mjs`: **no COLLISION in any banner cell**. |
| #3 stale as-built coordinates | ✅ **closed** | All four re-derived and reproduced live: heading `x140.78…452.78`, screen `x140.78…796.75`, widgets `x822.34 w176.06`, content edge `x176`, `--size-widget-col` note now 220. |
| #4 "all three rounds uncommitted" | ✅ **closed** | `git show --stat 63fcc6d` confirms the 2026-08-28 round in full, and `git show 63fcc6d:…/sidebar.component.html` confirms the three rail glyphs at `--size-icon-sm` with the popover tile at `md`. All three places corrected. |
| #5 the 24px glyph | ✅ **closed** | Reverted on lines 18/39/90; line 70 (the tile) untouched. Squareness re-measured over 32 cells, Δ 0.00 everywhere — see the table above. |
| #6 `5.25rem` comments | ✅ **closed** | `sidebar.component.css:213, 241` now read `5.5rem` with the previous value and date. |
| #7 tile without the cap | ✅ **closed** — and reviewed as new code | Cap present at `sidebar.component.css:265`; measured load-bearing and rendered-inert (above). Its *stated reason* is wrong — see finding #3. |
| #8 the "longest label" superlative | ⚠️ **mostly closed** | Corrected in `tokens.md:457` and `home.md:259`, and the numbers are real measurements, reproduced exactly: `Einstellungen` 77/77 → 4.5/4.5 @1280 and 63/66 → 2.91/**1.41** @1024; `Hausordnung` 78/79 → 4.0/3.5 and 64/64 → 2.41. **Two sites were missed** — see finding #7. |
| #9 in-place corrections | ✅ **closed** (spot-checked) | The five annotations are present at `handoff.md:166–174`. Their *values* were re-derived above; the annotations themselves were read, not re-litigated line by line. |
| #10 "six colourless" | ✅ **closed** | `tokens.md:603` reads "The other **seven**", and the "nine rows" sentence became "ten rows". |
| #11 4px attribution | ⚠️ **closed but incomplete** | The A/B reproduces every published number exactly. It omits the one element that also moved — finding #1. |
| #12–#14 (🟢) | **open by design** | Not in the fix run's brief; none asks for a change. Re-confirmed #13 is still exactly two line boxes and #14 still 1.41px at the floor. |
| #15 (🟢) | ✅ **honoured** | `home.md`'s reference-PNG table is untouched ("Every number above stays as written") and the built app's figures sit beside it as a dated deviation. |

## Findings

| # | Severity | Category | File | Finding | Fix |
|---|----------|----------|------|---------|-----|
| 1 | 🟠 Major | Design fidelity / Impact analysis | `frontend/src/app/features/home/hero-artwork/hero-artwork.component.ts:47` (and `:32`) | **The hero artwork silently left its specified box, and it is the one element the widening's impact analysis missed.** `left: 14.03125rem` is a **literal derived from the old content origin** (`396.5 − 172`); the component's own comment at line 32 names the 172. It is the only place in the layout that consumes `--size-sidebar`'s *result* instead of the token, so it translated with the content column instead of staying put. Measured by A/B in one live render: **x 396.5 → 400.5** at 1280 × 832 and **317.19 → 320.38** at 1024 × 768, against `docs/design/screens/home.md:431`'s `775 × 499.5 @ x396.5 / y77.5`. `home.md`'s new deviation table (`:110–127`) lists sidebar width, active pill, heading/card-row x, card-row right edge and the untouched vertical baseline — and omits this. All the right-anchored overlays (status bar, staleness pill, staleness banner) genuinely did not move; this one did. | Owner's call, and it is a one-line choice: **derive it** (`left: calc(24.78125rem - var(--size-sidebar))`, which is the same "derived, not literal" rule `sidebar.component.css` states twice) **or** record the 4px as a deviation in `home.md` → "Hero artwork (2:5)" and in the deviation table beside every other element that moved. Deriving is preferable — it makes the next `--size-sidebar` change free. |
| 2 | 🟠 Major | Verification / Record | `docs/features/home-screen-prototype/handoff.md` (CURRENT STATE), `docs/features/index.md:8` | **`measure-states.mjs`'s four `SPEC: artwork` findings were reported to `lead` as "the pre-existing SPEC finding", and they are not pre-existing.** `docs/features/guest-frontend-integration/handoff.md:407` records the artwork at `{x:397, y:78, w:775, h:500}` with the content column at **172** — the +4 / +3 delta is exactly this widening. Worse, the durable record does not carry them at all: `handoff.md` does not mention them, and `index.md:8` states "`tools/measure-states.mjs` **clean**". An instrument finding dismissed as pre-existing is how a regression becomes permanent noise that every future review learns to scroll past. | Record the four findings against the widening; correct `index.md`. If #1 is fixed by deriving the offset they disappear on their own — which is the cleanest possible confirmation that the diagnosis was right. |
| 3 | 🟠 Major | Correctness of a load-bearing rationale | `frontend/src/app/layout/sidebar/sidebar.component.css:90` and `:256–258`; `docs/design/screens-inhouse/menu-overflow.md:191` | **All three say the rail item's height is held by `width: 100%` + `aspect-ratio: 1` alone, and that the tile — "unlike the rail item" — is not. Measured, that is false for both.** With the clamp neutralised and the cap removed the **rail item** goes 88 → **131.97** at 1280 and 70.41 → **105.52** at 1024, the same failure as the tile (88 → 135.56 / 70.39 → 108.39). `aspect-ratio` does not pin a flex item whose content overflows; the `max-height` cap does, for both. The contradiction is *inside the same file*: `sidebar.component.css:132–133` records the rail item going 70.41 → 103.91 without the cap, 130 lines below the comment saying it cannot. The code is correct — the reason written next to it is not, and it is the reason the next person will trust when deciding the rail's cap is redundant. That reintroduces D5 on any engine that ignores `-webkit-line-clamp`, which the same file argues at length must not be assumed away. | Pull all three onto "the item's `aspect-ratio` **plus** the two-line cap, and the same for the tile" — the wording `01-prd.md:351` and `02-design.md:557` already get right. |
| 4 | 🟡 Minor | Docs vs. code (the 26px drift) | `docs/features/home-screen-prototype/01-prd.md:284` | "Nothing else in the item wants that padding: the glyph is centred and **24px** wide." The word-for-word twin of this sentence in `sidebar.component.css:125` **was** corrected to 26px in this fix run; the REQ-007 copy was not. Exactly the drift class this fix run existed to close, in a requirement document. | 24px → 26px. |
| 5 | 🟡 Minor | Instrument | `frontend/tools/measure.mjs:312–329, 430–455` | **The border-box switch opens a 2px blind spot the size of the inset it was made for.** `truncated(el, boxEl, borderBox=true)` compares the rail label's ink against the **item's** border box (88 / 70.41), while the box carrying `overflow: hidden` is the **label** (86 / 68.81); the fallback parent-walk starts at `el.parentElement`, so the label's own clipping is never considered. Ink between 86 and 88 is clipped on screen and reported `truncated: false`. `labelBoxes` correctly guards label-vs-item, so the residual is exactly `2 × --size-sidebar-label-inset` — narrow, and precisely the dimension the instrument was rebuilt to be able to see. *(This file's mtime is 10:54, so it predates the fix run and belongs to the earlier uncommitted round; raised because it is part of what the owner is about to commit.)* | Compare the ink against the **label's** border box and keep `labelBoxes` for the label-vs-item half — then both questions are asked and neither is approximated. |
| 6 | 🟡 Minor | Registry honesty | `docs/features/index.md:8` | "`tools/measure.mjs` 168 cells clean and `tools/measure-states.mjs` clean" — the first drops the run-2 caveat that `handoff.md:51–62` carries carefully, the second is false (see #2). The registry is where anyone starts; it should never read as more settled than the handoff it summarises. | Align both statements with `handoff.md`. |
| 7 | 🟡 Minor | Superlative correction incomplete | `docs/features/home-screen-prototype/02-design.md:544`, `docs/features/home-screen-prototype/handoff.md:170–172` | Pass-1 #8 was fixed in `tokens.md` and `home.md`, and `handoff.md` claims the superlative "is corrected wherever it was written". Two sites still assert it: `02-design.md:544` ("The reference's own longest label (\"Einstellungen\") measures **~84px**" — measured, it is **77**, and `Hausordnung` is 78/79) and `handoff.md:170–172` ("`Einstellungen` — the longest seeded label"), the latter in a section whose own opening promises in-place correction. Mitigating: the `02-design.md` paragraph is explicitly superseded two lines below it. | Correct both, or soften the "wherever it was written" claim to what was actually done. |
| 8 | 🟡 Minor | Process / quality bar | `docs/features/home-screen-prototype/06-review.md` (this file, before this pass) | **Neither 2026-08-29 review existed in the repo.** This file recorded only the 2026-08-18 PASS while four UI rounds and at least two NEEDS CHANGES verdicts had landed, and `index.md` still opened with `06_review PASS ✅`. The constitution measures "done" against this artifact, so it was being read against a stale one — and pass 2 could not check pass 1's findings against their source, only against a relay, the diffs and `handoff.md`. **This is reviewer debt, not `dev`'s.** Partially repaired by this pass (both 2026-08-29 passes are now written down); the earlier 2026-08-29 review that `measure.mjs` cites is **not recoverable** and is recorded as a gap. | Write the review result at the end of every review, not only when it is a PASS. |
| 9 | 🟢 Info | Measurement hygiene | `docs/features/home-screen-prototype/handoff.md:51–62` | **Recording run 2's 48 `PREFERENCE NOT APPLIED` + 7 `CONTRAST OFF SPEC` as instrument flakiness, instead of re-running until green, was the right call.** This pass's own independent grid came back **168 cells / 0 findings** with the token seeded, which corroborates the reading; the failing check is the preference *write* path timing out at 10s on a Pi carrying Quarkus dev mode, `ng serve` and Chromium at once, and geometry, wrapping and no-scroll were identical in all three of `dev`'s runs. The honest thing was done and it should stay done that way. |
| 10 | 🟢 Info | Scope | — | The fix run touched **11** files (mtimes 12:22–13:09): `sidebar.component.html`, `sidebar.component.css` and nine documents, all traceable to enumerated pass-1 findings. `frontend/src/styles/tokens.css` (10:50) and `frontend/tools/measure.mjs` (10:54) belong to the earlier uncommitted round — the "13 files" figure `dev` self-reported against the five-file limit conflates two runs. **No unrequested work was found**, and the throwaway probes are verifiably gone (`git status --untracked-files=all`: 0 untracked). |

> ⚠️ **Superseding note, added 2026-08-29 by pass 3 (finding #3) — the text
> above is unchanged and stays as the record.** Finding **#1**'s Fix column
> recommends `left: calc(24.78125rem - var(--size-sidebar))`. **Do not apply it
> literally: it is 72px off spec** at 1280 × 832 (artwork at x 468.5 instead
> of 396.5) and 57.6px at the 1024 × 768 floor — the error is a `rem`
> quantity, so it scales with the root. It subtracts the sidebar but not the
> shell's inline padding and row gap (`--space-7` 32 + `--space-8` 40 = 72).
> The **shipped and verified** expression is
> `left: calc(24.78125rem - var(--space-7) - var(--size-sidebar) - var(--space-8))`
> (with `top: calc(4.84375rem - var(--space-6))`), measured at x 396.5 / 317.17
> and unmoved — Δ 0.000px — under all ten token mutations pass 3 applied.
> The *substance* of finding #1 stands: deriving instead of keeping a literal
> was right, and it is what shipped.

## Explicitly cleared (checked this pass, not defects)

- **The two-layout-model claim in `02-design.md` is right.** With
  `-webkit-line-clamp` set, Chromium 151 computes the label's `display` to
  `flow-root`; with the clamp removed the same declarations compute to a real
  legacy `-webkit-box`. Verified in both models. The conclusion that neither
  `align-content` nor `-webkit-box-pack` can matter under B2 (there is no free
  space inside the label box to distribute) holds in both.
- **B2's stated price is real and is the one that was accepted.** A one-line and
  a two-line item no longer share an icon line; the open popover shows it
  directly. Recorded in `01-prd.md` → REQ-007 as an owner decision with the
  alternative (B1) named and rejected — which is the right shape for it.
- **`--size-sidebar-label-inset` is not an approval-gate breach.** It is a new
  *token*, not a change to how tokens are realised in code (constitution →
  Decisions Requiring Your Approval), it sits in the in-house section with
  provenance, and its row says plainly that nothing corroborates the 1px — it
  is the owner's choice. That is the honest form.
- **Constitution constraints, re-checked against the diff:** no
  dependency-manifest edit; no raw hex or px introduced outside the token layer;
  no external runtime request; `rem` throughout; **nothing scrolls** (200 ×
  NO-SCROLL OK, and the popover grid does not scroll internally at either
  reference viewport); work is on a feature branch, not `main`. No new
  cross-cutting convention was introduced, so no nested `CLAUDE.md` entry was
  owed.
- **No unit test was owed for this change.** `frontend/CLAUDE.md` → Tests puts
  geometry and wrapping explicitly out of jsdom's reach and names
  `tools/measure.mjs` as the instrument. 333/333 in 37 files is unchanged, as it
  should be for a pure-geometry change.

## REQ-ID coverage — sidebar-rail passes

Only three criteria were touched by the four UI rounds; the rest of
REQ-001…REQ-025 is unchanged since the 2026-08-18 verification above and was not
re-litigated here.

| REQ-ID | Verified how | Status |
|---|---|---|
| REQ-007 (rail label within its item, at most two lines, pair centred, item square) | 32-cell live probe: item 88 × 88 / 70.41 × 70.41 with Δ 0.00 in every cell; label box 86 / 68.81; nothing clamped; `measure.mjs` 168 cells, 0 truncated, 0 clamped | ✅ |
| REQ-014 (content column geometry) | Live: heading `x176 y88 w390`, card row `x176 w820 right996 bottom744`, widget column present, `flex: 1`, empty, 220px | ✅ against the **rewritten** criterion — and see finding #1, which is the one element inside this screen still measured against the *old* origin |
| REQ-022 (sidebar box, item square, group gaps) | Live: sidebar `x32 y24 w104 h784`; item width = `--size-sidebar` − 2 × `--space-3` = 88 / 70.41, square | ✅ against the rewritten criterion |

## Verdict against the constitution's quality bar (v1.10.0)

- Builds and all tests green — ✅ 37 files / 333 tests, `ng build` green, both
  re-run independently.
- Conventions from nested `CLAUDE.md` files followed — ✅ for
  `frontend/CLAUDE.md`'s token, layout and testing rules. Finding #1 is the one
  place the codebase's own *"derived, not tokenised — keeps one source of
  truth"* convention is not honoured, and that is exactly what broke.
- No sensitive data in logs — ✅, nothing in scope touches content.
- `06-review.md` result is PASS with every `REQ-ID` implemented — **not yet.**
  Every REQ-ID is implemented and independently re-measured, but this pass's
  result is **NEEDS CHANGES** on three 🟠.

**NEEDS CHANGES ❌.** Zero Critical. The 26px revert and the popover tile's cap
are both correct and should stay: the item is exactly square with a 26px glyph
in all 32 cells, and the cap is load-bearing in one layout model and inert in
the other. What blocks the commit is one measured, unrecorded deviation from the
design spec (#1), the mis-triage that hid it (#2), and a load-bearing guard
documented with a reason its own file disproves (#3).

**The working tree is uncommitted and stays that way** — 13 modified files, 0
untracked, on `refactor/bce-subpackages-and-boundary-security`. The owner
commits; no agent does.

### Top 3 recommendations

1. **Decide the hero artwork (#1) before committing** — derive `left` from
   `--size-sidebar` (preferred: it makes the next rail change free and it is the
   convention this codebase states twice) or record the 4px as a deviation in
   `home.md`. Either way, correct the "pre-existing" triage in `handoff.md` and
   the "clean" claim in `index.md` (#2). Deriving it also makes
   `measure-states.mjs` go quiet on its own, which is the cheapest possible
   confirmation the diagnosis was right.
2. **Fix the rationale on the two caps (#3)** — three sentences, no code change.
   This is the finding most likely to cost something later: it is the argument
   someone will use to delete the rail's cap.
3. **Sweep the four remaining text drifts (#4, #6, #7) and keep writing review
   results down (#8).** They are minutes of work each, and #8 is the reason this
   pass had to reconstruct its predecessor from a relay instead of reading it.

---

# Sidebar-rail review — pass 3 (2026-08-29, `reviewer`)

**Result: PASS ✅** · 🔴 0 · 🟠 0 · 🟡 4 · 🟢 3 — final verification of the
`dev` run that worked pass 2's #1–#7. **All three of pass 2's 🟠 Majors are
closed and independently re-measured.** Everything left is a wrong *number* or
a wrong *attribution* in a write-up whose underlying change is correct; none of
it blocks the commit.

> **Scope:** the 15 uncommitted files on
> `refactor/bce-subpackages-and-boundary-security` (~1912 insertions), against
> `docs/constitution.md` **v1.10.0**, `frontend/CLAUDE.md`,
> `docs/design/tokens.md`, `docs/design/screens/home.md` and
> `docs/design/screens-inhouse/`. Three questions on top of "are pass 2's
> findings closed": is the hero-artwork **derivation** — never-reviewed code at
> a place nobody had previously found faulty — correct *and* robust to the
> tokens it derives from; is the instrument fix right in **both** directions
> (a false positive noises up every future run, a false negative is the blind
> spot again); and does the record still say the same thing in all three places
> after a third documentation pass.
>
> **Both instruments ran end to end, and nothing below is taken from the
> implementation report.** `./mvnw quarkus:dev` (Quarkus 3.33.3.1, Dev Services
> PostgreSQL 17.11) on :8080 behind the `ng serve --port 4300` that was already
> running (pid 5025, since 12:20 — **not** started by this pass and left as
> found). The Quarkus process this pass started was stopped again. Four
> throwaway Playwright probes were written to `/tmp` and deleted; `git status
> --untracked-files=all` reports **0 untracked**.

## Verification performed (independent)

```
npm run build   → green. Initial total 477.05 kB / 108.58 kB transfer.
npm test        → Test Files 37 passed (37) · Tests 333 passed (333) · 0 failed
GXP_MEASURE_DEVICE_TOKEN=… node tools/measure.mjs
                → 168 route x theme x language x viewport combinations,
                  0 overflowing, 0 truncated, 0 clamped, 0 LABEL BOX,
                  200 x NO-SCROLL OK, 0 findings
GXP_SMOKE_ADMIN_PW=… node tools/measure-states.mjs http://localhost:4300
                → 52 cells NO-SCROLL OK, 9 blocks, no COLLISION,
                  0 findings — the four `SPEC: artwork` findings are gone
```

Every figure `dev` reported reproduces exactly, including the two bundle sizes.

**The hero-artwork derivation, treated as fresh code (finding #1's fix).**
Measured on the live render, and then re-measured with each token the
expression consumes mutated at runtime:

| | 1280 × 832 | 1024 × 768 |
|---|---|---|
| artwork host box | `x 396.5, y 77.5, w 775, h 499.5` | `x 317.17, y 61.98, w 620, h 399.59` |
| spec (`home.md`:448) and its 0.8× | `396.5 / 77.5 / 775 / 499.5` | `317.2 / 62.0 / 620 / 399.6` |
| computed `left` / `top` | `220.5px` / `53.5px` | `176.4px` / `42.8px` |
| `.home-content` origin | `x 176, y 24` | `x 140.78, y 19.19` |
| `--size-sidebar` → 9rem / 4rem | Δx **0.000** / **0.000** | Δx **0.000** / **0.000** |
| `--space-7` → 3.5rem | Δx **0.000** | Δx **0.000** |
| `--space-8` → 0.5rem | Δx **0.000** | Δx **0.000** |
| `--space-6` → 3rem | Δy **0.000** | Δy **0.000** |

In every one of those ten mutations `.home-content` moved (by 40 / −40 / 24 /
−32 / 24px at the design size) and **the artwork did not**. That is the whole
point of the derivation and it holds. The decomposition is correct against the
source: `.shell` is `padding: var(--space-6) var(--space-7)` with
`gap: var(--space-8)`, `.sidebar` is `width: var(--size-sidebar)`,
`.shell__content` is `flex: 1` with no padding, and `.home-content` is
`width/height: 100%` with no padding — so the containing block's padding-box
origin *is* `--space-7 + --size-sidebar + --space-8` horizontally and
`--space-6` vertically. `396.5 − 32 − 104 − 40 = 220.5`, exactly as the
component's comment says.

**Visual check (`code-review` step 0), done for real.** Home at 1280 × 832
light/de and 1024 × 768 dark/de: rail glyphs visibly at 26px, all six labels on
one line, `Einstellungen` tight but clear of the pill at the floor, card row
bottom-aligned, nothing clipped. Then the artwork layer itself, with a
stand-in image at the real 1550 × 999 aspect injected into the live host: the
box renders at `x396.5 … 1171.5 / y77.5 … 577`, **behind** the headline and
**behind** the card row (`z-index: -1` resolving as documented), and a uniform
0.8× of the same composition at 1024 × 768. `measure-states.mjs`'s ARTWORK cell
independently reports the *real* uploaded image at `{x:397, y:78, w:775, h:500}`
and `{x:317, y:62, w:620, h:400}`.

**The instrument fix, both directions (finding #5's fix).** Measured in the
live rail, with the label's `overflow-wrap: anywhere` neutralised (the
regression the check exists to catch is a future change losing it):

| case | ink | label box | item box | old cmp (item border) | new cmp (label border) | actually clipped |
|---|---|---|---|---|---|---|
| `Einstellungen..` @1280 | 87 | 86 | 88 | **false** | **true** | yes (`scrollWidth 87 > clientWidth 86`) |
| `Einstellungen.` @1024 | 70 | 68.81 | 70.41 | **false** | **true** | yes (`70 > 69`) |
| `Einstellungen.` @1280 | 82 | 86 | 88 | false | **false** | no |
| `Hausordnung` @1280 | 79 | 86 | 88 | false | **false** | no |
| `Einstellungenxx` @1280 | 91 | 86 | 88 | true | true | yes |

Both of `dev`'s constructed cases reproduce exactly. **No false positive
anywhere**: the six shipped labels measure 35 / 23 / 65 / 72 / 29 / 77 px of ink
in an 86px box at 1280 and 28 / 19 / 52 / 59 / 25 / 63 in 68.81 at 1024, and the
full grid returns **0 truncated across 168 cells**. The label carries no padding
and no border (`0px` on all four sides, measured), so `borderWidth()` *is* the
box that `overflow: hidden` clips against — the right box, not merely a closer
one. The other half of the question is genuinely asked: forcing a label to
120px produces `delta 32` / `delta 49.59` against an `expected −2`, i.e. the
`LABEL BOX` note fires, so Major 1's original defect cannot come back unseen.

**The two cap measurements — both are right, and the explanation for the
difference is exactly correct.** Re-measured on the rail item and the popover
tile with the clamp neutralised and each cap removed, at several line counts:

| lines | rail item @1280 | rail item @1024 | tile @1280 | tile @1024 |
|---|---|---|---|---|
| 3 | 100.78 | 80.58 | 88.78 | 70.98 |
| 4 | 116.38 | 93.05 | — | — |
| 5 | **131.97** | **105.52** | 119.97 | 95.92 |
| 6 | 147.56 | **117.98** | **135.56** | **108.39** |

Per-line increments are 15.595 (1280) and 12.47 (1024) in both boxes. So pass
2's rail figures were a **5-line** string; `dev`'s were a string that takes 5
lines at 1280 and **6** at 1024 — and his tile figures (135.56 / 108.39) are
that same string at 6 lines in the narrower 65 / 52 px tile label box. All four
of his numbers come from one string and are mutually consistent; pass 2's
105.52 is also correct, for its own. **Neither measurement is wrong**, the
string-length explanation carries, and `sidebar.component.css`:95–98 states it
honestly. The conclusion — `aspect-ratio` does not pin either box, both need
the cap — is unaffected.

**Pass-1 🟢 #13 and #14 re-measured and still exactly true.** The two-line cap
is still exactly two line boxes (label height `31.19` = 2 × 15.595 at 1280,
`24.95` = 2 × 12.47 at 1024, zero slack); `Einstellungen` active at the floor is
still 66px of ink in a 68.81px box = **1.405px** of clearance per side.

## Pass-2 findings — verification

| Pass-2 # | Status | How verified in this pass |
|---|---|---|
| #1 🟠 hero artwork off spec | ✅ **closed** | Derived from the token layer. Artwork at `396.5 / 77.5` and `317.17 / 61.98`, i.e. the spec box and its 0.8×; **Δ 0.000 under all ten token mutations**; `measure-states.mjs` ARTWORK cell clean at both viewports; visually confirmed behind headline and cards. |
| #2 🟠 "pre-existing" mis-triage + `index.md` "clean" | ✅ **closed** | `handoff.md` §2 and `index.md:8` both name the four `SPEC: artwork` findings, their origin (the widening) and the correction; `index.md` explicitly retracts its own "clean". Re-run: **0 findings, 52 cells, 9 blocks**. |
| #3 🟠 cap rationale wrong in three places | ✅ **closed** | `sidebar.component.css:84–105` (rail), `:267–281` (tile) and `menu-overflow.md:191–194` all now say `aspect-ratio`/grid raster **plus** cap, for both boxes. Numbers re-measured (table above) and correct. |
| #4 🟡 `01-prd.md:284` 24px | ✅ **closed** | Reads `26px` with the `24px` struck through and dated. |
| #5 🟡 instrument 2px blind spot | ✅ **closed** | Both directions measured (table above); `labelBoxes` verified to still catch a widened label. See finding 2 below for the *justification* of the second call site. |
| #6 🟡 registry honesty | ✅ **closed** | `index.md:8` carries the run-2 caveat for `measure.mjs` and the corrected `measure-states.mjs` state, matching `handoff.md`. |
| #7 🟡 superlative correction incomplete | ✅ **closed** | `02-design.md:546–556` and `handoff.md:319` corrected; the missing hero-artwork row is in `home.md`'s deviation table (`:128`) with a full note. Numbers reproduced exactly: `Einstellungen` **77** and `Hausordnung` **79** active at 1280, **66** vs **64** at the floor. |
| #8 🟡 review results not written down | ✅ **honoured** | Both 2026-08-29 passes are in this file; this one is being written before the turn ends, as the finding asked. |
| #9 · #10 🟢 | ✅ **honoured** | The run-2 flakiness reading is carried, not laundered; scope is enumerated against the findings, including the honest "seven files — over the five-file limit, covered by the enumerated findings". |
| Pass-1 🟢 #12–#14 | **open by design, correctly** | None asks for a change; all three re-measured this pass and still exactly true (see above). Leaving them open is the right call — closing them would mean deleting a live fragility note (#13) and an owner decision's price (#14). |

## Findings

| # | Severity | Category | File | Finding | Fix |
|---|----------|----------|------|---------|-----|
| 1 | 🟡 Minor | Stated as measured | `docs/features/home-screen-prototype/handoff.md:38` | **"The reviewer's suggested `calc(24.78125rem - var(--size-sidebar))` is 68px out" — it is 72px out, and only at one viewport.** Measured by applying pass 2's formula to the live host: it puts the artwork at **x 468.5** against the correct **396.5** at 1280 × 832 = **72px**, and at **374.78** against **317.17** at 1024 × 768 = **57.61px** (the error is in `rem`, so it scales with the root; quoting it as one absolute number is wrong in a second way). **68** is the distance from the *old, already-wrong* literal (400.5) — the wrong baseline, since the whole point is that the literal was off spec. `dev`'s own justification in the same sentence gives the right answer: "it subtracts the sidebar but not the shell's padding and gap" is `--space-7` 32 + `--space-8` 40 = **72**. The substance is right and re-deriving instead of adopting was correct; only the figure is wrong. | 68 → **72px at 1280 × 832 (57.6px at the 1024 floor — the error is a `rem` quantity)**, and note that it is measured against the spec position, not against the old literal. |
| 2 | 🟡 Minor | Correctness of a load-bearing rationale | `frontend/tools/measure.mjs:521–528`, `docs/features/home-screen-prototype/handoff.md:92` | **The overflow trigger's call site was *not* "the same bug", and the comment now in the instrument says it was.** Measured: the old call passed `boxEl = null`, so `truncated()` fell to `el.clientWidth` — for a label with `display: flow-root` and zero padding/border that is **86** at 1280 and **69** at 1024, i.e. the label's own padding box, already the right box. Both of the constructed cases the fix was verified with were **already caught** by the old trigger comparison (`Einstellungen..` 87 > 86.5 → `true`; `Einstellungen.` @1024 70 > 69.5 → `true`). What the change actually buys is (a) sub-pixel precision — `borderWidth` 68.81 instead of an integer-rounded `clientWidth` 69 — and (b) the genuine `singleLine: true → false` correction, which is a different, real defect and is separately cited. Harmless in effect, wrong in the record — and it is a code comment, which is what the next person will trust when deciding whether the two call sites must stay in step. | Reword to what was measured: the `labels` site had the item-border-box blind spot; the trigger site was measuring the label's own box already, and was changed for sub-pixel precision and to stop claiming `singleLine`. |
| 3 | 🟡 Minor | Record integrity | `docs/features/home-screen-prototype/06-review.md:800` (pass-2 finding #1, Fix column) | **This file still recommends, unannotated, a formula now known to be 72px wrong.** Pass 2's Fix column names `left: calc(24.78125rem - var(--size-sidebar))` as the preferred one-line fix. It is kept verbatim as history, which is right — but every other superseded claim in this file carries a dated superseding note, and this one does not. A future reader who applies the recommendation literally moves the artwork 72px off spec, and `measure-states.mjs` would then report it as a regression against a review that recommended it. | Add a one-line dated note beside it: the formula omits `--space-7` and `--space-8`; the shipped, verified expression is `calc(24.78125rem - var(--space-7) - var(--size-sidebar) - var(--space-8))`. |
| 4 | 🟡 Minor | Attribution | `docs/features/home-screen-prototype/handoff.md:137` | **"Pass 2's 🟡 #12–#14 from the *earlier* review remain open by design" mis-cites the pass, the number-space and the severity, in the newest and most authoritative section of the handoff.** Pass 2 has no #12–#14 at all (it stops at #10); pass 1's #12–#14 are **🟢**, not 🟡; and "Pass 2's … from the earlier review" names two different reviews in one clause. Pass 2 flagged this exact mismatch in its reconstruction warning (`06-review.md:623–632`) as evidence of the lost 2026-08-29 review whose numbering `measure.mjs` cites — and the fix run restated it, adding a wrong attribution on top. Low harm on its own; it matters because the open-findings list is the one sentence a future session will act on. | "Pass 1's 🟢 #12–#14 remain open by design (none asks for a change)", and keep the note that the *earlier, unrecoverable* 2026-08-29 review may have numbered them differently. |
| 5 | 🟢 Info | Ratchet | `frontend/tools/measure-states.mjs:963–974` | Worth recording, because it is what makes finding #1's fix safe rather than merely correct: the ARTWORK cell already compares the rendered image box against `ARTWORK_SPEC` scaled by `w/1280`, at ±1px, at both reference viewports. If a future change breaks the assumption the derivation rests on — that `.home-content`'s origin is `--space-7 + --size-sidebar + --space-8` — the instrument catches it in the same cell that caught this one. The derivation is guarded, not just commented. |
| 6 | 🟢 Info | Scope | `frontend/src/app/features/home/hero-artwork/hero-artwork.component.ts:61` | **Deriving `top` as well as `left` is covered, and it should stay.** Only `left` was faulted, but the literal `3.34375rem` was the identical error class (`77.5 − 24`, against `--space-6`) waiting for a padding change. Its value is unchanged — computed `top` is `53.5px`, and the measured `y` is `77.5` / `61.98`, identical before and after — the whole declaration block is now internally consistent, and the extension is declared in both the code comment and the handoff. This is the good shape of "one line past the brief". |
| 7 | 🟢 Info | Docs | `docs/design/screens/home.md:445–464` ("Hero artwork (2:5)" as-built note) | The as-built note for the artwork still describes it only as "implemented in `rem`, absolutely positioned"; the fact that the offset is now *derived* from `--size-sidebar` and the shell's padding and gap lives 320 lines earlier, in the deviation table. Both are correct and the table's artwork row points forward, but a reader who jumps straight to the artwork section will not learn the thing that changed. |

## Explicitly cleared (checked this pass, not defects)

- **The `left` expression is robust in the way it was meant to be.** Not just
  "the number is right today": four separate tokens were mutated at runtime, at
  both reference viewports, and the artwork's position moved by **0.000px**
  every time while the content column moved under it. Pass 2's finding said
  deriving "makes the next `--size-sidebar` change free" — measured, it makes
  the next `--space-7` and `--space-8` change free too.
- **The instrument does not report a false positive.** 168 cells, 0 truncated,
  0 clamped, 0 `LABEL BOX`; and the six shipped labels sit 9–51px inside their
  boxes at 1280 and 6–41px at 1024. The 0.5px tolerance is unchanged from
  before the fix, so nothing got twitchier.
- **Nothing else in Home moved.** `heading.x 176`, card row `x176 … right 996
  bottom 744`, `heading.y 88` — reproduced in the ARTWORK cells and in the grid
  run's baseline check, with and without an artwork present. REQ-010's "the
  artwork occupies nothing" still holds: the host is out of flow in both
  variants and `pointer-events: none`.
- **Constitution constraints, re-checked against the diff (v1.10.0):** no
  dependency-manifest edit (`git diff --name-only` names no `package*.json`,
  no `pom.xml`); every changed declaration is a `calc()` over semantic tokens —
  no raw hex, no raw px introduced outside `tokens.css`; `rem` throughout; no
  external runtime request; **nothing scrolls** (200 × NO-SCROLL OK plus 52
  state cells); work is on `refactor/bce-subpackages-and-boundary-security`,
  not `main`. `frontend/CLAUDE.md` and `docs/constitution.md` are **byte-
  identical to `HEAD`** — no nested-`CLAUDE.md` gate was opened and none was
  owed, because deriving a layout offset from the tokens that compose it is the
  convention `sidebar.component.css` already states twice, not a new one.
- **No approval-gated decision was taken over the owner's head.** The one
  decision in this run — derive the artwork rather than record the 4px — is
  recorded as the owner's own ruling (*"abgeleitet, bleibt bei 396.5"*,
  `handoff.md:21`), which is one of the two options pass 2 put to him. Nothing
  else touches a dependency, infrastructure, the token *mechanism*, a metered
  design-tool request, or a nested `CLAUDE.md`.
- **No unit test was owed.** `frontend/CLAUDE.md` → Tests puts geometry out of
  jsdom's reach and names the instruments; 333/333 in 37 files is unchanged, as
  it should be for a pure-geometry change, and both instruments were run.
- **Security.** Nothing in scope touches content, logging, an endpoint or a
  handler; no secret, no guest data. No `architect` escalation warranted.
- **Repo left as handed off.** Four throwaway probes in `/tmp`, deleted; the
  Quarkus process this pass started was stopped; the pre-existing `ng serve`
  (pid 5025) was left running. `git status --untracked-files=all`: **15
  modified, 0 untracked** — the same 15 files this pass was asked to review,
  plus this section.

## REQ-ID coverage — sidebar-rail pass 3

Only the criteria the sidebar-rail rounds and this fix run touched were
re-litigated; the rest of REQ-001…REQ-025 is unchanged since the 2026-08-18
verification above.

| REQ-ID | Verified how | Status |
|---|---|---|
| REQ-007 (rail label within its item, at most two lines, pair centred, item square) | Item 88 × 88 / 70.41 × 70.41; label box 86 / 68.81 with `LABEL BOX` delta −2 = expected in all six items; `measure.mjs` 168 cells, 0 truncated, 0 clamped; the clip check now measures the box that actually clips, verified in both directions | ✅ |
| REQ-009 (card titles do not wrap or ellipsize) | 168 cells, 0 truncated; visually confirmed at both viewports | ✅ |
| REQ-010 / REQ-513 (artwork occupies nothing; authorized fetch) | Host out of flow in both variants, `pointer-events: none`, `z-index: -1` behind the layout; content column identical with and without an image; fetch path untouched | ✅ |
| REQ-014 (content column geometry) | Live: heading `x176 y88 w390`, card row `x176 w820 right996 bottom744`, widget column `flex: 1`, empty, 220px — and now the one element inside this screen that was measured against the old origin sits back on its spec box | ✅ |
| REQ-022 (sidebar box, item square, group gaps) | Sidebar `x32 y24 w104 h784`; item = `--size-sidebar` − 2 × `--space-3`, square at both viewports | ✅ |
| REQ-024 / REQ-123 (nothing scrolls) | 200 × NO-SCROLL OK across the 168-cell grid plus 52 kiosk-state cells, both instruments | ✅ |
| REQ-025 (scaling contract) | 1024 × 768 is a uniform 0.8× of 1280 × 832 in every box measured this pass, artwork included (`317.17 = 396.5 × 0.8`); nothing newly wrapped | ✅ |

## Verdict against the constitution's quality bar (v1.10.0)

- Builds and all tests green — ✅ `ng build` green (477.05 kB / 108.58 kB),
  37 files / 333 tests, both re-run independently.
- Conventions from nested `CLAUDE.md` files followed — ✅. The one place pass 2
  found the codebase's own "derived, not literal" rule unhonoured is now the
  place that honours it best.
- No sensitive data in logs — ✅.
- `06-review.md` result is PASS with every `REQ-ID` implemented — ✅ both.

**PASS ✅.** Zero Critical, zero Major. All three of pass 2's Majors are closed
and were re-measured rather than read: the artwork is back on its specified box
and stays there under every token it derives from, the mis-triage is recorded
with its origin in both the handoff and the registry, and the two caps'
rationale is corrected at all three sites with numbers this pass reproduced
line-count by line-count. The instrument fix is right in both directions — it
now sees the 2px band it was blind to, and it invents nothing across 168 cells.
The four 🟡 are all wrong *numbers* or wrong *attributions* attached to changes
that are themselves correct; none of them can produce a wrong pixel, and each
is a one-line edit. **The owner can commit.**

**The working tree stays uncommitted until he does** — 15 modified files, 0
untracked, on `refactor/bce-subpackages-and-boundary-security`. The owner
commits; no agent does.

### Top 3 recommendations

1. **Commit.** Nothing 🔴 or 🟠 is open against this feature for the first time
   since the rail work started, and every claim in the write-ups that could
   move a pixel has now been reproduced by an independent hand.
2. **Sweep the four 🟡 in one pass, ideally before the commit** — 68 → 72
   (finding 1), the trigger-call-site rationale in `measure.mjs`'s comment
   (finding 2), a dated superseding note on pass 2's formula (finding 3), and
   "Pass 1's 🟢 #12–#14" (finding 4). All four are single-line edits in files
   already in the diff, and three of them are exactly the drift class the last
   two passes were spent closing.
3. **Let pass-1 🟢 #12–#14 stay open, and stop re-litigating them.** They are
   the two-line cap's zero slack, the 1.41px floor clearance and a note about
   the instrument; all three were re-measured this pass and are still exactly
   true, and none asks for a change. They are notes for whoever next touches
   `--text-label-line-height` or the 1024 floor — which is what 🟢 is for.
