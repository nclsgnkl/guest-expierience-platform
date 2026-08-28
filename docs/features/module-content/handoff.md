# Handoff — module-content (Part C)

**Last session:** 2026-08-28, `reviewer` — **Phases 1–4 (T-01 … T-10) reviewed:
PASS ✅.** Full report in [`06-review.md`](06-review.md). Nothing was fixed,
nothing was committed, the tree was restored byte-for-byte after every probe.

**Findings: 🔴 0 · 🟠 0 · 🟡 3 · 🟢 7.** The three 🟡:

1. **F-1** — `GuestControl.resolveLocalized`'s javadoc still says *"`stay == null`
   only for standby texts, whose allowed placeholder set has no stay fields"*,
   which §2.5 made false. Comment-only fix, on the one function REQ-964's
   argument rests on.
2. **F-2** — `03-todo.md` T-02 and T-22 still say `pom.xml` carries *"only the one
   approved line"*; there are two approved options now.
3. **F-3 — read this before writing T-12.** The wire carries **`"module": null`**,
   not an absent key (measured; `GuestContent.home` already behaves this way).
   `02-design.md` §4.2's `wire === undefined` guard would reach `wire.type` on
   `null` and throw — through a branch §3.2 forbids a `try`/`catch` — turning a
   one-module drop into a blank screen. Use `== null` / `?? null`, matching the
   shipped mapper's `content.home ?? null`.

**Independently re-measured, all confirming dev:** the union interface and
`@JsonTypeName("info")` read off the generated tree; `"info"` on the wire proved
by Jackson including through `GuestModule asUnion = m;`; three clean
regenerations byte-identical (`90090e21b79278c06e4a789c623371dc`, 91 files); no
`ZzProbe`/`spike` residue and exactly **one** `GuestModule` implementor in
`target/classes`; T-09's red probe reproduced (2 of 3 red, reverted to
`md5 40bd69e30868825aabbc7630926656fe`); suite 438/0F/4E/3S here, all four error
classes green in isolation, and the full-green total reconstructed as **449**
(443 from surefire XML + 6 from `SecondFactorLoginTest`, which reported
`Tests run: 0` when its shared `@TestProfile` sibling failed to boot) — so **441
was real**. Both `pom.xml` options were removed one at a time and each proved
load-bearing; with either missing the tree **no longer compiles**, which is a
third guard nobody claimed.

**Next action: Phase 5 (T-11) may start.** Fix F-1 and F-2 when convenient; carry
F-3 into T-12.

---

**Previous session:** 2026-08-28, `dev` — **T-03 unblocked and Phases 2, 3 and 4
finished.** Branch `refactor/bce-subpackages-and-boundary-security`.
**Nothing committed.**

**Status: T-01…T-10 done ✅. Phase 5 (the kiosk boundary, T-11) is next, after
the planned review round.**

## The blocker is closed

The owner approved the second `pom.xml` line (2026-08-28). It went in as ONE
plugin-level `<configuration>` — `<openapiNormalizer>SIMPLIFY_ONEOF_ANYOF=false
</openapiNormalizer>` — rather than twice, because plugin-level configuration is
inherited by both executions, and the Java side needs it for the interface while
the TypeScript side needs it for the alias. Four comment lines beside it, in the
file's existing style. **Nothing else in `pom.xml` changed:** no version bump, no
plugin, no dependency.

## The generated union, verified by reading it and by serialising it

All five checks the task named are present, verbatim:

```java
// target/generated-sources/openapi/.../de/gxp/api/model/GuestModule.java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = GuestInfoModule.class, name = "info"),
})
public interface GuestModule {
    String getType();
}
```

```java
@JsonTypeName("info")
public class GuestInfoModule  implements GuestModule {
```

```ts
// packages/api-client/model/guestModule.ts
export type GuestModule = GuestInfoModule;
```

**The discriminant on the wire is `"info"`, proved from serialisations and not
from the annotations** — three of them, plus a round-trip:

    MODULE_JSON={"type":"info","rows":[…]}
    ITEM_JSON={"key":"info",…,"module":{"type":"info","rows":[…]}}
    UNION_JSON={"type":"info",…}          // serialised through the interface type
    ROUNDTRIP_CLASS=de.gxp.api.model.GuestInfoModule
    ROUNDTRIP_TYPE=info

and again over real HTTP, in two committed tests
(`DevDataSeederTest.seedsTheInfoModuleAndRendersOneStoredCheckOutTimeTwice`,
`GuestModuleContentTest.anOccupiedRoomGetsEveryRowResolvedPerOfferedLanguage`),
both asserting `module.type == "info"` off the served payload.

**Regeneration is byte-identical.** Three runs; the 91-file `packages/api-client`
tree hashed identically each time, and again after the full test suite.

**One orphan was found and removed — on the Java side, not the TypeScript one.**
`ZzProbeModule.java` (timestamped 10:46 — last session's throwaway probe) had
survived in `target/generated-sources/openapi/.../de/gxp/api/model/` and,
compiled, in `target/classes`: the generator does not delete files for removed schemas,
exactly as warned, and `target/` is gitignored so `git status` could not show it.
Cleared with `./mvnw clean` (174 → 172 source files). `packages/api-client/`
itself lost nothing — the `FILES` diff is three additions and no deletions — and
no `zzProbe` residue exists anywhere. The only `spike` string in the client is
the deliberate `d9-spike.md` reference inside the schema description.

## Recorded, unchanged

- **`maxLength: 240` under `additionalProperties` does not reach Java.**
  `GuestInfoRow` is `Map<String, String>` with no `@Size`. Recorded, not a
  blocker — the kiosk enforces it at T-13.
- **`maxItems: 10` DOES survive**, as `@Size(max=10)` on
  `GuestInfoModule.getRows()`. Worth knowing: the two bounds behave differently.

## What each task did

| Task | Change | Verification |
|---|---|---|
| T-03 | regenerated `packages/api-client/` (3 new models, 2 index entries, 1 optional property) | union read by hand, wire proved by serialisation, 3× byte-identical, orphan sweep |
| T-04 | `V12__info_module_content.sql`; `MigrationTest` head re-pinned `V11` → `V12`, method renamed, two-table existence assertion added | `MigrationTest` 6/6, all 12 migrations applied to a fresh schema |
| T-05 | `InfoModuleRow`, `InfoModuleRowTranslation`, two repository reads, `MenuMapper.toInfoModule` | the **five** architecture guards 10/10 — no sixth guard, no exemption list edited |
| T-06 | `MenuControl.guestModuleContent()`; one word added to `NOT_A_MANAGER_MUTATION` | `AuditCoverageTest` 5/5 |
| T-07 | `GuestControl.attachModules` + `resolveModule` (one-arm pattern switch, defensive `default`) | compiles; `GuestResource` still 2 methods, 2 delegating lines, no `if`/`for`/`try` |
| T-08 | `%dev` seeds info.md's copy table verbatim; `CARDS[0]` becomes `Bis {stay.checkOut\|time} Uhr am Abreisetag` | `DevDataSeederTest` 3/3; `grep -rn "Bis 11:00" backend/app/src/main` empty |
| T-09 | `de.gxp.guest.GuestModuleContractTest` (plain JUnit, no container, 0.5 s) | 3/3; **and red by hand** — renaming the mapping key to `infoRENAMED` failed 2 of 3 with both literals on screen, then reverted byte-identically (`md5 40bd69e30868825aabbc7630926656fe`) |
| T-10 | `de.gxp.guest.GuestModuleContentTest` — occupied/vacant/disabled/order | 4/4 first run |

## The suite, measured rather than claimed

`./mvnw -o test`: **449 run, 0 failures, 2 errors, 2 skipped**, 19:43 min.

Both errors are the box, not the code, and both are **before application code**:
`UntrustedPeerAttributionTest` → `ContainerLaunchException` for `postgres:17.11`;
`OpenApiDisabledTest` → Flyway `Acquisition timeout while waiting for new
connection`. Both classes are custom-`@TestProfile` and spin their own database.
**Re-run in isolation they are 4/4 green.** The 2 skips are the sibling cases of
those two aborted classes.

449 = the previously reported 441 plus this session's 8 new tests (3 contract,
4 content, 1 seeder). That is the only reason to believe 441 was ever the real
number; **it is still not an established baseline** — do not chase container
startup errors on this box.

## Two things a reviewer should look at rather than skim

1. **`DevDataSeederTest` gained assertions** although T-08 listed only
   `DevDataSeeder.java`. T-08's validate was written as a hand-check ("a fresh
   `quarkus:dev` database serves …, and Home's check-out card shows the same
   time"); it is now mechanical instead, and it is the single place where
   REQ-967 is actually proved — the test extracts the time from the module row
   and asserts the action card renders that exact time. No later task touches
   that file.
2. **`MigrationTest` gained one `information_schema` assertion** (the two V12
   tables exist), in the file's established style. Deliberately **not** a
   row-count assertion: the suite shares one database and `GuestModuleContentTest`
   seeds rows, so "the tables are empty" would have been order-dependent — the
   exact hazard that file's own comment names.

## Proposed, NOT written — one nested `CLAUDE.md` entry

`backend/CLAUDE.md`, under **Contract-first**. Exact text:

> - A `oneOf` needs **both** generator settings on the openapi plugin:
>   `useOneOfInterfaces` (member classes implement an interface) and
>   `openapiNormalizer=SIMPLIFY_ONEOF_ANYOF=false` (a ONE-member union is not
>   collapsed first). Without either, the build stays green and the wrong
>   discriminant goes on the wire. The literals are pinned by hand in
>   `de.gxp.guest.GuestModuleContractTest` — an ordinary feature test, not a
>   sixth architecture guard.

**Do not write it without the owner's word on the wording.** No constitution
amendment is proposed and none is needed.

## Next

**Review round**, then **Phase 5 (T-11)** — the kiosk domain model. Phase 5
starts from: the contract and the regenerated client in the tree, the backend
serving `menu.items[].module` for both occupancy states, `V12` applied, and the
`%dev` instance authoring two `info` rows. `frontend/` is **untouched** so far
(baseline 311/311, unverified this session).

---

---

# Earlier — planning (superseded above, kept for its decisions)

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
