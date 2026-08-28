# Analyze: Module Content

**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** **PASS — with one standing owner approval that `T-01` must obtain before any build runs.** Not a coverage gap; see §7.
**Run:** 2026-08-28, first round (not a correction round — no defect trace applies)
**Governing:** `docs/constitution.md` **v1.10.0**

> **What `Status: ✅` means below:** a task exists that covers the requirement.
> **It does not mean the requirement is met** — that is `06_review`'s job. On
> 2026-08-18 three shipped defects violated `REQ-ID`s already ticked in a
> matrix exactly like this one.

---

## 1. Coverage — 19 requirements, 22 tasks, 1 declared exclusion

| REQ-ID | Design section | TODO task(s) | Status |
|---|---|---|---|
| REQ-961 | §1.1, §1.4, §1.5 | T-01, T-02, T-09 | ✅ |
| REQ-962 | §2.4, §3.3 | T-07, T-10, T-13 | ✅ |
| REQ-963 | §1.2, §1.3, §3.2 | T-02, T-10, T-13, T-17 | ✅ |
| REQ-964 | §2.3, §2.4 | T-07, T-10 | ✅ |
| **REQ-965** | — | **none, by decision** | ⛔ **Declared exclusion (D16)** — *not* a gap |
| REQ-966 | §10 | T-03, T-22 | ✅ |
| REQ-967 | §2.7 | T-08 | ✅ |
| REQ-968 | §2.1, §2.2, §2.3 | T-04, T-05, T-06 | ✅ |
| REQ-969 | §2.3 | T-06 | ✅ *(vacuously — see O-1)* |
| REQ-970 | §4.1, §4.2, §4.4 | T-11, T-12, T-14, T-21 | ✅ *(with F-1's amendment)* |
| REQ-971 | §4.2, §8 | T-09, T-12 | ✅ |
| REQ-972 | §3.2, §12.1 | T-13, T-16, T-17 | ✅ |
| REQ-973 | §3.2, §6.2 | T-13, T-14, T-16, T-19 | ✅ |
| REQ-974 | §6.1, §6.2 | T-14, T-19, T-21 | ✅ |
| REQ-975 | §2.2, §2.7 | T-04 | ✅ |
| REQ-976 | §2.7, §12.1 | T-08, T-15, T-16 | ✅ |
| REQ-977 | §3.1, §3.2, §5 | T-11, T-13, T-15, T-21 | ✅ |
| REQ-978 | §12.2, §12.3 | T-15, T-20, T-22 | ✅ *(with F-2's instrument correction)* |
| REQ-979 | §7 | T-18, T-22 | ✅ |
| REQ-980 | §4.3 | T-12 | ✅ |

**19 / 19 in-scope requirements covered. 0 uncovered. 1 declared exclusion**
(REQ-965 — descoped by **D16**; the PRD's Blocking map instructs this gate to
record it here rather than flag it, and it is the **only** such carve-out).

## 2. Orphans — none

Every task carries a `— *REQ-…*` line and every ID it names is in the PRD's
table (961…980). Task → REQ, one by one:

T-01 → 961 · T-02 → 961/963 · T-03 → 966 · T-04 → 968/975 · T-05 → 968 ·
T-06 → 968/969 · T-07 → 964/962 · T-08 → 976/967 · T-09 → 961/971 ·
T-10 → 964/962/963 · T-11 → 970/977 · T-12 → 970/971/980 ·
T-13 → 962/963/972/973/977 · T-14 → 974/970/973 · T-15 → 976/977/978 ·
T-16 → 976/972/973 · T-17 → 963/972 · T-18 → 979 · T-19 → 973/974 ·
T-20 → 978 · T-21 → 970/974/977 · T-22 → 966/978/979.

**0 orphans.** No task names REQ-965 — which is the shape D16 requires.

## 3. Acceptance criteria — each mapped to the task that asserts it

| PRD acceptance criterion | Asserted by |
|---|---|
| REQ-961 the `oneOf` spelling, and the three generated facts the `enum` breaks | T-02 (contract), T-03 (generated), **T-09** (`@JsonSubTypes` / `@JsonTypeName` / `String getType()`) |
| REQ-962 no re-sort; a disabled section's module absent from the payload | T-13 (grep + spec), T-10 (c) |
| REQ-963 bounds present, discriminator carved out, kiosk drops 11 rows / 241 chars | T-02, T-17 |
| REQ-964 row 1 carries no `{`, formatted per language in the property timezone | T-10 (a) |
| REQ-966 byte-identical twice, no orphan under `model/` | T-03, T-22 |
| REQ-967 the card resolves from the placeholder; `Bis 11:00` gone from `src/main` | T-08 |
| REQ-968 `V12`, `de.gxp.menu.entity`/`.control`, five guards, **no sixth** | T-04, T-05, T-22 |
| REQ-969 one exemption entry, no audit row from a guest read | T-06 |
| REQ-970 both compile-time totality guards, **verified by hand** | T-12, T-14 |
| REQ-971 `Object.hasOwn` is the set; the literal in exactly two adjacent places | T-12 |
| REQ-972 one module drops, the envelope survives, **nothing is logged** | T-13, T-16, T-17 |
| REQ-973 unknown type → `section.comingSoon`, sidebar intact | T-16, T-19 |
| REQ-974 placeholder unmodified, spec green without edits, no `{path:'info'}` | T-14, T-19 |
| REQ-975 `V11` → `V12` upgrade changes nothing a guest sees | T-04 |
| REQ-976 `%dev` renders two rows; the fabricated type dispatches (hit/miss/drop) | T-08, T-15, T-16 |
| REQ-977 the import boundary, no strings in the template, hand-validation | T-13, T-15 |
| REQ-978 `/info` no-scroll on the route grid; the banner measured, not computed | T-20 |
| REQ-979 three classification levels + explicit nested reach; no name filter | T-18 |
| REQ-980 `TS2344 … constraint 'never'`, **verified by hand** | T-12 |

**Three criteria are verified by making the build fail on purpose** (REQ-970
twice, REQ-980 once, and REQ-979's compile-time half in T-18). That is
deliberate: a guard nobody has watched fail is a guard nobody knows works, and
Vitest cannot assert a compile error.

## 4. Constitution compliance (v1.10.0)

| Clause | Verdict |
|---|---|
| Approved dependencies — both allow-lists **closed** | ✅ Nothing added. `package.json` untouched; `pom.xml` gains **one generator option and nothing else**, which the spike proved pulls in no artifact. T-22 pins both |
| **No dependency-manifest edits without explicit approval** | ⏸ **The one open item.** `pom.xml` is approval-gated by the constitution *and* by `backend/CLAUDE.md` regardless of it not being a dependency. **T-01 asks and stops.** See §7 |
| Principle 1 — property content is data, never code | ✅ Module content is rows in Postgres. The only literals are in the `%dev` seeder, beside the property translations that already live there |
| Principle 2 — content is reached through a service | ✅ `InfoModuleComponent` takes a resolved input from the host, which reads `PropertyContentService`. It imports no generated model and no data file |
| Principle 3 — guest-sensitive data is never logged | ✅ The mapper logs nothing and its drop path carries no message; §3.2 makes that explicit, and T-17 asserts it with a `console` spy. Backend: resolution reuses `resolveLocalized`, which logs nothing. **The WiFi row is exactly where this would be broken by reflex** |
| Principle 4 — offline, untrusted LAN, room-scoped | ✅ No external request, no new fetch, no lazy chunk. The room still comes exclusively from the device identity — no guest operation gains a parameter |
| Kiosk cache — may hold guest-facing content | ✅ v1.9.0 and D12: the WiFi row is cached and `/info` renders identically from cache, which is the point of the amendment |
| Kiosk cache — **never** anything extending reach beyond the device | ✅ REQ-979, now classified at three levels (§7), with Part B's credential assertions inherited verbatim — **explicitly not name-based** |
| Threshold clause (v1.10.0, new) | ✅ **No subject here.** Part C introduces no threshold. `rows: 10` and `maxLength: 240` are wire bounds, not gates on a state's visibility |
| Nothing scrolls (document scope) | ✅ T-20, both instruments, both viewports, both themes, both languages. No `overflow: hidden` anywhere in the plan |
| Append-only audit (v1.8.1) | ✅ **No manager mutation is introduced**, so none is audited. `AuditCoverageTest` gains exactly one exemption entry — the classification step the test's own contract requires |
| Contract-first | ✅ T-02 edits `api/openapi.yaml`; T-03 regenerates the client as **one reviewable step** and reads the diff before anything else moves |
| BCE / `backend/CLAUDE.md` | ✅ No new endpoint, so no Boundary change at all. Entities in `.entity`, repository in `.control`, `@Transactional` on the Control, generated DTOs across the feature boundary |
| The **five** architecture guards | ✅ Unchanged, **no sixth added**, and — the reason §2.1 put the tables in `de.gxp.menu` — **no exemption list edited**. §8's pinned contract test is an ordinary feature test in `de.gxp.guest` |
| Styling — plain CSS, `rem`, semantic tokens only | ✅ `info.md` introduces **zero new tokens and zero new icons**; T-15 forbids raw hex and `px` |
| No user-visible string in a component | ✅ The screen is entirely content; it needs no accessible-name string (the `<dl>` semantics), so **no translation key is added** |
| Design-tool requests are metered | ✅ None. `/info` is in-repo design and already written |
| Decisions Requiring Your Approval | ⏸ **One: the `pom.xml` line (T-01).** No dependency, no datastore, no new architectural pattern, **no nested `CLAUDE.md` entry, no constitution amendment** — §11 records the two places that came closest and why the design avoided both |

## 5. Design fidelity — contradictions actively looked for

Eleven places where this plan could have contradicted the PRD or a settled
decision, each checked:

1. **REQ-970 vs. §4.4 — the component is not in the registry object.** A real
   deviation, not a wording difference. Recorded as **F-1** and repaired in its
   owning phase before this gate issued.
2. **REQ-963 vs. the discriminator.** Not a deviation: the PRD *asked*
   `02_design` to choose between a carve-out and a `maxLength` ("Open items" 1).
   §1.3 chose the carve-out on semantic grounds — a length bound admits `"nfo"`
   — and the criterion asserts the absence of both `enum` and `maxLength`.
3. **REQ-972 vs. REQ-963 — granularity.** A bounds violation drops the whole
   module, not the offending row. Consistent with REQ-972's own wording
   ("a structurally invalid **single** module") and with the mapper's stated
   reject-never-truncate posture. The alternative is named and rejected in §3.2.
4. **REQ-972/973 vs. D19.** "Dropped", "unknown" and "unauthored" all render
   `section.comingSoon`, which REQ-972's own note requires. D19's distinction is
   preserved where it matters: a module delivering **zero rows** would render
   that type's empty state — and `info` provably cannot be empty, so none is
   invented.
5. **D18 vs. the mapper.** No placeholder-shaped logic anywhere; T-13 states the
   prohibition and names the rejected option (a). **No** Part C requirement
   asserts the server-side refusal, which is the successor feature's.
6. **A2/D11 vs. §2.5.** Modules are property-scoped, delivered in both occupancy
   states, with **no room scope** in the schema. The vacant-payload consequence
   (raw braces nobody sees) is written down rather than engineered around, and
   it is exactly the state D18's correction describes as unreachable on screen.
7. **REQ-961 "one member per module type" vs. one type.** Consistent: D14 makes
   a second type a second designed screen and D17 puts the second type in a
   test. The single-member `oneOf` is the untested part, and §10.1 makes it
   verify-at-implementation with "stop and report" rather than a workaround.
8. **REQ-976 vs. open item 2.** §8 and T-16 both state that the fabricated-type
   test **does not** cover discriminant-literal drift. The PRD asked for that
   sentence explicitly; it exists in three places (design §8, TODO T-16, the
   REQ-980 criterion) because it is exactly the assumption a later reader makes.
9. **REQ-979 vs. D15's known cost.** The compile-time guard that D15 quietly
   defeated is repaired by two nested tables, and the runtime half reaches in
   explicitly. Both, not either — and the residual (no recursion) is recorded.
10. **REQ-966 vs. the orphan sweep.** No schema is removed or renamed here, so
    the sweep has nothing to delete. Recorded as **O-2** rather than ticked
    silently, because "the check found nothing" and "the check was not run" look
    identical in a green build.
11. **REQ-975 vs. Part A's D5.** This Part introduces no catalogue row, so D5's
    "arrives property-disabled" rule has no subject. Its *spirit* is honoured by
    `V12` seeding no content: an upgraded property's guest screens change in no
    way until somebody authors something, which under D16 nobody can.

## 6. Markers

`grep -n "NEEDS CLARIFICATION"` across `01-prd.md`, `02-design.md`,
`03-todo.md`: **none** (the design's §15 says the words "no
`[NEEDS CLARIFICATION]` marker is left", which is the only match and is a
statement, not a marker).

**Two items are explicitly *verify-at-implementation*** (§10.1) — a
single-member `oneOf` through both generators, and `maxLength` under
`additionalProperties` on the Java side. Neither is an unresolved decision,
neither changes a requirement, and each carries its instruction: **stop and
report** for the first, **record and continue** for the second.

## 7. The one approval — why this is `PASS` and not `GAPS`

**`backend/app/pom.xml` gains `<useOneOfInterfaces>true</useOneOfInterfaces>`,
and the owner has not approved it.** It is approval-gated twice over
(`docs/constitution.md` → Constraints; `backend/CLAUDE.md` → "Dependencies,
database, build"), so the checklist item *"no approval-gated decision still
unconfirmed"* is **⏸ open, deliberately** rather than ticked.

It is not a coverage gap and does not send this plan back a phase:

- **It is planned, not assumed.** `T-01` is a stop-and-ask task that writes
  nothing and comes **before** the contract edit. Nothing downstream may
  proceed without an answer.
- **The plan does not depend on the answer being yes.** T-01 states what
  happens if the owner declines: **stop and report.** Variant C is strictly
  worse (no TypeScript union, a concrete instantiable base) and a free-form bag
  cannot be boundary-validated at all — neither is a fallback an agent may take.
- **The PRD has said so since 2026-08-28:** it blocks the commit, not the plan.
  What this gate adds is `02-design.md` §1.5's sharpening — it blocks the
  **build**, because with the flag off `./mvnw verify` goes **green** and ships
  `{"type":"GuestModule"}` on the wire. That is why the ask moved to the front.

**No other gate is open.** No dependency, no datastore or infrastructure choice,
no new architectural pattern, no nested `CLAUDE.md` entry, no constitution
amendment, no metered design-tool request.

## 8. Findings — both fixed in their owning phase before this result issued

This gate did not start clean. Per the rule that it detects and does not
repair, both were fixed in `01_prd` first:

| # | Finding | Owning phase | Resolution |
|---|---|---|---|
| **F-1** | **REQ-970 asks the registry to carry "the component that renders it", and the design does not put it there.** Honouring the letter would make `core/guest/module-registry.ts` import a `features/` component — **the app's first `core → features` import**, checked against the tree. Inverting the app's layering to satisfy a requirement's wording is a worse trade than splitting one table | `01_prd` | REQ-970 carries an inline amendment recording the split **and the property that keeps it honest**: the host's renderer map is `satisfies Record<ModuleKind, Type<unknown>>`, total over the same key set the registry is keyed on, so a type without a renderer and a renderer without a type both fail the build. The registry stays the single **source** of the type set. The criterion asserts both guards, each verified by hand |
| **F-2** | **REQ-978 named an instrument that cannot see half of what its own note demands.** `tools/measure.mjs` measures a **route** grid — `/info` is already in it — but the escalated staleness banner is a kiosk **state** it cannot reach, and REQ-978's note is precisely about the banner clearing `/info`'s panel by 1.2 px / overlapping by 0.2 px | `01_prd` | REQ-978 amended to name **both** instruments: `measure.mjs` for the route half (no change to the file needed) and `measure-states.mjs` for a `/info` variant of Part B's BANNER cell. The assertion is unchanged. **Part B's REQ-952 carried the identical defect and was corrected the same way at its own gate** — the second occurrence of one mistake, which is worth saying out loud |

Four observations, none of them gaps, recorded so `06_review` does not have to
rediscover them:

- **O-1. REQ-969 is satisfied vacuously, and will look like non-coverage.**
  Part C introduces **no manager mutation** (D16), so there is no audit call to
  write. Its entire footprint is one entry in `AuditCoverageTest`'s
  `NOT_A_MANAGER_MUTATION` for the new read. That is the correct outcome of a
  requirement whose subject this Part descoped — not a missed task.
- **O-2. The orphan sweep has nothing to sweep.** No schema is removed or
  renamed, so REQ-966's failure mode (the generator does not delete files it
  previously wrote) cannot fire here. T-03 and T-22 still check, because a
  green build cannot distinguish "nothing found" from "nothing looked".
- **O-3. Four kiosk touchpoints per module type, against D10's "two places".**
  A `ModuleWireMap` line, a registry entry, a `resolveModule` arm and a
  `RENDERERS` line. Three are compiler-enforced and the fourth is REQ-980's
  `never` check, so none can be forgotten silently — which is the property D10's
  approval was actually buying. Worth naming because the owner approved a
  number, and the number changed.
- **O-4. Part C still cannot serve a real property.** D16, stated in the PRD in
  as many words. What ships is the transport, the per-type wire shape, the
  registry and one rendered module against seeded content. Nothing in this gate
  should be read as saying otherwise.

## 9. Start condition

Parts A and B are **committed** (`8b3856e`), so nothing blocks T-01 but the
owner's answer. `docs/features/module-content/d9-spike.md` is committed too;
its findings are load-bearing for T-02 and T-03 and should be read before
either.

---

## Result

**PASS** — 19/19 in-scope requirements covered by 22 tasks in 8 phases, 1
declared exclusion (REQ-965, D16), 0 orphans, 0 unresolved markers, no
constitution conflict, and the two PRD defects this gate found repaired in
`01_prd` before it issued.

**One standing owner approval is open and is the first task, not a
precondition of this result:** the single `pom.xml` generator-option line.

**Next:** `05_implement` (`dev`), starting at **T-01 — which ends the turn and
waits for the owner.**
