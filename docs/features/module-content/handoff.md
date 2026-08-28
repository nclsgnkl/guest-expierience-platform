# Handoff — module-content (Part C)

**Last session:** 2026-08-28, `planner` — **the fourth fold of the day, and the
one that finished the plan.** Wrote [`02-design.md`](02-design.md),
[`03-todo.md`](03-todo.md) and [`04-analyze.md`](04-analyze.md), closed the
PRD's missing **Acceptance criteria** section, answered its three open items,
and made two corrections to [`docs/design/screens-inhouse/info.md`](../../design/screens-inhouse/info.md).
*(Earlier the same day: the D9 fold, the D14/D15 fold, then the D16…D19 fold.)*
**Branch:** `refactor/bce-subpackages-and-boundary-security`. **Nothing was
committed.**

**Status: PLANNED THROUGH `04_analyze` ✅ — result PASS.**
19/19 in-scope requirements covered by **22 tasks in 8 phases**, 0 orphans, 0
unresolved markers, 1 declared exclusion (REQ-965, D16), no constitution
conflict.

**⏸ ONE OWNER APPROVAL IS OPEN, AND IT IS THE FIRST TASK.**
`backend/app/pom.xml` needs `<useOneOfInterfaces>true</useOneOfInterfaces>` on
the `jaxrs-spec` execution. **T-01 asks and ends the turn. Nothing may be built
before the answer** — see the next section for why that is stronger than the
PRD's earlier wording.

**Constitution:** **v1.10.0**, and it still **binds nothing here** — its
threshold clause has no subject in this Part. The v1.9.0 cache boundary is what
governs.

---

## Why T-01 comes before the contract edit

The PRD said the `pom.xml` line "blocks the commit, not the plan". The design
sharpened that to **it blocks the build**: with a `oneOf` in
`api/openapi.yaml` and the flag **off**, `jaxrs-spec` does not fail. It emits a
flattened concrete class with `@JsonTypeName("GuestModule")`, `./mvnw verify`
goes **green**, and the wire carries `{"type":"GuestModule"}` — a discriminant
in no `mapping` that no kiosk `switch` can match. Anyone who regenerates between
the contract edit and the approval gets a green build they can believe.

It is a **generator option, not a dependency** (the spike checked: nothing is
pulled in), gated anyway by `docs/constitution.md` → Constraints and
`backend/CLAUDE.md`. **If the owner declines, stop and report** — Variant C is
strictly worse (no TypeScript union, a concrete instantiable base) and a
free-form bag cannot be boundary-validated at all.

## What the design decided — the calls a later session must not re-take

1. **The union.** `GuestModule` = `oneOf` over one member `GuestInfoModule`,
   `discriminator.mapping: {info: …}`, member `type` a **plain `string`**.
   Reached through the **optional `GuestMenuItem.module`** (D15) —
   `required` unchanged, no top-level `modules[]`.
2. **The discriminator is carved out of REQ-963** (open item 1). Not given a
   `maxLength`. The decisive reason is semantic, not the generator risk: a
   length bound still admits `"nfo"` and `"Info"`, so it bounds the wrong
   dimension. What pins the value is a **set**, pinned three times already.
3. **Bounds:** `rows: maxItems 10`, and a new **`ModuleText`** schema at
   `maxLength: 240` for module label/value. **These are the contract's first
   `maxLength` declarations** (`grep -c maxLength api/openapi.yaml` was 0).
   `LocalizedText` stays unbounded on purpose — bounding it would put `@Size`
   on every existing text field in the contract.
4. **Persistence joins `de.gxp.menu`** — two tables in `V12`, no room scope
   (A2/D11), **no seed rows** (a release must not put content on a running
   property's screens). A new `de.gxp.module` package was rejected: with no HTTP
   surface it would force an edit to `FeaturePackages.FEATURES_WITHOUT_BOUNDARIES`,
   i.e. to an architecture guard's exemption list.
5. **`MenuControl` returns raw templates; `GuestControl` resolves them** — the
   `guestResolvedCards` precedent verbatim, so REQ-964 is met by the mechanism
   that already meets it for `GuestHome`.
6. **Modules are delivered to a vacant room too** (property-scoped, A2). The
   consequence is written down: a vacant payload carries the raw
   `{stay.checkOut|time}` text, nobody sees it (`/standby`), and
   `GuestControl`'s *"no braces ever leave the server"* javadoc is scoped to
   `home` and **must not be generalised**.
7. **The registry is `core/guest/module-registry.ts`** — the only location
   permitted to import `@gxp/api-client`, so `core/modules/` would have needed a
   `frontend/CLAUDE.md` amendment. **The renderer map lives in the section
   host**, not in the registry, because a registry importing a feature component
   would be **the app's first `core → features` import**. The split is held
   total by `satisfies Record<ModuleKind, Type<unknown>>`; REQ-970 carries an
   inline amendment recording it (finding F-1).
8. **REQ-980's guard, written out:**
   `AssertNever<Exclude<GuestModule, ModuleWireMap[ModuleKind]>>`. Its one
   limitation (assignability, so a structurally-subset member would slip) is
   recorded in the design rather than left to be discovered.
9. **The third drift axis is covered** (open item 2) — by a **pinned contract
   test on the Java side**, `de.gxp.guest.GuestModuleContractTest`, the
   `ScheduledJobInventoryTest` precedent. **Not** in `de.gxp.architecture`: a
   sixth architecture guard would need a constitution amendment.
   **The fabricated-type test cannot cover this axis and says so in its own
   header** — its discriminant is defined by the test file, so there is no
   contract for it to disagree with.
10. **The cache guard gains two nested classification tables AND an explicit
    runtime reach** (open item 5) — both, not either. The residual (it is two
    hand-written tables, not recursion) is recorded as a known gap.
11. **The domain union is a real discriminated union** (`type: 'info'` as a
    literal), so `switch` narrows everywhere above the boundary. D9's loss is
    confined to `guest-content.mapper.ts`, exactly as REQ-977 predicted.

## Two things to verify at implementation — neither changes a requirement

1. **A single-member `oneOf`.** The spike ran **two** members; this Part ships
   **one**. If either generator degrades it (no TS union alias, an inlined
   member), **stop and report**. **Do not add a second contract member** —
   D17 put the second type in a *test*.
2. **`maxLength` under `additionalProperties`** on the Java side. If the bound
   does not survive to `@Size`, **record it and continue**: the kiosk enforces
   it, which is where REQ-963's protection actually matters.

## Two findings this gate produced, both already repaired in `01_prd`

- **F-1 — REQ-970 vs. the renderer map** (see 7 above). The PRD now carries the
  inline amendment; the criterion asserts both compile-time guards.
- **F-2 — REQ-978 named an instrument that cannot see the banner.**
  `tools/measure.mjs` measures **routes** (and `/info` is already in its grid);
  the escalated staleness banner is a kiosk **state**, so the 1.2 px / 0.2 px
  figure `info.md` flags needs `tools/measure-states.mjs`. REQ-978 amended to
  name both. **Part B's REQ-952 carried the identical defect** — this is the
  second occurrence of one mistake.

## Corrections made to `docs/design/screens-inhouse/info.md`

- **Decision 2's factual error is fixed.** It framed the unresolvable
  placeholder as *"an occupied stay with no check-out timestamp"*. **That state
  cannot exist** — `stay.check_out` is `NOT NULL`, and a null `checkOut` reaches
  `resolveLocalized` only when `stay == null`, i.e. a vacant room, which is on
  `/standby`. The outcome is **unreachable, not unlikely**. The same correction
  is carried inline in the PRD's "Open items" 3 and its "What stood in the way"
  row 3.
- **Open questions 2 and 3 are closed** in the file (D18, D19) rather than left
  reading as open.
- **"What generalises" gained the line D19 implies:** *every module type states
  its empty state — or a stated reason the type cannot be empty*, with `info`'s
  reason in the Info-specific column.

## Do not re-derive these

- **Manager-authored module content is a Non-goal (D16).** No manager write
  path, no `manager*` content endpoint, no `module_content` write API, and **no
  `module-content-authoring` directory** — it is named and tracked, not planned.
- **Do not mint a Part C requirement for D12's disclosure obligation.** Owner
  task with a trigger, in `docs/features/index.md` → "Tracked, not blocking".
- **No placeholder-shaped logic in the mapper** (D18, option (a) rejected). A
  `{…}` in a value is not a drop trigger.
- **Do not design a second module type** (D17). The second type is fabricated
  and lives in T-16's spec file.
- **No shared generic empty state** (D19), and do not collapse "no module"
  (→ `SectionPlaceholderComponent`) with "zero entries" (→ that type's own
  empty state).
- **The generic-rows option was offered to the owner and declined** (D14). Not
  a discovery.
- **The module carrying its own route was rejected** (D15) — two sources for
  one fact, and a renamed `route` would silently orphan content.
- **A single-value `enum` on a member's `type`** is the one spelling that
  provably breaks (D9). Not "for safety".
- **D12's three options are withdrawn, all of them** — no per-field `sensitive`
  flag, no per-type cache classification, no "module content is never cached".
  What survives is the credential line (REQ-979), and the test must **not**
  filter on names like `password` or `code`.
- **`/info` must render identically from cache.** No degradation, no hidden
  row. And **the REQ-972 drop must not quote the offending content** — value or
  label — a console warning is not exempt (principle 3).
- **Do not add a `{ path: 'info' }` route**, an SSID row, a WPA QR code (a new
  dependency), a per-row icon, a per-row sort key, a row id, or a `title` on the
  module — all closed by `info.md`.
- **`SectionPlaceholderComponent` is not modified.** It survives demoted, and
  its existing spec must pass unchanged.
- **REQ-969 is vacuously satisfied** — no manager mutation exists to audit. Its
  whole footprint is one entry in `AuditCoverageTest`'s exemption list. It will
  look like non-coverage at review; it is not (O-1).
- **REQ-966's orphan sweep has nothing to sweep** — this Part removes and
  renames no schema. The check still runs, because a green build cannot tell
  "nothing found" from "nothing looked" (O-2).
- **Part C still cannot serve a real property** (D16, O-4). What ships is the
  transport, the per-type wire shape, the registry and one rendered module
  against seeded content.

## Next

**`05_implement` (`dev`), starting at T-01 — which writes nothing and waits for
the owner.** After the answer: T-02 (contract + the approved `pom.xml` line
together), T-03 (client regeneration as one reviewable step), then the backend,
the kiosk boundary, the screen, the tests, and T-20…T-22 (measure, docs, sweep).
The owner commits; one commit carries contract + `pom.xml` + backend +
`packages/api-client/` + frontend + docs.
