# Handoff — guest-payload-consolidation (Part B)

## CURRENT STATE (read this first — 2026-08-29, `lead`, the pill's WLAN glyph)

**Owner ask, uncommitted, on `refactor/bce-subpackages-and-boundary-security`:**
*"Das no connection banner finde ich jetzt gut. Aber leider ist in der pill das
wlan symbol nicht mehr da. Das hätte ich dort gerne statt dem Punkt."* The
banner from the section below is **accepted as-is and was not touched**.

The staleness pill's decorative `0.5rem` dot is now `phosphorWifiSlash` — the
same glyph the connection screen and the escalated banner already carry, so
the discreet hint names *what* is missing instead of only marking *that*
something is. Size `--size-icon-xs` (1.125rem), **not** the banner's
`--size-icon-md`: xs is `tokens.css`'s own step for a glyph beside a
`--text-status` label. Colour `--color-status-offline` on the `ng-icon` host
(the injected `<svg>` is unstylable from component CSS), `aria-hidden` — still
decorative, the text still carries the information.

**Height did not move at all:** 28.19px @1280 / 22.94px @1024, byte-identical
to the dot in the same render (reviewer swapped it back to check). The glyph
sits just inside the label's own line box, so the pill stays label-driven.
Width grew +8.0px / +6.4px, leftwards from the right anchor into empty space.

**Changed:** `staleness-pill.component.{html,ts,css}` (the `.ts` had **no**
`imports` array before — `NgIcon` added), `shell.component.spec.ts` (the
existing STALE test now asserts the glyph + `aria-hidden`),
`registered-icons.ts` (comment only: three uses now), and
`docs/design/screens-inhouse/connection.md` → "The staleness pill".
`ng test` 333/333 (37 files), `ng build` green.

**The `gap` also went `--space-3` → `--space-2`** — an implementation
consequence, deliberately **not** attributed to the owner in the spec: the
status bar's own trigger pairs an xs glyph with a `--text-status` label at
exactly that gap. Flagged to the owner; revertible in one line.

### Two corrections the review forced — read before trusting old numbers

1. The first pass wrote *"the envelope it has to fit is the 20px/16px vertical
   clearance, which it does."* **False, and now removed.** The pill is 28.19px
   tall and fits no 20px band — and that band was never the pill's constraint:
   it is the status bar's drop to Home's **heading ink**, measured in the
   heading column, from which the pill is **562px (1280) / 447px (1024)** clear
   horizontally. It hangs over the widget column, empty by design. The record
   now says *it collides with nothing because the region under the anchor is
   empty*.
2. **The spec's `≤ 1.75rem` pill budget was always fiction.** Confirmed
   pre-existing, not caused by this change: `1.6375rem` of scaling box + a 2px
   hairline that stays `px` by constitution → **1.7619rem @1280 / 1.7922rem
   @1024** (larger in rem at the 1024 floor, because the border does not
   shrink). Rewritten as a descriptive fact, not a budget. If the pill ever
   does collide, it moves into the status bar's row rather than pushing content.

**Open / not done:** `measure-states.mjs` owns the STALE-pill cell but needs
the dev backend + manager API (`GXP_SMOKE_ADMIN_PW`); both `dev` and `reviewer`
measured in throwaway Playwright harnesses against the **real** built bundle
instead. Re-confirm the +8px width against the recorded 48px/38px horizontal
clearance on the next full run. Prettier flags `shell.component.spec.ts`
(`menuItems`, ~line 47) and `connection.md` — **both pre-existing at HEAD**,
both verified by stash, both deliberately left alone.


## CURRENT STATE (read this first — 2026-08-28, `dev`, the banner's horizontal padding)

**Variante B was built, rejected by the owner, and has been fully rolled
back.** *"B gefällt mir nicht, breche das bitte ab. Ich finde das Wort bringt
Persönlichkeit rein, ohne 'besteht' sieht es sehr technisch aus."* Reverted to
`HEAD`: `core/i18n/catalog/de.ts` (the word *besteht* is back in **both**
`stale.banner.body` and `stale.banner.bodyStandby`; English was never
touched), and `connection.md` / `info.md` restored and re-edited from scratch
so **no passage describing B survives** — no copy cut, no B geometry.

**What is in the tree instead (Stufe 1, owner-approved shape, uncommitted, on
`refactor/bce-subpackages-and-boundary-security`):** the banner's horizontal
padding becomes **asymmetric at an unchanged total** —
`--space-4 --space-5 --space-4 --space-3`, i.e. 8 px left / 16 px right where
it was 12/12. `--space-3 + --space-5 = 2 × --space-4` is the only such pair on
the scale, so the bar's **width, left edge and clearance to Home's headline
column are unchanged to the pixel** (9.23 px at 1024, 7.0 px at 1280 — the
floor that was not allowed to move). No copy change, no anchor change; the
pill is untouched at `HEAD`.

**Changed:** `staleness-banner.component.css`,
`docs/design/screens-inhouse/connection.md` (padding clause rewritten, three
measured rows added to the table, the stale "20 banner cells"/"44/44"
corrected to **28**/**52** — `module-content` had added eight `/info` cells
without the sentence being touched), `docs/design/screens-inhouse/info.md`
(one clarification only: its `--space-4` inset is the *vertical* one, and
every figure on that page is unchanged because the geometry is).

**The measurement that matters, and it corrected the brief.** The pinch is
**not** primarily the timestamp. The bar is `width: max-content` and the body
is what sizes it, so the **body's own bottom-right corner** sits at exactly
the right padding inside the bottom `--radius-2xl` arc — measured
perpendicular to the arc it was **4.4 px (1024) / 5.1 px (1280)**, tighter
than the timestamp's 6.4 / 7.8. Stufe 1 lifts the bar's **worst** clearance
to **6.4 / 7.7 px** (+46 % / +50 %) for free.

| perpendicular to the `--radius-2xl` arc | 1024 before → after | 1280 before → after |
|---|---|---|
| body's bottom-right corner (**the worst point**) | 4.4 → **6.4** | 5.1 → **7.7** |
| timestamp's top-right corner | 6.4 → 12.4 | 7.8 → 15.5 |
| glyph → left edge (straight there) | 9.4 → 6.4 | 11.5 → 7.7 |
| bar box / clearance to headline column | `x459 w540` / 9.23 px — **unchanged** | `x569 w679` / 7.0 px — **unchanged** |

**Stufe 2 was built, measured, photographed and then reverted — it is NOT in
the tree.** (`right: --space-7 → --space-6` on banner **and** pill together,
plus right padding `--space-6`.) It lands on the predicted numbers: net +8 px
width, +8 px shift, headline clearance still 9.25 / 7.0, timestamp corner
12.4 / 15.5. **But it does not improve the bar's worst clearance at all** —
that is the left glyph at 6.4 / 7.7, identical to Stufe 1, because Stufe 2
only adds padding on the right. Its price is real: pill and bar sit
**6.4 px (1024) / 8 px (1280) further right than the status-bar clock**, which
shares the shell's `--space-7` line. Screenshots and the full table are in the
session report — **the owner chooses**.

**Verified:** `measure-states.mjs` **52/52 `NO-SCROLL OK`, 0 findings** both
before and after; title, body and timestamp one line in every cell; `/info`
clearance untouched. `ng test` **333/333 in 37 files**; `ng build`
**467.38 kB / 106.44 kB** (baseline 467.13 / 106.41 — the delta is the CSS
comment). Backend mutations all restored by the instrument.

**Next:** owner picks Stufe 1 (in the tree) or Stufe 2, then `reviewer`.

---


## CURRENT STATE (read this first — 2026-08-28, `dev`, the banner's horizontal padding)

**Owner-approved UI fix ("Variante B"), on
`refactor/bce-subpackages-and-boundary-security`, not committed.** The
escalated staleness banner's timestamp sat visually pinched into the
`--radius-2xl` corner. Horizontal padding `--space-4` → **`--space-6`**
(vertical stays `--space-4`), paid for by dropping the word *besteht* from
**both** German body variants (`stale.banner.body`, `stale.banner.bodyStandby`
— the standby cut is symmetry the owner has been told about and may reject).
English is untouched.

**Changed:** `staleness-banner.component.css`, `core/i18n/catalog/de.ts`,
`docs/design/screens-inhouse/connection.md` (padding clause, measurement table,
copy table, the two consequence bullets; the stale "44/44 cells" corrected to
**52**), `docs/design/screens-inhouse/info.md` (banner row re-measured).

**Measured before → after** (`tools/measure-states.mjs`, 52/52 cells
`NO-SCROLL OK`, 0 findings both runs): timestamp corner → corner arc
6.4 → **12.4px** at 1024 and 7.8 → **15.5px** at 1280; clearance to Home's
headline column 9.2 → **32px** / 7.0 → **37px**; bar `x459 w540` → `x483 w516`
(1024 de) and `x569 w679` → `x599 w649` (1280 de). Title, body and timestamp
stay on one line in every cell.

**Also measured, deliberately NOT implemented:** moving the bar right, `right:
var(--space-7)` → `--space-6` / `--space-5`. It buys 6.4 / 12.8px (1024) and
8 / 16px (1280) of extra clearance to the headline, and costs the shared
anchor: the bar's right edge is flush with the status bar's today (Δ0) and
would sit 8px / 16px past it. Screenshots and numbers are in the session
report — **the owner chooses**.

---

**Last session:** 2026-08-28, `reviewer` — **round 3**, the verification of fix
round 2 (see the section at the end of this file). Previous: `dev` — the
**second fix round**: round 2's findings 1–7; `reviewer` — re-review round 2;
`dev` — the first **fix round**: the owner's banner redesign plus
`06-review.md` findings 2–8 and 10–13; `reviewer` — `06_review` over the whole
feature (T-01 … T-19); `dev` — Phases 4–6; `dev` — Phases 1–3; `planner` —
`02_design` → `03_todo` → `04_analyze` (**PASS**).
**Branch:** `refactor/bce-subpackages-and-boundary-security` (owner instruction)
**Status:** **`06_review` round 3 PASS ✅ — 🔴 0 · 🟠 0 · 🟡 1 · 🟢 6. Part B is
ready to commit; the owner commits.** The blocker round 2 raised after its
report is closed: `node tools/measure.mjs` ran its full grid — **168/168 cells,
0 overflowing, 0 truncated, 0 findings**, with Part A's committed Home
baselines **`heading.y 88` and `cardRow.bottom 744` unmoved**, so
`overflow-wrap: anywhere` costs no intrinsic-sizing regression anywhere.
`measure-states.mjs` re-run independently: **44/44 `NO-SCROLL OK`, 0
findings**, all 8 blocks, 20 banner cells; deleting the one CSS line reproduces
**8 findings in exactly the 4 long-name cells**. Frontend **313/313 in 34
files**, `ng build` clean (460.30 / 9.27 kB). Backend deliberately not re-run —
no file under `backend/`, `api/` or `packages/` changed since round 2's
**441 · 0 failures**. The review made **one** edit of its own: `360` → `335`
in three documents (round 3 finding 1). **Nothing is open for the owner** on
this feature.

## Review result — the input to the fix round (done; kept for the record)

The review's next action was the banner fix round (`docs/handoff.md` → "Part B
fix round — the staleness banner", owner-approved 2026-08-27) carrying findings
2–7 with it. **That round is done** — see "The fix round" further down.

The reviewer's own runs, none taken from this file on trust:

| Run | Result |
|---|---|
| `./mvnw verify` (full) | 429 run · 1 failure · 4 errors — **all five environmental**, each class green elsewhere |
| `./mvnw test`, 19 classes, `ClassOrderer$Random` secondary orderer | **167 run · 0 failures · 0 errors** |
| `./mvnw test`, the four `@TestProfile` classes alone | **12 run · 0 failures · 0 errors** |
| `ng test` | **313 / 313, 34 files** — matches |
| `ng build` | green, `main` 459.51 kB |
| Own playwright grid, banner × 2 viewports × 2 hosts × 2 themes × 2 languages | **16/16 rendered, 16/16 no-scroll, 16/16 pill down**, boxes identical to this file's |
| `./mvnw verify` regenerating `packages/api-client/` | `git status` unchanged — byte-identity confirmed a second time, by a second agent |

**The `connection.md` open item is closed** — the owner ruled while the review
ran, with a fourth option (drop the clamp, widen to a one-line body, warning
colour, motion). **Do not re-open it.** But read `06-review.md` →
"The queued banner fix round, measured" first: the width bound in the owner's
note is the sidebar edge (892 / 1116 px) where the binding bound is the **widget
column** (523 / 654 px), one line needs **567 / 712 px**, and the binding string
is **`stale.banner.bodyStandby` in English**, not the German copy. The shortfall
is 44 / 58 px at the two viewports. *(Superseded 2026-08-27, round 2: the
"523 / 654 px widget column" was measured from the heading **block**, not the
flex item — the real widget column is 179 / 224 px and the bar's bound is
neither. The binding-string half of this note is correct and stands. See "Where
it actually lands" below.)*

## Phases 4–6 — what landed (uncommitted)

Frontend **313/313 green** (was 298 at Part A's close; +15 from the rewritten
and new specs). `ng build` green. Backend `./mvnw verify` run twice:
**440 run / 0 failures**, errors only from the established environmental
Testcontainers/`@TestProfile` startup class — run 1 flagged
`TrustedProxyLoginTest`, `OpenApiDisabledTest` and a `PairingFlowTest` socket
timeout, run 2 flagged only `DevDataSeederTest`, and **every class that failed
in one run passed in the other**. `packages/api-client/` is **byte-identical**
across the two builds (88 files, no diff).

- **T-10** `GuestDataService` collapsed to one resource: `CONTENT_CACHE_KEY =
  'gxp.cache.content'`, one `etag`, `payload()`, `preferenceOverride()`,
  `fetchContent()` calling `getGuestContent(etag ?? undefined, 'response')`.
  `HOME_CACHE_KEY`, `homeEtag`, `heldHome`, `fetchHome()`, `asVacant()`,
  `withIfNoneMatch()`, both `409` branches and the `If-None-Match`-mechanism
  paragraph are gone. The two legacy keys are evicted once, in the
  constructor. **452 → 316 lines**; the REQ-949 grep is empty.
- **T-11** `mapGuestContent(content)` — one parameter, `home` read from
  `content.home`, plus §1.2's invariant (`OCCUPIED` with no `home` →
  `GuestContentMappingError`). `HomeContentComponent`'s comment corrected.
- **T-12** `guest-data.service.spec.ts` rebuilt around one request; the two
  `409`-on-home cases became "the server answered vacant" cases; REQ-945 and
  REQ-951 added. `provide-guest-api.spec.ts` follows the operation rename.
- **T-13** `core/guest/staleness.service.ts` — `STALE_ESCALATION_MS = 60 min`,
  tri-state `level`, derived from `connection()`/`staleSince()`/`ClockService`.
  Nine cases, including a **compile-time** assertion that `ConnectionState`
  still has exactly three members.
- **T-14/T-15** `layout/staleness-banner/` built against `connection.md`;
  `StalenessPillComponent.visible` is now `level() === 'PILL'` (one line);
  rendered in both hosts; the three catalogue keys added to `de.ts`/`en.ts`.
- **T-16** Copy verified **verbatim** against `connection.md`'s table,
  programmatically, all six strings. The spec was **not** rewritten.
- **T-17** `measure-states.mjs` grew a BANNER cell for both hosts.
- **T-18/T-19** `docs/system-context.md`, `docs/features/index.md` swept,
  plus three stale endpoint references the sweep's own check turned up
  (`docs/design/screens-inhouse/standby.md` and two comments in
  `core/content/property.model.ts` still named `guest/session` /
  `screens/home`);
  `pom.xml`/`package.json` untouched; `AuditCoverageTest` carries exactly the
  one intended entry swap; the **five** architecture guards are untouched and
  no sixth exists.

## The one open item — **CLOSED by the owner, 2026-08-27**

`connection.md` specified the banner as `min(22rem, 40vw)` wide **and** its body
as "at most two lines"; with the approved copy the body measures **three** lines
in all 16 cells. `dev` changed neither and escalated with options (a) keep the
geometry and amend the clause, (b) widen to ~`32rem`, (c) shorten the copy.

**The owner took a fourth option** (`docs/handoff.md` → "Part B fix round"):
drop the width clamp entirely and widen until the body is one line, stopping at
the content-area edge; add a warning colour as a global `--status-warning` role
derived from `design-sheet.md` §Status's v1 `Warning #F8B84E`; keep `WifiSlash`;
add a 350 ms `easeOutExpo` entrance and a slow glyph-only pulse, both behind
`prefers-reduced-motion`; the marquee is rejected. `connection.md`'s "the pill
grown into a panel" rationale must be rewritten — it is a bar now, and
`02-design.md` §6.3's "one body line" needs the same correction.

**The reviewer measured the consequence** — see `06-review.md`, finding 1 and
"The queued banner fix round, measured". Short version: the one-line bar does
fit inside the content area, but it does **not** fit inside the empty widget
column the design cites as its anchor, and the 4–5 px by which it currently
clears Home's hero headline is an accident of the seed guest's name, not a
margin. *(Superseded 2026-08-27, round 2: the widget-column figure was wrong by
~3× and the anchor is really the empty band between Home's heading and its card
row. The headline half was right and was fixed twice — the wrap, then
`overflow-wrap: anywhere` for names that do not wrap.)*

## Measurement (T-17), with numbers

`GXP_SMOKE_ADMIN_PW=… node tools/measure-states.mjs` — **40/40 cells
`NO-SCROLL OK`, 0 overflow**, of which **16 are the new BANNER cells**
(shell + standby × 1024×768/1280×832 × light/dark × de/en).

- banner box `x717 y58 w282 h114` at 1024×768, `x896 y72 w352 h143` at
  1280×832 — right edge flush with the status bar's (998 / 1248), top
  `--space-1` below it (bar bottom 54 / 68);
- no collision with the status-bar cluster, Home's card row (`y 541 / 548`) or
  the standby block; nothing clipped; **`pillStillUp` false in every cell**
  (the tri-state, measured rather than assumed);
- title one line, timestamp one line, body three lines (the open item above).

**Two instrument fixes were needed to get there, both reported as deviations:**

1. `POST /api/manager/login` requires `username` since the authentication
   hardening — the instrument's two pre-existing login sites still posted
   `{ password }` alone, got a `400` and crashed the whole run on
   `JSON.parse`. All three sites now go through one `managerToken()` helper
   (`GXP_SMOKE_ADMIN_USER`, default `admin`). The STANDBY and ARTWORK cells
   had been dead since that commit and run again.
2. The banner cells drive theme/language **after** the offline boot, through
   the real status bar, and never reload afterwards: the preference override
   lives *in the payload*, so a reload drops the choice (and while vacant the
   write `409`s by design). A first version reloaded per cell and silently
   measured `light/de` eight times — it is now asserted per cell
   (`PREFERENCE NOT APPLIED`).

## Deviations from the plan, all deliberate

- **T-10 spilled into three spec files** (`app.routes.spec.ts`,
  `http-preference-gateway.spec.ts`, `preferences.persistence.spec.ts`): their
  `GuestDataService` DI doubles are untyped object literals, so `session` →
  `payload` and `sessionPreference` → `preferenceOverride` compile fine and
  fail at runtime. They were renamed with T-12, which keeps that task at
  exactly five files.
- **T-14 touches eleven files** — the three new banner files plus the two
  hosts (`.ts` + `.html`), their two specs and the pill. The TODO's own file
  list for T-14 names all of them; the cap is exceeded by the task's
  definition, not by scope creep.
- **T-15 was implemented before T-14**, so T-14's `ng build` check was
  meaningful (a missing catalogue key fails the build by design).
- **The pill's shell spec had to change its timestamp.** It pinned
  `2026-08-20T14:05` — seven days before today against the real clock, 168x
  the 60-minute threshold, so under the new derivation it is `BANNER`, not
  `PILL`. It now uses a relative `Date.now() - 5 min` and derives the expected
  label with the same `Intl.DateTimeFormat` the component uses.

## Do not re-derive these

- **Everything in the previous two handoffs still holds** (the stamp scheme,
  `max(id)` over `count(*)`, the 300 s bucket, §3.2's confirmed
  verify-at-implementation, the withdrawn cross-module argument).
- **`ConnectionState` still has exactly three members**, and
  `staleness.service.spec.ts` fails to *compile* if that changes. The
  escalation is a presentation level, never a fourth state (REQ-943, D13).
- **`staleSince()` is contact, not change.** `adopt()` refreshes it on a
  `304`, `adoptCache()` seeds it from `storedAt` — so two hours of `304`s
  never escalate and a cold boot from an hour-old cache escalates at once.
  Both are asserted.
- **REQ-951's cache test is not name-based.** Under constitution v1.9.0 a WiFi
  password is permitted content and a device token is not, so the spec asserts
  a top-level key allow-list plus the literal absence of the device token and
  of any bearer/authorization string. A filter on `password`/`code`/`secret`
  would assert close to the opposite rule.
- **`packages/api-client/.openapi-generator/openapi.yaml-null.sha256`** is
  written by `quarkus:dev` and is gitignored. Do not resurrect or stage it.

## The fix round — done 2026-08-27 by `dev` (uncommitted)

The banner redesign **plus** review findings 2–8 and 10–13. Findings 9 and 14
were "no action" by the review's own judgement and were left alone.

**The bar.** Clamp dropped (`width: max-content`, `max-width` at the content
area's left edge), glyph `--size-icon-lg` → `--size-icon-md` in the new
`--color-status-warning`, horizontal padding `--space-5` → `--space-4`, and the
timestamp moved onto the title's row. Measured over the instrument's **20
banner cells** (2 viewports × 2 hosts × 2 themes × 2 languages, plus 4 with a
37-character unbroken guest name — the last four added in the second fix
round): **every body renders on ONE line**, title one line, timestamp one line,
no scroll, pill down, no collision with the status bar, Home's heading block,
Home's headline lines, Home's card row or the standby block.

| | 1024 × 768 | 1280 × 832 |
|---|---|---|
| bar, longest German cell | `x459 y58 w540 h60` | `x569 y72 w679 h74` |
| bar, longest English cell (standby) | `x446 y58 w553 h60` | `x554 y72 w694 h74` |
| chrome (glyph + gap + padding + border) | 51.6 px (was 66) | 64.0 px (was 82) |
| `.heading-block` ends / `.home-content__widgets` starts | x450 / x819 | x562 / x1024 |
| `.home-content__screen` (Home's whole left item) | x138…794 | x172…992 |

**Where it actually lands** *(corrected 2026-08-27 in the second fix round;
this row previously read "x450 / x475" and "x562 / x594", which was the gap
measured from the heading **block** as though that were Home's flex item)*: the
bar lies over the **empty band between Home's heading and its card row** —
335 px of its 540 at 1024, 423 px of its 679 at 1280 — plus the gutter and the
whole 179 / 224 px widget column. It never reaches `.heading-block`, the card
row or the sidebar. The copy is untouched (only the English standby string
could have been shortened, and it did not need to be). The 4–5 px headline
clearance the first review called an accident of the seed guest's name is gone:
`.heading-block` is a fixed `--size-heading` and `.heading-block__headline`
carries `overflow-wrap: anywhere`, so the clearance is **≥ 9 px / ≥ 7 px for
any name** — measured with a 37-character unbroken name (five lines, reaching
x450 / x560, 9 px clear), which the instrument now drives as a cell of its
own.

**The warning colour** is a global role, not a banner colour:
`--status-warning-raw` `#895806` + `--status-warning-dark-raw` `#f9b84e` +
`--color-status-warning` with a `[data-theme='dark']` override of the **role**
only. Foreground use only, never a surface fill; warning is not `Danger`. Both
values re-measured on glass here: light 4.64 / 5.17, dark 5.63 / 4.86 — the
owner's table said 5.18 and 5.66, which is the same numbers to rounding.

**Motion**, measured in a real browser rather than read off the CSS: entrance
`staleness-banner-in 0.35s cubic-bezier(0.16, 1, 0.3, 1) both` on the bar, a
4 s infinite pulse on the glyph, **`animationName: none` on title and body**,
and both animations `none` under `prefers-reduced-motion: reduce`. The live
region's text is set once at creation and no animation touches it.

**Docs rewritten against the measurement:** `screens-inhouse/connection.md`
(the layout section is now "a bar, not a panel", plus Motion and a measured
"where the bar lands" table), `02-design.md` §6.3 and the new §6.7,
`design/tokens.md` (In-house tokens row + the foreground-only note),
`design-sheet.md` §Status (Warning realised) and §Motion (first use; the
marquee rejection recorded).

**Findings:** 2 (REQ-947 preference-write case — mutation-probed: deleting
`language`/`appearance` from `fingerprintOf` fails that case and only that
case), 3 (compile-time allow-list — probed by adding a field to the generated
`GuestContent`: both `tsc` and `ng test` fail), 4 (`specification.md`
supersession markers), 5 (T-10/T-19 line-budget text), 6 (`measure-states.mjs`
manager blocks wrapped in `guarded()`; the instrument's own WRAP threshold is
now "one line" and it checks the bar against Home's heading column), 7 (both
hosts assert content keeps rendering behind the bar), 8 (`frontend/CLAUDE.md`,
the owner's verbatim text), 10, 11, 12, 13.

## What must happen next

**Re-review only what the second fix round changed** (findings 1–7 of round 2,
below), then **the owner commits**: one commit carrying contract + backend +
`packages/api-client/` + frontend + docs. The review over Phases 1–6 is closed;
do not re-run it.

**Withdrawn, not open** *(2026-08-27, lead decision after the re-review)*: this
file previously asked the owner to rule on the bar "reaching 16 / 25 px past
the widget column's left edge, into the gutter". **That question rested on a
wrong figure and no longer exists.** The widget column is x819…998 / x1024…1248
— 179 / 224 px, not the 523 / 654 px the number came from — so the bar's left
edge is **360 px** past it at 1024 and **455 px** at 1280, and the alternative
offered (bound the bar at the widget column) would have meant bounding a 553 px
bar at 179 px: five or six lines, not two. On the corrected geometry the bar
sits in the **empty band between Home's heading and its card row**, clears
`.heading-block` by ≥ 9 / ≥ 7 px for any name and the card row by 424 / 402 px,
and nothing is left to rule on. Recorded rather than deleted so a future reader
sees the question existed and was answered **by measurement**, not by the
owner. Numbers: `docs/design/screens-inhouse/connection.md` → "Where the bar
lands, measured".

---

## Re-review of the fix round — 2026-08-27, `reviewer`

**Result: NEEDS CHANGES ❌ — 🔴 0 · 🟠 3 · 🟡 3 · 🟢 4.** Full round in
[`06-review.md`](06-review.md) → "Review — Round 2".

**Everything round 1 asked for landed, and both hard probes were re-done by the
reviewer's own hand.** Deleting `language`/`appearance` from
`GuestContentVersion.fingerprintOf` fails `theGuestsOwnPreferenceWriteInvalidates
TheStamp` **and only that case** (5 run, 1 failure); adding a field to
`GuestContent` fails **both** `tsc` and `ng test`. The banner redesign is sound
as built — no rendering defect at any of 24 reviewer cells or 16 instrument
cells. Both suites re-run: backend **441 · 0 failures · 2 errors** (both
`@TestProfile` startup timeouts, both 2/2 green in isolation — environmental);
frontend **313/313 in 34 files**; `ng build` clean; all four warning-colour
contrast ratios reproduced exactly (4.64 / 5.17 light, 5.63 / 4.86 dark).

**What blocks the commit is the record of where the bar lands, not the bar.**

1. 🟠 `measure-states.mjs`'s new heading-column guard tests
   `.home-content__screen`, which is the whole left flex item (x137.6…**793.6**,
   sized by the card row) — not the heading. It therefore reports
   `COLLISION: banner x Home's heading column` in **all 8 shell cells of a
   correct build**, and the 9 px margin it was added to protect is unguarded.
   It should test `.heading-block` (x137.6…**449.6** / x172…**562**, fixed by
   `--size-heading`).
2. 🟠 `connection.md` and `02-design.md` §6.3 carry the same wrong figures:
   `.home-content__widgets` is **x819…998 / x1024…1248**, only 179 / 224 px
   wide — not x475…998 / x594…1248. So *"the bar overlaps Home's empty widget
   column"* is false; it sits over `.home-content__screen`'s empty middle band.
3. 🟠 **"Open for the owner" below is posed on that wrong figure** — the bar is
   **360 / 455 px** past the widget column's left edge, not 16 / 25. The
   alternative it offers him (bound the bar at the widget column) would mean
   bounding a 553 px bar at 179 px. **Re-pose it or withdraw it** before it
   reaches the owner.
4. 🟡 *"≥ 9 px / ≥ 7 px for any name"* is falsified by an unbroken display-name
   token: nothing breaks a long word in `.heading-block`, so a 37-character
   unbroken name runs to x878.6 and passes under the bar. One-line fix
   (`overflow-wrap: anywhere`) or soften the wording.
5. 🟡 `docs/features/index.md:19` still describes the pre-redesign banner
   (`min(22rem, 40vw)`, "three lines", "overlay panel", the closed open
   finding) — **the one thing the crash plausibly left half-applied.**
6. 🟡 `staleness-banner.component.ts:17` and `shell.component.html:43` still
   say "grown into a panel", which `connection.md` now explicitly retires.

Plus 🟢 7–10: the "24 cells" figure is not reproducible from the instrument;
the `max-width` subtracts the sidebar on standby too (harmless); `docs/handoff.md`'s
dark ratio 5.66 is the unrounded figure and `dev`'s 5.63 is the better one;
the `role="status"` region is created with its text already present.

**Next action:** fix 1–6, re-run `measure-states.mjs` and expect **0 findings**,
then the owner commits. Findings 3 and 4 carry a question and a choice that are
the owner's, not a reviewer's. Nothing was committed, fixed or branched by this
review.

---

## Second fix round — 2026-08-27, `dev` (uncommitted)

Round 2's findings 1–7. **No backend, contract or generated-client file was
touched**, so the backend baseline (441 run · 0 failures · 2 environmental
`@TestProfile` startup errors) is untouched and was not re-run. Frontend
**313/313 in 34 files**, `ng build` clean.

| # | What changed |
|---|---|
| 🟠 1 | `measure-states.mjs` no longer guards `.home-content__screen` (Home's whole left flex item, x138…**794** / x172…**992**, sized by the card row). It collects `.heading-block` — fixed `--size-heading`, x138…**450** / x172…**562** — and, tighter, the headline's own `Range.getClientRects()` extent, and reports a `COLLISION` against each. `.home-content__screen` and `.home-content__widgets` are still **logged**, so the corrected doc figures are reproducible from a run; neither is guarded, because the bar legitimately lies over both |
| 🟠 2 | `connection.md` → "Where the bar lands, measured" and `02-design.md` §6.3 corrected to the measured values, with the element renamed and the root cause recorded (x475 = x449.6 + `--space-7`, i.e. the gap measured from the heading **block**). The anchor is restated as **"the empty band between Home's heading and its card row"** |
| 🟠 3 | The owner question was **withdrawn**, not re-posed — see "What must happen next" above |
| 🟡 4 | `overflow-wrap: anywhere` on `.heading-block__headline`. Mutation-probed: removing that one line makes a 37-character unbroken name render **one** line to x935 de / x953 en (1024) and x1173 de / x1195 en (1280) and the instrument reports **8 findings in exactly the 4 long-name cells**; with it, five lines reaching x450 / x560 and **0 findings** |
| 🟡 5 | `docs/features/index.md:19` rewritten against the redesign (status → In Review, owner → `dev → reviewer`, the closed clamp finding removed, the verification sentence taken from the re-run) |
| 🟡 6 | `staleness-banner.component.ts` and `shell.component.html` say **BAR**, matching the CSS comment and the spec's retirement of "the pill grown into a panel" |
| 🟢 7 | The stronger option taken: the instrument gained a **display-name axis** — it renames the active stay to a 37-character unbroken name through the manager API, measures 4 cells (2 viewports × 2 languages) and renames it back inside a `finally`. That is also finding 4's ratchet, and it makes the "24 cells" claim reproducible: the run is now **20 banner cells** of 44 |
| 🟢 8, 9 | No action, per the round's own judgement (`--size-sidebar` on standby is harmless and disclosed; `dev`'s 5.63 stands) |

**Verification:** `GXP_SMOKE_ADMIN_PW=… node tools/measure-states.mjs` against
the dev backend and `ng serve` — **44/44 cells `NO-SCROLL OK`, 0 findings**
(was 40/40 with 8 false `COLLISION`s). Bar x459 y58 w540 h60 / x569 y72 w679
h74 in the longest German shell cell, x446 w553 / x554 w694 in the binding
English standby cell; body/title/timestamp 1/1/1 in all 20 banner cells; pill
absent in all of them. One of the four runs made today skipped
its ARTWORK block on a 60 s `waitForSelector` timeout — the `guarded()`
behaviour working as intended, environmental, and green in the runs before and
after it.

**Style note for whoever reviews the instrument diff:** `measure-states.mjs` is
**not** Prettier-clean at HEAD (printWidth 100 vs. its long template-literal
log lines). A `prettier --write` slipped in during this round and was reverted
line by line, so the file keeps its existing style. The revert is one line
short of complete, and round 3 measured it: of the 24 hunks of
`git diff -U0 measure-states.mjs`, normalised for whitespace, **exactly one is
a pure reflow** — `@@ -249 +353,2 @@`, the STALE-pill
`if (heading && …) note('COLLISION', …)` line broken across two, in a block
this round did not otherwise touch. It is semantically identical and moves the
file *toward* Prettier-clean, so it was left in place; the other 23 hunks are
intended changes.

---

## Round 3 — verification of the second fix round, 2026-08-28, `reviewer`

**PASS ✅ — 🔴 0 · 🟠 0 · 🟡 1 · 🟢 6.** Full round in
[`06-review.md`](06-review.md) → "Review — Round 3". Nothing committed, nothing
staged, no branch switched.

**The blocker is closed.** Round 2 warned, after its report, that
`overflow-wrap: anywhere` changes the headline's `min-content` contribution on
**every** Home render and that Part A's committed baselines had to be re-run.
The lead's fix-round brief named only `measure-states.mjs`, so it had not been.
It has now: `node tools/measure.mjs` — **168 route × theme × language ×
viewport cells, 0 overflowing, 0 truncated, 0 findings**, and in all four
1280×832 cells `baseline check: heading.y 88 vs 88, cardRow.bottom 744 vs 744`.
The headline's own line rects are **byte-identical with and without the CSS
line** for the seed copy (3 lines, right 391 / 446 / 491 / 558), and even the
37-character name leaves the card row at `y541…698` / `y548…744`. No
regression, measured rather than argued.

| Check | Result |
|---|---|
| `node tools/measure.mjs` (warm) | **168/168 · 0 · 0 · 0 findings**; Home 88 / 744 unmoved |
| `node tools/measure-states.mjs` | **44/44 `NO-SCROLL OK`, 0 findings**, 8/8 blocks, 20 banner cells |
| same, with `overflow-wrap` deleted | **8 findings in exactly the 4 long-name cells** (x935/x953/x1173/x1195) |
| `ng test --watch=false` | **313/313, 34 files** |
| `ng build` | clean — 460.30 / 9.27 kB, total 469.58 kB |
| backend | **not re-run** — nothing under `backend/`, `api/`, `packages/` changed since round 2's 441 · 0 failures; the generated client regenerated byte-identically a third time |

**All three of round 2's 🟠 are closed**, and both declared deviations were
judged: the Prettier revert is complete but for one semantically identical line
break (🟢 3), and the ARTWORK skip is the `guarded()` guard working — a noted
skip cannot hide inside a "0 findings" run — though the 60 s timeout behind it
is the one worth raising if it recurs. `ng test`'s worker crashes reproduce
without `ng serve` when any JVM is up, and are resource starvation, not defects.

**One fix made by the review, declared:** the corrected 1024 decomposition was
itself wrong — 360 + 25 + 179 = 564 of a 540 px bar, because 360 is the
distance to the *widget column's* left edge and already contains the gutter.
The band is **335 px** (793.6 − 458.8). Three numerals changed, in
`connection.md`, `02-design.md` §6.3 and this file, plus one sentence in
`connection.md` recording where 360 came from.

**Open, for whoever next touches the instruments** (neither blocks the commit):
`measure-states.mjs`'s three restores ignore their response and log success
unconditionally — a failed rename would leave a 37-character guest name on the
dev kiosk; and `measure.mjs`'s `applyPreferences` still kills the whole 168-cell
grid on one `page.goto` timeout (Part A's 🟢 12, third occurrence — warm the
routes before running it).

**Next action: the owner commits Part B** — one commit carrying contract +
backend + `packages/api-client/` + frontend + docs. Then the `getSectionRoute()`
retirement round, and his ruling on the queued `frontend/CLAUDE.md` → Layout
entry about `overflow-wrap` on manager-supplied strings.
