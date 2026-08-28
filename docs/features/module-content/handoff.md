# Handoff — module-content (Part C)

**Last session:** 2026-08-28, `planner` — the **third narrow document fold** of
the day, closing four owner decisions **D16…D19** into `01-prd.md`. No design,
no TODO, no analyze gate; Part C was deliberately not planned further.
*(Earlier the same day: the D9 fold, then the D14/D15 fold. Before that,
2026-08-27: `01_prd` as a preservation document, then a reconciliation closing
D10 and D12.)*
**Branch:** `refactor/bce-subpackages-and-boundary-security` (owner instruction).
**Nothing was committed** — the working tree carries the D14/D15 fold, this
fold, `docs/design/screens-inhouse/info.md`, `docs/features/index.md` and
`docs/design/design-sheet.md` uncommitted.
**Status:** **READY FOR `02_design` ✅ — nothing owner-gated is left.** The
single blocker (*"no module screen is designed"*) was discharged by
`docs/design/screens-inhouse/info.md`; the three product calls that blocked a
clean gate are answered by **D16, D17, D18**, and **D19** reclassified a fourth
question out of the open list. **No approval gate is open.**
**Two things are outstanding and neither blocks `02_design`** — see
"What is genuinely outstanding".
**Constitution:** **v1.10.0**. It **binds nothing here** — its new clause
requires a shorter `%dev` value for a threshold gating an otherwise unreachable
state, and this Part introduces no threshold. v1.9.0's cache boundary, untouched
by the amendment, is what governs. *(The lead confirmed the reconciliation and
confirmed the new clause binds nothing here. **One consequence: the pre-D17
claim that v1.10.0 "leans towards" seeding a deliberately unknown type in `%dev`
is withdrawn** — do not cite the clause for that.)*

## The third fold — four owner decisions

- **D16 — REQ-965 is DESCOPED (option (a)).** The owner declined to amend
  `docs/backend/specification.md` §3 decision 5; it **stands unamended**. Part C
  delivers **the wire shape, the rendering path and the registry**, and **module
  content comes from the `%dev` seed only**. The manager cannot author module
  content in this Part. REQ-965 keeps its ID and meaning, takes **no TODO
  task**, and **`04_analyze` must record it as a declared exclusion, not a
  coverage gap** — it is the only such carve-out.
  **Say the honest thing, because it is written into the PRD deliberately:** a
  WiFi password is per-property and somebody has to type it, so a Part whose
  content can only come from release seed data **cannot serve a real customer**.
  What Part C is worth on its own is the transport, the per-type shape, the
  registry and one rendered module against seeded content — a real foundation
  for the twenty types that follow, and **not a shippable manager feature**.
  The owner anticipated this himself at D8 and confirmed it stays future work.
  **Successor feature named, not created, not planned: `module-content-authoring`.**
  **Its precondition is not code — it is an owner-approved amendment to decision
  5**, which is the decision that makes the platform partly a CMS. Tracked in
  `docs/features/index.md` → "Tracked, not blocking".
- **D17 — one authored module type in `%dev`, not two.** REQ-976 is amended:
  `%dev` seeds `info` only; the registry's multi-type dispatch is proven by a
  **fabricated second type that exists only in a test** — never in the contract,
  never in the seed, never as a screen. **Why that is legitimate:** the
  requirement exists to prove *dispatch on the discriminant*, which a fabricated
  type proves directly, and the same test can also drive the REQ-973 miss path
  and the REQ-972 drop path that a well-formed second seed never reaches.
  Designing a screen to satisfy a seed requirement is **work for the requirement
  rather than for the product**, which is exactly the trap D14 makes expensive.
  **What it costs:** `%dev` never shows two module layouts side by side — a demo
  affordance, not a correctness property.
- **D18 — an unresolvable placeholder is refused at the WRITE; the frontend does
  nothing.** Two halves, in two different features:
  - **Server (not Part C's).** A manager may not *save* content carrying a
    placeholder the engine cannot resolve. **This already ships:**
    `TemplateControl.findInvalidPlaceholders` is called by
    `PropertyControl.validateTemplates` (property text; standby fields against
    the narrower `STANDBY_PLACEHOLDERS`) and `MenuControl.collectInvalidTemplates`
    (card templates), both raising `400 unknown-placeholder` naming field and
    placeholder. **Under D16 Part C has no write path, so this half belongs to
    `module-content-authoring`** — writing it here would reproduce the REQ-965
    shape D16 just removed.
  - **Frontend (Part C's, and it costs no code).** If one slips through, **the
    raw placeholder text simply appears** — no mapper logic, no row-dropping, no
    substitution. That is already `TemplateControl.resolve`'s documented
    behaviour, so the fallback is free and stays a last-resort *visible* error.
  - **Option (a) is REJECTED and will be re-proposed:** the mapper must not drop
    a value because it contains `{…}` — it hides an authoring error and
    false-positives on legitimate braces. Recorded on **REQ-972**: the drop path
    is for **structural** invalidity, and the mapper carries no
    placeholder-shaped logic at all.
  - **Syntax:** `{stay.checkOut|time}` — **curly braces, no dollar sign**. The
    rule is *"show the raw placeholder text as authored"*.
- **D19 — empty states are PER MODULE TYPE.** Consistent with D14: what "empty"
  means and what belongs in its place depend on what the module shows, so
  **there is no shared generic empty state.** This **reclassifies** the old open
  question rather than answering it — it is **a line on the checklist for
  designing any module type**, alongside layout, fields and copy. `info` has
  none, and that is a **finding rather than an omission**: the check-out row is
  a stay fact that is always present, so `info` cannot be empty.
  **Keep the two cases distinguished — they look alike and mean opposite
  things:**

  | case | meaning | renders |
  |---|---|---|
  | menu item carries **no module** | nothing authored for this section yet | `SectionPlaceholderComponent` — "coming soon" |
  | module delivers **zero entries** | authored, and empty right now | **that module type's own empty state** |

## Two findings from this fold

1. **A factual correction, found by checking D18 against the code.** This PRD
   and `docs/design/screens-inhouse/info.md` → Decision 2 both framed the
   unresolvable placeholder as *"an `OCCUPIED` stay with no check-out
   timestamp"*. **That state does not exist.** `stay.check_out` is
   `timestamptz NOT NULL` (`V2__domain_config.sql`), and
   `GuestControl.resolveLocalized` passes a null `checkOut` into the template
   context **only when `stay == null`** — which for `/info` means a vacant room,
   and a vacant kiosk is on `/standby` while `screens/*` answer
   `409 room-vacant`. So the raw-braces outcome on `/info` is currently
   **unreachable**, not merely unlikely. That **strengthens** D18: the
   do-nothing fallback is free, correct, and already in place for the day
   somebody adds a placeholder whose value may legitimately be absent.
   **`info.md` still carries the old framing** — it is a design document and
   this fold edited none. Correct it whenever `02_design` or a design pass next
   opens it.
2. **`info.md`'s "What generalises" table is missing a line, and D19 is why.**
   Every module type's design must state **an empty state, or a stated reason
   the type cannot be empty**. Flagged, not written — editing a design document
   is design work.

## What is genuinely outstanding — do not call Part C "clean"

**`02_design` may start today. Neither of these blocks it.**

1. **The PRD has no `## Acceptance criteria (testable)` section**, which
   `docs/features/_template/01-prd.md` requires and `04_analyze` reads. It is
   the one thing this document lacks. **Nothing owner-level blocks writing it any
   more** — all three conditions that used to are discharged. When it is written:
   **REQ-965 gets none** (descoped, correctly), and **three wait on `02_design`**
   — REQ-963's (open item 1), whatever covers discriminant-literal drift (open
   item 2), and REQ-979's (open item 5). **`04_analyze` cannot pass without this
   section.**
2. **The single `pom.xml` line `<useOneOfInterfaces>true</useOneOfInterfaces>`.**
   Owner-gated and outstanding; it blocks the **commit** that first ships a
   union, not the plan. Do not add it now — `api/openapi.yaml` still has no
   `oneOf`, so it is a no-op in an approval-gated file with nothing to review it
   against.

## Open items — three, all ordinary `02_design` work

*(Numbering is unchanged so older references still land. **3 is answered by
D18**, **4 is reclassified by D19**, **6 is reassigned out of this Part**.)*

1. **REQ-963 vs. the discriminator property.** Variant B's member `type` is an
   unbounded `string`, and REQ-963 forbids unbounded strings. Either REQ-963
   carves the discriminator out, or `maxLength` goes on it — and the second is
   **untested and suspect**: a `@Size`-annotated `getType()` is the same shape
   that made the `enum` fail to implement the generated interface.
2. **REQ-980 does not catch a wrong discriminant *string*** — only an unhandled
   union member. With `type` typed as `string`, a registry keyed on a typo'd or
   renamed literal typechecks fine. **After D17 this can no longer lean on the
   `%dev` exercise** — `%dev` seeds one type, so by construction there is no
   second type there. **The fabricated-type test D17 introduces is the only
   place in this Part where this axis could be covered**, and `02_design` should
   say so explicitly rather than assume the seed covers it.
5. **D15 puts module content under an already-classified top-level key, and Part
   B's compile-time guard will not fire.**
   `guest-data.service.spec.ts`'s `Record<keyof Required<GuestContent>, true>`
   enumerates **top-level** payload keys and `menu` is already `true`, so
   `GuestMenuItem.module` arrives without breaking the build the guard exists to
   break. **Honestly a point against D15 and for the rejected top-level
   `modules[]` array**, recorded rather than traded. Open: whether the guard
   should recurse, or whether REQ-979's runtime test reaching explicitly into
   `menu.items[].module` is enough.

## Do not re-derive these

- **Manager-authored module content is a Non-goal of Part C (D16).** Do not
  invent a manager write path, a `manager*` content endpoint or a
  `module_content` write API here. Do not create a `module-content-authoring`
  directory either — it is named and tracked, not planned.
- **Do not mint a Part C requirement for D12's disclosure obligation.** It is an
  **owner task with a trigger** in `docs/features/index.md` → "Tracked, not
  blocking": *before the first property authors real module content.*
- **Do not add placeholder-shaped logic to the mapper (D18, option (a)
  rejected).** A `{…}` in a value is not a drop trigger.
- **Do not design a second module type to satisfy REQ-976 (D17).** The second
  type is fabricated, and it lives in a test.
- **Do not invent a shared generic empty state (D19).** And do not collapse
  "no module" (→ `SectionPlaceholderComponent`) with "zero entries" (→ that
  type's own empty state).
- **The generic-rows option was offered to the owner and declined.** A
  `GuestRowsModule` as one member of the union would have made REQ-976 free. He
  chose per-type (D14). Read it as already answered, not as new.
- **The module carrying its own route was rejected** under D15: two sources for
  one fact, which is exactly what Part B's consolidation removed, and a renamed
  `route` would silently orphan content.
- **`info.md`'s "What generalises" table is the contract for every later module
  screen** — screen frame, the row, absent value ⇒ absent row, a module never
  restates the section's name, delivered array order with no per-row sort key,
  provenance invisible, values left-aligned because the staleness banner shares
  the screen by design. Zero new tokens, zero new icons.
- **`info.md` closed four things that will be re-proposed:** a per-row icon (no
  — design principle 7), a per-row ordering field (no — array order), a
  `sensitive` marker (no — D12 closed it, and the WiFi row is precisely where
  somebody will want one), and a `title` on the module (no — the `<h1>` is
  `GuestMenuItem.label`).
- **Do not add a `{ path: 'info' }` route.** The `einstellungen` precedent is app
  chrome, not a catalogue row.
- **No SSID row and no WPA QR code on `/info`.** The SSID belongs to a future
  `wlan` module; the QR code needs a generator, a **new dependency**, and is
  approval-gated.
- Part A needs **nothing** from this Part. Its eight placeholder catalogue rows
  are **disposable fixtures**, not candidate modules.
- REQ-962 is where `guest-content.mapper.ts`'s redundant `sortIndex` re-sort gets
  resolved. Part A deliberately leaves it alone.
- **D12's three options are withdrawn, all of them** — no per-field `sensitive`
  flag, no per-type classification in the registry, no "module content is never
  cached". What survives is the **credential line** (REQ-979); Part B's REQ-951
  is the precedent, including its warning that the test must **not** filter on
  names like `password` or `code`.
- **`/info` must render identically from cache.** No degradation, no hidden row,
  no "unavailable" substitute. Principle 3 is untouched: **the REQ-972 drop must
  not quote the offending content**, value or label, and a console warning is not
  exempt.
- **Three consequences of D9/Variant B stay folded into the requirements that
  own them** — lost `switch` narrowing (REQ-970, bought back cast-free by a
  1-line predicate, confined to `guest-content.mapper.ts` by REQ-977); lost
  compile-time exhaustiveness (**REQ-980**, new); weaker soundness (REQ-977 — the
  mapper's hand-validation is what proves the shape now). Do not "fix" any of
  them by reverting to the `enum`.
- **REQ-966's orphan-file hole:** the generator does not delete files it
  previously wrote under `packages/api-client/model/`. Any commit here that
  removes or renames a schema must sweep the orphans in the same commit.
- **REQ-978's `/info` figures are computed, not measured.** The one needing a
  real measurement: the escalated staleness banner clears the panel by **1.2 px
  at 1280** and **overlaps by 0.2 px at 1024**.
- **The `%dev` duplication REQ-967 owns:** `DevDataSeeder.java:97` hardcodes
  *"Bis 11:00 Uhr am Abreisetag"* while `{stay.checkOut|time}` ships two files
  away. Under D16 fixing it is a **seed edit**, not a manager action, and it is
  safe under D18 because that placeholder is a *known* one.
