# Review: Appearance & Language (dark mode + i18n)
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md) | **Analyze:** [04-analyze.md](04-analyze.md)
**Result:** **PASS ✅** against constitution **v1.3.0** — 🔴 0 · 🟠 0 · 🟡 5 · 🟢 10
**Reviewed:** 2026-08-19 · **Reviewer:** `reviewer` subagent
**Scope:** the whole uncommitted `appearance-and-language` work on branch
`feature/home-screen-prototype`. The branch also carries the *previous*
feature's uncommitted rounds 3–6 (`home-screen-prototype`, already PASS); those
were **not** re-reviewed. **They are not entangled** — see "Entanglement check".

> Nothing below was taken on trust from `03-todo.md`'s checkmarks,
> `04-analyze.md`'s PASS or `handoff.md`'s claims. Build, tests, the 72-cell
> measurement grid, two source mutations and one live browser session were run
> independently for this review. Where a `handoff.md` item is re-stated here it
> is because I verified it myself and either agree or disagree explicitly.

---

## Independent verification performed

| What | Result |
|---|---|
| `ng build` | Green — main **350.39 kB**, styles **7.49 kB**. Matches the reported figures exactly |
| `ng test` (run 1) | 22 files / 177 tests / **1 failed** — `shell.component.spec.ts` timed out at 5 s (7.73 s) |
| `ng test` (run 2, 3, 4, 5) | Green — **22 files / 177 tests / 0 failures**. The run-1 failure is the known intermittent timeout (finding #4) |
| `tools/measure.mjs`, full default grid | **72/72** combinations, **0 overflowing, 0 truncated**, 0 findings. Independently reproduced against a dev server; the handoff measured a production build and got the same numbers |
| Contrast composites | 8 of 9 rows reproduce `02-design.md` §3.4 within ±0.02. The 9th is finding #2 |
| Live browser session | Keyboard model, focus return, storage contents and the absence of cookies/localStorage confirmed by hand (see REQ-115/REQ-127 rows) |
| Mutation check ×2 | Both bite exactly as claimed — see "Test quality" |
| Dependency manifest | `package.json` / `package-lock.json` **untouched**; dependency set is exactly the constitution's approved list |
| Branch | `feature/home-screen-prototype` — not `main`. ✅ |
| Servers | One `ng serve` started on 4433 for the visual check and **killed**; port range verified clear afterwards. A second listener briefly collided on 4411 and was killed before any measurement was taken from it |

---

## Visual verification

**Done, not skipped.** Four screenshots captured at 1280 × 832 from a real
Chromium via `tools/measure.mjs --screenshot`, with the theme/language axes
driven **through the UI**, plus one hand-driven dropdown capture:

| Screenshot | Route × theme × language |
|---|---|
| `/tmp/rev-home-light-de.png` | `/` · light · de |
| `/tmp/rev-home-dark-de.png` | `/` · dark · de |
| `/tmp/rev-settings-light-de.png` | `/einstellungen` · light · de |
| `/tmp/rev-settings-dark-en.png` | `/einstellungen` · dark · en |
| `/tmp/rev-dropdown-light.png` | `/` · light · de, appearance dropdown **open** |

Compared against [`../../design/assets/home-concept-ai.png`](../../design/assets/home-concept-ai.png)
(the only reference that shows this corner) and against
[`../../design/screens/home.md`](../../design/screens/home.md):

- **Status bar matches the reference's composition** — globe + `DE` + caret,
  then the appearance control, then a hairline divider, then the time, in that
  order, at the same corner. The reference shows *no* appearance control; the
  implementation adds one, which is **REQ-113 as written** and is recorded as a
  committed extension in `screens/home.md`. Not a deviation.
- **Home's geometry is visually unchanged** between the reference-era layout and
  now: heading, paragraph and the 4-card row sit where they did. The bar
  overlays; nothing was pushed. Confirmed numerically by the measurement grid
  (heading y = 88, card row bottom y = 744).
- **Dark mode is a re-colouring, not a re-layout.** Glass panels stay
  translucent and blurred, radii/spacing/type are identical, and the brand
  accents (icon tiles, logo, active nav glyph) stay brand-coloured against the
  night mesh — exactly what REQ-104 claims.
- **Settings renders as specified in §9.1** — H1 title, two glass panels, all
  options visible, selection marked three ways (background + weight + check).
- **Dropdown renders in the overlay container**, anchored below-end, glass
  recipe, 44 px rows, selected row marked by gradient **and** check glyph. No
  scrollbar on either axis at any size.

**Two things the screenshots surfaced that a text review would not have:**
finding #3 (the light-mode Settings hint is visibly faint) and finding #5 (the
selected option's bold weight changes the row's width).

---

## Findings

| # | Severity | Category | File | Finding | Fix |
|---|----------|----------|------|---------|-----|
| 1 | 🟡 Minor | Correctness / Input validation | `frontend/src/app/core/content/property-content.service.ts` (`validateConfig`) | **`defaultAppearance` is never validated against `appearances`**, while `defaultLanguage` *is* validated against `languages` (check 2). The asymmetry is real: with `defaultAppearance: 'system'` and `appearances: [light, dark]`, the config is accepted, `appearance()` returns `'system'`, and the Settings screen renders **no option marked selected** (`isSelected()` matches nothing) until the guest taps. The gap is in the design too — `02-design.md` §5.2 enumerates only three checks — so this is not a deviation from the plan, it is a hole in the plan. Constitution → Constraints: *"Input validated at every system boundary"*, and this config is explicitly described as the boundary that becomes an HTTP response. | Add a fourth check to `validateConfig()`: `defaultAppearance` must be one of `appearances[].mode`. One `if`, one test, symmetric with the existing language check. Record it in `02-design.md` §5.2. |
| 2 | 🟡 Minor | Documentation accuracy | `docs/design/dark-theme.md` §4 (and `02-design.md` §3.4) | The **light** status-bar contrast row records **10.49**. A 52-pixel scan across the bar's actual box gives **7.66 – 8.71** (dark: 11.80 – 12.39 against the recorded 10.90). I reproduced this independently — it is not a measurement artefact. `handoff.md` flags this only against `02-design.md` §3.4; **it is also in `dark-theme.md` §4**, which is the *permanent* design record future work will read, not a feature planning artifact. No accessibility consequence — 7.66 still clears AAA — but the figure is optimistic by ~2 points and the row's parenthetical ("the bar sits over the lightest point of the light mesh") is the part that is wrong. | Correct the light value in **both** files to a measured range, and drop or rewrite the "lightest point" rationale. `tools/measure.mjs` already prints the band scan, so re-measuring costs nothing. |
| 3 | 🟡 Minor | Accessibility | `frontend/src/app/features/settings/setting-choice/setting-choice.component.css` (`.setting-choice__hint`) | The Settings hint text uses `--color-text-secondary`, which measures **3.80 : 1** in light mode on the glass panel — **below WCAG AA (4.5 : 1)**. Dark measures 5.31 and passes. The *role* is a known pre-existing weakness (`dark-theme.md` §4 records 3.99 and 3.48 for it, both signed off in the previous feature), so nothing regressed — but this feature **authored a new screen and new copy** onto that role, which grows the exposure rather than inheriting it. Visibly faint in `/tmp/rev-settings-light-de.png`. | Do not patch inside this feature — changing `--ink-600` alters the signed-off light screen. Raise it as its own tracked item: either darken `--color-text-secondary` for light (a token-layer change, and therefore a design decision), or give hints their own role. Until then it is a documented, accepted defect and should be *tracked*, not only noted in a handoff. |
| 4 | 🟡 Minor | Testing | `frontend/src/app/layout/shell/shell.component.spec.ts` | `ShellComponent` now renders `StatusBarComponent`, but this spec **does not register the three glyphs it needs**, so every run prints `No icon named phosphorGlobe / phosphorSun / phosphorCaretDown was found` — i.e. the spec exercises the status bar in a degraded state, and the suite carries permanent stderr noise. This is also the spec that intermittently exceeds the 5 s limit (it did on my first of five runs, at 7.73 s). **The flake is not pre-existing**: it appears for the first time in this feature's handoff and in no `home-screen-prototype` record, and it arrived with the extra component this spec now renders. Diagnostic: adding the three icons to the spec's `provideIcons()` removed the warnings and gave 3/3 green full runs — suggestive of the root cause, though 3 runs is not proof for an intermittent failure. | Register `phosphorGlobe`, `phosphorSun` and `phosphorCaretDown` in this spec's `provideIcons()`. **Do not raise the timeout** — the handoff is right about that. If the timeout still recurs afterwards, the next step is stubbing `StatusBarComponent` in this spec rather than widening the limit. |
| 5 | 🟡 Minor | Visual / Layout | `frontend/src/app/features/settings/setting-choice/setting-choice.component.css` (`.setting-choice__option--selected`) | Selecting an option switches its `font-weight` from `--text-body-weight` (400) to `--text-title-weight` (800), which **changes the label's advance width**, so the option row reflows and neighbouring buttons shift on every tap. The rule immediately above it comments that the border is declared on every option *"so choosing one cannot change any option's box"* — box stability was an explicit goal, and the weight change (plus the appearing check glyph) defeats it. Cosmetic only: no wrap, no truncation, no overflow (72/72 clean). | Either drop the weight change and rely on the background + check glyph (already "three signals, never colour alone" without it — it would still be two), or reserve the bold metrics on every option so the box does not move. |
| 6 | 🟢 Info | Documentation | `docs/features/appearance-and-language/02-design.md` §3.3 | **`--shadow-panel` — which is canonical: the code.** §3.3's table writes `0 10px 40px rgb(…)`, omitting the `0` spread. `tokens.css` ships `0 10px 40px 0 rgb(…)` for **both** themes, and `dark-theme.md` §3 — the permanent record — also carries the spread. §3.3 abbreviates the *light* row too, which the same table marks *"unchanged"* and which predates this feature. So it is a transcription shorthand in a planning artifact, not a code defect. | Optional one-line correction in `02-design.md` §3.3. Nothing in the code or in `dark-theme.md` needs to change. |
| 7 | 🟢 Info | Conventions / Testability | `frontend/src/app/core/content/property-content.service.ts` | **Exporting `resolveProperty()` / `validateConfig()` was the right call.** Verified: they are pure functions with no DI and no state; the exports add two keywords and change no signature or behaviour; **only `property-content.service.spec.ts` imports them** (grep-confirmed); and the data file stays private to the service, so neither constitution principle 2 nor `frontend/CLAUDE.md`'s import rule is touched. The reported blocker is real — the Angular unit-test system rejects `vi.mock` for relative imports — and the alternative seam (an injection token for the raw config) is a new cross-cutting pattern and therefore approval-gated, so taking it unilaterally would have been the *violation*. Residual risk: nothing mechanically stops a future component importing them; today only the doc comment does. | No action. If a hard guarantee is ever wanted, the raw-config injection token is the clean follow-up — and it needs the user's approval first. |
| 8 | 🟢 Info | Conventions / Test tooling | `frontend/src/node-builtins.d.ts` | Correct as done. `readdirSync` + a minimal `Dirent` are declared only to the extent S35 uses them, matching the file's own stated policy, and `tsconfig.app.json` excludes the file **by name** (verified: `"exclude": ["src/**/*.spec.ts", "src/node-builtins.d.ts"]`), so none of it reaches the app compilation. No dependency added; the bundle is unchanged. | No action. |
| 9 | 🟢 Info | Correctness | `frontend/src/app/core/clock/clock.service.ts` | After the first aligned tick the clock runs on `setInterval(60_000)`, which **never re-aligns**. REQ-114 is met as written (the first timer fires at the next `:00`, not 60 s from load), but this is a kiosk that runs for weeks, and `setInterval` accumulates drift and is subject to throttling. Over a long uptime the displayed minute can lag its boundary. | Optional hardening: replace the interval with a self-rescheduling `setTimeout(msUntilNextMinute(new Date()))` chain, which re-aligns on every tick and needs no new test beyond the existing boundary cases. |
| 10 | 🟢 Info | Correctness | `frontend/src/app/core/preferences/preferences.service.ts` (`hydrate`) | `Promise.race([load, this.delay(1000)])` always arms the 1 s timer, and it is **not cancelled when `load()` wins**. Harmless — `hydrate()` runs once, and `DestroyRef` clears it (there is a test for exactly that) — but it means a fast boot leaves one dangling timer for a second. | Optional: clear the timer once the race settles. |
| 11 | 🟢 Info | Scope | `frontend/src/app/core/preferences/preferences.service.ts` (`reset`) | `reset()` has **no production caller** — grep finds it only in two specs. REQ-120 asks for the *model* (`a reset() returns the screen to the property's defaults`) and that is implemented and tested, so the requirement is met; but it ships as unreachable code. Its natural caller ("what clears the record at check-out") is explicitly deferred to the backend feature in `docs/system-context.md`. | No action. Intentional and recorded. |
| 12 | 🟢 Info | Testing | `frontend/src/app/core/i18n/german-literals.spec.ts` | The scan's two nets are *catalogue values* and *German-specific characters*. REQ-107 is broader than German — it forbids **any** hardcoded user-visible string. A hardcoded English `aria-label="Main navigation"` would pass both nets. I grepped every template by hand and found **none**, so the requirement holds today; the *guard* is narrower than the requirement it guards. | Optional: add a third net for literal text in `[attr.aria-label]="'…'"` / bare `alt="…"` bindings, or accept the narrower guard and say so in the spec's header. |
| 13 | 🟢 Info | Conventions | 4 component stylesheets | `outline: 2px solid …; outline-offset: 2px` and `transform: scale(0.98); transition: transform 180ms cubic-bezier(0.16, 1, 0.3, 1)` are now repeated in `sidebar`, `action-card`, `status-bar-menu` and `setting-choice`. **Not a new violation** — the first two are pre-existing and shipped through the previous review; the new components copied the established pattern, which is the right instinct. But the pattern is now at four copies with no token behind it, and `design-sheet.md` covers motion. | Candidate for a `--focus-ring` / `--motion-press` token pair in a later pass. Not this feature's to introduce — a new token is a design-layer change. |
| 14 | 🟢 Info | Documentation | `docs/design/tokens.md` lines 187, 402 | Pre-existing broken image link: `../assets/home-no-artwork.png` where the file is at `assets/home-no-artwork.png`. Confirmed present in `HEAD` (2 occurrences) — **predates this feature**. Agree with leaving it alone. | Fix opportunistically in a docs pass. |
| 15 | 🟢 Info | Process | `docs/features/index.md` | The feature's row still reads `05_implement COMPLETE` with owner `dev`. Deliberately **not edited** by this review, per the "do not fix anything" instruction, so the record stays clean. | Update the row to `06_review **PASS ✅**` with this file's counts when the work is committed. |

---

## Assessment of the items `handoff.md` already recorded

Each was checked independently rather than accepted:

| Recorded item | My disposition |
|---|---|
| `resolveProperty()` / `validateConfig()` exported for testability | **Agree — right call.** Finding #7. The alternative was approval-gated; taking it would have been the violation |
| `node-builtins.d.ts` gained `readdirSync` + `Dirent` | **Agree.** Finding #8. `tsconfig.app.json` exclusion verified by reading it |
| `design-sheet.md` legend widened; `system-context.md` "database" → "stored server-side" | **Agree, and both were the right call.** The legend genuinely was false (📋 meant "not yet drawn" for two things that are built), and "lives in the database" asserted a datastore choice nobody has made — an approval-gated decision. Correcting it *prevented* a violation rather than committing one. Neither edit exceeded its step's intent in any way that matters |
| `--shadow-panel` spread discrepancy | **Agree it is a doc issue, and the code is canonical.** Finding #6 adds the reason: `dark-theme.md` agrees with the code, and §3.3 abbreviates the light row too |
| §3.4's light status-bar contrast row is a probable spec error | **Agree, and I reproduced the numbers — but I extend it.** Finding #2: the wrong figure is also in `dark-theme.md` §4, the permanent record, not just the feature's planning doc. That raises it from a stale planning note to a design-record defect |
| Pre-existing broken image link in `tokens.md` | **Agree, out of scope.** Confirmed present in `HEAD`. Finding #14 |
| Three optional `frontend/CLAUDE.md` proposals pending | **Correctly left pending.** `frontend/CLAUDE.md` contains *only* the S13 text; none of the three proposals leaked in. Not treated as conventions anywhere in this review |
| Settings hints at 3.80 in light — "nothing regressed; the exposure grew" | **Agree on the facts, disagree on the disposition.** Finding #3: a handoff note is not a tracked item, and the next feature will not read this handoff. It needs to become a tracked accessibility item |
| `shell.component.spec.ts` 5 s timeout = "known hardware flake, not a defect" | **Partially disagree.** Finding #4. It is genuinely intermittent and must not be "fixed" by loosening the timeout — but it is not pre-existing (it appears in no `home-screen-prototype` record) and it has a plausible, testable cause in this feature's own change: the spec now renders a component whose icons it does not register |

---

## `frontend/CLAUDE.md` — approval gate

`frontend/CLAUDE.md` **is** modified (44 lines), which is expected: S13 writes
the REQ-103 amendment. I diffed the result against `02-design.md` §4's
*"Approved verbatim by the user on 2026-08-18"* text, edit (a) and edit (b):
**both match word for word**, differing only in line wrapping, exactly as the
approval permitted. Nothing else in the file changed, and none of the three
still-pending proposals appear. **Approval gate satisfied.**

---

## Constitution v1.3.0 compliance

| Principle / Constraint | Verdict |
|---|---|
| 1 — Property content and branding are data | ✅ Every option list, default, endonym, locale and glyph name arrives via `settingsConfig`. No component hardcodes an option list |
| 2 — Content through a service, never imported | ✅ Only `PropertyContentService` and its own spec import `example-property.data.ts` |
| 3 — Guest-sensitive data never logged | ✅ **Zero `console.*` in `src/` outside `main.ts`'s pre-existing bootstrap handler.** No preference value, no key, no identifier reaches any log. `validateConfig`/`resolveText`/`getSectionRoute` throw with *field paths and ids only* — never content values — so even a boot-time throw reaching `main.ts` carries nothing sensitive. A dedicated test spies on six `console` methods across a failing load **and** an exhausted write cycle and asserts none is called |
| 4 — Self-hosted, offline, untrusted LAN, unpatchable | ✅ Built `browser/` grepped for external URLs: only W3C/Angular XML-namespace and doc strings, **no host, no CDN, no font URL**. Fonts, icons, the CDK overlay stylesheet and both catalogues ship inside the artifact. The preference path issues **no request at all** today (`sessionStorage`). Live browser check: 0 cookies, empty `localStorage`, one `sessionStorage` key. No new dependency |
| 5 — Semantic tokens only, no raw hex/px | ✅ `grep` for `var(--mesh-|--ink-|--brand-|--night-|--surface-*-raw` across `src/` matches **only `tokens.css`** — REQ-103 removed the last exception. Raw px limited to the sanctioned hairlines (`1px`) plus the pre-existing focus/motion pattern (finding #13) |
| 6 — Tests accompany code | ✅ 177 tests / 22 files, covering every behaviour REQ-124 enumerates. Quality independently spot-checked below |
| 7 — Traceability | ✅ 27 REQ-IDs, 39 steps, every step traced; no orphan step and no uncovered requirement found |
| Angular + TS strict, standalone, signals | ✅ Standalone throughout, zoneless, signals for all state; no NgModule |
| Plain CSS + custom properties, `rem` | ✅ No SCSS, no Tailwind, no component library. New tokens authored in `rem` |
| Nothing scrolls, 1024×768 → 1280×832 → beyond | ✅ **72/72 independently measured**, `overflow: visible` on `html`/`body` everywhere, including with a dropdown open. No `overflow: hidden` anywhere |
| Approved dependencies only | ✅ Manifest untouched; `@angular/cdk` (menu/overlay, unstyled) and `@ng-icons` are both pre-approved |
| Vitest only, no committed E2E | ✅ `measure.mjs` reports and asserts nothing, always exits 0, is not wired into `npm test`, and contains no assertion library — Playwright stays a review-time instrument, and the E2E decision stays open |
| No dependency-manifest edits without approval | ✅ `package.json` / `package-lock.json` unmodified |
| Branch discipline | ✅ `feature/home-screen-prototype`, nothing committed |
| Secrets never hardcoded/logged | ✅ None present |
| Input validated at every system boundary | ⚠️ Mostly — three config checks at construction, plus untrusted-stored-value validation on every field. **One gap:** `defaultAppearance` (finding #1) |
| No external runtime requests | ✅ See principle 4 |
| Security is a review category | ✅ Assessed: this feature adds **no** new HTTP surface, no new port, no new unauthenticated read or write. It adds one `sessionStorage` key with no identifier. The *future* exposure — the preference endpoint becoming the product's first unauthenticated write surface on a shared LAN — is correctly raised as an open `[NEEDS DECISION]` in `docs/system-context.md` and deferred to the backend feature |
| Design-tool requests metered | ✅ No MCP request issued by this review or by the implementation; everything read from `docs/design/**` |
| Decisions requiring approval | ✅ All four (i18n mechanism, persistence ownership, the `frontend/CLAUDE.md` amendment, the appearance state model) recorded as user-approved 2026-08-18. The one that writes to a nested `CLAUDE.md` was verified verbatim |

**Quality bar ("done"):** builds ✅ · tests green ✅ · nested `CLAUDE.md`
conventions followed ✅ · no sensitive data in logs ✅ · this file is **PASS**
with every REQ-ID implemented ✅.

---

## Test quality — spot-checked, not counted

177 is not evidence. Two mutations were applied to the **real source**, run, and
reverted (both files `cmp`-identical afterwards, mtimes preserved, `git diff`
back to the feature's own change):

| Mutation | Expected to fail | Actually failed |
|---|---|---|
| `app.config.ts` initializer stops returning `hydrate()` (`void` it instead) | 3 bootstrap tests | ✅ exactly 3: *"first thing `<html>` ever sees"*, *"defaults in effect before any stored value"*, *"boots after the bounded timeout"* |
| `preferences.service.ts` `applyStored()` drops `isConfiguredLanguage(language)` | the REQ-126 (c) test | ✅ exactly 1: *"discards a stored value the property does not configure, per field"* |

**Both reproduce the handoff's claims precisely. I agree the mutation checks
were really run.** Two structural points that make these tests good rather than
merely present:

- The no-flash test uses a **delayed** `load()` plus a synchronous
  `fixture.detectChanges()`. Both are load-bearing: with an instant `load()`
  the microtask ordering hides a missing `await`, and `await whenStable()`
  first would let a late hydration slip in ahead of the first render. The spec
  documents exactly this and warns against "strengthening" it into a claim
  about statement order it cannot make. That is an unusually honest test.
- The `<html>` assertions use a `MutationObserver` recording **every value the
  attribute ever takes**, so `toEqual(['dark'])` is a statement about the
  *absence of a correction*, not about the end state. A light→dark flash would
  produce `['light','dark']` and fail. This is the right instrument for REQ-126 (a).

**Independently confirmed test-hygiene facts:** `FakePreferenceGateway` lives
**inside** `preferences.persistence.spec.ts` (not in `src/`, not exported from
a spec); the `console` assertion is scoped to service-level paths rather than a
full boot, which correctly avoids Angular's own bootstrap logging turning
REQ-127 into a flake; and the S35 scan's "guards the guard" case proves both
nets bite before relying on them.

---

## REQ coverage — independently confirmed

`04-analyze.md` claims 27/27 with 0 orphans. **Confirmed: 27/27 delivered.**
Each row below is what *I* verified in the code, build, browser or measurement
output — not that a step exists.

| REQ-ID | Implemented | Evidence I checked |
|---|---|---|
| REQ-101 | ✅ | `docs/design/dark-theme.md` exists (227 lines) with the provenance header, the role rule, the `--night` grid, the role table, measured contrast, the active-glyph case and "what is absent". `tokens.md`'s `[NEEDS CLARIFICATION: no dark palette]` is **removed** and resolved by pointer; `design-sheet.md` cross-references it |
| REQ-102 | ✅ | Read `[data-theme='dark']` line by line: it sets **only** `--color-*` / `--gradient-*` / `--shadow-*` roles. Every dark raw value (`--ink-050`, `--ink-300`, `--night-1…16`, three `--surface-*-dark-raw`) is a **new** `:root` primitive |
| REQ-103 | ✅ | `shell.component.css` reads `--color-mesh-1…16` + `--color-mesh-base` only. `grep` for primitive reads across `src/` → **matches only `tokens.css`**. `frontend/CLAUDE.md`'s replacement clause is the approved text verbatim |
| REQ-104 | ✅ | Reproduced §3.4 myself: 8 of 9 rows within ±0.02. **Dark's weakest text role is 5.42** (glass) / 6.32 (sidebar) — no dark text role below AA. `--blur-glass` unchanged, menu:glass alpha 0.09/0.14 = the 2:3 ratio, radii/spacing/type untouched, `--gradient-icon` unchanged. Confirmed visually in `/tmp/rev-home-dark-de.png`. (The one row that does not reproduce is finding #2, a light figure, not a dark one) |
| REQ-105 | ✅ | `AppearanceMode = 'light'\|'dark'\|'system'`; `resolvedTheme()` can only return `'light'\|'dark'`; the effect writes it unconditionally, so the absent state never renders. Shipped `appearances` = light + dark only. `tokens.md` → Implementation carries the dated correction |
| REQ-106 | ✅ | Catalogues are plain TS modules — no dynamic import, no fetch. Grep for `fetch(`/`XMLHttpRequest`/`HttpClient`/`sendBeacon` across `src/` → **zero**. Built bundle contains no external host. The switch's only side effect is a `sessionStorage` write it does not await |
| REQ-107 | ✅ | Every template checked by hand: all `aria-label`s bind through `t(…)` or an input; the one `alt` is `""` (decorative). Both former literals migrated. Guarded by a 5-case scan spec (narrowness noted in finding #12) |
| REQ-108 | ✅ | `Localized<T>` maps for all 5 field groups; `resolveText()` implements active → property `defaultLanguage` → throw, with empty/whitespace treated as absent. `validateConfig()` rejects a missing default-language entry, naming the field. 5 fallback tests + 5 validation tests |
| REQ-109 | ✅ | `grep` for language literals in non-spec app source: matches **only** `catalog/index.ts` (the registry) and `example-property.data.ts` (the config). No component names a language. `DEFAULT_CATALOG_LANGUAGE` is derived from first registration, not written as `'de'` |
| REQ-110 | ✅ | `app.component.ts` holds the **only** `document.documentElement` writes in `src/` (grep-confirmed), in two effects beside `applyBranding()`. 7 tests, including that the two attributes move independently. Verified live: `<html lang>` became `en` after a keyboard-driven switch |
| REQ-111 | ✅ | `TranslationKey = keyof typeof de`; `en: TranslationCatalog = Record<TranslationKey, string>` — a missing key is a **build** error. Plus 5 runtime tests: identical key sets, no empty/whitespace values, and that English is actually translated rather than German copied through |
| REQ-112 | ✅ | `layout/status-bar/`, rendered in `shell.component.html` as a sibling of `<main>`, `position: absolute` inside `.shell`'s padding box, `:host { display: contents }`. Measured: heading y = **88**, card row bottom y = **744** at 1280×832 — unmoved, in all four theme × language combinations |
| REQ-113 | ✅ | Confirmed in the screenshot: globe + `DE` + caret, sun/moon + caret, hairline divider, time — left to right, matching the reference corner. Both controls open a dropdown listing the configured options |
| REQ-114 | ✅ | `ClockService` — `setTimeout(msUntilNextMinute)` then `setInterval`, state in a signal, no `NgZone`, no `detectChanges()`, both timers cleared via `DestroyRef`. 7 fake-timer tests including the exact-boundary start and `vi.getTimerCount() === 0` after teardown. Formatting via memoised `Intl.DateTimeFormat` on the config's locale (drift note = finding #9) |
| REQ-115 | ✅ | Built on `CdkMenuTrigger`/`CdkMenu`/`CdkMenuItem`. **Verified live in Chromium**: Enter opens, ArrowDown+Enter selects, Escape closes *and returns focus to the trigger* (`document.activeElement` = `cdk-menu-trigger status-bar-menu__trigger`). Rows are `min-height: var(--size-touch-min)` (44 px at design size). Open overlay adds no scrollbar — included in the 72/72 |
| REQ-116 | ✅ | `app.routes.ts` points `einstellungen` at `SettingsComponent` with `data: { sectionId }` dropped; the other four keep `SectionPlaceholderComponent`. Routing tests assert both halves |
| REQ-117 | ✅ | `SettingChoiceComponent` renders every option, no dropdown, each ≥ 44 px on both axes. Both screens bind the *same* `PreferencesService` signals — there is only one piece of state, so this is true by construction. Test: *"the status bar reflects a choice made on the Settings screen"* |
| REQ-118 | ✅ | `02-design.md` §9 opens with an explicit "⚠️ Design gap" section; `design-sheet.md` gained a 📋 "Selbst entworfen, ohne Figma-Vorlage" section naming both screens; `tokens.md` gained "In-house tokens — no Figma counterpart" for the seven tokens. The 📋 legend was widened so the marker is not read as "not built" |
| REQ-119 | ✅ | `PropertySettingsConfig` carries `languages` (code, shortLabel, label, locale), `defaultLanguage`, `appearances` (mode, icon), `defaultAppearance`; reached only via `settingsConfig()`. No component hardcodes an option list — the status bar and Settings both `.map()` over config |
| REQ-120 | ✅ | Overrides are `signal<T \| null>`, effective value is `override ?? default`, and `null` is never populated from `defaults`. `reset()` sets both to `null` (unreachable in production — finding #11, requirement still met) |
| REQ-121 | ✅ | One gateway contract (`load`/`save`/`clear`, whole-record, Promise-based) behind one token. `SessionPreferenceGateway` is documented as a stand-in to be *deleted*, has no schema of its own, and never resolves synchronously — deliberately, so ordering bugs surface now |
| REQ-122 | ✅ | **Checked hardest, as instructed.** `preferences.persistence.spec.ts` spreads the **real `appConfig.providers`** and overrides **only** `PREFERENCE_GATEWAY`; `App`, the initializer and both effects are the real ones. `FakePreferenceGateway` lives inside that spec. `PreferencesService` holds all policy — validation, late-arrival, optimistic update, coalescing, bounded retry — and the gateway holds none. **Was the service edited to make tests pass?** No: I read it in full and found no test-only branch, no exported internals and no hook; its file mtime moved only within the mutate-and-revert window the handoff describes, matching `app.config.ts` and `section-placeholder.component.html` (the other two mutation targets) and *not* matching any other source file; and the bundle is byte-identical to the pre-test baseline. Components inject only `PreferencesService` / `TranslationService` / `PropertyContentService` — never a gateway |
| REQ-123 | ✅ | **72/72 independently measured** across 6 routes × 2 themes × 2 languages × 3 viewports; 0 overflow, 0 truncation, 0 wraps of single-line labels, `overflow: visible` throughout. English and German both clean at the 1024 floor |
| REQ-124 | ✅ | 22 files / 177 tests covering every item the requirement enumerates: defaults and overrides, translation resolution and fallback, catalogue completeness, localised resolution + the missing-default rejection, the two `<html>` writes, the clock (fake timers, alignment, teardown), the status bar and both dropdowns, the Settings screen, and the `/einstellungen` route change. Quality spot-checked by mutation above |
| REQ-125 | ✅ | `docs/system-context.md` gains all 8 new components, the appearance/language cross-cutting section, a now-substantial **Persistence** entry, a now-substantial **Messaging / async** entry, the reserved top-right corner with its clearance numbers, the two backend seams, and a new `[NEEDS DECISION]` for endpoint security |
| REQ-126 | ✅ | (a) `provideAppInitializer` applies defaults then **returns** `hydrate()`, so bootstrap blocks — proven by mutation, and by a `MutationObserver` showing `<html>` takes the value `['dark']`/`['en']` and never a corrected pair. (b) `applyStored()` writes a field only while that override is still `null` — proven by mutation and by the deferred-load test where a tap beats the answer. (c) unconfigured values are discarded per field, for both settings, and are discarded even when no config has been applied. (d) `hydrate()` **never rejects** — the rejection handler swallows, and the race is bounded at 1 s; verified by `resolves.toBeUndefined()` and by a full boot on a `never`-resolving gateway. (e) `sendCurrentState()` never rejects, retries exactly 3 times with the configured backoff then stops, coalesces rapid changes into one fresh whole-record write, and a real Settings click with an always-failing write leaves the guest's choice selected with no `[role="alert"]`, `[role="status"]` or `.error` node |
| REQ-127 | ✅ | `StoredPreferences` is `{ language?, appearance? }` — **no identifier of any kind**, and a test asserts the stored JSON has exactly those keys. **Verified live in Chromium**: after a real language switch, `sessionStorage` held exactly `gxp.preferences = {"language":"en"}`, `localStorage` was empty and there were **0 cookies**. Nothing logs on any path (see principle 3 above); the gateway swallows storage failures without reporting a value |

**Orphan check:** 0 requirements without an implementation, 0 steps without a
requirement. Matches `04-analyze.md`.

---

## Entanglement check — is this feature separable from the uncommitted previous rounds?

**Yes, cleanly enough to commit together or separately.** The two features touch
overlapping *files* but not overlapping *concerns*:

- Every file this feature modifies from the previous feature's set is a
  **mechanical migration**: `getProperty()` → `property()`, `MenuItem` →
  `ResolvedMenuItem`, `property.x` → `property().x`, and adding
  `PREFERENCE_GATEWAY` to six specs' DI. No previous-feature *behaviour* was
  changed or re-decided.
- The one substantive edit to shared code is the mesh's move to semantic roles
  in `shell.component.css` — which is this feature's REQ-103 and carries its own
  user approval.
- Home's signed-off geometry (y = 88 / y = 744) is **unchanged and re-measured**,
  so the previous feature's REQ-014/REQ-022 sign-off still holds.

The practical consequence: they would have to be committed in order
(previous → this), because this feature's changes assume the previous rounds are
present. That is a sequencing note, not a defect.

---

## Spec drift

**None that matters.** Three deliberate, recorded deviations, all with the
implementation and the spec now in agreement:

1. `screens/home.md` — the top bar moved from "prior art, not a requirement" to
   "committed and built", including the added appearance control and the
   reserved-corner constraint. Spec updated; implementation matches.
2. `design-sheet.md` — appearance trigger carries no text label (bar width must
   not move on a language change) and the clock is Bold 700 rather than the
   sheet's Caption Medium 500 (only 400/700/800 are self-hosted). Both logged
   as deviations in the sheet itself and in `tokens.md`.
3. `tokens.md` → Implementation — the dark-mode trigger entry corrected, with
   the superseded sentence quoted rather than silently rewritten.

Findings #2 and #6 are documentation *accuracy* issues within these updates, not
drift between spec and code.

---

## Escalation to `architect`

**Not required for this feature.** It introduces a new cross-cutting seam (the
preference gateway) and the app's first persisted state, both of which would
normally warrant an architecture pass — but both were designed, written up in
`02-design.md` §6, and **approved by the user on 2026-08-18** (decisions #2 and
#4) before implementation, and the implementation matches that design.

**Recommended for the backend feature that implements `PreferenceGateway` over
HTTP.** That change turns this from a zero-request feature into the product's
first unauthenticated *write* surface on an untrusted LAN, and it lands the
still-open datastore decision. `docs/system-context.md` already carries the
`[NEEDS DECISION: how is the guest-preference endpoint secured?]` marker for it.

---

## Next action

**Mark implementation done; the user commits.** No finding blocks the merge.

Recommended order:

1. **Commit** — nothing here needs fixing first.
2. **Fix before or with the next feature:** #1 (validate `defaultAppearance`),
   #4 (register the three icons in `shell.component.spec.ts`).
3. **Correct the docs:** #2 (both `dark-theme.md` §4 and `02-design.md` §3.4),
   #6 (optional).
4. **Track, do not patch here:** #3 — light-mode secondary text below WCAG AA
   is a token-layer decision that alters the signed-off light screen.
5. **Still with the user:** the three optional `frontend/CLAUDE.md` proposals.
   None blocks anything; the file is correct as it stands.
6. **Update `docs/features/index.md`** with this review's result (left untouched
   on purpose — finding #15).
