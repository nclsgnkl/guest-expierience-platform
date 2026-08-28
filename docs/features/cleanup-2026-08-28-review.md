# Review — cleanup round, 2026-08-28

Two unrelated jobs in one small round, belonging to no feature directory, so
this file rather than an appended `06-review.md`.

- **Job 1** — retire `PropertyContentService.getSectionRoute()` and document
  the mechanism that actually runs (owner-approved 2026-08-27, all four parts).
- **Job 2** — four leftovers from `guest-payload-consolidation` review round 3
  (🟡 2, 🟢 3, 🟢 4, 🟢 5).

Base: `4459859` (Part B committed). Branch
`refactor/bce-subpackages-and-boundary-security`, unchanged. Nothing committed
by this review; nothing in the tree changed by it.

## 🔍 Verdict: NEEDS CHANGES ❌

**🔴 0 · 🟠 1 · 🟡 2 · 🟢 7**

The single 🟠 is confined to `frontend/tools/measure.mjs` — an instrument, not
shipped code — and is ~8 lines of fix. **Job 1 is clean and correct in every
respect I could check.** Everything else in Job 2 is verified and closed.

| # | Sev | Category | File | Finding | Fix |
|---|-----|----------|------|---------|-----|
| 1 | 🟠 | Instrument (4th occurrence) | `frontend/tools/measure.mjs:520-527`, `:688-694` | **Round 3's 🟢 4 is not closed, and I reproduced its exact failure on this tree with the fix in place.** `COLD_COMPILE_TIMEOUT_MS` is applied via `page.setDefaultTimeout()`, so it reaches the `page.goto` and the two `.click()`s in `applyPreferences` — but **not** the line that actually expires. `waitForSelector('.setting-choice', { timeout: 60000 })` already carried an explicit 60 s, and an explicit timeout wins over the default; raising the default *to the same 60 s* changes nothing for it. Live reproduction, this tree: `node tools/measure.mjs http://localhost:4200 --route / --viewport 1024x768 --theme light --lang de` against a warm-`/`, cold-`/einstellungen` `ng serve` died with `page.waitForSelector: Timeout 60000ms exceeded … waiting for locator('.setting-choice')`, uncaught, through `triggerUncaughtException`. The 5 s `waitForFunction` on the theme/lang attributes is likewise explicit and uncaught. Because the script is top-level `await` with no top-level `try`, that throw also kills the process **before `############ SUMMARY ############` prints** — so the cells already measured lose their findings summary too, not just the remaining cells. | See **The ruling** below. Keep the constant; add `try / note() / continue` around the five `applyPreferences` calls. |
| 2 | 🟡 | Docs (stale claim) | `docs/system-context.md:148` | `dev` correctly removed the `getSectionRoute()` clause from this row — and left two false claims **inside the same sentence it edited**: *"backed by static TS today, swappable for the future Quarkus backend"* (wire-fed since REQ-510; `example-property.data.ts` was **deleted** per D6, as the service's own spec header says), and *"**Two signals:**"* introducing a list that then says *"a third, `locale()`"*. `dev` declared both and did not touch them. **This belongs in this round, not later.** The round's entire thesis is that a document naming a dead mechanism sends the next agent down the dead path; leaving a dead mechanism named in the clause you just corrected is that same defect, at a cost of about eight words. The `getSectionRoute()` sentence took three passes precisely because each pass fixed only the half it was pointed at. | `:148` → drop *"backed by static TS today, swappable for the future Quarkus backend"* (or restate it as wire-fed), and `Two signals:` → `Three signals:`. |
| 3 | 🟡 | Record accuracy (already propagated) | `docs/handoff.md` → "Two stale claims `dev` found and did not touch" | **`dev`'s diagnosis of `frontend/README.md:47` is wrong, and the wrong root cause is already written into the lead's handoff.** The claim is that following the README (`node tools/measure.mjs http://localhost:4200`, no `GXP_MEASURE_DEVICE_TOKEN`) leaves the app on `/pairing`. It does not. `ng serve` defaults to the `development` configuration, whose `fileReplacements` swap in `environment.development.ts` (`devDeviceToken: 'gxp-dev-device-token'`), and `DeviceTokenStore.load()` falls back to it whenever `localStorage` is empty — without persisting it. Tested directly: a clean Playwright context, no seeded token, `http://localhost:4200/einstellungen` → the full shell, **`.setting-choice` count 2**, `localStorage` token `null`, zero console errors. And the README's own command, re-run once `/einstellungen` had compiled, completed **1/1, 0 findings, no token**. What killed `dev`'s first attempt — and mine — is finding 1: the cold compile of the first lazy-route hit. `measure.mjs:167-171` says the token is for a **production** bundle, and the script's default base URL is `4300`, not `4200`. **The README is correct as written; adding the token to it would enshrine a false explanation.** | Correct the bullet in `docs/handoff.md` to name the cold compile. Do **not** edit `frontend/README.md:47`. A warm-up note there would be useful, and is subsumed by finding 1. |
| 4 | 🟢 | Report accuracy | `frontend/src/app/layout/sidebar/menu-split.ts:82` | `dev` reports `sidebar.component.ts:82` and `section-placeholder.component.ts:56` as `routeMatches`/`itemForRoute`'s *only* production callers. There is a third: `splitMenu` calls `itemForRoute(delivered, activeUrl)` at `menu-split.ts:82`. Intra-module, so nothing shipped is wrong — and the approved `CLAUDE.md` text claims only that the two components *share* them, which is true. | None. Recorded so the "only" is not re-quoted. |
| 5 | 🟢 | Report accuracy | `frontend/CLAUDE.md:32-42` | *"re-wrapped to 75 columns to match the file"* — measured max is **77**. That is right for the file (its lines run 71–82, mode 76–79); the number in the report is the thing that is off, not the wrap. Words verified byte-identical after whitespace normalisation. | None. |
| 6 | 🟢 | Attribution | `LOGO DID NOT DECODE` | Attribution to the accepted flake is **most likely right but under-evidenced, and the cited precedent does not support it as stated.** `sidebar-catalogue-overflow/06-review.md:691` accepted that flake *because it did not reproduce* — "0 findings across 168 cells … a third independent observation". `dev` saw it in **both** of its runs; a finding that now appears once per run is a different statistical picture from one that never appears, so the citation supports "it is a race", not "it is nothing". The mechanism is a race by construction (`!m.logo.complete \|\| naturalWidth === 0`, read after a fixed `waitForTimeout(120)`), and the note **prints `m.logo` itself** — `complete:false` means still loading (flake), `complete:true, naturalWidth:0` means a genuine decode failure. That one field settles it and was in the log being quoted from. My own full grid: **0 decode findings in 168 cells** — a fourth non-reproduction, which is why I accept the reading. | Next time quote the `m.logo` JSON the note already emits. |
| 7 | 🟢 | Guardrail deviation | Job 1, 10 files | `dev.md:154` says *"Maximum 5 file edits per step. If more are needed: **STOP**, ask for confirmation"* — `dev` proceeded and declared afterwards. **Accepted.** Deleting a field from `ActionCardShape` forces every producer and fixture in the same edit or the tree does not compile, the owner had already approved the deletion as one unit (four enumerated sub-items), and the cap exists to prevent unreviewable batches — ten mechanically-forced edits across one type deletion is not that. `tsc` 0 / 311 green confirm the tree never had to be left broken. | None. The honest declaration is what made it reviewable. |
| 8 | 🟢 | Style | `docs/features/guest-payload-consolidation/02-design.md:502`, `docs/design/screens-inhouse/connection.md:240` | Re-wrap artefacts from the 🟢 5 edit. `02-design.md:502` runs to **93 columns** against neighbours at 74–83, and stacks three em-dash clauses (*"… the English one — straight under the bar — which is a **pre-existing** …"*). `connection.md:240` is a 14-column orphan line holding only `instrument's`. | Cosmetic; re-flow if either file is touched again. |
| 9 | 🟢 | Error message | `frontend/tools/measure-states.mjs:290` | The unreadable-body branch emits `restore FAILED — <desc>: 500 ` — trailing space, no reason. The status is the actionable part, so this is fine; `?? '(body unreadable)'` would say why. | Optional. |
| 10 | 🟢 | Docs | `docs/system-context.md:148` | The *"**Two signals:**"* half of finding 2, split out because it self-corrects two clauses later (*"a third, `locale()`"*) and so is sloppy rather than misleading. | Fold into finding 2's edit. |

---

## The ruling — `measure.mjs`'s timeout (round 3's 🟢 4)

`dev` chose raise-the-timeout over report-and-continue and argued it. The lead
read the two sides as talking past each other on **setup step vs. measurement
cell** and asked for a signed judgement. Here it is.

### `dev` is right about the 168 cells, and I adopt the argument

**Do not wrap the measurement cells.** A `try/catch` around a cell converts a
real layout failure into a skipped cell, which is exactly the "slow traded for
no numbers" failure both sides argue against. `guarded()` in
`measure-states.mjs` protects *state-mutating* blocks with real preconditions;
the 14-route loop has none, and wrapping it would swallow genuine findings.
That reasoning is correct and it is now the recorded position — nobody should
re-propose wrapping the grid loop.

### `dev` is wrong that this closes the finding, and the evidence is mechanical

The finding named `applyPreferences`. `applyPreferences` has four throw sites:

| line | call | timeout before | timeout after | caught? |
|---|---|---|---|---|
| 521 | `page.goto('/einstellungen')` | 30 s (default) | **60 s** | no |
| 522 | `waitForSelector('.setting-choice')` | **explicit 60 s** | **explicit 60 s — unchanged** | no |
| 524-525 | two `.click()` | 30 s (default) | **60 s** | no |
| 527 | `waitForFunction(theme/lang)` | **explicit 5 s** | **explicit 5 s — unchanged** | no |
| 533-547 | `waitForFunction(preference write)` | explicit 10 s | explicit 10 s | **yes — `note()`s** |

The fix moves the default. **The line that expires does not use the default.**
`dev`'s own strongest supporting point — *"the file already concedes 60 s for
this same cold compile, one line below the `goto`"* — is, read carefully, the
argument **against** the fix: the concession is on the line that actually times
out, so matching the default to it buys that line nothing.

Reproduced on this tree, with the fix in place, on this box:

```
$ node tools/measure.mjs http://localhost:4200 --route / --viewport 1024x768 --theme light --lang de
grid: 1 routes x 1 themes x 1 languages x 1 viewports = 1 combinations
############ PASS 1 …
page.waitForSelector: Timeout 60000ms exceeded.
  - waiting for locator('.setting-choice') to be visible
    at applyPreferences (tools/measure.mjs:522:14)
```

Uncaught, process dead, **no SUMMARY**. The same command after
`/einstellungen` had compiled: `1 … measured, 0 overflowing, 0 truncated,
0 finding(s)`. So the cause is the cold compile and 60 s is demonstrably not
enough here. Raising it to 120 s would be the fourth re-timing of the same
class and would still be a number someone guessed.

### What report-and-continue should wrap — exactly

The **setup call**, per `(theme, language)` axis. Not the cell.

```js
for (const theme of themes) {
  for (const language of languages) {
    try {
      await applyPreferences(page, theme, language);
    } catch (error) {
      note('INSTRUMENT',
        `preferences ${theme}/${language} at ${w}x${h} skipped: ${error?.message ?? error}`);
      continue;                       // never measure against unknown state
    }
    /* the 14-route loop stays completely unguarded */
  }
}
```

Three properties make this a closure and not a swallow:

1. **It wraps a step that produces no number.** Nothing in `applyPreferences`
   is a measurement; it only puts the page into a known state.
2. **`continue` is load-bearing, not optional.** If setup failed, the page's
   theme and language are unknown, and running the 14 routes anyway yields 14
   *mislabelled* numbers — worse than none. (`PREFERENCE NOT APPLIED` would
   catch it, but as 14 findings that read like a product defect.) Skipping is
   the honest outcome.
3. **The blast radius is arithmetic, not a hope.** 14 routes × 2 themes ×
   2 languages × 3 viewports = 168; one axis is **14 cells, 8.3 %**. Today the
   same expiry costs up to 168 cells *and* the summary. The instrument's
   purpose is to produce numbers; 154 numbers plus a named gap beats zero
   numbers.

The same guard belongs on the four later `applyPreferences` calls (`:791`,
`:844`, `:912`, `:1012`), where it is per-cell and costs even less.

**Keep `COLD_COMPILE_TIMEOUT_MS`.** It genuinely helps the `goto` and the two
clicks, it is well reasoned and the docblock is good writing. It is simply not
the finding. The answer is **both**, not either.

**Signed: raising the window is not the fix.** This is the fourth pass at the
class (Part A 🟢 12 · Part B round 1 🟡 6 · round 3 🟢 4 · here) and the first
one with a live reproduction against the current tree. If it ships unguarded
again it should be reported as 🟠 on sight, not 🟢.

---

## The two items `dev` found and did not touch

| Item | Judgement |
|---|---|
| `frontend/README.md:47` | **Neither here nor later — it is not a defect.** See finding 3. The README's command is correct for `ng serve`; the token is a production-build concern. The wrong root cause is already in `docs/handoff.md` and should be corrected there. |
| `docs/system-context.md:148` | **Here.** See finding 2. `dev` edited that very clause; the stale claims are inside the sentence it corrected, and they are the same defect class the round exists to close. |

## The four declared deviations

| # | Deviation | Judgement |
|---|---|---|
| 1 | Job 1 touched 10 files, over the 5-per-step cap | **Accepted** — see 🟢 7. Correct outcome; `dev.md:154` asks for a stop-and-ask that did not happen, mitigated by the owner's prior approval of the deletion as one unit and by the honest declaration. |
| 2 | The approved `CLAUDE.md` text re-wrapped | **Accepted.** Verified word-for-word (below). The stated column (75) is off by two; the wrap itself matches the file. |
| 3 | ARTWORK's two restores guarded beyond the three named | **Accepted, and it is the better call** — not merely consistency. Before, `await putProperty(…)` was followed by `await fetch(DELETE …)`; a throwing `putProperty` skipped the `DELETE` and orphaned the uploaded throwaway asset in the dev instance. The two are now independent, so the `DELETE` runs regardless. That is a real leak closed. |
| 4 | Four stale prose references the brief did not enumerate | **Accepted, and required rather than a deviation** — the brief's own instruction was to *"grep for any other place that presents it as current"*. All four verified (`resolveText` docblock, `action-card.component.ts` docblock, its spec comment, the service spec header), and I found no fifth: `getSectionRoute` and `targetSectionId` are **zero** in `frontend/src/`, and every surviving hit is a dated feature document or the retirement note itself. |

---

## Job 1 — verified item by item

| Claim | Result |
|---|---|
| The `CLAUDE.md` bullet is the owner-approved text, verbatim | ✅ Extracted `docs/handoff.md:415` and the bullet from `frontend/CLAUDE.md:32-42`, stripped the `> - ` marker, collapsed all whitespace: **equal**. The re-wrap changed no word, no punctuation, no backtick. |
| The sentence is **true**, not just approved | ✅ `routeMatches` exported at `menu-split.ts:39`, `itemForRoute` at `:44`; `sidebar.component.ts:82` and `section-placeholder.component.ts:56` are the component callers; both normalise through the same `normalise()`; `''`/`'/'` equivalence is covered at `menu-split.spec.ts:53-54`. |
| It is now **complete** — the third pass at this sentence | ✅ It names the live mechanism, both its functions, both its callers, the reason they share it, *and* records the retirement so the next agent does not go looking. Nothing in the frontend resolves a route by id any more. |
| `getSectionRoute()` deleted with its docblock bullet and both spec `it` blocks | ✅ Method, docblock bullet at `property-content.service.ts:27`, and the two `it` blocks gone. (The handoff said *"four spec cases"* — that was four **assertions** across two `it` blocks; 3 + 1. Not a lost case.) |
| Nothing else lost a test | ✅ Static `it(`/`test(` count per spec file, HEAD vs. tree: **exactly one file changed, exactly −2**; the other 33 identical. 313 → 311 is honest. |
| The deleted cases' surviving claim is still covered | ✅ The only claim not about the dead function — *"the settings entry resolves too"* — is covered against the **live** mechanism at `section-placeholder.component.spec.ts:100` (`resolves the settings item, which is not part of menuItems`). |
| `ActionCardShape.targetSectionId` deleted, nothing read it | ✅ Declaration, the `requireText` that wrote it, one mapper-spec assertion, three fixtures and two comments — and no reader. `requireText` still has 8 other call sites, so no helper went dead. |
| `card.targetKey` still read, wire field untouched | ✅ Both halves. `guest-content.mapper.ts:211` still builds `` id: `${card.targetKey}-${index}` ``, consumed by `home-content.component.html`'s `@for (card of home.cards; track card.id)`. `packages/api-client/model/guestActionCard.ts:22` `targetKey: string` untouched, and `packages/` has no diff at all. |
| `docs/system-context.md` rows rewritten | ✅ `ActionCardComponent` now reads *"navigates the server-resolved `route` the wire delivers (REQ-511) — there is no client-side id→route lookup"*. `PropertyContentService` row: correct on this point, see 🟡 2 for what was left beside it. |

## Job 2 — verified item by item

| Item | Result |
|---|---|
| 🟡 2 `restore()` helper | ✅ Applied at **all four** `finally` blocks, five calls; no unguarded restore remains. I probed an exact copy against six fabricated responses — ok / 409-with-body / rejected fetch / unreadable body / **a non-`Response` return** / **a non-`Error` throw**: **0 throws, 5 findings, ok path silent**. The last two `dev` did not probe; both are handled (`Cannot read properties of undefined (reading 'ok')`, `boom`). `putStay` and `putProperty` both return the `fetch` promise, so `restore` really does receive a `Response`. |
| 🟢 3 style note | ✅ Now states the revert is one line short, names the hunk (`@@ -249 +353,2 @@`), and says why it was left. Matches round 3's recommendation exactly. The hunk reference is a dated record of round 3's measurement and no longer re-derivable against the current file — correct, and attributed as such. |
| 🟢 5 language attribution | ✅ **Four** documents, not three: `connection.md:238`, `02-design.md:501`, `guest-payload-consolidation/handoff.md:355`, `index.md:19`. Each attributes de/en. Note the shorthand *"x935…953 German / x1173…1195 English"* is **not** what the documents say and is wrong: the pairs are per-viewport (1024: de 935 / en 953; 1280: de 1173 / en 1195). The documents are right; the shorthand should not be re-quoted. |
| 🟢 4 timeout | ❌ See the ruling. `setDefaultTimeout` present at all five `newPage()` sites (5 of 5, verified by count). |

---

## My own numbers

Every number below was produced by this review, not read from a report.

| Check | Result |
|---|---|
| `npx tsc --noEmit` | **0 errors** |
| `npx ng test` (run alone, no dev server, no JVM) | **34 files, 311/311 passed**, 18.8 s |
| Test arithmetic | Static per-file `it(` diff vs. HEAD: **−2, in one file**. No case lost silently. |
| `npx ng build` | Clean. **main 460.08 kB** (105.03 kB transfer), styles 9.27 kB — matches `dev` exactly |
| `node tools/measure.mjs` full grid | **168/168 cells, 0 overflowing, 0 truncated, 0 finding(s)** |
| Home baselines, all four 1280×832 cells | `baseline check (1280x832): heading.y 88 vs 88, cardRow.bottom 744 vs 744` ×4 — Part A's committed baselines unmoved |
| `LOGO DID NOT DECODE` in my grid | **0** (fourth independent non-reproduction) |
| `GXP_SMOKE_ADMIN_PW=… node tools/measure-states.mjs` | **44/44 `NO-SCROLL OK`, 0 finding(s), 8 blocks**, all five restores reporting success |
| Dev-instance end state after the run (manager API) | stay `Anna & Tom` / room 1 / `BOOKED`; `property.artworkAssetId: null` — restores genuinely restored |
| Backend | `git status` shows **no** change under `backend/`, `api/` or `packages/` → the **441 · 0** baseline stands, not re-run |
| Dependency manifests | `package.json` / `package-lock.json` / `pom.xml` untouched |
| Constitution / nested `CLAUDE.md` | Constitution untouched. Only `frontend/CLAUDE.md` changed, only the owner-approved bullet. The queued `overflow-wrap` entry is **not** written (`grep overflow-wrap frontend/CLAUDE.md` → nothing) |
| Branch | `refactor/bce-subpackages-and-boundary-security`, no commit, tree unchanged by this review |

### Visual check (skill step 0 — not skipped)

Captured Home at 1280×832 / light / de and compared against
`docs/design/assets/home-no-artwork.png`. Composition matches: mesh gradient,
sidebar with the Home pill, sparkle + subtitle, headline, paragraph, the
deliberately empty widget band, four glass panels with icon / header /
description / arrow, status bar top-right. Every difference traces to a landed
decision, none to this round — `Mehr` in slot 5 (Part A's overflow trigger),
`Smart Home` instead of `Haus & Funktionen` (`home-screen-prototype` 06-review
finding 1), the seed guest name and logo. The headline wraps to three lines for
`Anna & Tom` with no overflow, and `heading.y 88` / `cardRow.bottom 744` are on
baseline. This round has no rendered surface of its own — a deleted type field,
comments, docs and two instruments — so the check is a regression guard, and it
is clean.

---

## Ready to commit?

**Not yet — one 🟠 stands between here and a commit, and it is small.**

The product half (Job 1 plus 🟡 2 / 🟢 3 / 🟢 5) is finished, verified and
would be safe to commit as it stands. The instrument half is not: 🟢 4 was
escalated for a signed decision, the decision is *"the window is not the fix"*,
and signing it without implementing it is precisely how this became a
fourth occurrence.

### Top 3 recommendations

1. **Guard `applyPreferences`, keep the timeout** — the `try / note() /
   continue` above, at all five call sites. ~8 lines. Then re-run the grid
   against a genuinely cold `ng serve`; the run should reach `SUMMARY` even if
   an axis is skipped.
2. **Finish `docs/system-context.md:148`** — drop *"backed by static TS
   today"*, `Two signals:` → `Three signals:`. Eight words, and it closes the
   last stale sentence about this service.
3. **Correct the README bullet in `docs/handoff.md`** to name the cold compile
   rather than a missing device token, and leave `frontend/README.md:47`
   alone.

---

# Review — Round 2 (the fix round), 2026-08-28

Round 1's 🟠 was implemented, and implementing it uncovered **two further
defects in the same three lines**. Both were fixed in the same thread. This
round reviews all three changes plus the documentation that carries them.

Base still `4459859`; branch `refactor/bce-subpackages-and-boundary-security`
unchanged; nothing committed or edited by this review. Round 1's Job 1 and its
🟡 2 / 🟢 3 / 🟢 5 items were re-checked and are unmoved.

## 🔍 Verdict: PASS ✅

**🔴 0 · 🟠 0 · 🟡 3 · 🟢 7**

The three defects are genuinely closed, and I closed them by experiment rather
than by reading the diff. Nothing blocks the commit. Two of the three 🟡 are
~10-word documentation edits that belong in the same commit; the third is a
fourth silent path I recommend **recording, not fixing**.

## The three defects, each tested against the shipped bytes

| # | Defect | How I tested it | Result |
|---|---|---|---|
| 1 | `applyPreferences` killed the run before `SUMMARY` | fulfilled `**/einstellungen` with an empty document for **one** theme axis, so `waitForSelector('.setting-choice')` really expires at its own explicit 60 s | `INSTRUMENT: preferences light/de at 1024x768 skipped: page.waitForSelector: Timeout 60000ms exceeded`, the **dark axis still measured** (`1 combination`, `NO-SCROLL OK`), PASS 2/3/4 ran, **`SUMMARY` printed, exit 0** |
| 2 | the poll never blocked | four shapes of `page.waitForFunction` timed against a deliberately mismatching server state | the **real old predicate resolved in 103 ms holding `false`** (98 ms on a second run); `() => Promise.resolve(false)` 84 ms; `async () => { await 0; return false; }` 13 ms; the sync control `() => false` correctly timed out. Playwright 1.62's own poller is `const success = predicate(); if (success) { fulfill(success); return; }` — a **synchronous truthiness test on a Promise**. The node-driven loop with the same predicate body ran the full 10 s and returned `false` |
| 3 | `!token` short-circuits before any fetch | full unseeded grid + narrowed unseeded runs | the startup note fires **exactly once per process** (1 finding in a 168-cell run, 1 finding in a `--route / --viewport 1024x768 --lang de` run); **no per-cell `unpaired` note anywhere** |

### The forced race, end to end

`PUT /api/guest/preferences` aborted in the browser so the write provably
cannot land; server pre-set to `light/de`; the axis asks `dark/en`.

| run | `PREFERENCE WRITE NOT CONFIRMED` | server after |
|---|---|---|
| **OLD** (`git show HEAD:frontend/tools/measure.mjs`, abort injected) | **0 — silent** | still `light/de` |
| **NEW** (this tree, abort injected) | **5** (one per `applyPreferences` call) | still `light/de` |
| **NEW**, write allowed | 0, run clean | `dark/de` |
| **NEW**, poll path swapped to a non-existent `/api/guest/contentXX` | — | `INSTRUMENT: GET /api/guest/content answered 404 — the preference guard confirmed nothing` ×5 |

### `dev`'s "168 clean cells prove the field path" — tested, and it holds

Injecting `content.preference?.appearanceXX` produced **5 ×
`PREFERENCE WRITE NOT CONFIRMED` and nothing else**: no `PREFERENCE NOT
APPLIED`, no contrast noise, and the server ended on the asked value. The app
was fine; only the comparison failed. So the fetch, the
`content.preference.appearance/language` path and the comparison are all live,
and the clean 168-cell run is a real confirmation rather than an accident.

### The 16-mislabelled-cells claim — **reproduces**, under control

The natural race did not fire in either of my unseeded runs (168/168, 1
finding, the note). So I forced the condition it needs — a *slow* write, not a
blocked one — by delaying `PUT /api/guest/preferences` by 2000 ms, and ran the
same command twice at `1024x768 --lang de` (28 cells):

| run | findings |
|---|---|
| unseeded (guard inert) | **23** — the note, **14 × `PREFERENCE NOT APPLIED: … asked light/de got dark/de` (one entire axis, all 14 routes)**, and **8 spurious `CONTRAST OFF SPEC`** inherited from the mislabelled axis |
| seeded (guard live) | **0** |

Same tree, same command, same injected latency; the guard is the only
difference. That is the claim justifying this thread, reproduced with its blast
radius — and it also settles what the guard buys: not tidiness, but the
difference between 23 findings and none.

## Findings

| # | Sev | Category | File | Finding | Fix |
|---|-----|----------|------|---------|-----|
| 1 | 🟡 | Docs (falsifiable claim) | `frontend/README.md:51-55` | **It has not drifted back to the withdrawn `/pairing` claim — but it overshoots the other way.** *"the app renders and measures the same either way"* is false in exactly the situation the paragraph exists for: with a slow write the unseeded run measured a **whole axis under the previous theme** and reported 23 findings where the seeded run reported 0 (table above). *"nothing is written to `localStorage`"* is also broader than intended — it is the **device token** that is not written. | Keep the paragraph; qualify the claim: the app still renders identically because the dev build carries its own token, **but** a slow preference write can then be measured under the previous theme, which surfaces as `PREFERENCE NOT APPLIED`. |
| 2 | 🟡 | Record accuracy | `docs/handoff.md:299`, `:319` | The handoff still says `frontend/README.md:47` *"must not be 'fixed'"* and **"Do not 'fix' the README."** — while this round adds a paragraph to that file, and the handoff records neither the fourth-option decision nor the addition. The next session reads a standing prohibition and a diff that appears to violate it. | Scope the prohibition to the withdrawn `/pairing` diagnosis and record the decision that replaced it (report once at startup; option (b) rejected because defaulting the token would silently pair a production build). |
| 3 | 🟡 | Instrument (a fourth silent path) | `frontend/tools/measure.mjs:572`, `:759` | **Asked whether there is a fourth inertness: yes, one, and it is narrow.** `unpaired` is silent *unconditionally*, but the startup note that speaks for it is gated on `!seedToken`. Those are different conditions — a **seeded** run whose page has no token says nothing at all: no startup note (a token was supplied) and no per-axis note (the branch is silent). Reachable via the 401 wipe (`GuestDataService.handleUnauthorized()` clears the key; `seedContext`'s `addInitScript` only re-seeds on the *next* navigation) or a blocked `localStorage`. Same shape as the three defects this thread found: a state meaning "confirmed nothing" returning like a success. | **Record beside the `401`/`403` case, do not fix.** It is latent for the same reason that one is, and this round should not grow. One line if ever wanted: note when `outcome === 'unpaired' && seedToken`. |
| 4 | 🟢 | Instrument | `measure.mjs:614` | The skip note embeds Playwright's whole multi-line `error.message` (the `Call log:` block), so one skipped axis occupies 4 lines of the `SUMMARY` list and the `- ` lines stop matching the finding count by eye. Observed in every forced-failure run. | `error.message.split('\n')[0]`. |
| 5 | 🟢 | Instrument | `measure.mjs:573`, `:589` | The 404 note hardcodes `/api/guest/content` in node while the fetch names the path in the page — two copies of the one value whose drift caused the original defect. | One `const` used by both, interpolated into the message. |
| 6 | 🟢 | Docs | `docs/system-context.md:148` | *"**Three signals:**"* is a wrong count in a new way — the service exposes **five** (`loaded`, `branding`, `settingsConfig`, `property`, `locale`). **Round 1 prescribed this exact edit, so this is the review's residue, not `dev`'s error.** | *"Three of its signals:"*. |
| 7 | 🟢 | Docs | `docs/system-context.md:22` | The header note quotes references to *"static TS today"*; that phrase no longer occurs verbatim. `:592` (*"the kiosk still renders static TS until …"*, inside a dated log bullet) is a genuine referent, so **the lead's decision to keep the note is right** — only its quotation marks are now approximate. | None, or widen the quote. |
| 8 | 🟢 | Instrument | `measure.mjs:566-585` | The deadline is checked only at the top of the loop, so the wait can overshoot `PREFERENCE_CONFIRM_TIMEOUT_MS` by one `evaluate` plus the 500 ms sleep (the lead measured 12.2 s against a 10 s budget). Harmless — worth knowing before anyone treats 10 s as a bound. | None. |
| 9 | 🟢 | Docs | `frontend/README.md:48` | Every unseeded run now carries a finding, so the documented command can never read *"0 findings"* again. If the seeded form is the recommended one, make **it** the documented command and keep the note purely as the deviation warning. | Optional. |
| 10 | 🟢 | Instrument | `measure.mjs:608-616` | `preferencesApplied` returns `true` when the poll reported NOT CONFIRMED or `endpoint-missing` — a known-bad axis is still measured, deliberately (the per-cell `PREFERENCE NOT APPLIED` is the direct check and 14 numbers beat none). Correct, but the docblock explains the *thrown* case only. Cost when it happens, measured: 1 mislabelled cell drags 8 spurious `CONTRAST OFF SPEC` with it. | One sentence in the docblock. |

## The two decisions I was asked to disagree with, if I did

**The `401`/`403` case stays recorded, not fixed — agreed.** The reasoning is
sound and it is the same family as finding 3 above, which I also recommend
recording rather than fixing. Both are latent: a rejected token wipes the kiosk
to `/pairing`, and the next axis then dies loudly in `waitForSelector` — which
I saw incidentally when a 404-injected probe made the settings screen
unreachable: 5 loud INSTRUMENT skips, `SUMMARY`, exit 0.

**The stale Chromium tree — agreed, and the lead's measurement is right.**
Measured again: the main process is **10.1 MB RSS**, its four children between
0.4 and 3.8 MB, against **2.6 GB available**. My round-3 wording ("a standing
drag on exactly the runs that are flaky here") was overstated; the flakiness on
this box tracks the Quarkus JVM and the first Playwright injected-script
install, both of which I saw again today (the first `waitForFunction` of a
session took 11.8 s to service a 3 s timeout, the sync control 4.7 s).

## My own numbers

| Check | Result |
|---|---|
| `npx tsc --noEmit` | **0 errors** |
| `npx ng test`, run alone, both servers stopped first | **34 files, 311/311**, 32.1 s |
| `npx ng build` | clean — **main 460.08 kB** (105.03 kB transfer), styles 9.27 kB |
| `GXP_MEASURE_DEVICE_TOKEN=… node tools/measure.mjs` full grid | **168/168, 0 overflowing, 0 truncated, 0 finding(s)** |
| Home baselines, all four 1280×832 cells | `heading.y 88 vs 88, cardRow.bottom 744 vs 744` |
| same grid **unseeded** | **168/168, 1 finding** — the startup note, once |
| `LOGO DID NOT DECODE` | **0** in both grids (fifth and sixth non-reproduction). The note does emit `m.logo` as round 1 asked; `{"complete":false,…}` reads as a loading race |
| Guard forced to fire | axis skipped, remaining axis measured, `SUMMARY` printed, **exit 0** |
| Backend / `api/` / `packages/` | no diff → **441 · 0** stands, not re-run |
| Dependency manifests | `package.json`, `package-lock.json`, `pom.xml` untouched |
| Constitution / nested `CLAUDE.md` | untouched; the queued `overflow-wrap` entry is still **unwritten** (`grep` → 0) |
| Branch / commits | `refactor/bce-subpackages-and-boundary-security`, HEAD `4459859`, nothing committed |
| Environment | Quarkus dev, `ng serve` and the Dev Services Postgres container all started by this review and all stopped again |

### Visual check (skill step 0 — not skipped)

This round has no rendered surface — an instrument, a README paragraph and two
documentation lines — so the check is a regression guard. Home captured at
1280×832 / light / de and compared against
`docs/design/assets/home-no-artwork.png`: composition matches, every difference
traces to a landed decision (`Mehr` in slot 5, `Smart Home`, the seed guest
name and logo), the headline wraps to three lines with no overflow, and the
baselines are on figure.

## Ready to commit?

**Yes.** 🔴 0 · 🟠 0. Findings 1 and 2 are ~10-word edits that belong in this
commit rather than after it; finding 3 belongs in the handoff's "recorded,
deliberately not fixed" section beside the `401`/`403` case; 4–10 are optional.
