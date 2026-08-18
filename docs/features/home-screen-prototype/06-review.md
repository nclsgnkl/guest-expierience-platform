# Review: Home Screen Prototype
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md) | **Analyze:** [04-analyze.md](04-analyze.md)
**Result:** **PASS ✅** (round 3 final verification — see "Round 3 — final
verification" at the bottom of this file, which supersedes every earlier result
for status purposes: 🔴 0 · 🟠 0 · 🟡 1 · 🟢 4, against constitution **v1.3.0**)
**Reviewed:** 2026-08-18 (round 1) · **Re-verified after fixes:** 2026-08-18 ·
**Round 3 (visual-defect correction, first review with the visual check):** 2026-08-18 ·
**Round 3 final verification (rounds 4 + 5 fixes re-measured):** 2026-08-18

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
