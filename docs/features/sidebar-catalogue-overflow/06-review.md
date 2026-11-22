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

---
---

# Review — Part A, Phases 4–6 (T-10 … T-21) — **round 2**

**Result:** **NEEDS CHANGES ❌** — 🔴 0 · 🟠 1 · 🟡 5 · 🟢 8. **No defect was found
in shipped behaviour, and every requirement in this cut was verified in a real
browser rather than read.** The single 🟠 is a plan-document correction: the
isolation flag that the last fix round wrote into `03-todo.md` T-03/T-20 —
*on my predecessor's own recommendation (round 1, 🟠 3)* — **aborts a full
`./mvnw` run before a single test executes.** Measured, twice.

Run 2026-08-27 by `reviewer` against `docs/constitution.md` **v1.9.0**,
`CLAUDE.md`, `frontend/CLAUDE.md`, `backend/CLAUDE.md`,
`docs/design/screens-inhouse/menu-overflow.md`, `docs/design/tokens.md`,
`docs/design/design-sheet.md` and the working tree at
`refactor/bce-subpackages-and-boundary-security` (nothing committed, nothing
staged, no branch switched).

> **Scope.** T-10 … T-21 only. T-01 … T-09 are committed (`33f745a`) and were
> reviewed in round 1 above. `docs/features/guest-payload-consolidation/**` and
> `docs/features/module-content/**` are Part B/C planning committed in `c8081a8`
> — deliberately not read, not reviewed, not written.

## Step 0 — the visual check was owed this time, and was performed

Round 1 recorded that no screenshot was owed because that cut contained no UI.
**This cut is the UI.** The app was run end-to-end (`quarkus:dev` on a fresh Dev
Services Postgres with `V10` applied and `%dev` delivering **13** menu items;
`ng serve` on 4300), the popover was opened and **screenshotted in both themes
and both languages at 1024×768 and 1280×832**, and each render was compared
against `docs/design/screens-inhouse/menu-overflow.md`. It matches: 5 columns
row-major, alphabetical in the active language, the glass recipe, the trigger as
an ordinary `.sidebar-item`, no active variant on any tile, no entry animation,
dark mode inherited with no theme-specific rule. One observation the spec's prose
could not have caught is recorded as 🟡 5.

---

## Measured numbers — my own runs

Host: aarch64 Raspberry Pi, 4 cores, 3796 MB RAM, ~1.7 GB already swapped, six
unrelated containers running (Immich ×3, Valkey, Home Assistant, Matter).
`JAVA_HOME=/tmp/gxp-jdk/jdk-21.0.12+8`, `sg docker -c …`.

| Run | Command | My result | dev reported |
|---|---|---|---|
| Backend | `./mvnw verify` | **425 run · 0 failures · 4 errors · 3 skipped** | 436 · 0 · 0 · 0 |
| Backend, isolation A | `-Dtest=SecondFactorLoginTest` | **6 · 0 · 0 · BUILD SUCCESS** | 6/6 in isolation |
| Backend, isolation B | 5 classes, `secondary-orderer=…$Random`, seed `20260827` | **31 · 0 · 0 · BUILD SUCCESS** | — |
| Backend, isolation C | same 5 classes, `secondary-orderer=…$ClassName` | **31 · 0 · 0 · BUILD SUCCESS** | — |
| Backend, isolation D | `./mvnw test -Djunit.jupiter.testclass.order.default=…$Random` | **0 run · BUILD FAILURE — Quarkus aborts** | dev reported the same |
| Frontend | `ng test --watch=false` | **298 passed / 298, 33 files** | 298/298, 33 files ✅ |
| Frontend | `ng build` | clean — `main` 457.69 kB, `styles` 9.10 kB | clean ✅ |
| Browser | `node tools/measure.mjs` | **168 cells · 0 overflowing · 0 truncated · 0 findings** | identical ✅ |
| Prettier | `--check` over every changed frontend file | **clean** | — |
| Manifests | `git diff HEAD -- pom.xml package.json package-lock.json` | **empty** | ✅ |
| Generated client | `git status --porcelain packages/ api/ backend/` | **empty** | ✅ |

**The four backend errors are the established environmental Testcontainers
flake**, not a regression, and the evidence is the same shape as round 1's:
`org.rnorth.ducttape.TimeoutException` → `WaitAllStrategy.waitUntilReady` →
`ContainerLaunchException: Container startup failed for image postgres:17.11`,
thrown at `QuarkusTestExtension.interceptBeforeAllMethod` **before any test body
ran**, only ever on `@TestProfile` classes (4 of the 6 this time —
`TrustedProxyLoginTest`, `DevDataSeederTest`, `UntrustedPeerAttributionTest`,
`OpenApiDisabledTest`), with the failed container's own log showing `initdb`
completing normally and simply not finishing inside the wait window. **Every one
of the 70 default-profile classes passed. Zero assertions failed in any run.**

**On 425 vs. dev's 436.** A class that errors at boot reports far fewer tests than
it has: `TrustedProxyLoginTest` reported **1** of 7, and `SecondFactorLoginTest`
— which shares that profile's application — reported **0**. The gap is entirely
under-counting, not lost coverage. dev's 436 · 0 · 0 · 0 is a plausible clean run
on a quieter box; it is **not** reproducible here today, which is worth one clause
in `docs/features/index.md` (🟢 8).

### `node tools/measure.mjs` — my own full grid

**168 route × theme × language × viewport combinations, 0 overflowing, 0 truncated
strings, 0 findings.** `14 routes × 2 themes × 2 languages × 3 viewports`, with
the popover measured **open** in all four cells at each constitutional viewport.

**Home's signed-off baselines are unmoved** at 1280×832 in all four cells:
`heading {x:172, y:88, …}` and `cardRow {… bottom:744}` — exactly the
`home-screen-prototype` numbers.

**PASS 3B — the popover open** (identical in all four cells per viewport, and
byte-identical to dev's table):

| | 1024×768 (root 12.8 px) | 1280×832 (root 16 px) |
|---|---|---|
| trigger | `{x:32, y:474, w:67, h:67}` | `{x:40, y:528, w:84, h:84}` |
| panel | `{x:109, y:427, w:376, h:160}` | `{x:136, y:470, w:470, h:200}` |
| `grid-template-columns` | 5 × 67.1875 px | 5 × 84 px |
| computed border / padding | `1px` / `9.6px` | `1px` / `12px` |
| gap to trigger | 10 px = `--space-4` | 12 px = `--space-4` |
| vertical centre offset | −0.5 px | 0 px |
| clear of the sidebar | +3 px | +4 px |
| tiles | 9 × 67×67, smallest 67 px ≥ **35.2 px** | 9 × 84×84, smallest 84 px ≥ **44 px** |
| labels | 9, longest 2 lines, **none clamped**, none truncated | same |
| glyphs | **9/9 rendered, 0 fell back to `phosphorCircle`** | same |
| roles | panel `menu`, every tile `menuitem` | same |
| accessible names | "Weitere Bereiche" · "More sections" | same |
| overlay | `onBody=true`, `inShell=false`, `withinViewport=true` | same |
| document scroll | 1024/1024 · 768/768 **NO-SCROLL OK** | 1280/1280 · 832/832 **NO-SCROLL OK** |

### Live interaction — driven in Chromium by me, not taken from the report

```
slots closed             [Home, Info, Umgebung, Smart Home, Mehr]
aria-expanded            false -> true on open -> false on close
focus on open            first tile (Aktivitäten)
ArrowDown                Anreise & Abreise            (next in reading order)
ArrowRight               UNCHANGED                    (inert, as designed)
ArrowLeft                UNCHANGED                    (inert, as designed)
End / Home               WLAN / Aktivitäten
typeahead "p"            Parken
Escape                   panel gone, focus back on the trigger ("Mehr")
outside click            panel gone
choose "Parken"          url /parken, slots [Home, Info, Umgebung, Parken, Mehr], active Parken
then click slot 2        slots UNCHANGED, active Info            <- REQ-928 live
RELOAD                   slots [Home, Info, Umgebung, Smart Home, Mehr]  <- REQ-929 live
deep link /gastronomie   slots [.., Essen & Trinken, Mehr], active Essen & Trinken  <- REQ-932 live
/gibt-es-nicht           "Dieser Bereich ist nicht verfügbar", no note, url kept, 1280/1280 832/832
/info/tiefer/noch-tiefer "Dieser Bereich ist nicht verfügbar"    <- the ** child
/einstellungen           the real .settings screen, active       <- REQ-914
storage, whole session   localStorage ['gxp.cache.home','gxp.cache.session'] unchanged,
                         sessionStorage {} , document.cookie ""  , non-GET requests: []
```

**REQ-930 driven live, which no unit test can do.** With `parken` promoted and the
guest then back on Home — so only `chosen` held slot 4, not the active route — I
disabled `parken` at property scope through `PUT /api/manager/menu-config` and
waited. **The next poll dropped the promotion within 30 s** and slot 4 reverted to
the delivered `Smart Home`. No stale entry survived the content change. The dev
database was restored afterwards (13 items again).

### Priority checks, each settled by evidence

| Priority | Verdict | Evidence |
|---|---|---|
| **1 — promotion never persists (REQ-929)** | **Holds, by construction and by driving it.** | `chosen` is a `protected` component signal with one writer (`choose()`); the component injects only `Router` and `TranslationService` — no gateway, no storage, no HTTP. Grep: no `localStorage`/`sessionStorage`/`cookie`/`indexedDB` reference anywhere in `layout/`. Live: localStorage, sessionStorage and `document.cookie` were **byte-identical** before and after a promotion, and **zero non-GET requests** were issued for the whole session. Reload restored the manager's order. Standby is structural — `/standby` is a **top-level** route, so it destroys `ShellComponent` and the signal with it. |
| **1b — REQ-930 poll-drop** | **Holds.** | Driven live (above), plus `menu-split.spec.ts` *"drops a chosen key the poll no longer delivers"*. |
| **2 — `SidebarComponent` alone owns active (REQ-921), independent of promotion (REQ-928)** | **Holds.** | Only `isActive()` writes `.sidebar-item--active`, in one component, for both surfaces; the popover has no active variant at all. Live: after promoting Parken and navigating to a slot-1–3 module, the slots were **unchanged** and Parken was inactive. `isActive()` is behaviourally identical to the version it replaced, only now normalising both sides via the shared `routeMatches()`. |
| **3 — the keyboard model is the ARIA contract, not a reimplementation** | **Holds; nothing is faked.** | `sidebar.component.html` carries **no `(keydown)` handler and no `setActiveMenuItem` call** — the entire model is the CDK's. Verified in the installed `@angular/cdk` 22.1.2: `CdkMenu.orientation = 'vertical'`, and `_handleKeyEvent` `preventDefault()`s LEFT/RIGHT and hands them to a vertical `FocusKeyManager` that ignores them. Live: ←/→ moved focus **nowhere**. |
| **4 — REQ-903 disposability** | **Holds in substance.** | I greped all **8 keys** and all **16 labels**, both languages, across `backend/app/src`, `frontend/src`, `frontend/tools`, `api/`, `packages/`, specs included. Code hits only in `V10`, `DevDataSeeder`, `MenuFixtures` and `measure.mjs`'s `ROUTES`. The only other hits are **prose**: `registered-icons.ts`'s comment (🟢 1) and `DevDataSeeder`'s **pre-existing** action-card copy *"Hausregeln, WLAN, Parken & mehr"* (verified against `33f745a^` — unchanged, coincidental). No `.ts`/`.html`/`.css` and **no test** depends on a fixture key or label; all four new/changed spec files invent their own. Only V10's *header sentence* is now inaccurate — 🟡 1. |
| **5 — no-scroll** | **Holds.** | 168/168 cells clean, both viewports, both themes, both languages, all 14 routes; and clean again with the panel **open** in all 8 popover cells. `overflow: hidden` appears exactly once, on `.sidebar-overflow__tile-label` for the 2-line clamp — a label, not a screen. The panel is `position: fixed` in the overlay container on `document.body` (`onBody=true`, `inShell=false`), so it structurally cannot grow the document. |
| **6 — no user-visible string in a component; no `@gxp/api-client` in a component** | **Holds.** | Both new templates resolve every chrome string through `t(...)`; module labels arrive as inputs. `grep -l "@gxp/api-client" src/` lists only `core/guest/`, `core/api/`, `core/preferences/` and `features/pairing/pairing.service.ts` — all pre-existing, none a component, none touched here. |
| **7 — memoised collator, built after language resolution** | **Holds.** | `collatorFor` is a module-level `Map<string, Intl.Collator>`; the spec asserts instance identity per locale. `sortByLabel` runs on `ResolvedMenuItem.label`, a `string` — an unresolved label is not in scope in that file, so the sort *cannot* precede resolution. Live: the panel re-sorted from German order (`Aktivitäten, Anreise & Abreise, Ärztliche Versorgung, …`) to English (`Arrival & departure, Food & drink, House rules, …`) on a language switch, and German collation put `Ärztliche` between `Anreise` and `Essen` — not where its code point sits. |
| **8 — constitution v1.9.0 / `frontend/CLAUDE.md` / no new dependency** | **Holds.** | No `pom.xml`, `package.json` or `package-lock.json` diff. `@angular/cdk/menu` was already approved and already in use. v1.9.0's only delta — the kiosk-cache boundary and the staleness escalation — is untouched: this cut adds no cacheable value at all, and no token, key or credential. No new pattern, no nested `CLAUDE.md` entry proposed, no amendment proposed. |

---

## Findings

| # | Sev | Category | File | Finding | Fix |
|---|---|---|---|---|---|
| 1 | 🟠 | Verification | `03-todo.md` T-03 & T-20 | **The prescribed isolation flag aborts the whole suite before a single test runs** — and it is *my predecessor's own text*, written into the plan by round 1's 🟠 3. Measured today: `./mvnw test -Djunit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer$Random` → *"Critical failure. Quarkus tests would fail with corrupted application errors. The reason is that they would not run in the right order, because the Quarkus JUnit configuration has been overridden."* → **`Tests run: 0`, `BUILD FAILURE`**. It appeared to work in the fix round only because that was a nine-class `-Dtest=` subset containing **no `@TestProfile` class**. dev is right, and dev was right to leave the plan unedited. **But the replacement dev names needs a caveat I measured myself:** `-Djunit.quarkus.orderer.secondary-orderer=…$Random` runs, but it **defeats Quarkus' profile grouping** — with seed `20260827` it put the `@TestProfile` class `SecondFactorLoginTest` **first**, ahead of four default-profile classes, whereas the natural order and `…$ClassName` both group every profiled class last (measured: natural order = 48 default-profile classes, then all 6 profiled). On a full suite that multiplies re-augmentations and is the most likely cause of finding 4. | Replace both validation lines with the text under *"Exact replacement text"* below. Net effect: whole suite in its **natural** order; adverse order proved over a **`-Dtest=` subset with no `@TestProfile` class in it**; and the two flags that do not work named with their measured symptoms so nobody re-tries them a third time. |
| 2 | 🟡 | Docs accuracy | `V10__catalogue_expansion.sql:20-23` | **The header's greppable claim is now false.** It says *"no fixture KEY or LABEL appears outside this file, DevDataSeeder and MenuFixtures"*; `measure.mjs`'s `ROUTES` list names all eight (`/wlan … /aktivitaeten`) and is the fourth site. **REQ-903 itself is not breached** — the requirement forbids a *code path*, a *test* or a *design decision* depending on the fixtures, and a route list in a review instrument that asserts nothing is none of the three; its own comment states the obligation and the removal path. Only the sentence is wrong. | **Do not edit the migration** — see the ruling on open item 3. Record the fourth site in the doc layer instead (`docs/system-context.md`'s new "Added by …" block; `03-todo.md` T-21 already names it). Exact text below. |
| 3 | 🟡 | Docs accuracy | `02-design.md` §7.4 · `docs/design/screens-inhouse/menu-overflow.md` → "Tile grid" | **The panel-width table is 2 px out, and the spec is what is wrong.** dev's arithmetic is correct and I confirmed it against the live computed style: `--border-glass` is `1px solid`, the panel's computed `border-left-width` is **`1px`**, so the table's `2 × 0.75 + 5 × 5.25 + 4 × 0.375 = 29.25rem` is the **padding box**. Border box = 374.4 + 2 = **376.4 → 376** at root 12.8 px, and 468 + 2 = **470** at root 16 px — both measured exactly. The CSS departs from nothing; the table omits a hairline the constitution deliberately exempts from the rem system. | Correct the two tables, not the CSS. Exact text below. |
| 4 | 🟡 | Tracked item | `de/gxp/user/control/SecondFactorLoginTest.java` (untouched by this cut) | **A pre-existing anomaly, recorded so it is not silently absorbed — and it is *not* a suite-isolation defect.** dev saw `aWrongCodeIsTheGenericFailureAndCountsAgainstTheAddress` expect 429 and get 200 under `secondary-orderer=…$Random`. **dev's reasoning verified:** this cut changes **zero** backend files (`git diff HEAD -- backend/` and `git status --porcelain backend/` are both empty), the default-order run is clean, and the class is **6/6 in isolation** (my run A). **And I went further:** `203.0.113.10x` appears in **that one file only** across the whole test tree, and the throttle bucket is per-address and in-memory — so no other test can fill or drain it, and there is no cross-test data dependency of the kind T-03 was about. A 200 where 429 is expected is only reachable if `Forwarded` was **not** honoured, i.e. the class ran against an application that is not `TrustedProxyProfile`'s. That is a profile-switching artifact of the ordering flag (finding 1), not a defect in the test. **I could not reproduce it** in a 5-class subset with the profiled class first (run B) or last (run C) — both green. | Track it against the suite, not against this feature: *"`SecondFactorLoginTest` has once been observed running against a non-`TrustedProxyProfile` application under `-Djunit.quarkus.orderer.secondary-orderer=…$Random` on the full 76-class suite (2026-08-27). Not reproducible in subsets. Do not randomize class order across profile boundaries — see `03-todo.md` T-03."* Does **not** block this commit. |
| 5 | 🟡 | Visual fidelity | `sidebar.component.css` `.sidebar-overflow` · `menu-overflow.md` → "Panel" | **From the mandatory visual check, and only visible in a render.** The panel opens over the action-card row, and the cards' bright gradient icon tiles read **through** the glass — two of them sit directly behind the second tile row in both themes. I measured it rather than judging by eye: sampled label-vs-backdrop contrast is **11.02–12.65** in light and **4.53–9.25** in dark, the floor being *Services* and *Wi-Fi* over the two icon tiles. **Nothing is violated** — WCAG AA for this size is 4.5, the CSS implements `menu-overflow.md`'s recipe token-for-token, and the spec has no reference image to deviate from. It is recorded because the spec claims the panel *"stays visually subordinate to the content area"* and the render shows the content area coming through it, and because a property whose branding is brighter than the seed's would push that 4.53 under the line. | Owner's call, and no code change is required to ship. If it is to be addressed, the honest lever is the token (`--color-surface-glass`'s alpha) or the panel's anchor, not a one-off override in this component — and either is a design change, not a review fix. |
| 6 | 🟢 | Docs | `frontend/src/app/core/icons/registered-icons.ts:71` | The comment *"`phosphorWifiSlash` is deliberately NOT reused for the WLAN fixture"* names a fixture in prose. It sits in the one file REQ-903's own removal path already names as the co-edit, and it is a comment, not a dependency. Recorded only because it is the single hit outside the four sanctioned sites. | None. |
| 7 | 🟢 | Scope | `frontend/src/app/core/i18n/catalog/en.ts:26` | A drive-by Prettier reformat of `'pairing.instruction'`, unrelated to this feature. Verified benign: that line was **already** Prettier-nonconformant at `HEAD` (`prettier --check` on the `HEAD` blob warns), so this **fixes** a pre-existing violation rather than introducing churn. | None. |
| 8 | 🟢 | Design nuance | `menu-split.ts:84-87` · `sidebar.component.ts:95-98` | `chosen` is never *cleared* on a poll-drop, only ignored (`rest.find(…) ?? rest[0]`). REQ-930's required behaviour holds — I drove it — but if the manager re-enables the module **within the same session**, the old promotion resurrects. Design §6.1 chose this shape deliberately (*"Nothing else references it"*) and the five-line expression is explicitly not to be branched. | None. Worth knowing before anyone "fixes" it. |
| 9 | 🟢 | Duplication | `sidebar.component.ts:86` | `navigateTo()` still writes `item.route === '' ? '/' : item.route` inline while `menu-split.ts` owns the same normalisation in a private `normalise()`. Two copies of one rule, in the two files the feature explicitly unified. | Export `normalise()` (or a `routeUrl(item)`) at the next touch. |
| 10 | 🟢 | Tokens | `sidebar.component.css:193-194` | `.sidebar-overflow__tile:active` adds a **fifth** raw `transform 180ms cubic-bezier(0.16, 1, 0.3, 1)` literal to the four that already exist. Consistent with the established press treatment and unavoidable — the token layer has no motion tokens and `design-sheet.md` → Motion is an unspent spec — but it is one more site to sweep when they land. | None now. |
| 11 | 🟢 | Instrument | `tools/measure.mjs` PASS 3B | The fallback-glyph detector is **sound and cannot produce a false negative** — I checked the installed package: `phosphorCircle` renders **exactly one** `<path>` whose `d` is exactly `FALLBACK_GLYPH_PATH`, so `paths === 1 && d === FALLBACK` is an exact characterisation of the fallback render and nothing can escape it. Residual: it inspects **popover tiles only**, so the four sidebar slots' glyphs are unchecked by it. | Optional: run the same check over `.sidebar-item ng-icon` at the next touch. |
| 12 | 🟢 | Instrument | `tools/measure.mjs` `applyPreferences` | A single `waitForSelector('.setting-choice')` timeout **crashes the whole run** with an uncaught rejection instead of `note(…)`-ing and continuing. I hit it on my first attempt against a cold `ng serve` (the settings route had not been compiled yet) and lost the entire 168-cell grid; the warm re-run was clean. The script's own contract is *"it deliberately only reports … always exits 0"*, which this path breaks. | Wrap the `applyPreferences` call site in a `try/catch` that `note()`s and skips the cell. |
| 13 | 🟢 | Docs accuracy | `docs/features/index.md` | The row states the backend baseline as *"436 run · 0 failures · 0 errors · 0 skipped"* without qualification. On this box today, **4 of the 6 `@TestProfile` classes errored on container startup** and the run reported 425 · 0 · 4 · 3. The zero-error figure is a lucky run, not a reproducible baseline. | One clause: *"(436 · 0 · 0 · 0 on a quiet box; `@TestProfile` classes flake on container startup under load — see 06-review.md)"*. |

**Counts: 🔴 0 · 🟠 1 · 🟡 5 · 🟢 8**

---

## Rulings on the four open items

### 1. `SecondFactorLoginTest` under a randomized class order — **dev's reasoning is CORRECT, and it is not an isolation defect**

Verified independently, and taken one step past dev's argument:

- **Not this cut's doing.** `git diff HEAD -- backend/` is empty and
  `git status --porcelain backend/` is clean. Phases 4–6 touch **no** backend
  file, no `pom.xml`, no contract, no generated client.
- **Green in isolation** — my run A: 6 · 0 · 0, BUILD SUCCESS.
- **No cross-test coupling exists.** `203.0.113.10[0-9]` appears in **that file
  only**, across the entire test tree, and only inside that one method; the login
  budget is per attributed address and in-memory. Nothing else can fill or drain
  that bucket. The property T-03 was about — a shared database row written by
  another class — has no analogue here.
- **The symptom names its own cause.** 200 instead of 429 requires the five prior
  failures not to have been attributed to `203.0.113.102`, which requires
  `Forwarded` to have been ignored, which requires
  `quarkus.http.proxy.allow-forwarded` to have been off — i.e. the class ran
  against an application that is **not** `TrustedProxyProfile`'s.
- **Not reproducible in a subset.** Run B (profiled class first, `$Random`, dev's
  seed) and run C (profiled class last, after four default-profile classes,
  `$ClassName`): both 31 · 0 · 0, BUILD SUCCESS.

**Ruling: record it as a tracked item against the *test suite's ordering
mechanism*, not against `SecondFactorLoginTest` and not against this feature.**
It must not be silently absorbed — finding 4 carries the exact wording. It does
not block the commit.

### 2. T-20's isolation flag — **dev is RIGHT, the plan is wrong, and so is half the replacement**

Confirmed by measurement, not by reading dev's report: the flag round 1 put into
the plan **aborts before a single test runs**. And the flag dev proposes instead
works, but randomizes something the plan never intended to randomize.

**Ruling: correct `03-todo.md` T-03 and T-20 with the text below.** The rule that
comes out of it is one sentence: *randomize the order of the classes that share a
database; never randomize across a profile boundary.*

#### Exact replacement text — `03-todo.md` T-03, final `Validate:` bullet

> - Validate: the whole backend suite green, **run twice in its natural order**
>   (`./mvnw verify`), plus **one adverse-order run over the menu subset**:
>   ```
>   ./mvnw test -Dtest='AuditCoverageTest,ActionCardTest,CatalogueExpansionTest,\
>   MenuCatalogueAndConfigTest,MenuDisableGuardTest,MigrationTest,GuestSessionTest,\
>   GuestHomeScreenTest,FirstRunSeedTest' \
>     -Djunit.quarkus.orderer.secondary-orderer='org.junit.jupiter.api.ClassOrderer$Random' \
>     -Djunit.jupiter.execution.order.random.seed=<recorded>
>   ```
>   Record the seed **and** the order it produced, and check a property-scope
>   writer really preceded `GuestSessionTest` — the point is that its
>   `menu.items hasSize(5)`/`hasSize(4)` no longer depend on class order.
>   **Three measured facts about the flags, so none of them is tried a third
>   time (all 2026-08-27):**
>   (a) `-Dsurefire.runOrder=…` is **inert** — Quarkus' profile-aware orderer
>   overrides Surefire's and the 76-class order comes out byte-identical.
>   (b) `-Djunit.jupiter.testclass.order.default=…` **aborts the whole suite**:
>   *"Critical failure. Quarkus tests would fail with corrupted application
>   errors … the Quarkus JUnit configuration has been overridden"*, `Tests run:
>   0`, `BUILD FAILURE`. It only appeared to work in the fix round because that
>   was a nine-class subset with no `@TestProfile` class in it.
>   (c) `-Djunit.quarkus.orderer.secondary-orderer=…$Random` **does** randomize —
>   but it also randomizes `@TestProfile` classes **relative to** default-profile
>   ones, where the natural order groups all six of them last. **Scope it to a
>   `-Dtest=` subset that contains no `@TestProfile` class.** The nine classes
>   above qualify.

#### Exact replacement text — `03-todo.md` T-20, second bullet

> - **One adverse-order run of the menu subset**, exactly as T-03 prescribes —
>   **not** the whole suite. The whole suite runs twice in its **natural** order.
>   Record the seed and the order. Do **not** use `-Dsurefire.runOrder` (inert)
>   and do **not** use `-Djunit.jupiter.testclass.order.default` (aborts the run
>   with *"corrupted application errors"*, 0 tests). Both measured 2026-08-27 —
>   see `06-review.md` round 2, finding 1.

### 3. `V10`'s header vs. `measure.mjs` — **do NOT edit the migration; record the fourth site in the doc layer**

Three things decide it:

1. **REQ-903 is not breached.** It forbids a *code path*, a *test* or a *design
   decision* from depending on the fixtures. `ROUTES` is a list in an instrument
   that asserts nothing and is not wired into `npm test`; a stale entry costs one
   unmeasured route, which the list's own comment says in as many words.
2. **The cost of editing is real and the benefit is a comment.** Flyway validates
   checksums, so the edit breaks any database already holding V10. That V10 has
   so far only met ephemeral Dev Services containers is true but is **not** the
   binding fact — the binding fact is that V10 is already **committed**
   (`33f745a`), so this would be a second commit rewriting an applied migration to
   fix a sentence.
3. **The obligation is already recorded twice** — in `measure.mjs`'s own comment
   and in `03-todo.md` T-21.

**Ruling: leave `V10__catalogue_expansion.sql` byte-identical. Add one clause to
`docs/system-context.md`**, in the "Added by `sidebar-catalogue-overflow`" block
that T-21 just wrote, immediately after *"…plus one edit to
`core/icons/registered-icons.ts`"*:

> `` …and one edit to `frontend/tools/measure.mjs`'s `ROUTES` list, which walks every route the `%dev` catalogue can name. Those are the four sanctioned sites; the migration's own header predates the fourth and names three. ``

dev's proposed migration-header wording is good and should be applied **only if**
the owner would rather carry the checksum change — in which case it belongs in
this same commit, while the only databases holding V10 are throwaway containers.

### 4. The 2 px — **the spec is wrong, the CSS is right; correct the spec**

dev's arithmetic checks out and I confirmed every term against the live computed
style rather than against the stylesheet:

```
content   5 × 5.25rem + 4 × 0.375rem            = 27.75rem
padding   + 2 × 0.75rem  (--space-4)            = 29.25rem   <- the table's number
border    + 2 × 1px      (--border-glass)       = 29.25rem + 2px
1024×768  29.25 × 12.8 = 374.4 + 2 = 376.4  ->  measured 376
1280×832  29.25 × 16   = 468.0 + 2 = 470.0  ->  measured 470
```

The 1 px hairline cannot appear in a rem figure at all — the constitution
explicitly exempts hairline borders and blur radii from the rem rule — so the
table can never be made to reconcile by arithmetic alone. It has to say which box
it is quoting.

**Ruling: change neither the CSS nor the token. Correct both tables**, in
`02-design.md` §7.4 and in `docs/design/screens-inhouse/menu-overflow.md` →
"Tile grid". Exact replacement for the table and the line under it:

> | Columns | Panel content+padding | Border box (what a browser reports) | at 1024×768 | at 1280×832 |
> |---|---|---|---|---|
> | 5 | 29.25rem | 29.25rem + 2px | **376 px** | **470 px** |
> | 6 | 34.875rem | 34.875rem + 2px | **448 px** | **560 px** |
>
> The `+ 2px` is `--border-glass`'s 1 px hairline on each side. It is deliberately
> not expressible in `rem` — `docs/constitution.md` → Styling exempts hairline
> borders and blur radii from the rem rule — which is why the rem column and a
> measured `getBoundingClientRect().width` differ by exactly 2 px and always will.
> Measured 2026-08-27: 376 px / 470 px.

---

## Rulings on the six declared deviations

1. **T-13's spec avoids the fixture label `Ärztliche Versorgung`** — **ACCEPTED;
   dev is right and the TODO is wrong.** Verified: `Öffnungszeiten` appears
   nowhere in `V10`, and `Services`/`Umgebung` are original `V2` sections, so the
   spec depends on no fixture. It still proves the requirement — German collation
   yields `Öffnungszeiten, Services, Umgebung` where code-point order yields
   `Services, Umgebung, Öffnungszeiten`, and the spec asserts both. This is
   exactly the trap round 1 corrected in T-03's `FirstRunSeedTest` bullet, one
   task later, and it should be closed the same way. Proposed correction to
   `03-todo.md` T-13, second `Validate:` bullet:
   > `Intl.Collator` de vs. en ordering **including an umlaut-initial label** and
   > `numeric: true`. Use an **invented** label — the PRD's acceptance criterion
   > names `Ärztliche Versorgung`, but that is a fixture label and REQ-903
   > forbids a test depending on one (the same correction T-03 carries).
   > `Öffnungszeiten` / `Services` / `Umgebung` proves the identical property.
2. **T-12 also touched `app.routes.spec.ts`** — **ACCEPTED, and the fix
   verified.** dev's premise is true: that spec's content double delivers **five**
   menu items, so `splitMenu` early-returns and the sidebar renders `Info`,
   `Umgebung`, `Smart Home` **and** `Services` inside the same `el` the loop was
   reading — every one of the four assertions would have passed with the
   placeholder resolving nothing. The new
   `expect(placeholder!.textContent).toContain(expectedLabel)` genuinely closes
   it, and the two added cases cover `:sectionKey` and the `**` child.
3. **T-14 also touched `sidebar.component.spec.ts`** — **ACCEPTED.**
   Mechanically forced: `input.required` throws NG0950 when read before it is
   set, so a new required input invalidates every existing `createComponent`.
   Nothing else in the seven cases changed.
4. **T-18's fallback detector** — **ACCEPTED; it cannot produce a false
   negative.** I checked the installed `@ng-icons/phosphor-icons`:
   `phosphorCircle` is exactly one `<path>` whose `d` is exactly the constant, so
   `paths === 1 && d === FALLBACK_GLYPH_PATH` is an **exact** characterisation of
   the fallback render — a genuine fallback cannot slip past it. The failure mode
   the tightening removed was a false *positive* (`phosphorClock` shares the first
   path but has more than one), and the mutation probe against
   `phosphorFirstAid` is the right proof it still fires. Residual noted as 🟢 11.
5. **The `LOGO DID NOT DECODE` flake** — **ACCEPTED.** My own full grid reports
   **0 findings across 168 cells**, including no decode finding, which is a third
   independent observation agreeing with dev's re-measure. Recorded, not chased.
   (I hit a *different* cold-start flake in the same instrument — 🟢 12.)
6. **T-21 folded the `docs/system-context.md` rows** — **ACCEPTED, and it was
   required, not a deviation.** Both `02-design.md` §11 and the pending block's
   own last line say T-21 folds them once `dev` has landed them. I checked each
   folded row against the shipped code: `SidebarComponent`, the new
   `menu-split.ts` row, `SectionPlaceholderComponent` and
   `PropertyContentService.locale` all describe what is actually there, including
   the `role="menu"` keyboard rationale and the whole-set-replace limitation.

---

## Requirement confirmation — REQ-912 … REQ-935

| REQ | Confirmed by |
|---|---|
| 912 | `:sectionKey` + `**` in `app.routes.ts`; live `/gibt-es-nicht` → `section.unavailable`, url kept, no redirect; `/info/tiefer/noch-tiefer` → same via the `**` child; 8 spec cases. |
| 913 | Structural — the route table is a static constant; `shellGuard` unchanged. Live: all 13 catalogue routes resolve; the LKG cache keys were present throughout. |
| 914 | `/pairing`, `/standby`, `/connection` untouched and still first; `einstellungen` declared **before** `:sectionKey`; live `/einstellungen` renders `.settings` and is marked active. |
| 915 | Live: `[Home, Info, Umgebung, Smart Home, Mehr]` — 4 + trigger, never more. |
| 916 | Measured: panel right of the trigger, gap = `--space-4` (10/12 px), centre offset −0.5/0 px. No URL. |
| 917 | 9 tiles, no `slice`/`maxItems`/"and N more" anywhere in the panel path; `max-height: calc(100dvh - 2*--space-6)` = 729.6/784 px measured; `overflow-y: auto` on the grid; document 1024/1024 · 768/768 and 1280/1280 · 832/832 with the panel open. |
| 918 | 5 columns measured at both viewports; smallest tile 67/84 px against `--size-touch-min` 35.2/44 px. |
| 919 | Row-major (`grid-auto-flow: row`); live ↓ walks reading order. |
| 920 | `splitMenu`'s only input is the delivered array; no `pinned` concept anywhere. |
| 921 | One component renders both surfaces; `.sidebar-item--active` written in one place; no active variant on any tile. |
| 922 | `CdkMenuTrigger`/`CdkMenu`/`CdkMenuItem` + `cdkMenuitemTypeaheadLabel`; roles `menu`/`menuitem`; Escape, outside-click, focus-return, typeahead all CDK. No new dependency. |
| 923 | Live: choosing Parken put it in slot 4 and displaced `Smart Home` into the panel; slots 1–3 unchanged. |
| 924 | Live tile order alphabetical in de and en, and materially different between them. |
| 925 | `collatorFor` memoised per locale (spec asserts identity); `numeric: true` (spec asserts `Etage 1/2/10`); `locale` from the property's own option list. |
| 926 | Live language switch re-sorted the panel; **zero non-GET requests** and no fetch observed. |
| 927 | Tie-break on `id`, asserted twice from reversed input. |
| 928 | Live: after promoting Parken and navigating to slot 2, slots unchanged and Parken inactive. |
| 929 | Live: localStorage, sessionStorage and `document.cookie` byte-identical across a promotion; reload restored the delivered order. |
| 930 | **Driven live** against the manager API — the poll that dropped `parken` dropped the promotion within 30 s. |
| 931 | Spec: no trigger in the DOM at ≤ 5, and the label absent from `textContent`. |
| 932 | Live deep link `/gastronomie` promoted it into slot 4 and marked it active. |
| 933 | No component imports `@gxp/api-client`. |
| 934 | `nav.more`, `nav.more.aria`, `section.unavailable` in both catalogues; live accessible names in de and en; trigger carries no `aria-label`. |
| 935 | 168/168 cells clean, plus 8 popover-open cells clean. |

**24 of 24 confirmed. REQ-901 … REQ-911 were confirmed in round 1.**

---

## Verdict

**The code is ready.** I could not find a defect in it, and unlike round 1 that
is not a reading — every behavioural requirement in this cut was **driven in a
real browser**, including the two that no unit test can reach (REQ-930's poll-drop
and REQ-929's survival across a reload). dev's report is honest: every number I
could reproduce, I reproduced exactly, and the one that differs (backend 436 vs.
425) differs only because four container starts timed out on my box.

**What must land in the same commit:**

- **🟠 1** — `03-todo.md` T-03 and T-20. This is the second time this sentence has
  been wrong, and the second time in the same direction: a verification step that
  cannot do what it claims. Exact text is above; it needs no judgement to apply.
- **🟡 2** and **🟡 3** — one clause in `docs/system-context.md` and two corrected
  tables. Both are one-line-of-thought edits with the wording written out.

**🟡 4** is a tracked item, not a fix. **🟡 5** is the owner's, and needs no code
change to ship. Every 🟢 is deferrable.

Nothing here needs `architect`: no architecture, auth, concurrency, external
integration or exposure-model change. No new dependency, no new pattern, no
nested `CLAUDE.md` entry and no constitution amendment — the last three
deliberately, and dev proposed none.
