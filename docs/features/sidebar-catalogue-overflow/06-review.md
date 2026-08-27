# Review: Sidebar & Catalogue Overflow — Part A, Phases 1–3 (T-01 … T-09)

**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** **NEEDS CHANGES ❌** — no 🔴; 3 × 🟠, 3 × 🟡, 4 × 🟢. Nothing found is a
defect in shipped behaviour; all three 🟠 are *evidence* problems — a
requirement whose acceptance criterion nothing enforces, and two verification
claims that do not hold up.

Run 2026-08-27 by `reviewer` against `docs/constitution.md` **v1.9.0**,
`CLAUDE.md`, `frontend/CLAUDE.md`, `backend/CLAUDE.md`, `docs/system-context.md`
and the working tree at `refactor/bce-subpackages-and-boundary-security`
(nothing committed, nothing staged, no branch switched).

> **Scope.** T-01 … T-09 only. T-10 onward is unimplemented and out of scope.
> `docs/features/guest-payload-consolidation/**`, `docs/features/module-content/**`
> and `docs/features/index.md` are Part B/C planning documents being written by a
> concurrent `planner` — deliberately not read and not written.
>
> **Part A's own docs cite constitution v1.8.3.** That is a correct historical
> record of a gate that predates the v1.9.0 amendment and is not flagged. I
> re-checked v1.9.0's only delta — the kiosk-cache clause and the
> staleness-escalation sentence — against every file in this cut: **nothing in
> Phases 1–3 touches either.** The cache's contents are unchanged (catalogue
> labels, icons and routes), and no credential, token or key material is added
> to anything cacheable.
>
> **No visual check was performed, and none was owed.** `code-review` step 0
> requires a rendered screenshot for any change touching a
> `docs/design/**`-backed component. This cut contains **no UI**: the eight
> catalogue rows arrive property-disabled in `%test`/`%prod` (D5), no sidebar,
> popover, route or template changed, and `registered-icons.ts` adds map entries
> that nothing yet renders. The screenshot obligation attaches to T-15/T-16 and
> is discharged by T-19. It is recorded here so the next reviewer does not read
> this PASS-on-a-UI-feature as a precedent.

---

## Measured numbers — my own runs, not dev's

Host: aarch64 Raspberry Pi, 4 cores, **3796 MB RAM (2.1 GB already in use, 1.6 GB
already in swap)**, load average 4–7, **six unrelated containers running**
(Immich ×3, Valkey, Home Assistant, Matter). `JAVA_HOME=/tmp/gxp-jdk/jdk-21.0.12+8`;
Docker was reachable directly, `sg docker` was **not** needed this session.

| Run | Command | Result |
|---|---|---|
| Backend #1 | `./mvnw verify` | **436 run · 0 failures · 2 errors · 2 skipped** |
| Backend #2 | `./mvnw verify -Dsurefire.runOrder=reversealphabetical` | **436 run · 0 failures · 2 errors · 2 skipped** |
| Backend, isolation | `./mvnw test -Dtest='DevDataSeederTest,UntrustedPeerAttributionTest'` | **4 run · 0 failures · 0 errors · BUILD SUCCESS** |
| Backend, adverse order A | 9-class menu subset, `ClassOrderer$Random` seed `424242` | **48 run · 0 failures · BUILD SUCCESS** |
| Backend, adverse order B | same subset, seed `7` | **48 run · 0 failures · BUILD SUCCESS** |
| Frontend | `ng test --watch=false` | **255 passed / 255, 32 files** |
| Frontend | `ng build` | clean — `main` 452.99 kB, `styles` 9.10 kB |

**436 and 255/255/32 match dev's report exactly.** In **zero** of the runs did a
single assertion fail.

### The `./mvnw verify` errors — my independent judgement

**Environmental. Not a blocker.** Evidence, in order of weight:

1. **Both errors are container-startup timeouts, thrown before any test body
   ran.** The stack is unambiguous — `org.rnorth.ducttape.TimeoutException` →
   `WaitAllStrategy.waitUntilReady` → `PostgreSQLContainer.waitUntilContainerStarted`
   → `ContainerLaunchException: Container startup failed for image postgres:17.11`,
   surfacing at `QuarkusTestExtension.interceptTestClassConstructor` with
   `Time elapsed: 0.006 s`. The failed container's own log shows `initdb`
   completing normally (`performing post-bootstrap initialization ... ok`) and
   simply not finishing inside the wait window.
2. **It moves.** Across four observations the flake has now landed on **four
   different `@TestProfile` classes** — `TrustedProxyLoginTest` (dev's
   pre-change baseline), `UntrustedPeerAttributionTest` (dev's run),
   `DevDataSeederTest` + `UntrustedPeerAttributionTest` (my run #1),
   `UntrustedPeerAttributionTest` + `OpenApiDisabledTest` (my run #2). A code
   defect does not wander.
3. **Only `@TestProfile` classes are affected** — the six classes that force a
   Quarkus re-augmentation and a *fresh* Postgres container. Every one of the 70
   default-profile classes passed in both runs.
4. **They pass in isolation.** The same two classes that errored in run #1 pass
   **2/2 each, BUILD SUCCESS** when run alone, and the same container that timed
   out after 94 s under load starts in 23–33 s when the box is quiet.
5. **The box is the cause.** Quarkus booted in **155.8 s**; an *observed
   successful* Dev Services container start took **23.8 s**; free memory during
   the run fell to 71 MB with 1.6 GB swapped.
6. **No causal path exists.** `UntrustedPeerAttributionTest` and
   `OpenApiDisabledTest` are untouched by this change and assert `Forwarded`
   header handling and OpenAPI exposure — neither reads `menu_item_definition`
   or `menu_item_config`.

Consequence worth recording: a class that errors at boot **reports 2 tests
(1 error + 1 skipped) instead of its methods**, which is exactly why dev's
420 → 436 accounting needed the "the baseline under-counted" clause. That is
correct.

### Test isolation — priority 1, and the finding hidden inside it

**The isolation fix is correct and is now proven.** But **T-03's prescribed
proof is a no-op on this project** — see 🟠 3. I extracted the full class
execution order from both `verify` runs (76 classes each) and diffed them:

```
diff order-fwd.txt order-rev.txt   →   IDENTICAL
```

`-Dsurefire.runOrder=reversealphabetical` changed nothing. The order is decided
by Quarkus' own profile-aware class orderer (default-profile classes first,
`@TestProfile` classes grouped at the end, plain JUnit classes last), which
overrides Surefire's. dev's *"Reverse class order: 0 failures"* is therefore
literally true and evidentially worthless — it re-ran the same order.

I proved the property another way, with an orderer that does work
(`-Djunit.jupiter.testclass.order.default='org.junit.jupiter.api.ClassOrderer$Random'`):

**Seed `7` produced the decisive order** — every property-scope writer ahead of
the victim:

```
AuditCoverageTest (W) → ActionCardTest (W) → CatalogueExpansionTest
→ MenuCatalogueAndConfigTest (W) → MenuDisableGuardTest (W) → MigrationTest
→ GuestSessionTest (victim) → GuestHomeScreenTest (W) → FirstRunSeedTest
                                         48 run · 0 failures · BUILD SUCCESS
```

`GuestSessionTest`'s `menu.items hasSize(5)` / `hasSize(4)` pass **after all four
whole-set writers have run**. That is the property T-03 exists to establish, and
it holds. Seed `424242` gave a second, different adverse order — also green.

Note the canonical order already exercises the hazard: `AuditCoverageTest`
(which does `PUT /api/manager/menu-config` at property scope) runs at position
**3**, fifteen classes ahead of `GuestSessionTest` at position **18**.

### D5 verified by resolution, not by reading the migration

`CatalogueExpansionTest` passed **5/5 in every one of the five backend runs**,
including both adverse orders. It asserts against the live `GET /api/guest/session`
that a room with no config rows sees **exactly the five original keys**; that
enabling one derived fixture key at property scope makes it the sixth; and that a
room-scope enable of a property-disabled key stays absent (the ceiling). D5 holds
in `%test` by resolution. `DevDataSeederTest` (dev profile, own database) asserts
`menu.items hasSize(13)` — so `%dev` resolves **enabled**, `%test`/`%prod`
**disabled**, exactly as REQ-906/REQ-908 require.

### Working tree is whole after dev's `git stash` / `git stash pop`

- `git stash list` — **empty**.
- The stash commit survives as dangling `2d96d34` (*"WIP on refactor/…"*). It has
  **two parents**, not three, so no untracked file was ever stashed — the three
  new files (`V10__catalogue_expansion.sql`, `MenuFixtures.java`,
  `CatalogueExpansionTest.java`) were never at risk.
- I compared all 19 tracked files the stash carried against the working tree
  today: **18 byte-identical**. The only difference is `docs/features/index.md`
  (2 lines), which the concurrent `planner` is editing — out of scope.
- No lost hunk. No orphaned stash entry.

---

## Findings

| # | Sev | Category | File | Finding | Fix |
|---|---|---|---|---|---|
| 1 | 🟠 | Requirement | `frontend/src/app/core/guest/guest-content.mapper.ts:53-60` · `02-design.md` §2/§5 | **REQ-901's acceptance criterion is unmet: `maxItems: 50` is enforced nowhere.** The criterion reads *"Given a payload above the declared `maxItems`, then the envelope is rejected — and the rejection message names a bound that actually exists"*. Server-side, design §2 correctly notes response DTOs are not runtime-validated (`@Size(max=50)` is generated but inert). Client-side, T-08 deleted the only check. The design contradicts itself: §2 says *"the enforcement that matters is the kiosk's, at the boundary (§5)"*, and §5 then removes it. §5's stated reason — *"the wire is trusted for count the same way `actionCards` is"* — is **factually wrong**: `MAX_CARDS = 4` still rejects at line 185. dev followed T-08 exactly; the plan is what is inconsistent. | Owner's call, two options. **(a)** Restore the guard at the real bound — `const MAX_MENU_ITEMS = 50;` + the existing three-line check, message `` `menu.items exceeds the contract's 0–${MAX_MENU_ITEMS}` `` (now a true statement), + one spec case. ~5 lines, makes the requirement true as written. **(b)** Strike REQ-901's rejection criterion, and correct design §2's *"enforcement that matters"* sentence and §5's false `actionCards` comparison. Do not leave both as they are. |
| 2 | 🟠 | Test quality | `backend/app/src/test/java/de/gxp/infrastructure/devseed/DevDataSeederTest.java:101,111-113` | **The new assertion cannot fail for the reason its message states.** It snapshots `count(… enabled = true)` *after* the first seed already set all eight to `true`, re-seeds, and asserts the count is unchanged. `enableCatalogueFixtures()` is an idempotent `UPDATE … SET enabled = true`, so the count is identical whether or not it ran. The message — *"the catalogue-fixture enablement sits inside the devDeviceExists() early return"* — is therefore unsupported by the assertion. This matters because deviation 4 offered this test **in place of** T-05's prescribed manual `quarkus:dev` run. (The behaviour itself is correct: `seedExampleContent()` is unreachable once `devDeviceExists()` returns `true` — verified by reading `seedIfMissing()`.) | Make it a real negative. Before re-seeding, turn one property-scope row back **off**, then assert it stayed off. Key-free so REQ-903 holds: `update menu_item_config set enabled = false where id = (select min(id) from menu_item_config where room_id is null and enabled = true)`, then `assertEquals` on `count(… enabled = false)` with the message *"re-seeding must not re-enable what a developer turned off"*. |
| 3 | 🟠 | Verification | `03-todo.md` T-03 & T-20 · `handoff.md` | **T-03's prescribed isolation proof is inert in this project.** `-Dsurefire.runOrder=reversealphabetical` produced a **byte-identical 76-class order** (measured: `diff` of both runs' `Running de.gxp.*` sequences is empty). Quarkus' profile-aware class orderer overrides Surefire's. So the handoff's *"Reverse class order: 0 failures — this is T-03's real point"* is not evidence for anything. Left uncorrected, every future feature inherits a validation step that always passes. The underlying fix **is** correct — I proved it with `ClassOrderer$Random` (above). | Replace T-03's and T-20's validation line. Proposed exact text for T-03: *"and once with a genuinely different class order — `-Djunit.jupiter.testclass.order.default='org.junit.jupiter.api.ClassOrderer$Random'` plus a recorded `-Djunit.jupiter.execution.order.random.seed=…`. **`-Dsurefire.runOrder` does not work here**: Quarkus' profile-aware orderer overrides Surefire's, and the class order comes out byte-identical (measured 2026-08-27). Record the order the seed produced, and check a property-scope writer really preceded `GuestSessionTest`."* Correct the handoff's claim to match. |
| 4 | 🟡 | Docs accuracy | `V10__catalogue_expansion.sql:18-21` | **The disposability claim is overstated.** The header says removal is *"THIS MIGRATION REVERSED PLUS ONE EDIT TO registered-icons.ts — AND NOTHING ELSE"*. In fact it also requires editing four catalogue-count assertions (`CatalogueExpansionTest.assertEquals(13, …)`, `MenuCatalogueAndConfigTest` `hasSize(13)`, `FirstRunSeedTest` `hasSize(13)`, `DevDataSeederTest` `menu.items hasSize(13)`) and two key lists (`DevDataSeeder.CATALOGUE_FIXTURE_KEYS`, `MenuFixtures.CATALOGUE_FIXTURE_KEYS`). REQ-903 permits relying on *count ≥ 12*; four sites assert `== 13`, which is stricter. T-21's grep is **key**-based and will not catch any of it. The keys themselves are genuinely clean (see below) — it is only the sentence that is false. | Reword the header to the property that actually holds and is actually greppable: *"no fixture KEY or LABEL appears outside this file, `DevDataSeeder` and `MenuFixtures`. Four tests additionally pin the catalogue COUNT at 13 (`CatalogueExpansionTest`, `MenuCatalogueAndConfigTest`, `FirstRunSeedTest`, `DevDataSeederTest`); removing or adding rows means updating those four numbers and nothing else."* Same correction belongs in T-21's grep step. |
| 5 | 🟡 | Docs accuracy | `frontend/src/app/core/content/property-content.service.ts:40-43` | The class comment still says *"`settingsConfig()`/`property()` **throw when nothing is loaded**"*. `locale()` now calls `requireConfig(this.raw())` too and throws identically, but is not named — a future caller reading the surface doc will think it is safe outside the guards. | One word: *"`settingsConfig()`/`property()`/`locale()` throw when nothing is loaded"*. |
| 6 | 🟡 | Intermediate state | `frontend/src/app/app.routes.ts` (unchanged) + `V10` + `DevDataSeeder` | **This cut is not runnable end-to-end in `%dev`.** Phase 2 landed the data and the `%dev` enablement; Phase 4's `:sectionKey` + `**` routes are T-11/T-12. So a `%dev` kiosk today receives 13 menu items against a router that knows four section paths — the remaining eight produce a router error, which is precisely the P1 defect this feature exists to fix. **`%test` and `%prod` are unaffected** (D5 keeps the fixtures disabled), so no property and no guest is exposed. Not a code defect, and an expected consequence of stopping at the end of Phase 3 — recorded so it is a known state rather than a surprise the first time someone runs `quarkus:dev`. | Nothing to change. Either finish through Phase 4 before committing, or commit knowing `%dev` is mid-refactor. Worth one line in `handoff.md`. |
| 7 | 🟢 | Plan wording | `03-todo.md` T-01, T-20 · REQ-902 | Deviation 5 ruling: **the TODO's wording is at fault, the outcome is right.** `packages/api-client/` shows exactly one changed file — `.openapi-generator/openapi.yaml-generate-ts-client.sha256`, the generator's *input hash*, which necessarily changes when the spec does. I verified no generated code moved: `model/guestMenu.ts` is untouched and `typescript-angular` emits nothing for `maxItems`. But *"`./mvnw verify` twice → `git status` clean under `packages/api-client/`"* is unachievable as literally written until that hash file is committed. | Tighten to: *"the second `verify` leaves the tree unchanged relative to the first; the only file the contract change touches under `packages/api-client/` is `.openapi-generator/*.sha256`, and no generated source moves."* |
| 8 | 🟢 | Commit hygiene | working tree | Part A's uncommitted work sits beside unrelated work: `docs/design/screens-inhouse/connection.md` (+104 lines — the escalated staleness banner, **Part B's REQ-952**), the `modular-menu-catalogue` retirement (`D 01-prd.md`, `?? README.md`), `?? docs/handoff.md`, and `docs/features/index.md` (a concurrent `planner` is writing it). T-21 says *"one commit carries contract + backend + `packages/api-client/` + frontend + docs"* — a `git commit -a` sweeps all of that into Part A's commit. | Owner's call at commit time. If Part A is to be one clean commit, stage explicitly rather than `-a`. |
| 9 | 🟢 | Style | 3 changed frontend files | `property-content.service.ts`, `guest-content.mapper.ts` and `guest-content.mapper.spec.ts` are not Prettier-clean — but **all three were already dirty before this change**, and every delta Prettier reports is in a pre-existing line. **No new formatting drift was introduced.** | Nothing. Recorded so it is not mistaken for a regression later. |
| 10 | 🟢 | Test strictness | `CatalogueExpansionTest.theCatalogueGrewAndTheFiveRealSectionsKeptTheirPositions` | `assertEquals(13, keys.size())` is stricter than REQ-903's *"count ≥ 12"*, and the test's own comment notices the tension. Plan-mandated (T-03 says `hasSize(5)` → `hasSize(13)`), and a hard count is the better test — it would catch a duplicated insert that `>= 12` would not. | None. Folded into finding 4's reword. |

**Counts: 🔴 0 · 🟠 3 · 🟡 3 · 🟢 4**

---

## Rulings on the five declared deviations

### 1. *"Exactly two existing tests change" was wrong; it is four* — **ACCEPTED, and dev's claim verified**

Both extras are real invalidations the plan missed, and both are the right
response:

- **`MigrationTest`** pins the Flyway head as a **literal**. `"9"` → `"10"` and
  the rename to `flywayHeadIsV10CatalogueExpansion` is exactly the conscious step
  a literal pin exists to force. Nothing else in the class changed.
- **`MenuDisableGuardTest`** twice asserted `GET /api/manager/menu-config`
  `hasSize(0)` to mean *"the rejected PUT changed nothing"*. Since D5 the property
  scope legitimately holds eight rows, so that assertion no longer says what it
  meant. Narrowing to `findAll { it.menuItemKey == '<key under test>' }` →
  `hasSize(0)` states the **same** atomicity, names the key actually under test,
  and depends on no fixture key. Strictly better than what it replaced.

**dev's claim that no guest-facing assertion changed is TRUE — verified line by
line.** All four changed assertions hit `/api/manager/menu/catalogue`,
`/api/manager/menu-config`, or the `flyway_schema_history` table. `GuestSessionTest`,
`GuestPreferenceTest`, `RoomScopingTest`, `GuestEtagTest` and `GuestAssetTest` are
byte-identical to `HEAD`; `GuestHomeScreenTest`'s only change is the `@AfterEach`
line. That is itself the evidence D5 was the low-risk choice.

I also confirmed the plan's "two" was not an undercount of **five**:
`MenuCatalogueAndConfigTest.propertyScopeOverrideRoundtrip` still asserts
`hasSize(1)` and correctly needed no change, because it reads the scope *after*
its own whole-set replace.

### 2. `FirstRunSeedTest` — **dev is RIGHT; the plan is wrong. `03-todo.md` needs correcting.**

This is the item dev escalated, and the escalation was correct.

T-03 says *"its exact five-key list → the 13-row catalogue"*, and design §9 says
*"Both become the 13-row catalogue."* Read literally, that means enumerating
thirteen keys — which would put **all eight fixture keys into a test file**.

That is forbidden by the requirement the task is derived from. REQ-903 as amended
2026-08-27: *"no code path, **no test** and no design decision may come to depend
on their existence"* and *"Any row may be renamed, re-iconed or dropped without
touching a requirement, a design decision or **a test**"*. T-21 then greps for
exactly that. Following T-03 literally would have created the violation T-21 is
built to detect, in the same feature.

A requirement outranks a task's phrasing — `03-todo.md`'s own preamble says every
design decision *"is referenced by section, never re-decided here"*, so the TODO
is derivative and loses. And design §9's very next sentence gives the intent away:
*"It deliberately keeps asserting **definitions only**"* — the point was the
*scope* of the assertion, not enumerating keys.

**`hasSize(13)` + `key[0..4]` = the five real sections is the correct
implementation.** It handles the invalidation (both the old size and the old
5-element list equality had to change), preserves everything the test actually
protected (the five real sections are seeded, in order, at 0–4), and creates no
forbidden dependency.

**`03-todo.md` needs correcting** so a re-run of T-03 is not misdirected.
Proposed replacement for the T-03 bullet (not written — `dev` or the owner
applies it):

> `FirstRunSeedTest` step 3 (`hasSize(5)` → `hasSize(13)`; its exact five-key
> list is **narrowed to `key[0..4]`**, never widened to thirteen — naming a
> fixture key in a test file is precisely what REQ-903 forbids and what T-21
> greps for).

And design §9's `FirstRunSeedTest` row, *"Both become the 13-row catalogue."* →

> The size becomes 13; the key list is narrowed to `key[0..4]` and keeps naming
> only the five real sections — REQ-903 forbids a test depending on a fixture key.

### 3. T-03 run as two sub-steps — **ACCEPTED, no comment**

Same seven files, same result. A file-cap split is not a deviation worth a line.

### 4. T-05 validated by test rather than `quarkus:dev` — **ACCEPTED IN PART**

The **positive** half is now *better* pinned than the plan asked for:
`DevDataSeederTest` boots a real dev-profile app against its own database and
asserts `menu.items hasSize(13)` — REQ-906 is a test, not a manual observation.
Good trade.

The **"inside the early return"** half is **not** pinned — see 🟠 2. The property
holds (verified by reading `seedIfMissing()`), but the assertion offered as proof
cannot detect its own failure. Fix the test rather than reverting to a manual run.

### 5. T-01's "zero diff" is one file off — **the TODO's wording is at fault, not the outcome**

See finding 7. The generator's input-hash file is bookkeeping that *must* change
when the input does; calling that a violation of "zero diff" is a defect in the
acceptance wording. The property that actually matters — no generated source
moves, and a second run is byte-identical — holds, and I verified it
independently.

---

## Requirement coverage — the nine tasks in scope

Verified by execution or by direct inspection, not by reading the handoff.

| REQ | Verdict | How I checked |
|---|---|---|
| REQ-901 | **Partial** | `MAX_MENU_ITEMS`, its check and the *"exceeds the contract's 0–5"* message are gone; `property.model.ts`'s two `0–5` comments corrected; `grep -rn "0–5" frontend/src` and `grep -rn "0-5" frontend/src` both **empty**; no remaining claim of a five-item menu bound anywhere in `frontend/src`. `maxItems: 50` is in the contract. **But nothing enforces it — 🟠 1.** |
| REQ-902 | ✅ | `packages/api-client/` diff = **exactly one file**, the generator input hash. `model/guestMenu.ts` untouched. Two full `verify` runs; no generated source moved. |
| REQ-903 | ✅ (keys) / see 🟡 4 (counts) | Grepped **all eight keys** word-boundary across the whole tracked *and* untracked tree, and all 16 labels across `backend/app/src`, `frontend/src`, `frontend/tools`, `packages`, `api`. Code hits are **only** `V10__catalogue_expansion.sql`, `DevDataSeeder.java`, `MenuFixtures.java` — the three sanctioned sites. The only other hits are prose in `docs/features/index.md` (planning narrative, no dependency) and the `WLAN`/`Wi-Fi`/`Parken`/`Aktivitäten` substrings inside **pre-existing** action-card copy in `DevDataSeeder`, which predate this change. |
| REQ-904 | ✅ | Cross-checked programmatically: every `default_icon` in *every* migration against the `REGISTERED_ICONS` map body — **zero missing**. Then loaded `@ng-icons/phosphor-icons/regular` (35.0.1) and resolved all eight new names plus `phosphorFileText`: all defined, all non-empty, **all ten SVGs distinct**, none equal to `CONTENT_FALLBACK_ICON` (`phosphorCircle`). No silent fallback is possible. |
| REQ-905 | ✅ | 8 definitions × `de` + `en` = 16 translation rows, matching the PRD table exactly; column is `NOT NULL CHECK (language IN ('de','en'))`. |
| REQ-906 | ✅ (behaviour) / see 🟠 2 (evidence) | `DevDataSeederTest` asserts `menu.items hasSize(13)` under a real `dev` config profile against its own `gxp_devseed` database. `enableCatalogueFixtures()` sits in `seedExampleContent()`, reachable only past `seedIfMissing()`'s `devDeviceExists()` early return — verified by reading the method. |
| REQ-907 | ✅ | Computed the real orderings with `Intl.Collator`. **de**: alphabetical ≠ `sortIndex` ✓, and the collator's order differs from code-point order ✓ — the PRD's own case lands exactly (`Ärztliche Versorgung` → `Services` → `Umgebung`). **en**: alphabetical ≠ `sortIndex` ✓. Umlaut-initial label present (`Ärztliche Versorgung`). All three permitted properties hold. |
| REQ-908 | ✅ | `CatalogueExpansionTest` 5/5 green in **five** separate runs including two adverse orders, asserting against the live guest session. |
| REQ-909 | ✅ | Resolution untouched — the only `src/main` change in the whole cut is `DevDataSeeder`. `CatalogueExpansionTest.aRoomScopeEnableOfAPropertyDisabledFixtureStaysAbsent` re-proves the ceiling for a new key. |
| REQ-910 | ✅ | `V10` is the next free slot; no DDL; column names and the `uq_menu_item_config_scope UNIQUE NULLS NOT DISTINCT` arbiter check out against `V2`; migrates a fresh Dev Services Postgres in every run. Header covers disposability, the removal path, why group 3 exists, and that `introduced_release` stays inert. |
| REQ-911 | ✅ | The five `de.gxp.architecture` guards are **unedited** (`git status` clean for that directory) and green. `AuditCoverageTest`'s assertions and its `NOT_A_MANAGER_MUTATION` list are untouched — the only change is an import and one line in its existing `@AfterEach`. No `@Transactional public` Control method was added: the sole `src/main` change is one **private** method on `DevDataSeeder`. |
| REQ-925 / 926 | ✅ | `PropertyContentService.locale` is one `computed`; three spec cases cover resolution from the offered options, fallback to the bare code, and following a language switch while asserting the held config object is **identity-equal** across the switch — i.e. no fetch. |
| REQ-933 | ✅ | No component imports `@gxp/api-client`; the mapper and model changes stay inside `core/guest/` and `core/content/`. |

---

## Convention compliance

| Rule | Verdict |
|---|---|
| **No new dependency** | ✅ `pom.xml`, `frontend/package.json`, `package-lock.json` and `packages/api-client/package.json` are all clean in `git status`. |
| **`backend/CLAUDE.md` → Tests** | ✅ `MenuFixtures` sits in `de.gxp.infrastructure.testsupport`, test scope only, on the `DeviceFixtures` precedent that file already names. Native SQL to arrange state is exactly what it sanctions. No new convention, so no nested `CLAUDE.md` entry is owed. |
| **`backend/CLAUDE.md` → BCE** | ✅ No Boundary, Control or entity added or touched. `PersistenceAccessTest`'s named exemption for `DevDataSeeder` covers the new native SQL. |
| **`backend/CLAUDE.md` → Flyway** | ✅ Versioned SQL in `src/main/resources/db/migration/`, no Hibernate generation, leaves a fresh instance usable. |
| **Contract-first** | ✅ `api/openapi.yaml` edited first; `@Size(max=50)` present on the generated `GuestMenu.getItems()` under `target/`; committed client regenerated, never hand-edited. |
| **`frontend/CLAUDE.md` → import rule** | ✅ Only `core/guest/` and `core/content/` touch wire types. |
| **`frontend/CLAUDE.md` → no user-visible string in a component** | ✅ None added. |
| **`frontend/CLAUDE.md` → jsdom asserts no geometry** | ✅ None of the four new/changed specs touches layout. |
| **Constitution principle 3 (never logged)** | ✅ Nothing new is logged anywhere in the cut. |
| **Constitution v1.9.0 kiosk-cache clause** | ✅ Not engaged. The cache gains catalogue labels, icons and routes only — no credential, token or key material. |
| **Branch discipline** | ✅ Still on `refactor/bce-subpackages-and-boundary-security`; nothing staged, nothing committed, no branch created. |
| **Approval gates** | ✅ Nothing approval-gated was implemented. No `CLAUDE.md` entry written, no constitution amendment written, no new pattern introduced. |

---

## Verdict

**NEEDS CHANGES ❌** — but narrowly, and none of it is behaviour.

The implementation is good. The migration, the `MenuFixtures` repair, the
`@AfterEach` placement in all five writer classes, `CatalogueExpansionTest`'s
refusal to name a fixture key, the icon registration and the two frontend edits
are all correct, and I could not find a defect in any of them. The isolation
hazard — the thing most likely to turn this into a green-then-flaky suite — is
genuinely closed, and I proved it with an order the plan's own method could not
produce.

What must be settled before the single commit:

- **🟠 1** needs an owner decision — enforce `maxItems: 50` at the kiosk boundary,
  or strike REQ-901's rejection criterion and fix design §2/§5. Leaving both is
  the one thing that is not acceptable, because the design currently asserts an
  enforcement that does not exist.
- **🟠 2** and **🟠 3** are corrections to a test assertion and to two plan lines.
  Both are small, and both matter because they are false *evidence* — the kind of
  thing that stays true-looking until it costs a day.

**Phase 4 may start.** Nothing in T-10 … T-12 depends on any of the three 🟠, and
none of them can be made worse by routing work landing on top. Fixing 🟠 2 and
🟠 3 in the same session is the cheap path; 🟠 1 needs the owner and should not
block routing.
