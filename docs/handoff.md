# Session Handoff — 2026-08-27, `lead`

**Branch:** `refactor/bce-subpackages-and-boundary-security` — **owner
instruction: stay on it**, even though the name does not match the work
("Ich habe meine Gründe"). Do not create or switch branches.
**Commits:** the owner granted commit authority 2026-08-27 (*"doch committe
auch gerne"*). Commit at **coherent checkpoints** — a reviewed block, a
finished amendment — never mid-implementation while a `dev` agent is writing.
Committed so far: `0ed4f03` constitution v1.9.0 · `c8081a8` the split and Part
B's plan · `33f745a` Part A phases 1–3 · `14c005e` Part A complete · `4459859`
Part B complete. The tree was clean after each.

## What this session did

The owner brought an **owner-written** PRD (`docs/features/modular-menu-catalogue/`)
and asked for it to be pressure-tested rather than accepted. It was rewritten
and **split into three features** (owner decision D1):

| Feature | Part | REQ | State |
|---|---|---|---|
| `sidebar-catalogue-overflow` | A — the owner's actual ask | 901…935 | **Done, committed** `33f745a` + `14c005e`. Two review rounds, both closed |
| `guest-payload-consolidation` | B | 941…952 | **Done, committed** `4459859`. Three review rounds, final PASS |
| `module-content` | C | 961…979 | **Not ready to plan** — blocked on D9 and on a real module screen existing |

`modular-menu-catalogue/` is retired to a `README.md` pointer.

**`docs/constitution.md` is now v1.9.0** (amended this session, owner-approved
verbatim): the kiosk cache clause changed from an absolute prohibition to a
threat-model boundary, and stale rendering now escalates to a prominent banner
after a prolonged loss of contact (the threshold lives in Part B, not in the
constitution).

## Owner decisions, all 2026-08-27

D1 split A→B→C · D2 flat catalogue + popover, no hierarchy · D3 `n = 5` sidebar
slots (4 entries + overflow), popover unbounded · D4 parameterised placeholder
route + `**` fallback · D5 new catalogue rows `enabled = false`, `%dev` true ·
D6 popover designed in-repo · D7 merge **only** `session` + `screens/home` ·
D8 manager selects and orders, no content authoring — spec §3 decision 5 stands ·
D10 module registry approved (frontend code file *and* the existing table) ·
D11 no per-room content override · D12 settled by the v1.9.0 amendment ·
D13 escalating staleness banner, 60 min, also on the vacant-room standby screen.

**D9 (`oneOf`/`discriminator` on the wire) is the one open gate.** The owner
deferred to expertise without understanding it, which is **not** informed
approval. It needs the generator spike first — `jaxrs-spec` has never emitted a
`oneOf` in this repo.

## Autonomy grant — and its limits

The owner left for several hours (2026-08-27) with: *"Bitte fahre so autonom wie
möglich fort … falls doch was blockiert und es nichts großes darfst du auch
entscheiden."*

**In scope to decide alone:** small implementation choices, naming, test
structure, ordering of planned work, deferring open questions to a list.

**Still blocked, grant or no grant** — `docs/constitution.md` → "Decisions
Requiring Your Approval": a new dependency, a datastore/infra choice, a new
architectural pattern, a nested `CLAUDE.md` entry and a constitution amendment
(exact text approved **before** writing). "Nichts großes" does not cover a
gate; a gate is by definition the big kind. **Committing is no longer on this
list** — see above.

## Decisions the lead took under the autonomy grant

**2026-08-27 — Part A review 🟠 1: restore the kiosk-side menu bound at 50.**
The reviewer found REQ-901's acceptance criterion unmet — the contract now
declares `GuestMenu.items.maxItems: 50`, but nothing enforces it: response DTOs
are not runtime-validated (the generated `@Size(max=50)` is inert) and T-08
deleted the only kiosk-side check. The plan was internally inconsistent:
`02-design.md` §2 says the enforcement that matters is the kiosk's boundary
check, and §5 then removes it, justifying that with *"trusted for count the
same way `actionCards` is"* — which is factually wrong, `MAX_CARDS = 4` still
rejects at `guest-content.mapper.ts:184`.

Decided: **restore the guard at 50**, keep the criterion, correct §2/§5. Reasons
— it is the conservative half of the choice; it matches the sibling precedent
in the very same file; and it preserves what the original finding was *about*.
The old `MAX_MENU_ITEMS = 5` was wrong because it **invented** a bound and lied
about its source, not because bounds are wrong. At 50 the message *"exceeds the
contract's 0–50"* is finally true. Removing the check instead would drop
boundary validation of an untrusted wire, which `01-prd.md` A3 calls the
frontend's contract with that wire.

Not a constitution gate: no dependency, no infra choice, no new pattern, no
nested `CLAUDE.md` entry. **Owner may reverse it** — it is ~5 lines and one
acceptance criterion.

**2026-08-27 — REQ-949's line budget struck, on the owner's instruction.**
Part B's REQ-949 demanded `guest-data.service.ts` end under **452 lines**. The
owner struck it: *"Ob das am Ende wirklich weniger Zeilen sind ist mir
eigentlich egal. Hauptsache es ist sauberer Code."* The metric was a proxy for
the case reduction and a bad one — a line count is always reachable by joining
statements, deleting a comment or declining a helper, each of which improves the
number and worsens the code. `01-prd.md` REQ-949, its acceptance criterion and
the REQ-952 interaction note now state the structural property directly: one
fetch, one ETag, one cache key, one held copy, no two-call sequence, no
`409` branch, and the five retired names absent.

**Open loose end:** `03-todo.md` may still reference the 452 figure in T-10 or
T-19. It was not edited because a `dev` agent held the file at the time. Reconcile
it at the Part B review, and read any dev report that cites the line count as
answering a criterion that no longer exists.

## Crash recovery, 2026-08-27 night

The Claude Code process died while the **Part B fix round** `dev` agent was
running. The owner restarted it. **The agent's work survived; only its final
report was lost.** The lead audited the tree item by item rather than assuming
either way, and found the round essentially **complete**: the warning-colour
tokens, the approved `frontend/CLAUDE.md` sentence, `specification.md`'s
supersession markers (including decision 7), the struck 452-line citations, the
REQ-947 preference-write case, REQ-951's compile-time contract half
(`Record<keyof Required<GuestContent>, true>`), REQ-952's content-keeps-rendering
assertions, the `guarded()` wrapper in `measure-states.mjs`, both design
documents rewritten, and 🟢 10–13 all landed. Frontend re-run by the lead:
**313/313 green**.

**The banner redesign succeeded and did better than the plan asked.** One line
was reached with the chrome trim plus the timestamp on the title row — the
English standby string did **not** have to be shortened. Measured ~540 × 60 at
1024×768 and ~679 × 74 at 1280×832 (~9 : 1). The bar takes the gutter between
Home's two columns for the longer strings but never reaches the heading column
or the sidebar. **Home's hero-headline clearance is now structural rather than a
margin** — *with one hole the re-review found.* The headline's real bound is
`.heading-block` at x450 / x562 and the bar starts at x459 / x569, so ≥ 9 px /
≥ 7 px **for every name that wraps** — measured 32 / 38 px with a 31-character
name. But nothing breaks a long *word*: `.heading-block` sets a width and no
`overflow-wrap`, so a **37-character unbroken display name renders one line to
x878.6 and passes under the bar** (screenshotted by the reviewer). The lead had
relayed "for any name" to the owner on dev's wording — **that was too strong and
is corrected here.** Fix: `overflow-wrap: anywhere` on
`.heading-block__headline`, which also repairs a pre-existing overflow.

**A measurement error propagated through three documents and the lead's own
summary.** `.home-content__screen` is the *whole left flex item*
(x137.6…793.6 / x172…992), sized by the four-card row — not the heading. The
x450 / x562 figures belong to `.heading-block`. And `.home-content__widgets` is
**x819…998 / x1024…1248, only 179 / 224 px wide** — not the 523 / 654 px that
round 1, the fix round and the lead all repeated. So *"the bar overlaps Home's
empty widget column"* is **false**: it sits over the deliberately empty band
between Home's heading and its card row, which is a perfectly good anchor and
simply a different one. Root cause: x475 = 449.6 + `--space-7`, i.e. someone
measured the gap from the heading block as if it were the flex item — correct
only for a 0-card Home.

**Unverified at handoff time:** the backend suite was not re-run after the
crash, and `measure-states.mjs` was not re-run by the lead. The re-review covers
both.

## Night plan, 2026-08-27 → 28 (owner asleep, agreed before he went)

Strictly serial — each step's files overlap the next, so nothing runs in
parallel:

1. **Part B re-review** — **done.** Round 2 (NEEDS CHANGES) → fix round 2 →
   **round 3 PASS ✅** on 2026-08-28, which also ran the 168-cell `measure.mjs`
   grid the risk note below demanded. Nothing is left unverified.
2. **Fix whatever it finds**, then **commit Part B** — nothing left to fix:
   round 3 found 🔴 0 · 🟠 0, and its single 🟡 (a 25 px arithmetic slip, `360`
   → `335`, in three documents) it corrected itself and declared. **Part B is
   the owner's to commit.**
3. **Retire `getSectionRoute()`** — the owner-approved round below.
4. **The D9 generator spike** — throwaway two-member `oneOf`, `./mvnw verify`,
   read both the JAX-RS and the TypeScript output. Nothing is kept; the point is
   to turn D9 from a trust question into an evidenced one.

**Part C is NOT started, and the owner agreed.** He asked whether it could begin
if Part B went smoothly; the lead said no and he accepted. Two reasons, the
second decisive:

- It contradicts his own **D8**: *"Der manager darf nur auswählen welche Items
  angezeigt werden sollen … In Zukunft soll er platzhalter werte pro modul
  pflegen können. Aber das ist Zukunft und hat mit diesem feature nichts zu
  tun."*
- **No module screen exists.** Part C fixes the per-type wire shape, which
  cannot be determined before anyone knows what one module shows. The eight
  catalogue rows are deliberately disposable fixtures, not a specification.
  Designing schemas against invented content is work that gets thrown away.

**The one decision that unblocks Part C** — for the owner, when he is back:
**which module gets designed first.** `info` is the obvious candidate, since he
has already said `wlan`, `anreise`, `hausordnung` and `muell` all belong under
it. That is design work, not implementation, and it is his.

## ✅ CLOSED verification risk — `overflow-wrap: anywhere` (round 3, 2026-08-28)

**Discharged. `node tools/measure.mjs` ran its full grid: 168/168 cells, 0
overflowing, 0 truncated, 0 findings, and Part A's committed Home baselines are
unmoved — `baseline check (1280x832): heading.y 88 vs 88, cardRow.bottom 744 vs
744` in all four cells.** Stronger still, the headline's own line rects are
identical with and without the CSS line for the seed copy (3 lines, right
391 / 446 / 491 / 558 across the eight shell cells), and even a 37-character
unbroken name leaves the card row at `y541…698` / `y548…744`. No intrinsic
sizing moved anywhere. `measure-states.mjs` was re-run independently — 44/44,
0 findings, all 8 blocks — and deleting the one CSS line reproduces exactly 8
findings in exactly the 4 long-name cells. **Round 3 is PASS ✅ (🔴 0 · 🟠 0 ·
🟡 1 · 🟢 6); Part B is ready to commit.** Full round:
`docs/features/guest-payload-consolidation/06-review.md` → "Review — Round 3".
The original note is kept below because it is the reason the check happened.

---

Raised by the reviewer **after** its report, and it is the sharpest point of the
night. Fix round 2 adds `overflow-wrap: anywhere` to
`heading-block.component.css` to close finding 4. That property does not only
affect the pathological 37-character name: **it changes the headline's
`min-content` contribution, and therefore intrinsic sizing, in every Home cell.**

So the check is **not** limited to the 16 banner cells. `tools/measure.mjs`'s
**168 route cells must be re-run**, including Home's signed-off baselines
(`heading.y 88`, `cardRow.bottom 744` at 1280×832) which are **already committed**
in Part A (`14c005e`). A shift there is a regression in a landed feature, not in
Part B.

**Closed 2026-08-28 by the round-3 review**, which ran the grid: **168/168, 0
findings**, and Home's committed baselines **unmoved** — `heading.y 88 vs 88`,
`cardRow.bottom 744 vs 744`. It went further than asked and diffed the headline's
own `Range.getClientRects()` extent across the eight shell cells with and without
the CSS line: `{lines:3, right:391}`, `{446}`, `{491}`, `{558}` — **identical in
both trees**. The property costs nothing on any ordinary render. Risk closed by
measurement, not by argument.

Also recorded: the reviewer's round-2 verdict was measured against the
**pre-fix** tree — the 8 COLLISION findings, the 441/313 suite numbers and the
24-cell geometry all describe the tree before fix round 2 landed. Its "re-run and
expect 0 findings" is therefore a genuine outstanding check, not a formality, and
the fix round itself is **unreviewed**.

## Awaiting owner — a proposed `frontend/CLAUDE.md` entry (2026-08-27 night)

Fix round 2 proposed this and **correctly stopped** rather than writing it. It is
a nested `CLAUDE.md` entry, so it needs the owner's approval of the **exact
text**. Under `frontend/CLAUDE.md` → **Layout**:

> **A string a manager or a guest supplies is width you do not control.** Text that interpolates one — Home's headline is `{stay.displayName}` — sets `overflow-wrap: anywhere`: a fixed column width does not break an unbroken token, and the overflow is invisible in every seed-data render.

The lead's view: worth taking. The bug it generalises was **pre-existing**,
invisible in every seed render, and only surfaced because a reviewer drove a
37-character name by hand. That is exactly the class of thing a convention entry
is for. But it is the owner's call and nothing is written until he says the
words are right.

## Open disagreement for the reviewer to settle — `measure.mjs`'s timeout

The round-3 reviewer's 🟢 4 asked for **report-and-continue**. `dev` deliberately
chose **raise-the-timeout** instead (`COLD_COMPILE_TIMEOUT_MS = 60000` via
`page.setDefaultTimeout()` at all five `newPage()` sites) and argued it:

> a cold compile is a *wait*, not a broken cell, so skipping trades "slow" for
> "no numbers" — the exact failure the review argues against for ARTWORK;
> `guarded()` in `measure-states.mjs` protects state-mutating blocks with real
> preconditions, which the grid loop has none of, and wrapping 168 cells would
> swallow genuine layout failures; and the file **already concedes 60 s for this
> same cold compile** one line below the `goto` that kept the 30 s default.

The lead's read: both are right about different things, and the distinction they
are talking past each other on is **setup step vs. measurement cell**. The
finding was about `applyPreferences`, a *setup* step — guarding that costs one
unmeasured axis, not a wrong number. Guarding the 168 *cells* would indeed
swallow real layout failures, which is `dev`'s strongest point and is not what
was asked for. **Reviewer decides.** It is the third occurrence of this class, so
"we made the window bigger" needs to be a judgement someone signed, not a default.

### ⚖️ RULED 2026-08-28 by the cleanup-round review — the window is **not** the fix

Full reasoning: `docs/features/cleanup-2026-08-28-review.md` → "The ruling".

**`dev` is right about the 168 cells and that half is now settled**: nobody may
wrap the route loop — a `try/catch` there turns a real layout failure into a
skipped cell, which is the "no numbers" failure both sides argue against.

**`dev` is wrong that this closes 🟢 4, and it is mechanical, not a matter of
taste.** `COLD_COMPILE_TIMEOUT_MS` moves the *default*; the line that expires
is `waitForSelector('.setting-choice', { timeout: 60000 })`, which carries an
**explicit** 60 s that already wins over the default. Raising the default to
the same 60 s buys that line nothing — `dev`'s own "the file already concedes
60 s one line below the `goto`" is, read carefully, the argument against the
fix. Reproduced live on this tree, with the fix in place:
`node tools/measure.mjs http://localhost:4200 --route / --viewport 1024x768
--theme light --lang de` died on `Timeout 60000ms exceeded … locator('.setting-choice')`,
uncaught, **before `SUMMARY` printed** — so the already-measured cells lose
their findings too. The same command after `/einstellungen` compiled: 1/1,
0 findings.

**What must be wrapped:** the `applyPreferences` **call**, per `(theme,
language)` axis, with `note('INSTRUMENT', …)` and a mandatory `continue` —
never the 14-route loop. `continue` is load-bearing: measuring 14 routes
against unknown theme/language yields *mislabelled* numbers, which is worse
than none. Cost when it fires: **14 of 168 cells, 8.3 %**, and the run still
reaches `SUMMARY`. Today the same expiry costs up to 168 cells and the summary.
Same guard on the four later `applyPreferences` calls (`:791 :844 :912 :1012`).

**Keep the constant** — it genuinely helps the `goto` and the two clicks. The
answer is *both*, not either. Filed as the round's only 🟠. Fourth pass at this
class; if it ships unguarded again it is 🟠 on sight, not 🟢.

**Correction to the bullet below:** `frontend/README.md:47` is **not** broken
and must not be "fixed". `ng serve` runs the `development` configuration, whose
`environment.development.ts` supplies `devDeviceToken`, and `DeviceTokenStore`
falls back to it when `localStorage` is empty — tested: a clean context with no
seeded token renders the full shell at `/einstellungen` (`.setting-choice`
count 2), not `/pairing`. `GXP_MEASURE_DEVICE_TOKEN` is a **production-build**
concern (`measure.mjs:167-171`; the script's default base URL is `4300`). What
killed `dev`'s first run — and the reviewer's — is the cold compile above.
**Scope note 2026-08-28:** "do not fix the README" applies to the **withdrawn
`/pairing` diagnosis only**. A *different* and true reason to mention the
variable emerged later — without it the preference guard is inert — and one
qualified paragraph was added on those grounds. See the decision below.

## Two stale claims `dev` found and did not touch (lead's files, or out of brief)

- ~~**`frontend/README.md:47`**~~ — **withdrawn 2026-08-28, the diagnosis was
  wrong.** `dev` reported that following the README lands on `/pairing` because
  `GXP_MEASURE_DEVICE_TOKEN` is unset, and the lead repeated it. The reviewer
  disproved it live: `ng serve` runs the `development` configuration, whose
  `environment.development.ts` supplies `devDeviceToken`, and
  `DeviceTokenStore.load()` falls back to it when `localStorage` is empty —
  tested with a clean Playwright context, `/einstellungen` renders the full
  shell. `GXP_MEASURE_DEVICE_TOKEN` is a **production-build** concern
  (`measure.mjs:167-171`, default base URL 4300). What actually killed both
  first runs was the cold compile. **Do not "fix" the README.**
- **`docs/system-context.md:148`** still says `PropertyContentService` is *"backed
  by static TS today, swappable for the future Quarkus backend"* (wire-fed since
  REQ-510), and *"**Two signals:**"* now introduces three.

## ✅ CLOSED — the `getSectionRoute` cleanup round, reviewed 2026-08-28

**NEEDS CHANGES ❌ — 🔴 0 · 🟠 1 · 🟡 2 · 🟢 7.** Full round:
`docs/features/cleanup-2026-08-28-review.md`.

**Job 1 is clean.** The owner-approved `frontend/CLAUDE.md` bullet is verbatim
(whitespace-normalised comparison against `docs/handoff.md:415`: equal — the
re-wrap changed no word), the sentence is *true* as well as approved
(`routeMatches` `menu-split.ts:39`, `itemForRoute` `:44`; callers at
`sidebar.component.ts:82` and `section-placeholder.component.ts:56`), and it is
finally **complete** — third pass at that sentence. `getSectionRoute` and
`targetSectionId` are now **zero** in `frontend/src/`; `card.targetKey` still
builds the `@for` track key at `guest-content.mapper.ts:211` and the wire field
is untouched.

**Reviewer's own numbers:** `tsc` 0 · `ng test` **34 files / 311 passed**
(static per-file `it(` diff vs. HEAD is exactly −2 in exactly one file, so the
313→311 arithmetic is honest and no case was lost) · `ng build` clean, **main
460.08 kB** · `measure.mjs` **168/168, 0 overflowing, 0 truncated, 0 findings**
with `heading.y 88 / cardRow.bottom 744` in all four 1280×832 cells ·
`measure-states.mjs` **44/44, 0 findings, 8 blocks**, all five restores
successful, dev instance verified back to `Anna & Tom` / artwork `null` ·
backend untouched so **441 · 0** stands · Home screenshot compared against
`docs/design/assets/home-no-artwork.png`, no regression. `restore()` probed
independently against six fabricated responses (two more than `dev` tried):
**0 throws, 5 findings**.

**Blocking:** the 🟠 above — guard `applyPreferences`. **Non-blocking but
belongs in the same edit:** `docs/system-context.md:148` still says
`PropertyContentService` is *"backed by static TS today"* and *"Two signals:"*
for three, **inside the clause `dev` just corrected** — the same defect class
this round exists to close.

## Carry-forward for the review of the `getSectionRoute` round

The round-3 reviewer, watching that round land while reaping a stale monitor,
flagged one thing it will not review itself:

**🟢 4 asked for *report and continue*, not merely a longer wait.** The in-flight
work appears to raise `measure.mjs`'s cold-compile timeout and document it. **A
raised timeout still ends all 168 cells when it expires.** Whoever reviews that
round must check the grid genuinely **survives** a cold `ng serve` — i.e. that a
timing-out block `note()`s itself and the remaining cells still run, the way
`measure-states.mjs`'s `guarded()` already behaves. This is the third occurrence
of the class; re-timing it a third time is not closing it.

Confirmed good in the same glance: `measure-states.mjs` gained a `restore()`
helper that checks `response.ok` and emits `note('INSTRUMENT', 'restore FAILED …')`
for both the display-name and artwork restores, which closes the silent
half-restore of finding 🟡 2.

## A defect Part B shipped, found 2026-08-28 and fixed the same night

`measure.mjs:546`'s preference race guard confirmed a server-side write by
polling **`GET /api/guest/session`** — the endpoint **Part B retired**. Since
commit `4459859` it received a `404`, which
`if (!res.ok) return true; // offline states measure what they render` swallowed,
so the guard returned instantly and checked nothing. Observed, not inferred: a
full grid emitted `PREFERENCE NOT APPLIED: / asked light/de got dark/de` on the
first cell of the first axis — precisely the reload-races-the-PUT failure the
poll's own docblock describes.

**This was the lead's miss.** Part B's brief said to merge and rename the two
guest reads; nobody grepped for what else called the old paths, and the review
rounds looked at `src/` rather than at `tools/`. The lesson for the next
endpoint rename is one line: **an instrument is a client too.**

Fixed in the same night's cleanup round, with a grep for any other caller of
`/api/guest/session` or `/api/guest/screens/home`.

## Environment note for the next session — read before running the backend

**There is no JDK on `PATH` and `JAVA_HOME` is unset on this box.** Every backend
run this session used `/tmp/gxp-jdk/jdk-21.0.12+8`, left behind by an earlier
session. It is in `/tmp`, so **it will not survive a reboot**, and the next agent
that needs `quarkus:dev` or `./mvnw` will hit *"JAVA_HOME is not defined
correctly"* and may not find it. `docker` also needs `sg docker -c "…"` here.
Worth a permanent JDK and a line in `backend/README.md` — owner's call.

## The `unpaired` guard — decided and closed 2026-08-28

`measure.mjs`'s preference guard had **three** independent inertnesses in three
lines, all found and fixed in one night: it polled the endpoint Part B retired;
`page.waitForFunction` resolves on the truthiness of what the predicate
**returns**, so an `async` predicate returns an always-truthy Promise and the
poll **never blocked since it was written** (the real old predicate resolved in
**103 ms** holding `false`); and `if (!token) return 'unpaired'` short-circuits
before any fetch while the dev build keeps no token in `localStorage`.

**Lead's decision on the third**, a fourth option beyond `dev`'s three: report it
**once at startup**, not per axis. Noting it per cell would add 12 findings to
every unseeded run and destroy the "0 findings" reading — but that is an argument
about *where* the note belongs, not about whether to note. Defaulting the token
to the dev literal was rejected: it would silently pair a production build that
the seeding comment deliberately lets fail.

**Why it was worth the thread.** The reviewer forced the condition the race
needs — a 2 s delay on `PUT /api/guest/preferences` — and ran the same command
twice: unseeded, **23 findings** including one entire axis of 14
`PREFERENCE NOT APPLIED` plus 8 spurious contrast findings inherited from it;
seeded, **0**. The inert guard did not merely stay quiet, it silently mislabelled
whole axes.

`frontend/README.md` gained one qualified paragraph on those grounds — it states
that the app renders identically either way and that the *measurements* differ
only under that race, with the measured numbers.

## Recorded, deliberately not fixed — the `401`/`403` case, and a fourth `unpaired` path

`measure.mjs`'s preference poll still lumps `401`/`403` — a stale or wrong
`GXP_MEASURE_DEVICE_TOKEN`, i.e. the **instrument's** fault, the same family as
the `404` that was just closed — together with `5xx`, which is a genuine offline.
`dev` argued it is **latent, not live**: a rejected token makes the kiosk wipe
itself to `/pairing`, the axis is then skipped with a loud note, so it cannot
fire silently today. The lead accepted that and did **not** grow the round —
consistent with the owner's standing preference for not building against
conditions that are not yet real. If it is ever wanted, it is one line beside the
404 check: `if (res.status === 401 || res.status === 403) return 'bad-token';`

**A fourth, found by the reviewer and deferred for the same reason.** The
`unpaired` return is silent *unconditionally*, but the startup note that speaks
for it is gated on `!seedToken`. So a **seeded** run whose page has no token —
after a 401 wipe, or with storage blocked — says nothing at all. Same shape as
the other three. Latent today; recorded rather than fixed, on the same reasoning
that keeps the `401`/`403` case open.

## Awaiting owner — a SECOND proposed `frontend/CLAUDE.md` entry (2026-08-28)

From the dev-threshold review, finding 2. Proposed and **not written**. The fact
it records is now load-bearing and lives only in three spec comments:

**unit tests build under the `development` configuration**, so a spec sees the
**dev** value of anything in `environment.development.ts` — the 1-minute
staleness threshold, the dev device token. Proven from source:
`@angular/build/src/builders/unit-test/options.js:39` is
`options.buildTarget ?? '::development'`, and `angular.json`'s `test` target sets
no `buildTarget`. The exact wording the reviewer proposes is in
`docs/features/cleanup-2026-08-28-review.md` → round 3, finding 2.

Why it matters: a spec that hardcodes a production figure is **green and wrong**.
Four cases did exactly that before this round — one carried the comment
*"the escalation threshold is an hour"*.

## Queued work — approved, not yet done

### Two more owner approvals, 2026-08-27 (from the Part B review)

- **`frontend/CLAUDE.md` → Tests**, this **exact text**, verbatim, replacing the
  sentence that still says *"the three full-screen states and the staleness
  pill"*:

  > `tools/measure-states.mjs` is the review instrument for the three full-screen states and for the staleness chrome — the discreet pill and its escalated banner, the latter driven by ageing the cached record's `storedAt` past the threshold; it needs the dev backend and drives its state via the manager API (and restores it).

- **`docs/backend/specification.md`:69, 271–272** gets supersession markers. The
  owner's earlier "leave it as a dated log" ruling covered `:511–512`, an
  amendment *log*; this is the live **API table**, which still lists both retired
  endpoints as current.

**The lead's width estimate was wrong and the review corrected it.** The lead
wrote "~740 px needed, ~890 px available, fits". Measured: one line needs
**567 / 712 px**, and the real bound is Home's **widget column** (x475 / x594),
not the sidebar — leaving **523 / 654 px**, a **44 / 58 px shortfall**. Two
errors: the wrong bound, and sizing against the German copy when the binding
string is **`stale.banner.bodyStandby` in English**. Owner-approved way to close
it, in order: trim the chrome (icon `lg`→`md`, padding `--space-5`→`--space-4`,
~25/30 px), move the timestamp onto the bar's row (buys no width but flattens the
bar out of the headline's band), and only then shorten **the English standby
string alone** — the German copy the owner chose stays untouched.

**Independent of all this:** the banner clears Home's hero headline by **4 / 5
px**, and that headline is manager-authored with `{stay.displayName}`
interpolated. The margin depends on a guest's name. Latent collision, present
today.

### Part B fix round — the staleness banner (owner-approved 2026-08-27)

Runs **after** the Part B review closes, together with whatever that review
finds. Do not start it while the review is in flight — it changes the geometry
and the tokens the review is measuring.

1. **Drop the width clamp.** `min(22rem, 40vw)` was the **planner's** derivation
   from the pill, never the owner's — he never specified a maximum width. The
   banner widens until the body is **one line**, and stops at the **content-area
   edge** (it must not cover the sidebar, which stays navigable). Estimated from
   the measured boxes: the body needs ~650 px of text plus chrome ≈ 740 px, in a
   ~890 px content area at 1024×768 — fits, but **measure it, do not assume it**.
   This resolves the spec's own contradiction (`min(22rem, 40vw)` **and** "at most
   two lines" could not both hold with the approved copy) by removing the clamp
   rather than the clause — a third option neither dev nor the lead had offered.
   Side effect: one line makes the banner **flatter**, so it covers less.
   `connection.md`'s "the pill grown into a panel" rationale no longer describes
   it and must be rewritten — it is a bar now.
2. **A warning colour, as a global role.** *(See the derivation below.)*
3. **Keep `WifiSlash`.** The glyph names *what* is wrong (connection); the colour
   says *that* it matters. A warning triangle would replace one statement with
   the other. `connection.md`'s severity-order argument still holds: the
   connection screen is a full-screen takeover and nothing a bar does can
   out-rank it, so a warning colour on the milder state does not invert the order.
4. **Motion, both behind `prefers-reduced-motion`** (`design-sheet.md` requires
   it): a **350 ms `easeOutExpo` entrance** (the sheet's Page Transition token) so
   the pill→banner swap is an event rather than a silent substitution, and a
   **slow pulse on the glyph only** — never on text, which must stay re-readable.
   A **marquee was proposed by the owner and rejected together**: it contradicts
   *"Animationen ruhig und hochwertig"*, needs a WCAG 2.2.2 pause mechanism a
   kiosk cannot offer, disappears under reduced-motion exactly where a fallback
   is needed, and would run for hours in a guest room — the banner only appears
   after 60 min offline and stays until contact returns.

**The warning colour — decided, with its derivation**

`docs/design/design-sheet.md` §Status already carries a v1 palette
(`Warning #F8B84E`, `Danger #F26666`, …) marked *"noch nicht verwendet"*, and
`Offline #A79DBA` was already realised from it as `--status-offline-raw`. So the
system had an answer; the lead's first proposal (derived from `--mesh-3`) was
**withdrawn** in favour of continuing the existing decision rather than placing a
second one beside it.

| Theme | Value | Contrast on glass | Note |
|---|---|---|---|
| light | `#895806` | **4.64** over `--mesh-6`, 5.18 over `--mesh-8` | hue 37° from `#F8B84E`, darkened to a working lightness |
| dark | `#f9b84e` | **5.63** | `#F8B84E` essentially unchanged — the v1 value *is* the dark theme |

Both clear 4.5:1 **deliberately**: as a decorative glyph they need not, but that
is exactly the shortcut that left `--status-offline` with its permanent *"never a
text colour"* warning. Text-capable from the start means the role can later move
onto a title or border with no re-measurement.

Three guardrails, all of which must reach the token comment:
- **Name it after the status family, not the banner:** `--status-warning-raw`
  (primitive) + `--color-status-warning` (semantic) + a `[data-theme='dark']`
  override, mirroring `--status-offline`.
- **It is a FOREGROUND role** — text, glyph, border. **Never a surface fill:**
  `#895806` as a background would fail any dark text placed on it. A tinted
  warning surface, if ever needed, is a second token.
- **Warning is not error.** `Danger #F26666` sits beside it in the v1 palette for
  destructive actions. Nobody may repurpose warning for a delete dialog.

Documents to carry it: `frontend/src/styles/tokens.css`, `docs/design/tokens.md`
(as an in-house value with the derivation and both measured ratios — the
`--ink-600` precedent, which records its own Figma deviation the same way), and
`docs/design/design-sheet.md` §Status, where `Warning` moves from *"noch nicht
verwendet"* to realised, noting the v1 value is the dark theme and the light one
is derived from it.


**Retire the dead route-resolution convention.** Owner-approved 2026-08-27,
**all three parts**, after he had another agent walk him through the frontend
and it surfaced `getSectionRoute()` as callerless. Do it as its own small round
**after Part B lands** — it must not be mixed into an in-flight feature.

*Why it matters more than an unused function:* `frontend/CLAUDE.md` still names
`getSectionRoute()` as **the** convention for menu-shaped routes, while the
mechanism that actually runs — `layout/sidebar/menu-split.ts`'s
`routeMatches()` / `itemForRoute()`, used by `SidebarComponent` and
`SectionPlaceholderComponent` — is undocumented. So an agent finds the dead path
documented and misses the live one. `guest-frontend-integration`'s review found
the same sentence wrong on 2026-08-21 (🟠-2) and the correction was applied only
to its second half; Part A then introduced the replacement and neither of its
two review rounds proposed updating the file. Repeat miss, worth naming.

1. **`frontend/CLAUDE.md`** — replace the two "Routes for menu-shaped things…"
   lines with this **exact owner-approved text**, verbatim, no edits:

   > - **Routes come from the wire, not from a lookup.** Every menu-shaped thing — a sidebar entry, an overflow tile, an action card — navigates the server-resolved `route` the payload delivers, because enablement and ceiling resolution live on the server. For the reverse direction, from the current URL back to the item that claims it, use `layout/sidebar/menu-split.ts`'s `routeMatches(item, url)` / `itemForRoute(items, url)`; `SidebarComponent` and `SectionPlaceholderComponent` share them, so a route normalises identically in both. There is no id→route lookup service — `PropertyContentService.getSectionRoute(id)` was retired in 2026-08-27 once nothing called it.

2. **Delete `PropertyContentService.getSectionRoute()`** and its four spec cases.
3. **Delete the `ActionCardShape.targetSectionId` field** and the
   `requireText(card.targetKey, …)` line that fills it. The lead first advised
   keeping it for boundary validation, then withdrew that: validating a field
   nothing reads protects nothing and only breaks a kiosk over a value that does
   not matter. Action cards need `route`, which the server resolves.
4. **Reconcile `docs/system-context.md`** — lines ~148 and ~155 still describe
   `getSectionRoute()` as live.

**Related, not part of this:** Part B's 300 s stamp bucket means a `200` with
identical content re-maps the whole tree every ~5 minutes. `02-design.md` §2.3
rejected a deep-equality guard in `adopt()` partly because it would add code to
the file REQ-949 was shrinking — **that argument died with the line budget on
2026-08-27**. Re-weigh it when Part C makes module content real; not now.

## Deferred questions — for the owner, none blocking

1. **REQ-ID space is exhausted.** `9xx` was the last free three-digit block.
   Feature nine needs a scheme (four digits, or a slug prefix).
2. **`PUT /api/manager/menu-config` is a whole-set replace** — a partial submit
   deletes D5's `enabled = false` rows, and absence resolves back to *enabled*.
   Pinned with a test for now; it becomes a real trap when `backend/manager-ui/`
   is built.
3. **Part A is invisible in production at first.** With `n = 5` and D5, a fresh
   instance shows today's five sections and no overflow entry until a manager
   enables something.
4. **Migration `V10` is contested** with the deferred `instance-identity-and-certs`
   plan. Part A takes it; that plan renumbers when picked up.
5. **Constitution wording vs. the standby banner.** v1.9.0 says the escalated
   banner directs *"the guest to their host"*, but the owner decided it also
   shows on a **vacant** room's standby screen, where it addresses staff. Part B
   judged that nothing is contradicted (the clause's own rationale is
   operational alerting) and proposed no amendment. If the owner wants the
   constitution to name the staff case explicitly, that is an amendment and
   needs his approval of the exact text. Non-blocking.
6. **Watch item for Part B implementation, not a question:** REQ-946's
   acceptance criterion reads *"given no content write between two polls, then
   `304`"*, but the version stamp carries a **300 s refresh bucket**, so the
   poll pair that straddles a bucket boundary legitimately returns `200`. The
   test must control the clock or avoid the boundary, or it will flake roughly
   1 run in 10. `02-design.md` reasons the bucket through and it should stay —
   the criterion is what needs to be read precisely.
7. **`docs/backend/specification.md:511–512`** still quotes the retired cache
   wording as a v1.4.0 amendment instruction. Owner decided to leave it as a
   dated log.
