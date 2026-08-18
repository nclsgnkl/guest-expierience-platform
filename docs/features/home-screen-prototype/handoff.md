# Handoff: Home Screen Prototype

**Current phase:** `06_review` result is **PASS ✅** (findings #1–#5 from the
first pass below were fixed and spot-verified — see `06-review.md`'s own
"PASS ✅" section at the bottom, which supersedes the "NEEDS CHANGES" status
this file's header used to carry). **But see "Post-review visual-bug fix
(2026-08-18, outside the pipeline)" below** — that PASS was given before the
`code-review` skill had a visual-check step at all (added later this same
day, see that skill's own changelog note), so it did not catch two rendering
bugs a human found by actually looking at the running app. Those are now
fixed in source but **not yet verified** — see that section for exactly what
still needs to happen next, on a machine that actually has the toolchain.

## Post-review visual-bug fix (2026-08-18, outside the pipeline)

**Not done by `dev`, and not build/test verified** — done in a plain
(non-agent) Claude Code session on the user's Mac, which has **no Node.js/npm
at all**, at the user's explicit choice to accept unverified edits here
rather than route through `dev` on the Raspberry Pi. Both root causes were
already fully diagnosed in conversation before editing (not guessed):

1. **`frontend/src/app/layout/shell/shell.component.css`** — the 16
   `radial-gradient` mesh layers had no explicit size, so `circle`'s default
   (`farthest-corner` — the full canvas diagonal for a corner-positioned
   point) washed every layer out across nearly the whole background, with
   the four topmost layers (mesh-1..4, pink/peach) hiding everything below.
   Fixed by giving every layer an explicit `ellipse 44rem 29rem` size, sized
   to approximate one grid cell's spacing on the 1280×832 logical viewport.
   Full rationale in that file's own updated header comment.
2. **`frontend/src/app/features/home/action-card/action-card.component.css`**
   — `.action-card` had `width`/`min-width` but no `flex-shrink: 0`, **and**
   critically it's the template's root element, not the component's host —
   with Angular's default view encapsulation, `<app-action-card>` (the host,
   unstyled, no `:host` rule existed anywhere in this codebase) is the
   actual flex item inside `.home-content__cards`, not `.action-card`.  The
   host's default `flex-shrink: 1` let it shrink regardless of the inner
   button's own width, combined with `.shell__content`'s deliberate
   `min-width: 0` — each card then shrank by however much its own text's
   min-content width allowed, unevenly. Fixed with `:host { display:
   contents; }` (so `.action-card` becomes the real flex item) plus
   `flex-shrink: 0` on `.action-card` itself. Full rationale in that file's
   own new header comment.

**Round 2 (same session, after the user ran the app and looked at it).** The
two fixes above worked — the user confirmed the layout and background now
read correctly — but the rendered screen surfaced two further defects that no
document had caught:

3. **All icons rendered at roughly a third of their intended size.** Root
   cause was a **spec error, not an implementation error**:
   `docs/design/tokens.md` defined a single `--size-icon-glyph` at 12px,
   derived by reading Figma's `aspect-[24/24]` as a 24px dimension. It is an
   aspect *ratio* (24:24 = 1:1) and carries no size. `dev` implemented the
   spec faithfully; the spec was wrong. Worse, one token was applied to every
   icon regardless of context, so no single correct value existed.
   Replaced with three context-specific tokens, sizes re-derived from the
   surrounding Figma boxes and cross-checked against `design-sheet.md`'s own
   icon scale (which independently agrees):
   `--size-icon-lg` 2.25rem/36px (glyph inside a filled tile),
   `--size-icon-md` 1.625rem/26px (sidebar nav),
   `--size-icon-sm` 1.5rem/24px (card arrow, heading sparkle).
   All five call sites in `sidebar`, `action-card` and `heading-block`
   templates repointed. **`docs/design/tokens.md` was corrected too**, with a
   warning to watch for the same `aspect-[a/b]`-misread class of error
   elsewhere in that file — otherwise the next feature reproduces this bug.
4. **Colours read washed out versus the Figma reference.** Two causes, both
   in `shell.component.css`: there was no `background-color` at all, so the
   white page canvas showed through wherever the layers had faded out; and
   each layer ended at `transparent 60%`, leaving desaturated gaps between
   grid points. Added `background-color: var(--mesh-6)` as the floor and
   extended every layer to `transparent 100%` so neighbours overlap.

**What still needs to happen, on a machine with the toolchain (the Pi):**
- `npm install` (`frontend/package.json` gained a new devDependency,
  `playwright`, added in this same session for the new visual-check step —
  unrelated to these two fixes but landed at the same time) then
  `npx playwright install chromium` once.
- `npm run build` and `npm test` — neither has run since these edits.
- The visual check itself: start the dev server, screenshot, compare against
  `docs/design/assets/home.png` — exactly the step `code-review`'s new step 0
  describes. This is the first real exercise of that new rule.
- If anything looks off, this is exactly the kind of fix worth a `reviewer`
  pass rather than trusting it on sight — nothing here has been build/test
  verified yet.

## Review session (2026-08-18, `reviewer`)

## Review session (2026-08-18, `reviewer`)

Full review of `frontend/` (first code in this repo) against
`docs/constitution.md`, the four planning docs, and
`docs/design/screens/home.md`/`docs/design/tokens.md`. Read every source
file (not a diff review), independently re-ran `npm run build` and `npm
test` (both green: 10 files / 29 tests, matching the implementation
report), and verified two things empirically rather than trusting the
report:

1. **`tsconfig.json` is missing `"strict": true`** — confirmed via `npx tsc
   -p tsconfig.app.json --showConfig` (the string `strict` does not appear
   anywhere in the resolved compiler options), despite REQ-001 requiring it,
   `03-todo.md`'s scaffold step being checked off as validated, and dev's own
   report claiming the `ng new --strict` flag was used. Temporarily enabled
   it and reran `tsc --noEmit` + `ng build`: **zero new errors** — the
   codebase is already strict-clean, just not declared as such. Reverted the
   temporary edit before finishing (repo left exactly as handed off).
2. **`app.config.ts` never calls `provideZonelessChangeDetection()`**, even
   though this is a zoneless scaffold (no `zone.js` dependency, no
   `polyfills` entry) and all 9 spec files add it explicitly. Wrote and ran a
   throwaway diagnostic test bootstrapping the real, unmodified `App` +
   `appConfig` and clicking a sidebar button with no manual
   `fixture.detectChanges()` call: the DOM updated and the router navigated
   anyway, because Angular 22's default notification-based scheduler
   triggers CD on DOM events even without either zone.js or the explicit
   provider. Not a functional bug today, but production silently relies on
   an implicit framework default that every test explicitly opts out of
   relying on — flagged as Major per the review brief's explicit ask to
   check `provideZonelessChangeDetection()` usage is consistent. Diagnostic
   spec file deleted before finishing.

Six smaller findings (3 Minor, 3 Info) are cosmetic/consistency notes — see
`06-review.md` for the full table (redundant keydown handlers on
`ActionCardComponent` double-firing navigation on Enter; two unused tokens;
one component using constructor DI instead of `inject()`; hero-artwork's
flex-vs-absolute positioning noted for whoever builds the real artwork
variant; one integration-test gap on sidebar active-state; `@angular/cdk`
added but not yet used by any code).

**REQ-ID coverage: all 21 implemented and verified against running code**,
independent of `04-analyze.md`'s matrix — no functionality gap, both Major
findings are cross-cutting technical-constraint items, not missing
requirements. Constitution principles 1–7 and all constraints reviewed;
held throughout except the strict-mode gap above.

**Result: NEEDS CHANGES ❌** (not PASS) per constitution's quality bar —
builds/tests green ✅, no sensitive data in logs ✅, but `06-review.md` isn't
a clean PASS until findings #1–#2 are fixed. Both are one-line, verified
zero-fallout fixes.

## Previous phase notes (implementation session, superseded above for status purposes)

**Previous phase:** `04_analyze` — **PASS** (re-run 2026-08-18, second pass).

## What happened this session (2026-08-18, revision 2)

The first planning pass (PRD → Design → TODO → Analyze, all PASS) was done
in an earlier session. The user then reviewed it and answered the flagged
open questions — several answers changed the architecture, not just
confirmed it. This session updated all four docs to match and re-ran the
gate. Key decisions applied:

1. **Widget column** — space is reserved (`flex: 1`, empty) in Home's
   layout; content stays out of scope (future feature). REQ-014 amended.
2. **Background gradient** — confirmed as originally planned: fixed default
   palette, no per-property tinting. No change.
3. **Placeholder architecture — the big change.** Originally: 9 independent
   routes (5 nav + 4 cards) → one generic bare placeholder. Now:
   - Every route renders inside a shared `ShellComponent` (background +
     functional Sidebar, same as Home) — placeholders are never isolated
     stub pages.
   - The 4 action cards no longer have their own routes. Each carries a
     `targetSectionId` that resolves (via
     `PropertyContentService.getSectionRoute()`) to one of the sidebar's
     5 real sections. Confirmed mapping: Check-Out→Info,
     Haus & Funktionen→Smart Home, Entdecken→Umgebung, Wichtige
     Infos→Info (two cards intentionally share Info — not a bug).
   - Total non-Home routes dropped from 9 to 5: `menuItems`' 4 non-Home
     entries (Info, Umgebung, Smart Home, Services) + the fixed Settings
     route.
   - Sidebar's `menuItems` (5, including Home) are now config-driven data
     from `PropertyContentService`, not a `nav-items.ts` code constant.
   - **Settings decision (delegated to this agent, resolved):** structurally
     fixed position (always present, always last, own group — matches
     Figma's separate "Settings" group) but its label/icon/route content
     still comes from config, for consistency. Rationale in `02-design.md`
     → "Sidebar config & the Settings decision".
   New/changed REQ-IDs: REQ-012, REQ-014 rewritten; REQ-017…REQ-021 added
   (menuItems, settings, targetSectionId/getSectionRoute, ShellComponent,
   SectionPlaceholderComponent).
4. **Logo** — unchanged, placeholder SVG behind a swappable slot.
5. **Heading content** — clarified explicitly (not a mechanism change) as
   property-level config authored by the host, not per-guest/per-stay
   personalisation. No guest/stay data model exists or is implied.

All four docs (`01-prd.md`, `02-design.md`, `03-todo.md`, `04-analyze.md`)
were rewritten to match; `docs/system-context.md`'s Components table and the
widget-column note in Open Structural Questions were also updated to keep
that map current (renamed/added components: `ShellComponent`,
`SidebarComponent` under a new `layout/` area, `HomeComponent` →
`HomeContentComponent`, `PlaceholderComponent` →
`SectionPlaceholderComponent`). `docs/features/index.md`'s row was reviewed
— status (`In Review`) and phase (`04_analyze (PASS)`) were already accurate
and needed no edit.

## Gate result

**PASS.** Coverage matrix in `04-analyze.md` covers REQ-001…REQ-021, no
orphans, no contradictions found between PRD/Design/TODO, constitution
compliance reviewed principle-by-principle (1, 2 and 7 got the closest look,
since this revision touches "content as data" and traceability directly),
no approval-gated decision left unconfirmed.

## What's next (superseded by the 2026-08-18 implementation session below)

- Awaiting the user's confirmation to proceed. If confirmed, next step is
  `05_implement` — hand `03-todo.md` to `dev`.
- Still-open, explicitly non-blocking items carried in `01-prd.md` → Risks &
  open questions (unchanged by this revision, still valid): final logo
  asset, dark-mode palette, and the 14px/12px touchscreen-legibility
  hardware check. None block implementation.
- If the user wants to revisit any of the 2026-08-18 decisions (e.g. the
  Settings hardcode-vs-config call), re-open `02-design.md` →
  "Sidebar config & the Settings decision" and `01-prd.md` → REQ-017/018,
  then re-run `04_analyze`.

## Implementation session (2026-08-18, `dev`)

The user confirmed proceeding; this session ran the full `03-todo.md` top to
bottom, build + test after each task. Environment note: this machine had no
Node.js at all — installed Node v24.19.0 (LTS) system-wide
(`/usr/local/lib/nodejs`, symlinked into `/usr/local/bin`) before anything
else could run.

**Scaffold:** `frontend/` created via `ng new frontend --routing --style=css
--strict --standalone --test-runner=vitest --package-manager=npm
--file-name-style-guide=2016 --skip-git --defaults --interactive=false`
(Angular CLI 22.1.4). `--file-name-style-guide=2016` was chosen deliberately
so generated files match `02-design.md`'s own naming
(`*.component.ts/.html/.css`), not the CLI's newer 2025 concise default.
Vitest 4.0.8 + jsdom ship by default, confirmed no `zone.js` dependency —
this Angular version's new-project default is zoneless
(`provideZonelessChangeDetection()` is required in every component test's
providers; there's no separate opt-in step anyone chose).

**Dependencies added** (pre-approved by name in the constitution, so no
approval gate hit): `@angular/cdk@22.1.2`, `@ng-icons/core@35.0.1`,
`@ng-icons/phosphor-icons@35.0.1`.

**Font:** Plus Jakarta Sans self-hosted — downloaded static per-weight
woff2 files (400/700/800, latin subset, which covers German umlauts/ß)
directly from `fonts.gstatic.com` at scaffold time (a one-time build-time
fetch, not a shipped runtime request) into
`frontend/src/assets/fonts/plus-jakarta-sans/`. `@font-face` rules live in
`frontend/src/styles/global.css`. Confirmed zero external URLs anywhere in
the production `dist/` output except a documentation link embedded in
Angular's own framework bundle (`angular.dev/best-practices/security`,
static text in an error message, not a network request).

**Everything else built per `02-design.md`'s module layout** — token layer
(`tokens.css` + `global.css`), `PropertyContentService` +
`example-property.data.ts` + models, `ShellComponent` + `SidebarComponent`
(shared, route-agnostic, moved to `layout/`), `HomeContentComponent` +
`HeadingBlockComponent` + `ActionCardComponent` + `HeroArtworkComponent`,
`SectionPlaceholderComponent`, `app.routes.ts` (Shell parent + 6 children:
Home + 5 sections), branding hook wired in the root `App` component, min-
width guard rail. Full detail, file paths and every REQ-ID trace in this
session's `dev` report (see conversation / PR description) rather than
duplicated here.

**One implementation decision not pinned down by the design docs**, decided
and documented in code comments rather than escalated (not on the
constitution's approval-gated list): the `headline` field carries a literal
`{guestName}` placeholder string, substituted by `HeadingBlockComponent` at
render time — this is what makes REQ-008's "guest name interpolated"
language concrete in code, and keeps `guestName` load-bearing rather than a
redundant field next to an already-final `headline` string.

**Build:** green. **Tests:** 29 passed / 0 failed across 10 spec files.

**Still open / not done here:**
- No visual screenshot diff against `docs/design/assets/home-no-artwork.png`
  — this environment has no headless browser and installing one
  (Playwright/Puppeteer) would be a new dependency requiring approval, which
  is out of scope for this feature (no E2E framework is chosen yet, per
  `docs/system-context.md`). Verified instead via routing/rendering unit
  tests and manual `curl` checks that every route serves 200 and the font/
  logo assets resolve.
- The `frontend/CLAUDE.md` convention entry is **proposed, not written** —
  see the `dev` report for the exact text; needs the user's confirmation
  before it's created (constitution's approval gate for nested `CLAUDE.md`
  entries).
- Everything else carried as non-blocking in `01-prd.md` → Risks & open
  questions is still open and still non-blocking: final logo asset,
  dark-mode palette, the 14px/12px touchscreen-legibility hardware check.

## What's next

**Hand back to `dev`** to fix the two Major findings from the 2026-08-18
review (see above / `06-review.md`):
1. Add `"strict": true` to `frontend/tsconfig.json` → `compilerOptions`.
2. Add `provideZonelessChangeDetection()` to `frontend/src/app/app.config.ts`'s
   provider list.

Both are confirmed one-line, zero-fallout fixes (verified by this review).
The 3 Minor + 3 Info findings are non-blocking — fix opportunistically in the
same pass or track for later, at the user's discretion. Once #1–#2 land,
this does not need a full re-review — a build/test re-run plus a spot-check
of the two diffs is enough to flip `06-review.md`'s result to PASS ✅.

The `frontend/CLAUDE.md` proposal from the implementation session is still
only *proposed*, not written — needs the user's explicit confirmation before
anyone creates it, independent of the review outcome.
