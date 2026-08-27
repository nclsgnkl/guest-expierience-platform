# Session Handoff — 2026-08-27, `lead`

**Branch:** `refactor/bce-subpackages-and-boundary-security` — **owner
instruction: stay on it**, even though the name does not match the work
("Ich habe meine Gründe"). Do not create or switch branches.
**Commits:** the owner granted commit authority 2026-08-27 (*"doch committe
auch gerne"*). Commit at **coherent checkpoints** — a reviewed block, a
finished amendment — never mid-implementation while a `dev` agent is writing.
Committed so far: `0ed4f03` constitution v1.9.0 · `c8081a8` the split and Part
B's plan · `33f745a` Part A phases 1–3 · `14c005e` Part A complete. The tree was
clean after each.

## What this session did

The owner brought an **owner-written** PRD (`docs/features/modular-menu-catalogue/`)
and asked for it to be pressure-tested rather than accepted. It was rewritten
and **split into three features** (owner decision D1):

| Feature | Part | REQ | State |
|---|---|---|---|
| `sidebar-catalogue-overflow` | A — the owner's actual ask | 901…935 | **Done, committed** `33f745a` + `14c005e`. Two review rounds, both closed |
| `guest-payload-consolidation` | B | 941…952 | Planned, `04_analyze` **PASS**. Phases 1–3 in progress |
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

The lead's fix-round brief asked for `measure-states.mjs` and did **not** name
`measure.mjs`. If `dev`'s report does not show a 168-cell run, **run it before
committing.** Do not accept the banner cells as sufficient.

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
