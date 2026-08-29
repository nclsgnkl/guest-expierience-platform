# Handoff: Home Screen Prototype

## CURRENT STATE (read this first — 2026-08-28, `dev`, the rail's two-line labels)

**Owner-approved UI fix, on `refactor/bce-subpackages-and-boundary-security`,
not committed.** The rail's labels overflowed their items; the owner chose
"variant 1": reserve **two label lines for every item**, keep the item square
and keep its padding.

**Changed:** `sidebar.component.css` (`.sidebar-item__label`: `white-space:
nowrap` → fixed two-line box, `-webkit-line-clamp: 2`, `overflow-wrap:
anywhere`, `text-align: center`), `sidebar.component.html` (the three **rail**
glyphs step `--size-icon-md` → `--size-icon-sm`; the popover tile keeps `md`),
`tools/measure.mjs` (`truncated()` could not see an inline span with no
clipping ancestor — it now takes the box that bounds the text, and the rail
labels are measured against the item's content box; plus a `LABEL CLAMPED`
check). Docs: `01-prd.md` REQ-007 + the D5 note, `02-design.md` → "Text must
not wrap", `docs/design/screens/home.md` (glyph deviation), `menu-overflow.md`.

**Measured (real browser, both reference viewports × both themes × both
languages, every catalogue label in both languages probed in the live item):**
before — 208 truncated strings over 168 cells, `Smart Home` 59px ink in a
54.2px box at 1024, `Einstellungen` 63px; after — **168 cells, 0 overflowing,
0 truncated**, every item 84×84 (Δ0.00), icon tops identical within a group,
nothing scrolls. `ng test` 333/333 in 37 files, `ng build` green.

**One label still does not fit two lines:** `Ärztliche Versorgung` (20 chars)
is clamped with an ellipsis at 1024 (both states) and at 1280 in the **active**
ExtraBold state — `Versorgung` needs 69px against the 68px box; inactive at
1280 it fits (66px). Two German compounds break mid-word — `Einstellungen` →
`Einstellung/en`, `Hausordnung` → `Hausordnun/g` — because they need 79/80px on
one line. `hyphens: auto` was measured and produces **no** hyphen in this
Chromium (no German dictionary; an offline kiosk cannot rely on one).
**Owner's call**, per his own "report rather than rename" instruction.

## What's next

Review this change (`reviewer` / `/review`), then the owner commits. Nothing
here needs another `dev` step.

---

## CURRENT STATE (read this first — 2026-08-18, `dev`, round 6 — the approved `CLAUDE.md` clause)

**Phase:** `06_review` — **PASS ✅** · 🔴 0 · 🟠 0 · 🟡 0 · 🟢 4, against
`docs/constitution.md` **v1.3.0**. **Nothing is open.** The round-3 final
verification's single 🟡 (the `frontend/CLAUDE.md` primitive rule) was approved
verbatim by the user and **written on 2026-08-18**; `06-review.md` records it
as resolved. Full detail in `06-review.md` → "Round 3 — final verification".
Everything below the first horizontal rule is history: accurate, but superseded
for *what to do next*.

**Round 6 (Markdown only, `dev`):** `frontend/CLAUDE.md` → *Structure* bullet 2
now reads "Nothing else in `src/` may *read* a primitive", and names the one
allowed **write** — `app.component.ts`'s `applyBranding()` setting
`--brand-primary`, `--brand-primary-deep` and `--brand-secondary` on `:root`,
asserted by `app.component.spec.ts` — as REQ-003's branding hook *defining* the
primitive layer, not a component consuming a value. Written exactly as
approved; only line-wrapping adjusted. **Build green; 11 files / 35 tests, 0
failed** (unchanged, as expected). The exhaustive scan was re-run against the
new wording over all 25 primitives, every `.ts`/`.html`/`.css` under
`frontend/src/`, plus a `setProperty`/`getPropertyValue`/`getComputedStyle`
sweep and the one `.svg`: **reads** = `shell.component.css`'s documented
`--mesh-1…16` only, plus `app.component.spec.ts:24`; **write** =
`app.component.ts:14–16` only; `property.model.ts:8–10` names three in
comments, which is neither. The rule is now true to the letter.

**Independently re-verified, nothing taken from the round-4/5 reports:** build
green (237.20 kB initial / 64.71 kB transfer); **11 files / 35 tests, 0
failed**; no-scroll **18/18** across 6 routes × 3 viewports **plus 9 further
viewports up to 3840 × 2160** (the constitution's uncapped top end), all with
`overflow: visible` on `html`/`body`; sidebar `32/24/100×784`, nav item
`84×84`, group gaps `88`/`88`; heading `x172 y88 w390` wrapping to **2 lines at
every viewport**; card row `x172` bottom `744`; card title ink
`104/111/99/129` in a 162px box with **0 of 4 truncated everywhere**; sparkle
ink `(177,92,217)` vs the reference's `(180,89,216)` with 0 flat-navy pixels;
gradient stops `--brand-primary` → `--brand-secondary`, surviving `<base>` on
`/smart-home` and repainting live on a runtime `--brand-primary` override.

**All four round-3 🟠 Majors are fixed**, plus #5, #6, #7, #8, #9 and Infos
#12–#15. The four skips (#10 mesh Δ, #11 `gradientUnits`, #16 hero-artwork,
#17 the 1024 knife-edge) were each judged **defensible** — #11's reasoning in
particular is technically right, not an excuse: `userSpaceOnUse` would pin
pixel coordinates on glyphs sized in scaling `rem`, colliding head-on with the
viewport-proportional-root constraint.

**The `24px` glyph corrections in `home.md` were verified site by site** against
the code (`--size-icon-md` ×2 sidebar, `--size-icon-lg` + `--size-icon-sm`
card, `--size-icon-sm` sparkle) and the remaining `NEEDS CLARIFICATION` about
card 4's second empty "Info" node was correctly left as an *unverified record*
rather than "corrected" without a metered Figma query.

**The one 🟡 is closed** — see the round-6 note above and `06-review.md` →
"Proposed `frontend/CLAUDE.md` amendment (finding 1) — WRITTEN".

**What's next:** the feature is **ready to commit, and the user is doing the
commit themselves.** Rounds 3–6 are uncommitted. No agent should commit, and
the three untracked helpers `frontend/.measure.js`, `.scroll.js` and `.shot.js`
were **deliberately left in place** — `06-review.md`'s recommendation to delete
them (the committed `frontend/tools/measure.mjs` supersedes them) stands as a
suggestion for the user, not an action for an agent to take unasked.

---

## Round 5 (2026-08-18, `dev`) — the two approved doc tasks (superseded for status by the verification above; the evidence still stands)

**Phase:** `05_implement` **done, nothing open.** Round 4's single held item is
written; the round-3 review can be re-verified and flipped. Everything below
the first horizontal rule is history: accurate, but superseded for *what to do
next*.

**Build green. Tests 11 files / 35 passed / 0 failed** — unchanged from round 4,
as expected: this round touched only Markdown. No `package.json` /
`package-lock.json` edit, no token *value* changed, no E2E spec added, nothing
wired into `npm test`.

**1. `frontend/CLAUDE.md` — the three replacements the user approved verbatim
(closes #4, #7's CLAUDE.md clause, #9).** Written as given; only line-wrapping
was adjusted. In order:

- *Structure* bullet 2 now ends "…never a raw hex or px. One documented
  exception: the mesh-gradient background in `shell.component.css` composes the
  `--mesh-1…16` primitives directly, because those are gradient **layers**, not
  a colour role… Nothing else in `src/` may reference a primitive."
- *Structure* bullet 3 now carries the `property-content.service.spec.ts`
  exception, and distinguishes it from the design smell (a component doing it
  is a bug; any *other* spec doing it is the smell).
- *Tests* now names `node tools/measure.mjs` as the instrument, and records
  that constitution v1.3.0 approves `playwright` as review-time tooling only,
  with the **E2E-framework decision still open**.

**Both new rules were re-checked against the shipped code, not assumed — the
point of finding #4. Both came back clean:**

- `var(--<primitive>)` across `src/`, excluding `tokens.css`: **only**
  `shell.component.css`, 17 hits (`--mesh-1…16` in the layers plus `--mesh-6`
  as `background-color`). No other primitive reference exists anywhere in the
  app. One nuance worth a reviewer's eye rather than a silent widening of the
  rule: `app.component.ts` *writes* `--brand-primary/-deep/-secondary` with
  `setProperty()` — that is REQ-003's branding hook defining the primitives at
  runtime, not a component consuming one as a value, so the rule as worded
  holds. `property.model.ts` names them in comments only.
- `example-property.data`: imported by `property-content.service.ts` and
  `property-content.service.spec.ts` — nothing else. Exactly the exception the
  new wording carves out.

**2. `docs/design/screens/home.md` — the `aspect-[24/24]` misread purged.** The
correction landed in `tokens.md` after round 2 but never here, so this file was
still teaching the bug that made every icon render at ~⅓ size. Each of the four
`24px` claims now names the token, with **both** CSS and raw-Figma values (that
section states raw values elsewhere, so a bare "36px" would have been ambiguous):

| Site | Was | Now |
|---|---|---|
| `MenuIcons` states | "Icon glyph box is `24px`, 1:1" | box is **1:1**; size `--size-icon-md` (1.625rem / 26px CSS, ~52 raw) |
| Glass Panel icon block | "`24px` glyph" | `--size-icon-lg` (2.25rem / 36px CSS, 72 raw) |
| Glass Panel action column | "a `24px` `ArrowRight` glyph" | `--size-icon-sm` (1.5rem / 24px CSS, 48 raw) |
| Heading subtitle row | "`24px` `Sparkle` glyph" | `--size-icon-sm` (1.5rem / 24px CSS, 48 raw) |

All four match the code exactly (`sidebar.component.html` ×2 `md`,
`action-card.component.html` `lg` + `sm`, `heading-block.component.html` `sm`).
The last two are numerically unchanged, and that is stated in the file as the
coincidence it is — `--size-icon-sm` genuinely *is* 24px, so those lines were
right for the wrong reason and must not be read as corroborating the misread.

A warning block under `## Components` records the trap itself (second defect
round it has cost), in the same spirit as `tokens.md`'s.

**Other `aspect-[…]`-derived claims found:** one, unfixed and flagged in place —
the `NEEDS CLARIFICATION` note about "card 4's icon tile contains a second,
empty **24px** 'Info' node". Same suspicious number in the same file; it cannot
be confirmed without a Figma query, which is forbidden, so the new warning block
names it explicitly as unverified rather than silently "correcting" a record of
what the design file shows. `docs/design/design-sheet.md` needed **no** change:
its "Glyph-Box 12 px" was already fixed in round 4 (review Info #14).

**What's next:** `reviewer` — re-verify round 3's findings against the working
tree and flip `06-review.md`. Rounds 3–5 are still **uncommitted**.

---

## Round 4 (2026-08-18, `dev`) — round-3 review fixes (superseded for status by round 5 above; the evidence still stands)

**Phase:** `05_implement` **done for round 4**, with exactly one item held at
the approval gate. Everything below the horizontal rule is history: accurate,
but superseded for *what to do next*.

**Build green. Tests 11 files / 35 passed / 0 failed** (round-3 baseline was
34; +1 is the new sparkle test). No `package.json`/`package-lock.json` edit.

**All four 🟠 Majors and the 🟡/🟢 items named in the task are done except #4:**

1. **#1 card title** — the user chose copy over padding. Card 2 is now
   **"Smart Home"** (`id: 'smart-home-karte'`, `targetSectionId` untouched);
   padding stays `--space-5`. Measured: title ink **111px in the 162px box**
   (the old copy needed 167px), **0 of 4 titles truncated** at 1024 and 1280.
   The ellipsis fallback still works — probed by injecting the old string, it
   goes straight back to 167px/`TRUNCATED`.
2. **#2 `--size-heading` 780px → `24.375rem` (390px)**, plus `tokens.css`,
   `docs/design/tokens.md`, `docs/design/screens/home.md` (Components entry
   *and* the measured Layout table), REQ-014 and `02-design.md`.
   `--text-display` untouched, as instructed. **The visible consequence is
   there and verified: the headline now wraps to two lines** — 359 + 375px
   against the reference's 356 + 369, paragraph 347 + 150 against 348 + 150.4,
   and headline line 1's ink band is **y 130–166 in build and reference
   alike**. Zero layout side effects.
3. **#3 sparkle** — `phosphorSparkleFill` + the existing `.icon-gradient-active`
   paint server. Verified **by rendered pixels**: ink runs `(201,91,199)` top →
   `(173,92,220)` bottom, **0 flat-navy pixels**, against the reference's
   `(220,96,185)` → `(179,95,216)`; a headline control in the same scan stays
   flat `(44,31,74)` in both, so the method discriminates.
4. **#4 / #7's CLAUDE.md clause / #9 — NOT written, deliberately.** These edit
   `frontend/CLAUDE.md`, and the constitution requires the exact text of a
   nested `CLAUDE.md` entry to be confirmed **before** it is written — which is
   literally what finding #9 says was skipped last time. The exact proposed
   wording is in the round-4 report; write it only after the user confirms.
5. **#5** — `src/node-builtins.d.ts` is now excluded from `tsconfig.app.json`.
   Re-probed: an app-side file importing `node:fs` and calling `process.cwd()`
   now fails with TS2591 on both (probe deleted).
6. **#6** — `02-design.md` records that the shipped copy did **not** fit and
   why it does now; `164px` → `162px` there, in `01-prd.md` → D5 and in
   `action-card.component.css`.
7. **#7** — `frontend/tools/measure.mjs` is committed: no-scroll on 6 routes ×
   3 viewports, the REQ-022/REQ-014 boxes, wrapping/truncation, logo decode,
   optional `--screenshot`. It reports and always exits 0; it is not matched by
   `tsconfig.spec.json`'s spec pattern, so `npm test` does not pick it up, and
   it needed no `package.json` entry — run `node tools/measure.mjs`.
8. **#8** — resolved by the user's constitution v1.3.0. The docs that read as
   stale next to it (`02-design.md` test strategy, `04-analyze.md`,
   `docs/system-context.md`) now say playwright is approved *as a verification
   tool* and that the **E2E-framework decision is still open**.
9. **Info** — #12 (both unreferenced tokens now explain themselves at the
   definition site, including that nothing binds `--gradient-icon-active` to
   the SVG paint server), #13, #14, #15 applied. #10, #11, #16, #17 skipped
   on purpose — see the round-4 report.

**Re-verified after the fixes (the whole round, because #2 moves layout):**

| | 1024 × 768 | 1280 × 832 | 1440 × 900 |
|---|---|---|---|
| root font size | 12.8px | 16px | 17.3079px |
| no-scroll, 6 routes | 6/6 | 6/6 | 6/6 |
| sidebar box | 26, 19, 80 × 730 | **32, 24, 100 × 784** | — |
| nav item | 67 × 67 | **84 × 84** | — |
| heading block | x 138, y 70, w 312 | **x 172, y 88, w 390** | — |
| card row | x 138, bottom 698 | **x 172, bottom 744** | — |
| widget column | 179px | 224px | — |
| card titles truncated | 0 / 4 | 0 / 4 | — |

18/18 no-scroll with `overflow: visible` on `html` and `body`; 1024 is a
uniform 0.8× of 1280 with nothing newly wrapped (the headline is two lines at
both sizes now, by design). Mesh background vs the reference, 8 × 6 grid: worst
cell Δ **36** (round 3 measured 40) — Info #10 is unchanged, not regressed.

**What's next:** confirm (or amend) the proposed `frontend/CLAUDE.md` wording,
then `dev` writes it, then `reviewer` re-verifies. Round 3 + 4 are still
**uncommitted** in the working tree.

---

## Round 3 review (superseded by the fixes above, kept for the evidence)


**Phase:** `06_review` for round 3 — **NEEDS CHANGES ❌** (🔴 0 · 🟠 4 · 🟡 5 ·
🟢 8). Full detail in `06-review.md` → "Round 3 review". Everything below this
section is history: accurate, but superseded for *what to do next*.

**This was the first review to run the `code-review` skill's visual check
(step 0 / category 9) for real, and it paid for itself** — two of the four
Majors are invisible to any source-level review.

**Independently re-verified, not taken from the implementation report:** build
green; **11 files / 34 tests, 0 failed**; the baseline really was **28** (24
literal `it(` at HEAD + 4 from `app.routes.spec.ts`'s 5-way loop) — the `29` in
`03-todo.md`:283 and `02-design.md`:798 is a stale pre-keydown-removal number,
not a miscount; no-scroll **18/18** across 6 routes × 3 viewports with
`overflow: visible` on `html`/`body`; sidebar `32/24/100×784`, item `84×84`,
heading `x172 y88`, card row `x172` bottom `744`, cards `196` × 4 with `12`
gaps (identical to the reference edge-scan); the active glyph renders pink→
violet **on `/smart-home` too** (so `<base href="/">` does not break the
fragment reference) and follows a runtime `--brand-primary` override; 1024×768
is a uniform 0.8× of 1280×832 with nothing newly wrapped.

**The four 🟠 Majors — three of them are yours to decide, one is a plain fix:**

1. **Card title truncates** ("Haus & Funktion…"): needs 167px in a 162px box
   (196 − 2×1px border − 2×16px padding). The design's copy does not fit a real
   browser at 196px/18px ExtraBold — in the reference render Figma already
   over-runs its own padding by ~1px. Recommended fix: card horizontal padding
   `--space-5` → `--space-4` (170px box; the arithmetic limit is ≤ 13.5px).
   **Do not shrink `--text-title` — it is verified correct.**
2. **`--size-heading` is wrong by 2×, and this — not the type size — is why the
   headline renders on one line where the reference wraps to two.**
   `--text-display: 3rem` is verified correct (ink height 37px vs the
   reference's 36.8px; advance 736px vs 738px). `home.md`'s "Fixed 780px wide"
   is a **raw Figma value**; every other number in that section is 2× (199→100,
   112→56, 392→196, 443→220, 96→48). Setting `--size-heading: 24.375rem` (390px)
   at runtime reproduces the reference's line breaks *exactly* (headline
   359+375 vs 356+369; paragraph 347+150 vs 348+150.4) with no layout side
   effects. **Same error family as `aspect-[24/24]`, and it will propagate to
   the next screen if it stays.**
3. **The heading sparkle is wrong on two axes and nobody had noticed:** the
   reference shows a *filled* pink→violet gradient glyph, the build renders an
   *outline* glyph in flat `--color-text-primary`. Exactly D3's situation
   (implementation faithful to a spec that is wrong about the render), and D3
   already has your ruling — the mechanism to reuse (`.icon-gradient-active`)
   already exists.
4. **`frontend/CLAUDE.md` states "never a primitive" while
   `shell.component.css` references 17 primitives** (`--mesh-1..16` +
   `--mesh-6`), correctly and by design. This is the one Major with no decision
   attached: reword it (and the "nothing outside that service imports the data
   file" rule, which its own service spec contradicts) before anything else
   lands in `frontend/`.

**Cleared explicitly, so nobody re-opens them:** the tighter reference leading
is the *documented* 2026-08-17 line-height decision (Figma "Auto" ≈1.26 — my
measurements confirm that number); the logo mark difference is placeholder vs
placeholder; `overflow: hidden` on the card title is text truncation, not
scroll suppression; no secrets/guest data logged; no external runtime request
in `dist/`; `main` untouched.

**What's next:** rule on Majors #1–#3, then hand back to `dev` for those plus
#4 and the 🟡 items worth taking in the same pass (#5 `node-builtins.d.ts` is in
the *app* compilation — one `exclude` line; #6 `02-design.md`'s "the shipped
copy fits" is false). Round 3's work is still **uncommitted** in the working
tree. Once the fixes land, a build/test re-run plus a re-screenshot of the
heading and card row is enough to flip `06-review.md` to PASS — no full
re-review.

---

## Implementation session (2026-08-18, `dev`, round 3) — superseded for status by the review above

**Phase:** `05_implement` **done** for round 3. All of `03-todo.md` → "Round 3"
(R0–R8) is implemented, built and tested. **Next: `reviewer`, and the review
must include the visual-check step** — its absence is the entire reason round 3
existed. Everything below "CURRENT STATE (… `planner`, round 3)" is history.

**Approvals the user gave before this session, now applied:**
- Scaling mechanism: viewport-proportional root font size,
  `html { font-size: min(1.25vw, 1.9231vh) }`, **uncapped**; accessibility
  trade accepted. `docs/constitution.md` is at v1.2.0 with the "Nothing
  scrolls" constraint and the rewritten `rem`/root bullet. R7 unblocked.
- `frontend/CLAUDE.md` approved in principle and **now written** — its exact
  wording was reproduced in this session's report for the user to object to.
- Card 3's title is **"Umgebung"**, not "Entdecken", in both the shipped config
  and `docs/design/screens/home.md`'s card table.

**Result, measured in Chromium against the running dev server (port 4300):**

| | 1024 × 768 | 1280 × 832 |
|---|---|---|
| root font size | 12.8px | 16px |
| sidebar box | 26, 19, 80 × 730 | **32, 24, 100 × 784** (target, ±0) |
| nav item | 67 × 67 | **84 × 84** |
| logo→first-nav / last-nav→settings gap | 122 / 122 | **87 / 87** (>60 required) |
| heading top | y 70 | **y 88** (target 88) |
| card row | x 138, bottom 698 | **x 172, bottom 744** (targets 172 / 744) |
| overflow, all 6 routes | none | none |

Also verified: 1440 × 900 (root 17.31px) has no overflow on any route either;
the logo decodes (`naturalWidth` 24, no `error`); the active glyph renders
pink → violet on Home **and** on `/smart-home` (so `<base href="/">` does not
break the fragment reference), inactive glyphs stay flat `#2c1f4a`; overriding
`--brand-primary` at runtime recolours the glyph; no label and no card title
wraps; the four cards' title/description/arrow y-positions are pairwise
identical.

**Build:** green. **Tests:** 34 passed / 0 failed, 11 files (baseline was
28/10 — `03-todo.md` said 29, the code said 28; the code was right).

**Two things a reviewer should look at, not assume:**
1. **"Haus & Funktionen" is truncated with an ellipsis** — it needs 167px in a
   162px title box. It renders on one line (which is what R5 required) but the
   shipped copy does *not* fit, contradicting `02-design.md` → Risks ("the
   shipped copy fits"). Fixing it means either shorter copy or a design change
   (padding / card width / type scale) — neither is `dev`'s call.
2. **The headline renders one line where the reference render wraps to two**,
   because the reference's display type is visibly larger than the 3rem
   `--text-display` token. Pre-existing (rounds 1–2), no REQ covers it, not
   touched here.

**Open risk carried, deliberately not compensated for in code:** at the
1024 × 768 floor the 12px sidebar-label token renders at **9.6px** and the 14px
card-body token at **11.2px**. Recorded in `01-prd.md` → Risks with those
numbers; the hardware legibility check must happen at the floor, not only at
the design size. Bending the type scale to hide it was explicitly avoided.

**Untracked measurement helpers left in `frontend/`** (throwaway, not wired
into any npm script, no E2E framework introduced — that decision is still
open): the user's own `.shot.js`, plus `.measure.js` (geometry, wrapping, glyph
fills, logo decode) and `.scroll.js` (the no-scroll assertion across all 6
routes × 3 viewports). Delete them freely; they exist so the review can re-run
the same numbers.

---

## CURRENT STATE (read this first — 2026-08-18, `planner`, round 3)

**Phase:** `04_analyze` **PASS** for round 3 (visual-defect correction).
Planning docs are amended and consistent; **`dev` can start on
`03-todo.md` → "Round 3", tasks R1–R6 and R8.** R7 is blocked on a user
approval (below). Everything under "Post-review visual-bug fix" and earlier in
this file is history — accurate, but superseded for *what to do next*.

**Why this round exists:** the user ran the app, looked at it, and found six
visual defects. The `06_review` PASS predates the `code-review` skill's
visual-check step, so process never rendered a pixel; a green build, 29 green
tests and a full source review all passed through every one of these.

**What was changed this session (documents only — no application code):**

| File | Change |
|---|---|
| `01-prd.md` | REQ-022…REQ-025 added (full-height sidebar, active-icon gradient, no-scrollbars, scaling contract). REQ-014 and REQ-016 **amended in place** — their old wording caused two of the defects. Acceptance criteria strengthened on REQ-007/009/010/011. New "Defect trace (round 3)" section with each defect's *verified* root cause |
| `02-design.md` | New "Round 3" section: the `:host` layout convention, full-height sidebar mechanics, Home's vertical composition, the no-wrap rules, the gradient-glyph technique (four candidates compared), the no-scroll contract, and the approval-gated scaling proposal |
| `03-todo.md` | New "Round 3" section: R0 baseline → R8 close. R7 marked ⛔ blocked |
| `04-analyze.md` | Gate re-run: **PASS**, with the two open approval gates listed as such |
| `docs/design/tokens.md` | `--gradient-icon-active` added with full "measured, not extracted" provenance; `--size-sidebar-item` measured correction; "Minimum supported width" revised 1280 → 1024×768 landscape |
| `docs/design/screens/home.md` | The rejected-active-icon-fill passage now records the user's override (old rationale kept); measured layout geometry added to the Layout section |
| `docs/system-context.md` | Three new cross-cutting concerns; viewport question re-opened and re-resolved |
| `docs/features/index.md` | Status back to In Progress / round-3 analyze |

**Two things deliberately NOT written, awaiting the user:**
1. **`docs/constitution.md`** — the user approved that "no scrollbars" becomes
   a project constraint and that the version bumps 1.1.0 → 1.2.0, but the
   constitution requires the user to review the actual wording. Exact proposed
   text was delivered in this session's report; the file is untouched.
2. **The responsive scaling mechanism** (viewport-proportional root font size)
   — a new cross-cutting convention *and* a change to what the constitution's
   "16px root" means. Proposed with alternatives in `02-design.md`; blocks
   task R7 only.
   Still also open from the implementation session: the **`frontend/CLAUDE.md`
   entry**, proposed and never written. Round 3 adds to it. Confirm both as one
   file rather than in two passes.

**Verified facts worth not re-deriving** (measured this session against the
running app at 1280×832 and against `docs/design/assets/home-no-artwork.png`;
scripts were temporary and removed):

- Reference geometry: sidebar `x 32, y 24, w 100, h 784`; active nav pill
  `84 × 84` at `x 40`; card row `x 172 → 992` (4 × 196, gaps 12); card row
  bottom edge `y 744`; heading ink starts `y ≈ 88–98`.
- Built app: sidebar `y 148, h 536`; heading `y 360`; card row `x 204`
  (32px too far right — a defect the user had not spotted, caused by the empty
  `<app-hero-artwork>` still consuming a flex gap).
- **The broken logo is an XML bug, not an asset-pipeline one.** The file
  serves `200 image/svg+xml`; `new Image()` fires `error` on it; the same file
  with `--` removed from its comment loads fine. `--` is illegal inside an XML
  comment.
- `@ng-icons/phosphor-icons` `regular` glyphs are **fill-based**
  (`<svg fill="currentColor"><path/></svg>`), so `fill: url(#…)` is the right
  gradient hook — not `stroke`.
- `@ng-icons` appends its SVG via `Renderer2`, outside the template, so it
  carries no `_ngcontent-*` attribute and **no component-scoped CSS can reach
  it**.
- The measured violet `#8b5cf6` is within 1/255 per channel of the existing
  `--brand-secondary` `#8a5cf6` — it is that colour, not a new one. See the
  report and `tokens.md` for why adding a duplicate primitive would have broken
  per-property branding.
- At 1280×832 the app has **no** overflow today; the scrollbar only appears
  below 1280 because of the `min-width: 1280px` guard rail.

---

## History (superseded for status purposes)

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
