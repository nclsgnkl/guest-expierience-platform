# Handoff: Home Screen Prototype

## CURRENT STATE (read this first — 2026-08-29 **review pass 2 fix run**, `dev`: the hero artwork derived from `--size-sidebar`, two cap rationales corrected, the instrument's 2px blind spot closed — **reviewed by pass 3: PASS ✅, ready to commit**)

> **Status after `reviewer` pass 3 (2026-08-29, `06-review.md` → "Sidebar-rail
> review — pass 3"): PASS ✅** — 🔴 0 · 🟠 0 · 🟡 4 · 🟢 3 against
> constitution **v1.10.0**. All three of pass 2's 🟠 Majors are closed and were
> independently re-measured, both instruments re-run (`measure.mjs` 168 cells /
> 0 findings, `measure-states.mjs` 52 cells / 0 findings — the four
> `SPEC: artwork` findings are gone). The four 🟡 were wrong *numbers* or wrong
> *attributions* in these write-ups, never behaviour; they were swept in a
> follow-up `dev` pass the same day — the 72px figure in §1 below (pass-3 #1), the
> `measure.mjs` trigger-comment rationale (#2), a dated superseding note on
> pass 2's formula in `06-review.md` (#3), and the "Pass 1's 🟢 #12–#14"
> attribution below (#4). One code comment changed; no declaration did.
> **The rail work is through — and it is still uncommitted. The owner commits;
> no agent does.** 15 modified files, 0 untracked, on
> `refactor/bce-subpackages-and-boundary-security`; the 2026-08-28 round is
> already in `63fcc6d`. Two things stay on the record rather than being
> laundered: `measure.mjs` **run 2 of three carried 48 `PREFERENCE NOT APPLIED`
> + 7 `CONTRAST OFF SPEC` under load** while geometry was identical in all three
> (§ below), and **pass 1's 🟢 #12–#14 are deliberately left open**.

**Owner-requested, on `refactor/bce-subpackages-and-boundary-security`, not
committed.** `reviewer` pass 2 (written up in `06-review.md` → "Sidebar-rail
review — pass 2") returned **NEEDS CHANGES** (🔴 0 · 🟠 3 · 🟡 5 · 🟢 2 against
constitution v1.10.0). This run works findings #1–#7; #8 was already repaired by
that pass itself, #9 and #10 are 🟢 and ask for nothing. It supersedes the
section below for *what to do next*; nothing in it contradicts that section's
evidence except where said so in place.

> **Both instruments ran end to end against a live instance**, `./mvnw
> quarkus:dev` (Quarkus 3.33.3.1, Dev Services PostgreSQL 17.11) behind the
> `ng serve --port 4300` that was already running. Three throwaway Playwright
> probes in `/tmp` covered what the instruments do not report; they were
> deleted, never written into the repo. Everything below written as *measured*
> was measured in this session; the two arithmetic derivations say so.

**1. The hero artwork is derived from `--size-sidebar` now (owner decision).**
The owner ruled *"abgeleitet, bleibt bei 396.5"* — the artwork keeps its
specified position and stops following the rail. The **implementation
consequence** is one line in
`frontend/src/app/features/home/hero-artwork/hero-artwork.component.ts`:

```
left: calc(24.78125rem - var(--space-7) - var(--size-sidebar) - var(--space-8));
top:  calc(4.84375rem - var(--space-6));
```

*Arithmetic, not a measurement:* `.home-content`'s origin is not a constant —
it is `--space-7` (the shell's inline padding, 2rem) + `--size-sidebar` +
`--space-8` (the shell's row gap, 2.5rem) horizontally, and `--space-6`
(1.5rem) vertically. `24.78125rem` is the specified frame x of **396.5px** and
`4.84375rem` the specified y of **77.5px**, both at the 16px baseline root, so
the expression is "the spec coordinate minus the origin" and resolves to
220.5px today where the old literal `14.03125rem` was 224.5px. **The reviewer's
suggested `calc(24.78125rem - var(--size-sidebar))` is 72px out at 1280 × 832
and 57.6px at the 1024 × 768 floor** — it subtracts the sidebar but not the
shell's padding and gap (`--space-7` 32 + `--space-8` 40 = **72**), so it puts
the artwork at **x 468.5** where the spec says **396.5**, and at **374.78**
where the spec says **317.17**; the error is a `rem` quantity, so it scales with
the root and cannot honestly be quoted as one absolute number. It was re-derived
here rather than adopted. *(Corrected 2026-08-29 by review pass 3, finding #1:
this sentence said "68px out", which is the distance to the old, already-wrong
literal 400.5 — the wrong baseline. The figures above are measured against the
spec position.)* `top` was derived in the same shape although only
`left` was faulted: its literal was the identical mistake waiting for a padding
change, and its value is unchanged.

*Measured*, `tools/measure-states.mjs` ARTWORK cell, live: the rendered image
box is now **`{x:397, y:78, w:775, h:500}`** at 1280 × 832 and
**`{x:317, y:62, w:620, h:400}`** at 1024 × 768, in both themes — the spec box
of `docs/design/screens/home.md:431` (`775 × 499.5 @ x396.5 / y77.5`) and its
0.8× at the floor, to the instrument's whole pixel. Home's own geometry is
untouched in the same cells: `heading.x 176`, card row `x176 … right 996
bottom 744`.

**2. The four `SPEC: artwork` findings were a regression, not "pre-existing" —
and they are gone.** Before this run `measure-states.mjs` reported
`artwork x 401 vs 397` and `x 320 vs 317` in four cells. Pass 2 traced them to
the widening (`../guest-frontend-integration/handoff.md:407` records
`{x:397, y:78, w:775, h:500}` when the content column was at 172); the +4 / +3
is exactly that. **Measured after the fix: `tools/measure-states.mjs` — 52
cells `NO-SCROLL OK`, no `COLLISION` in any banner cell, 9 blocks, 0
findings.** No residual, not even a rounding one. `docs/features/index.md` said
the instrument was *clean* while it was not; that entry now carries this run's
numbers **and** the findings' origin.

**3. Both `max-height` caps are load-bearing, and the three places that said
otherwise are corrected** (`sidebar.component.css:83…` and its tile comment,
`docs/design/screens-inhouse/menu-overflow.md`). They claimed the rail item's
height is held by `width: 100%` + `aspect-ratio: 1` **alone**, and that the
tile — "unlike the rail item" — needed the cap. **Measured this session** with
`-webkit-line-clamp` neutralised and each cap then removed in turn, on one
overlong label:

| | 1280 × 832 | 1024 × 768 |
|---|---|---|
| rail item, clamp + cap (shipped) | 88 | 70.41 |
| rail item, clamp off, cap on | 88 | 70.41 |
| rail item, clamp off, **cap off** | **131.97** | **117.98** |
| popover tile, clamp + cap (shipped) | 88 | 70.39 |
| popover tile, clamp off, **cap off** | **135.56** | **108.39** |

`aspect-ratio` does not pin a flex item whose content overflows it. Both boxes
need the cap, for the same reason — which `01-prd.md:351` and `02-design.md`
already said correctly and the CSS's own comment 130 lines further down had
measured. Pass 2's 1280 figures reproduce exactly; its 1024 rail-item figure
(105.52) does not, because that number depends on how many lines the chosen
string takes in the narrower box — the conclusion is identical either way.

**4. `tools/measure.mjs`'s 2px blind spot is closed, and the fix was verified
with a case that used to slip through.** `truncated()` compared a rail label's
ink against the **item's** border box (88 / 70.41) while `overflow: hidden`
sits on the **label** (86 / 68.81), so ink in that 2px band — exactly
`2 × --size-sidebar-label-inset` — was clipped on screen and reported
`truncated: false`. Both rail-label call sites now pass the label itself
(`labels` and the overflow trigger). **Only the `labels` site had that blind
spot:** the trigger passed `boxEl = null`, so `truncated()` fell back to
`el.clientWidth` — for a label with zero padding and no border that is the
label's own box already, **86** at 1280 and **69** at 1024, and it caught both
constructed cases below on its own. What the change buys at that second site is
sub-pixel precision (`borderWidth` **68.81** instead of an integer-rounded 69)
and the separate, real `singleLine: true → false` correction. *(Corrected
2026-08-29 by review pass 3, finding #2: this sentence, and the comment in
`measure.mjs`, called the trigger site "the same bug". The change itself is
right and stays.)* **Constructed case, measured:** with the label's
`overflow-wrap: anywhere` neutralised — the regression this reconstructs is a
future change losing it — the string `Einstellungen..` renders **87px of ink in
the 86px label inside the 88px item** at 1280 × 832, and `Einstellungen.`
**70px in 68.81 inside 70.41** at 1024 × 768. Old comparison: `false` in both.
New comparison: `true` in both. Independently clipped on screen in both
(`label.scrollWidth 87 > clientWidth 86`, and 70 > 69), `overflow-x: hidden`.
The live grid is unaffected: **168 cells, 0 overflowing, 0 truncated, 0
clamped, 200 × NO-SCROLL OK, 0 findings**, with `labelBoxes` still
`delta −2 = expected −2, ok` in all six rail items and the reported `boxW` now
86 rather than 88.

**5. Four text drifts swept.** `01-prd.md` REQ-007's "the glyph is centred and
24px wide" → 26px (its twin in `sidebar.component.css` had already been pulled
— finding #4); `docs/features/index.md`'s two instrument claims (#2, #6);
`02-design.md`'s "the reference's own longest label (\"Einstellungen\") measures
~84px" (#7) — **re-measured here** in the shipped rail by injection into a live
item: `Einstellungen` **77 / 77** and `Hausordnung` **78 inactive / 79 active**
at 1280 × 832, `Einstellungen` **63 / 66** and `Hausordnung` **64 / 64** at the
floor. So the superlative is wrong at the design size and right at the floor,
which is the cell the decision turned on; the `~84` was never re-derived from
the reference render and nothing corroborates it. This file's own
"corrected wherever it was written" is softened to what was actually done, and
its point 1 in the section below is annotated in place.

**Changed — code:**
`frontend/src/app/features/home/hero-artwork/hero-artwork.component.ts`
(derived `left`/`top`, class comment),
`frontend/src/app/layout/sidebar/sidebar.component.css` (two comments only, no
declaration changed), `frontend/tools/measure.mjs` (two call sites + the
comments that misdescribed them).
**Changed — docs:** `docs/design/screens-inhouse/menu-overflow.md`,
`01-prd.md`, `02-design.md`, `docs/features/index.md`, this file.
**Seven files — over the five-file limit, covered by the enumerated findings**
(#1 code, #3 three sites, #4/#6/#7 three documents, #2 two records).

`ng build` green (477.05 kB initial / 108.58 kB transfer). `ng test`
**333/333 in 37 files** — unchanged, as it should be for a change jsdom cannot
see (`frontend/CLAUDE.md` → Tests).

## What's next

**The owner commits.** `reviewer` pass 3 covered this run together with the
uncommitted 2026-08-29 rounds below and returned **PASS ✅** (🔴 0 · 🟠 0 ·
🟡 4 · 🟢 3); its four 🟡 were accuracy corrections to this file, to
`06-review.md` and to one comment in `measure.mjs`, and are all swept. Nothing
🔴 or 🟠 is open against the feature, and nothing from pass 2 is left open by
this run. **Nothing here is committed, and no agent commits** — 15 modified
files, 0 untracked, on `refactor/bce-subpackages-and-boundary-security`; the
2026-08-28 round is already in `63fcc6d`, so what is uncommitted is the two
2026-08-29 rounds, the pass-2 fix run and the pass-3 sweep.

**Pass 1's 🟢 #12–#14 remain open by design** — none of them asks for a
change, and pass 3 re-measured all three as still exactly true: the two-line cap
is exactly two line boxes (label height **31.19 = 2 × 15.595** at 1280 × 832,
**24.95 = 2 × 12.47** at 1024 × 768, zero slack), and `Einstellungen` active at
the floor is **66px of ink in a 68.81px box = 1.405px of clearance per side**.
They are notes for whoever next touches `--text-label-line-height` or the 1024
floor. *(Corrected 2026-08-29 by review pass 3, finding #4: this said "Pass 2's
🟡 #12–#14 from the earlier review". Pass 2 has no #12–#14 — it stops at
#10 — and pass 1's are 🟢, not 🟡. The **earlier, unrecoverable** 2026-08-29
review whose numbering `measure.mjs` cites may have numbered them differently;
see `06-review.md`'s reconstruction warning, :623–632.)*

---

## CURRENT STATE (2026-08-29 **review round**, `dev`: the rail glyph back to 26px, a cap for the popover tile, and the record caught up)

**Owner-requested, on `refactor/bce-subpackages-and-boundary-security`, not
committed.** A `reviewer` run over the uncommitted rail work returned
**NEEDS CHANGES** (🔴 0 · 🟠 5 · 🟡 6 · 🟢 4). This pass works those findings.
It supersedes numbers in the sections below; every statement it overtook is
annotated in place there, dated, rather than left standing.

> **The measurement instruments really ran this time.** The reviewer could not
> run them — without a backend the build renders `/pairing` and the sidebar has
> zero items — and fell back to a hand-rolled harness. This pass brought up
> `./mvnw quarkus:dev` (Quarkus 3.33.3.1, Dev Services PostgreSQL 17.11) and
> `ng serve --port 4300`, so **`node tools/measure.mjs` (168 cells) and
> `GXP_SMOKE_ADMIN_PW=… node tools/measure-states.mjs http://localhost:4300`
> both ran end to end against a live instance.** Three throwaway probes
> (rail-item geometry, popover-tile geometry, `--size-sidebar` A/B) were used
> for what the two instruments do not report; they were **not** committed.
> Anything below written as *measured* was measured; anything derived says so.

**1. The rail glyph is `--size-icon-md` again** (`sidebar.component.html`
lines 18, 39, 90; line 70 was the popover tile and already `md`). The owner's
instruction was "measure, then decide", and the measurement retires the only
justification the 24px ever had (`26 + 4 + 2 × 15.6 = 61.2px` against a *60px*
content box — the item is 88px / 64px now, and the two-line reservation that
arithmetic was paid for is a cap). This is a **return to the extracted Figma
value**, not a new deviation. **It is a new change on top of `63fcc6d`, where
the 24px is committed** — not the repair of an uncommitted mistake.

Measured myself: **both reference viewports, active and inactive, with every
seeded catalogue label of both languages injected into a live rail item**,
one- and two-line. The probe deliberately does not vary the theme — no size or
weight token is theme-varying (`[data-theme]` overrides colour roles only,
`frontend/CLAUDE.md` → Appearance & language) — and the theme × language grid
is `tools/measure.mjs`'s 168 cells, quoted under the table.

| | 24px (before) | 26px (after) |
|---|---|---|
| item @1280, one line / two lines | 88 × 88 / 88 × 88 | **88 × 88 / 88 × 88**, Δ0.00 |
| item @1024, one line / two lines | 70.41² / 70.41² | **70.41² / 70.41²**, Δ0.00 |
| label box @1280 / @1024 | 86 × 15.59 / 68.81 × 12.47 | unchanged |
| ink widths, both weights, 17 labels | — | unchanged — the label box does not depend on the glyph |
| glyph top in the item @1280, one / two lines | 22.20 / 14.41 | **21.20 / 13.41** |
| glyph top in the item @1024, one / two lines | 17.78 / 11.55 | **16.97 / 10.73** |
| ink bottom above the item's bottom @1280 / @1024 | 22.80 / 18.25 | **21.80 / 17.45** |
| pair vs. item centre @1280 / @1024 | 0.30 / 0.23px | **0.30 / 0.24px** — unchanged |
| clamped / clipped / mid-word breaks | none | **none** |

`tools/measure.mjs`: **168 cells, 0 overflowing, 0 truncated, 0 LABEL CLAMPED,
200 × NO-SCROLL OK, 0 findings.** Sidebar box `x32 y24 w104 h784` (REQ-022).
> **Instrument note, so the next reader is not surprised.** The grid was run
> three times on this machine (a Raspberry Pi carrying Quarkus dev mode, the
> Angular dev server and Chromium at once). Two runs came back with **0
> findings**; the middle one reported **48**, all of them `PREFERENCE NOT
> APPLIED` — the theme/language write goes through the Settings UI to the
> backend and did not land inside its 10 s budget on some cells — plus the
> seven `CONTRAST OFF SPEC` rows that follow mechanically from comparing a
> dark cell against the light spec. **Geometry, wrapping and no-scroll were
> identical in all three runs** (0 overflowing, 0 truncated, 0 clamped,
> 200 × NO-SCROLL OK each time). It is instrument flakiness under load, not a
> regression — recorded rather than quietly re-run until green.
So: nothing else was needed — no new token, no changed spacing, no wider rail
— which is why this did not go back to the owner as a second decision.

**2. The overflow popover's tile got the rail's `max-height` cap**
(`sidebar.component.css`). The docs said the tile was *"checked and
deliberately left alone"*; that covered the **centring** only. The rail item's
height is pinned by `width: 100%` + `aspect-ratio: 1` whatever its label does
— the tile's is not. Measured with `-webkit-line-clamp` forcibly neutralised
on a five-line label: rail item stays **88 / 70.41** square, tile went
**88 → 104.38** at 1280 and **70.39 → 83.45** at 1024. With the cap the tile
is square again, and with the clamp armed (Chromium 151) **nothing rendered
changes**: tiles 88 / 70.39, panel 490 × 208 / 392.33 × 166.77.

**3. `--size-sidebar` has a third consumer and it is now written down.**
Besides `.sidebar` and `.sidebar-overflow__grid`, `staleness-banner.component.css`
bounds itself with `max-width: calc(100% - var(--size-sidebar) - …)`.
Measured with `tools/measure-states.mjs`:

- **First order: none.** The bound resolves 1076 → **1072px** at 1280 and
  860.8 → **857.59px** at 1024, against a bar at most **694px** wide. Not
  binding; the bar's boxes are unchanged (`x459 w540` / `x569 w679` German
  shell, `x446 w553` / `x554 w694` English standby).
- **Second order, the real one.** The bar is right-anchored and did not move;
  Home's headline column slid 4px (3.19 at 1024) right. In the worst cell the
  instrument produces — the 37-character unbroken display name, five headline
  lines — the bar clears the headline ink by **5px at 1280** (bar x569, ink to
  x564) and **~6px at 1024** (bar x458.83, ink to x453); with the seeded name
  7px / ~10px. **No COLLISION in any banner cell.** The clearance holds, so
  nothing was changed — but it is the margin
  `../guest-payload-consolidation/06-review.md` already called *"four and five
  pixels of luck"*, ~4px thinner, on a manager-authored headline.

**4. Where the 4px actually lands: nowhere visible.** Measured by comparing
both `--size-sidebar` values in one live render. At 1280 × 832
`.home-content__screen` stays **820** and `--size-heading` stays **390**; the
whole 4px comes out of `.home-content__widgets`, **224 → 220** (179.25 →
**176.06** at 1024). That column is `flex: 1` and empty by design. Recorded
because the owner rejected `+0.5rem` as "too expensive for the content area" —
its real price would have been 8px of an empty column. **Not an argument to
reopen the decision**, which he took and which shipped.

**5. `Einstellungen` is not the longest label.** Measured at 1280 × 832,
`Hausordnung` is wider (78 inactive / **79** active against 77/77) and is that
viewport's tightest cell — 4.0 / **3.5px** per side against 4.5px. At the
1024 × 768 floor the order reverses (`Hausordnung` 64/64 → 2.41px;
`Einstellungen` 63/66 → 2.91 / **1.41px**), and the floor is the cell the
decision turned on. The decision stands; the superlative was wrong and was
corrected in `docs/design/tokens.md` and `docs/design/screens/home.md`.
⚠️ **"Wherever it was written" was an overstatement** (review pass 2,
finding #7): two sites still carried it — `02-design.md`'s superseded
"Sidebar labels" paragraph and this file's own point 1 below. Both are
annotated in place as of the fix run at the top of this file, and the ink
figures were re-measured there rather than restated.

**6. The 2026-08-28 run is committed, in `63fcc6d`.** Verified against the
commit. Only the two 2026-08-29 runs are uncommitted. `handoff.md` and
`docs/features/index.md` said otherwise and now say this.

**Changed — code:** `frontend/src/app/layout/sidebar/sidebar.component.html`
(3 rail glyphs), `frontend/src/app/layout/sidebar/sidebar.component.css` (the
tile's `max-height` cap; three stale comments: `5.25rem` → `5.5rem` twice, and
"the glyph is centred and 24px wide" → 26px).
**Changed — docs:** `docs/design/tokens.md`, `docs/design/screens/home.md`,
`docs/design/screens-inhouse/connection.md`,
`docs/design/screens-inhouse/info.md`,
`docs/design/screens-inhouse/menu-overflow.md`, `01-prd.md` (REQ-014's
`x = 172` → `x = 176`, same treatment as REQ-022; the D5 note's glyph price),
`02-design.md`, this file, `docs/features/index.md`.

`ng build` green. `ng test` **333/333 in 37 files**.

## What's next

~~Review this pass (`reviewer` / `/review`) together with the two uncommitted
2026-08-29 runs below. Then the owner commits. Nothing here needs another
`dev` step.~~ **Overtaken 2026-08-29** — that review happened (`06-review.md`
→ "Sidebar-rail review — pass 2", NEEDS CHANGES) and its findings were worked
in the fix run at the top of this file. Read that section for what to do next.
The 26px glyph revert and the tile's cap, which this pass introduced, were
both verified positively by that review and are unchanged.

**Left open on purpose:** the review's 🟡 #12–#14 were not part of this pass's
brief and are untouched.

---

## CURRENT STATE (2026-08-29 afternoon, `dev`, the wider rail, the 1px label inset and variant B2 — read the section above first)

**Owner-requested UI fix, on `refactor/bce-subpackages-and-boundary-security`,
not committed.** This is the **second** run of 2026-08-29 and it supersedes
parts of the section below it (that morning's two rail corrections); every
statement there that this run overtook has been **corrected in place**, dated
and pointed here, rather than left standing.
⚠️ **That promise was only half kept, and the review of 2026-08-29 said so.**
Five figures in the morning section were left standing after this run
overtook them — the label box, two `in 84` box references, the icon top and
the single-line ink pair — and one of them (*"icon top inside the item …
unchanged"*) was wrong when written. All five are annotated in place now,
with the values re-measured in a real browser.

> **This session was interrupted by a power cut on the machine, and finished
> in a follow-up `dev` pass the same day.** Before the cut: `tokens.css`,
> `sidebar.component.css`, `tools/measure.mjs`, `docs/design/tokens.md`,
> `01-prd.md`, `02-design.md`. After it, catching the as-built record up:
> `docs/design/screens/home.md`, `docs/design/screens-inhouse/menu-overflow.md`,
> this file and `docs/features/index.md`. **The follow-up pass ran no
> measurements** — it re-used the numbers already recorded in
> `docs/design/tokens.md` and `02-design.md`, and says so wherever it restates
> one. No code was touched in it; the code side was complete and green before
> the cut.

**Three owner decisions, one implementation each.** All three are on the rail
label; the owner's complaint was that the ink sits flush against the active
pill's border and he asked for a **2px** inset per side.

1. **2px was rejected on measurement, not on taste, and so was 1px** at the
   width that then shipped: at `--size-sidebar: 6.25rem` both put
   `Einstellungen` — ~~the longest seeded label~~ **the tightest cell at the
   1024×768 floor** (corrected in place 2026-08-29, review pass 2 finding #7:
   `Hausordnung` is the wider label at 1280×832 — re-measured this session,
   78 inactive / 79 active against `Einstellungen`'s 77 / 77 — and the order
   reverses at the floor, 63 / 66 against 64 / 64, which is the cell the
   decision turned on), a standard module, on screen on
   every route — back onto two lines at the 1024×768 floor. The owner weighed
   `+0.5rem` (which would carry the full 2px), **rejected it as too expensive
   for the content area**, and took **`+0.25rem` with a 1px inset**:
   `--size-sidebar` 6.25rem → **6.5rem**, plus a new in-house token
   `--size-sidebar-label-inset: 0.0625rem`.
2. **`6.5rem` is a deliberate deviation from Figma**, which has 199 raw / 100
   CSS px. Recorded, not corrected, in `docs/design/tokens.md` → Sizing and at
   both places in `docs/design/screens/home.md` where a reader meets the
   figure. **A re-import will bring `6.25rem` back and that is not a
   correction.**
3. **Variant B2** — the two-line *reservation* of 2026-08-28 is gone. With the
   second line reserved but empty, the visible icon+label pair sat 4.20px
   above the item's centre (`align-content: center` moved the line inside the
   box and could not fix that). The label is now as tall as its own text and
   `.sidebar-item`'s own `justify-content: center` centres the real pair. What
   still keeps D5 fixed is the item's `aspect-ratio: 1` plus a two-line
   `max-height` **cap** — measured with `-webkit-line-clamp` forcibly removed,
   without the cap a five-line label takes the item 70.41 → 103.91px. The
   owner chose B2 over B1 (keep the reservation, keep every icon on one column
   line) knowing the price, which is in the table below.

**Changed:** `frontend/src/styles/tokens.css` (`--size-sidebar`, new
`--size-sidebar-label-inset`), `frontend/src/app/layout/sidebar/sidebar.component.css`
(label width/margin now carry the inset, fixed `height` → `max-height` cap,
`align-content` removed), `frontend/tools/measure.mjs`. Docs: `01-prd.md`
(REQ-007's precision note, REQ-022's `w = 100` → `w = 104`, the D5 blockquote),
`02-design.md` → "Text must not wrap", `docs/design/tokens.md`,
`docs/design/screens/home.md`, `docs/design/screens-inhouse/menu-overflow.md`,
`docs/features/index.md`.

**Measured (real browser, Chromium 151, both reference viewports × both themes
× both languages), before = the morning of 2026-08-29:**

| | before | after |
|---|---|---|
| `--size-sidebar` | 6.25rem (100px @1280) | **6.5rem** (104px) |
| rail item | 84 × 84 (67.22² @1024) | **88 × 88** (70.41²) |
| label box @1280 / @1024 | 84 / 67.23 | **86 / 68.81** |
| `Einstellungen` clearance @1280, both weights | 3.5px per side | **4.5px** |
| `Einstellungen` clearance @1024 | 2.11 inactive / **0.61 active** | **2.91 / 1.41** |
| content column x / right edge @1280 | 172 / 992 | **176 / 996** |
| heading ink top y / card row bottom y | 88 / 744 | 88 / 744 — **unchanged** |
| status bar → card row clearance | 52px | **48px** (status bar is right-anchored and did not move at all) |
| popover panel @1280 / @1024 | 470 × 200 / 376 × 160 | **490 × 208 / 392 × 167** |
| popover tile @1280 / @1024 | 84 / 67 | **88 / 70** |
| visible pair vs. item centre @1280 / @1024 | 4.20 / 3.37px high | **0.30 / 0.23px** (half-leading, not a layout error) |
| icons of a 1-line and a 2-line item | same line | **7.79px apart @1280** (6.23 @1024) |

`tools/measure.mjs`: **168 cells, 0 overflowing, 0 truncated, 0 clamped**,
200 × NO-SCROLL OK, plus every state cell of `tools/measure-states.mjs`. The
popover is still inside the viewport at both sizes and its grid does not
scroll. `ng test` 333/333 in 37 files, `ng build` green.

**Four things a reviewer should measure rather than take on trust:**

- **1.41px per side is now the tightest cell** — ExtraBold `Einstellungen` at
  1024×768. It is 0.8px better than the morning's 0.61px and it is what the
  owner accepted in exchange for not spending another 0.25rem of content area.
  Nothing beyond `+0.5rem` was tried, because `+0.5rem` was rejected.
- **The content area is 4px narrower at 1280×832** (3.19px at 1024×768) and
  that is the entire cost of the widening. Home's signed-off vertical baseline
  is untouched — the loss is horizontal only. Worth re-measuring rather than
  believing, because it is the one thing a "just make the rail wider" change
  can quietly break.
- **B2's price is real and is not visible today**: two items with a different
  number of lines no longer share an icon line. All six labels the rail
  currently shows are one-liners, but **6 of the 26 seeded catalogue labels
  take two lines in a rail item** ("Anreise & Abreise", "Müll & Recycling",
  "Ärztliche Versorgung", "Essen & Trinken", "Arrival & departure", "Waste &
  recycling"), so enabling one into a sidebar slot is enough to show it.
- **The rail glyph's 24px is now justified by an argument that no longer
  holds.** It stepped `--size-icon-md` → `--size-icon-sm` on 2026-08-28
  because 26 + 4 + 2 × 15.6 = 61.2px did not fit the then-84px item's 60px
  vertical content box. The item is 88px now (64px content box) and the
  two-line reservation that arithmetic was paid for is gone. Nothing was
  changed on the strength of arithmetic and **no measurement was made** —
  flagged in `docs/design/screens/home.md` under the 2026-08-28 deviation.
  **Whether the rail goes back to a 26px glyph is the owner's call.**
  ⚠️ **Answered 2026-08-29 (owner: "measure, then decide"): it goes back.**
  Measured, the item stays exactly square at 26px — see the section at the top
  of this file.

**All clearances above presume the shipped Plus Jakarta Sans** (self-hosted,
constitution principle 4). With the `woff2` blocked the fallback measures
`Einstellungen` at 78.69 / 62.91px in the same 86 / 68.81px box — it fits,
at 3.66 / 2.95px, but that is the fallback's metrics being kind, not a
property of the layout.

## What's next

Review this change **together with the 2026-08-29 morning run below it**.
⚠️ **Corrected 2026-08-29 after review: it is not "all three".** The
2026-08-28 run is **committed, in `63fcc6d` ("frontend adjustments")** —
verified against the commit: `sidebar.component.html` (the three rail glyphs
`--size-icon-md` → `--size-icon-sm`), the two-line `sidebar.component.css`,
`measure.mjs`'s `truncated(boxEl)` and its `LABEL CLAMPED` check, plus
`connection.md`, `info.md`, `menu-overflow.md`, `home.md`, `01-prd.md`,
`02-design.md`, this file and `staleness-banner.component.css`. **Uncommitted
are the two runs of 2026-08-29 only.** That matters for one thing in
particular: the rail's 24px glyph is committed, so putting `--size-icon-md`
back is a *new* change on top of `63fcc6d`, not the repair of an uncommitted
mistake. Then the owner commits.

---

## CURRENT STATE (2026-08-29 **morning**, `dev`, the owner's two rail corrections — superseded the same afternoon, read the section above first)

**Owner-requested UI fix, on `refactor/bce-subpackages-and-boundary-security`,
not committed.** Two complaints about the 2026-08-28 two-line rail, both fixed
in `sidebar.component.css` alone:

1. **"Einstellungen" broke mid-word** ("Einstellung/en"). Cause was not the
   two-line envelope but the box it was measured in: `width: 100%` on
   `.sidebar-item__label` is the item's **content** box (68px), 16px narrower
   than the 84px item that paints it, while the word needs **77px**. The
   label now gives the item's `--space-3` inline padding back
   (`width: calc(100% + 2 * var(--space-3))` + matching negative
   `margin-inline`) and spans the item edge to edge. No per-item exception,
   no new token, no type change, no wider sidebar.
   ⚠️ **The last three clauses were overtaken the same afternoon** — see the
   2026-08-29 section above: `--size-sidebar` did go to 6.5rem and
   `--size-sidebar-label-inset` is a new token, so the label spans the item
   edge to edge *less 1px per side*. The box argument in this paragraph is
   unaffected and still the reason the word fits.
2. **One-line labels sat at the top of the reserved two-line box.**
   `align-content: center` centres the single line in it.
   `-webkit-box-pack: center` was tried first and **does nothing** on this
   `-webkit-box` in Chromium 151 (measured one property at a time), so only
   the modern property is in the file.
   ⚠️ **Reverted the same afternoon (2026-08-29, variant B2).** Centring the
   *line* in a box whose second half was blank still left the visible pair
   4.20px above the item's centre; the reservation is gone and neither
   `align-content` nor `-webkit-box-pack` is in the file any more. The stated
   *reason* `-webkit-box-pack` was inert was also wrong — with the clamp set,
   Chromium computes `display` to `flow-root`, so it is not a `-webkit-box`
   at all. See the 2026-08-29 section above and `02-design.md` → "Text must
   not wrap".

`tools/measure.mjs` had to follow (one call site): the rail label now has a
box of its own that is *wider* than the item's content box, so comparing ink
against the item — correct on 2026-08-28, when the span had no box — would
report a TRUNCATED that is not there (77px of ink in an 84px box read as 77
in 68).

**Measured (real browser, Chromium 151, both reference viewports × both
themes × both languages; ink measured against the item's border box):**

| | before | after |
|---|---|---|
| label box @1280 / @1024 | 68 / 54.44 | **84 / 67.23** *(86 / 68.81 since the afternoon)* |
| `Einstellungen` @1280 | 2 lines (needs 77 on one) | **1 line**, 3.5px clear per side, 700 and 800 alike *(4.5px since the afternoon)* |
| `Einstellungen` @1024 | 2 lines (needs 63 / 66) | **1 line**, 2.11px clear inactive, **0.61px active** *(2.91 / 1.41 since the afternoon)* |
| `Settings` (en) | 1 line both viewports | unchanged, 1 line |
| `Smart Home`, `Hausordnung` | 2 lines | 1 line (72 / 78–79 in 84) *(same ink in an **86** box since the afternoon; `Hausordnung` at 79 is 1280's tightest cell, 3.5px per side — see the correction in `docs/design/tokens.md`)* |
| `Ärztliche Versorgung` | **clamped** at 1024 and at 1280 in ExtraBold | **not clamped anywhere** (69 in 84 at 800) *(in an **86** box since the afternoon)* |
| item box | 84×84 / 67.22² | unchanged, Δ0.00 *(88×88 / 70.41² since the afternoon)* |
| icon top inside the item | 12.41 / 9.94 | ~~unchanged~~ — **wrong, and it was already wrong when written**: re-measured 2026-08-29, **22.20 @1280 / 17.78 @1024** (the morning's `align-content` moved the line inside the reserved box, which is what `02-design.md` (c) describes). **21.20 / 16.97** since the glyph went back to 26px |
| single-line ink, top→bottom inside the item | 40.41 / 28.59 | ~~**48.2 / 20.8**~~ — re-measured 2026-08-29: glyph top **22.20**, ink bottom **22.80** above the item's bottom at 1280 (17.78 / 18.25 at 1024). **21.20 / 21.80** and **16.97 / 17.45** since the glyph went back to 26px |
| two-line ink | 40.41 / 13 | unchanged |

`node tools/measure.mjs`: **168 combinations, 0 overflowing, 0 truncated, 0
LABEL CLAMPED**, 200 × NO-SCROLL OK, popover panel 376×160 / 470×200 and its
9 tiles unchanged *(the panel grew to 392×167 / 490×208 with the afternoon's
wider rail — it derives from the same expression)*. The run's one finding is the pre-existing `INSTRUMENT`
note about `GXP_MEASURE_DEVICE_TOKEN`. Every catalogue label in both
languages was also injected into a live rail item at both viewports and in
both weights: **nothing clamps, nothing is 3 lines**. `ng test` 333/333 in 37
files, `ng build` green.

**Two things a reviewer should look at rather than take on trust:**

- **0.61px per side is the whole clearance** for an ExtraBold `Einstellungen`
  at 1024×768. It fits, it is not clipped, and a screenshot shows the pill's
  edge clear of the ink — but any inset large enough to see would re-break
  the word at that viewport, so none was added. If the owner does not like
  how tight that reads, the remedies are all his to choose (a wider
  `--size-sidebar`, or dropping ExtraBold on the rail label), and none was
  taken here.
  ⚠️ **The owner did not like it, and this is exactly the remedy he took the
  same afternoon**: `--size-sidebar` 6.25 → 6.5rem plus a 1px inset, which
  puts the tightest cell at 1.41px. The paragraph stands as the record of why
  the morning stopped where it did; "none was taken here" is true of the
  morning run only. See the 2026-08-29 section above.
- **A one-line label no longer shares its first baseline with a two-line
  neighbour** — it sits half a line (7.8px at 1280) lower. That is the direct
  consequence of centring, and the 2026-08-28 comment that claimed both
  alignments has been corrected in place. Icon alignment, the thing D5
  actually needed, is untouched.

**The overflow popover's tile was checked and deliberately left alone.** It
carries the same `-webkit-line-clamp: 2` but **reserves no second line**, so
its one-line labels are already centred (glyph top 19.2px / ink bottom 19.8px
in the 84px tile, against 11.41 / 12 for a two-line tile — the flex column
centres the whole group, at the price of tile glyphs not sharing a line,
which the panel accepts and the rail does not). `align-content` there would
be a no-op. Recorded in `menu-overflow.md`.
⚠️ **Two qualifications added 2026-08-29 after review.** (1) The four figures
above are the **84px** tile's, measured before the same day's widening to
88px; `menu-overflow.md` carries the same caveat and the conclusion does not
turn on them. (2) *"Deliberately left alone"* covered the **centring** and
only the centring — the rail's two-line `max-height` **cap** was never
assessed for the tile, and the tile needed it: measured with the clamp
neutralised, the rail item stays 88 / 70.41 square while the tile went
**88 → 104.38** at 1280 and **70.39 → 83.45** at 1024. The tile carries the
same cap now; with the clamp armed nothing about the rendered panel changed.

**Docs updated (as-built record, dated 2026-08-29, owner decision vs.
implementation consequence attributed separately):** `01-prd.md` REQ-007 (a
precision note) and the D5 blockquote's "every first text line aligns too",
`02-design.md` → "Text must not wrap", `docs/design/screens/home.md`
(second deviation on `MenuIcons`), `docs/design/screens-inhouse/menu-overflow.md`
(why the tile did not follow), and the `.sidebar-item__label` comment itself.
*(All five were rewritten again the same afternoon — read them as the
end-of-day state, not as this run's.)*

## What's next

~~Review this change, then the owner commits.~~ **Overtaken 2026-08-29
afternoon** — review the section above instead, which contains this one.

---

## CURRENT STATE (2026-08-28, `dev`, the rail's two-line labels — **committed in `63fcc6d`**; read the sections above first)

**Owner-approved UI fix, on `refactor/bce-subpackages-and-boundary-security`,
~~not committed~~ — ⚠️ **committed on 2026-08-29 in `63fcc6d` ("frontend
adjustments")**, correction made 2026-08-29 after review; every file this
section lists is in that commit.** The rail's labels overflowed their items; the owner chose
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
