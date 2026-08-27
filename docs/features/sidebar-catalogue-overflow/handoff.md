# Handoff — sidebar-catalogue-overflow (Part A)

**Last session:** 2026-08-27, `dev` (**fix round** on the `06_review` findings, T-01 … T-09)
**Branch:** `refactor/bce-subpackages-and-boundary-security` (owner instruction — do not switch, **nothing committed, nothing staged**)
**Status:** all three 🟠 and both actionable 🟡 are **fixed and verified**. Full report: [06-review.md](06-review.md).
**Phase 4 (T-10 → T-12) starts from here.**

## Fix round — 2026-08-27 (`dev`), all five items closed

| # | What changed | Verification actually run |
|---|---|---|
| 🟠 1 | **The kiosk-side menu bound is restored at 50** — `MAX_MENU_ITEMS = 50` in `guest-content.mapper.ts`, written as the exact sibling of `MAX_CARDS = 4`: named constant, length check, `GuestContentMappingError`, message `menu.items exceeds the contract's 0–50`, which `api/openapi.yaml` now really declares. **Lead decision** under the autonomy grant (`docs/handoff.md` → *"Decisions the lead took"*); the owner may reverse it. The constant's doc comment now explains the corrected history (the old `= 5` invented a bound and lied about its source) instead of describing the constant as gone. `02-design.md` §2/§5/§9 and `03-todo.md` T-08 corrected to match. | `guest-content.mapper.spec.ts` gained *"maps exactly maxItems menu items and rejects one more"* — 50 map, 51 throws against the `0–50` message. **Frontend: 256/256, 32 files** (was 255). `ng build` clean, `main` 453.07 kB. `grep -rn "0–5" frontend/src` → one hit, the `0–50` regex itself. |
| 🟠 2 | **`DevDataSeederTest.reseedingIsAFullNoOp` can now fail for its stated reason.** It turns one property-scope row **off** before re-seeding — chosen key-free via `min(id)`, so REQ-903 holds — asserts the arrangement took, then asserts the count is still `enabledConfigs - 1` afterwards, and restores the exact row in a `finally` so the other case in the class still sees 13 whichever order the two run in. The old version snapshotted `enabled = true` *after* the first seed had set all eight true, against an idempotent `UPDATE`. | Ran the class: **2 run · 0 failures · BUILD SUCCESS**. Then **mutation-probed** it — moved `enableCatalogueFixtures()` above `seedIfMissing()`'s `devDeviceExists()` early return and re-ran: **1 failure**, `re-seeding must not re-enable what a developer turned off … expected: <7> but was: <8>`. Probe reverted; `DevDataSeeder.java` is byte-identical to before it. |
| 🟠 3 | **The isolation proof is replaced with a mechanism that works.** `-Dsurefire.runOrder=reversealphabetical` is inert here (Quarkus' profile-aware orderer overrides Surefire's; the reviewer measured a byte-identical 76-class order). `03-todo.md` T-03 and T-20 now prescribe `-Djunit.jupiter.testclass.order.default='org.junit.jupiter.api.ClassOrderer$Random'` with a **recorded seed**, and say in as many words that `-Dsurefire.runOrder` must not be used. | Two runs of the 9-class menu subset, orders extracted from the `Running de.gxp.*` lines. **Seed `7`** — `AuditCoverageTest (W) → ActionCardTest (W) → CatalogueExpansionTest → MenuCatalogueAndConfigTest (W) → MenuDisableGuardTest (W) → MigrationTest → GuestSessionTest (victim, pos. 7) → GuestHomeScreenTest → FirstRunSeedTest`: **48 run · 0 failures**. **Seed `20260827`** — a genuinely different order (`ActionCardTest (W) → GuestSessionTest (pos. 2) → FirstRunSeedTest → MenuCatalogueAndConfigTest → MigrationTest → AuditCoverageTest → GuestHomeScreenTest → CatalogueExpansionTest → MenuDisableGuardTest`): **48 run · 0 failures**. Two different orders from two seeds is itself the proof the orderer is not inert. |
| 🟡 4 | `V10__catalogue_expansion.sql`'s *"AND NOTHING ELSE"* is reworded to the property that is actually greppable — no fixture **key or label** outside `V10`, `DevDataSeeder`, `MenuFixtures` — plus the five catalogue-count assertions across four test classes that an add/remove also touches. T-21's grep step carries the same correction. | Re-grepped the count sites: `CatalogueExpansionTest:81` and `:160`, `MenuCatalogueAndConfigTest:59`, `FirstRunSeedTest:59`, `DevDataSeederTest:82` — five, exactly as the header now claims. |
| 🟡 5 | `property-content.service.ts`'s class doc now reads *"`settingsConfig()`/`property()`/`locale()` throw when nothing is loaded"* — `locale` calls `requireConfig(this.raw())` at line 81. | Comment only; covered by the 256/256 run. |
| 🟡 6 | No action, as ruled. `%dev` is still not runnable end-to-end until Phase 4 — see the last item under *What must happen next*. | — |

**Also corrected, per the reviewer's ruling on deviation 2:** `03-todo.md` T-03's
`FirstRunSeedTest` bullet and `02-design.md` §9's `FirstRunSeedTest` row now say
the five-key list is **narrowed to `key[0..4]`, never widened to thirteen** —
enumerating the fixture keys is what REQ-903 forbids and what T-21 greps for.
`hasSize(13)` + `key[0..4]` as implemented stands. T-20 additionally absorbs
finding 7's wording fix (the `.sha256` input hash is expected to change).

**Not touched, deliberately:** `pom.xml`, `package.json`, any nested `CLAUDE.md`,
`docs/constitution.md`, `docs/handoff.md`, and the Part B/C planning documents
(`docs/features/guest-payload-consolidation/**`, `docs/features/module-content/**`,
`docs/features/index.md`).

## Review result — 06_review, 2026-08-27 (`reviewer`)

**No defect was found in shipped behaviour.** All three 🟠 are *evidence*
problems, and all three must be settled before the single commit:

| # | Sev | What |
|---|---|---|
| 1 | 🟠 | **REQ-901's acceptance criterion is unmet — `maxItems: 50` is enforced nowhere.** Response DTOs are not runtime-validated (the generated `@Size(max=50)` is inert), and T-08 removed the only kiosk-side check. Design §2 says *"the enforcement that matters is the kiosk's, at the boundary (§5)"* and §5 then deletes it; §5's reason (*"trusted for count the same way `actionCards` is"*) is factually wrong — `MAX_CARDS = 4` still rejects. **Owner decision:** restore the guard at 50 (~5 lines, makes the requirement true) **or** strike the criterion and fix design §2/§5. Leaving both as they are is the one option that is not acceptable. |
| 2 | 🟠 | **`DevDataSeederTest.reseedingIsAFullNoOp`'s new assertion cannot fail for the reason its message states.** It snapshots `enabled = true` *after* the first seed already set all eight true; the `UPDATE` is idempotent, so the count is unchanged either way. The "inside the `devDeviceExists()` early return" half of REQ-906 is therefore **unverified** — and this test was offered in place of T-05's manual `quarkus:dev` run. The behaviour is correct (verified by reading `seedIfMissing()`); the test is not. Fix: turn one row back **off** before re-seeding, assert it stayed off (key-free via `min(id)`). |
| 3 | 🟠 | **T-03's prescribed isolation proof is inert.** `-Dsurefire.runOrder=reversealphabetical` produced a **byte-identical 76-class order** (measured — `diff` of both runs' class sequences is empty); Quarkus' profile-aware orderer overrides Surefire's. *"Reverse class order: 0 failures"* was therefore not evidence. **The fix itself is correct** — proved with `-Djunit.jupiter.testclass.order.default='org.junit.jupiter.api.ClassOrderer$Random'`. Correct T-03's and T-20's validation lines, or every future feature inherits a check that always passes. |

🟡: the migration header's *"and nothing else"* removal claim omits four
catalogue-count assertions; `PropertyContentService`'s class doc does not list
`locale()` among the throwing signals; and **this cut is not runnable in `%dev`**
— 13 menu items against a router that still knows four section paths (Phase 4
fixes it; `%test`/`%prod` unaffected because D5 keeps the fixtures disabled).

🟢: T-01's *"zero diff"* wording is at fault, not the outcome; the tree also
carries Part B/C work, so stage explicitly at T-21 rather than `git commit -a`.

### Rulings on the five declared deviations

1. **Four tests changed, not two — accepted.** Both extras are right, and dev's
   *"no guest-facing assertion changed"* is **TRUE, verified line by line**: all
   four changed assertions hit `/api/manager/**` or `flyway_schema_history`.
2. **`FirstRunSeedTest` — dev is RIGHT, the plan is wrong.** `hasSize(13)` +
   `key[0..4]` is correct. Listing thirteen keys would have put eight fixture
   keys in a test file — exactly what REQ-903 forbids and T-21 greps for, in the
   same feature. **`03-todo.md` T-03 and design §9 need correcting**; exact
   proposed wording is in `06-review.md` (not written — `dev` or the owner
   applies it).
3. Two sub-steps — accepted, no comment.
4. Test instead of `quarkus:dev` — **accepted in part.** The positive half is
   better pinned than the plan asked. The early-return half is not — see 🟠 2.
5. The `.sha256` file — **the TODO's wording is at fault.** No generated source
   moved; verified independently.

### Measured independently (not taken from the report below)

- **Backend `./mvnw verify`: 436 run · 0 failures · 2 errors · 2 skipped.**
- **Backend, reverse flag: 436 · 0 failures · 2 errors** — and a **byte-identical
  class order**, which is finding 3.
- **The 2 errors are environmental — confirmed, with evidence.** Testcontainers
  container-startup timeouts thrown at `QuarkusTestExtension`
  `interceptTestClassConstructor` (`0.006 s` elapsed), only ever on
  `@TestProfile` classes, and they **move**: four observations have now hit four
  different classes (`TrustedProxyLoginTest`, `UntrustedPeerAttributionTest`,
  `DevDataSeederTest`, `OpenApiDisabledTest`). Both classes that errored pass
  **2/2 in isolation, BUILD SUCCESS**. The box: aarch64 Pi, 4 cores, 3796 MB RAM
  with 1.6 GB already swapped, six unrelated containers, Quarkus boot **155.8 s**,
  an observed *successful* container start **23.8 s**. Not a blocker.
- **Test isolation proved another way.** `ClassOrderer$Random` seed `7` put all
  four whole-set writers ahead of `GuestSessionTest`
  (`AuditCoverageTest → ActionCardTest → CatalogueExpansionTest →
  MenuCatalogueAndConfigTest → MenuDisableGuardTest → MigrationTest →
  GuestSessionTest → …`): **48 run · 0 failures · BUILD SUCCESS**, with
  `hasSize(5)`/`hasSize(4)` intact. Seed `424242` gave a second adverse order,
  also green.
- **D5 verified by resolution:** `CatalogueExpansionTest` 5/5 in all five backend
  runs; `%dev` delivers 13, `%test` delivers 5.
- **REQ-903 grep clean:** all 8 keys and all 16 labels across the whole tree —
  code hits only in the three sanctioned files.
- **REQ-904 verified at the data level:** all 8 icons resolve in the installed
  `@ng-icons/phosphor-icons` 35.0.1, all SVGs distinct, none equal to
  `phosphorCircle`.
- **Frontend: `ng test` 255/255, 32 files. `ng build` clean.**
- **Tree is whole after the `git stash`/`pop`:** `git stash list` empty; the
  dangling stash commit has two parents (no untracked file was ever stashed);
  all 19 tracked files it carried are byte-identical to the tree today except
  `docs/features/index.md`, which the concurrent `planner` is editing.

### Not owed, and why — the visual check

`code-review` step 0 requires a rendered screenshot for any change touching a
`docs/design/**`-backed component. **This cut contains no UI.** No sidebar,
popover, route or template changed; the eight catalogue rows arrive
property-disabled in `%test`/`%prod`; `registered-icons.ts` adds map entries
nothing yet renders. The screenshot obligation attaches to T-15/T-16 and is
discharged by T-19 — recorded so this is not read as a precedent for passing a UI
change without looking at it.

---

## What `dev` reported last session (05_implement) — kept for reference

## What landed

| Task | Change |
|---|---|
| T-01 | `api/openapi.yaml` — `GuestMenu.items.maxItems: 50`. Generated `GuestMenu` carries `@Size(max=50)`; `packages/api-client/` shows only its input-hash file, and regeneration is idempotent. |
| T-02 | `V10__catalogue_expansion.sql` — 8 definitions (`default_sort_index` 5…12, `introduced_release '1.1.0'`), 16 `de`/`en` translations, 8 property-scope `enabled = false` rows. Header states disposability, the one-migration-plus-one-icon-file removal path, and why statement 3 exists. |
| T-03 | `MenuFixtures.restoreCatalogueOptOut()` (test scope, `DeviceFixtures` precedent) + `@AfterEach` in the five writer classes; the two planned assertion amendments **plus two unplanned ones** (see Deviations). |
| T-04 | `CatalogueExpansionTest` — 5 cases, `@BeforeEach` arranges the D5 state. **Names no fixture key**: the key under test is derived from the catalogue. |
| T-05 | `DevDataSeeder.enableCatalogueFixtures()` inside the `devDeviceExists()` early return + `CATALOGUE_FIXTURE_KEYS`. `DevDataSeederTest` now proves REQ-906 against a real dev-profile database. |
| T-06 | Verification only. No Control added; five architecture guards green **and unedited**; `AuditCoverageTest` green with its assertions and `NOT_A_MANAGER_MUTATION` list untouched. |
| T-07 | Eight glyphs registered (`phosphorWifiHigh`, `Clock`, `Recycle`, `FirstAid`, `Car`, `ForkKnife`, `PersonSimpleHike`, `DotsThree`), all verified present in the installed `@ng-icons/phosphor-icons/regular`. |
| T-08 | `MAX_MENU_ITEMS`, its check and its false attribution deleted; `property.model.ts` comments corrected. `grep -r "0–5" frontend/src` is clean. |
| T-09 | `PropertyContentService.locale` — one `computed`, three spec cases. The two inline lookups deliberately not refactored (recorded follow-up). |

## Test results actually observed

- **Backend, final full `./mvnw verify`: 436 run, 0 failures, 1 error.** The
  error is a Testcontainers *container startup timeout*
  (`UntrustedPeerAttributionTest`, a `@TestProfile` class that needs its own
  Postgres); it passes **2/2 in isolation**. This box flakes this way on
  `@TestProfile` classes under load — the **pre-change baseline run flaked
  identically** (`TrustedProxyLoginTest`, also green in isolation).
- ~~**Reverse class order** (`-Dsurefire.runOrder=reversealphabetical`):
  **0 failures.**~~ **Withdrawn** — that flag is inert here (review 🟠 3): Quarkus'
  profile-aware orderer overrides Surefire's and the class order came out
  byte-identical, so the run proved nothing. The property *does* hold; it is
  proved with `ClassOrderer$Random` and a recorded seed — see the fix round at
  the top of this file.
- 420 → 436 is fully accounted for: `CatalogueExpansionTest` +5, plus 11 tests
  the *baseline* run under-counted because two classes reported a boot error
  instead of their methods. **No existing test was lost.**
- **Frontend: `ng test` 255/255, 32 files** (baseline 251; +1 T-08, +3 T-09).
  `ng build` clean.

## Deviations from the plan — read these before reviewing

1. **The plan said "exactly two existing tests change". It is four.** The two
   extra are real invalidations the plan missed, both manager-scope, **no
   guest-facing assertion changed anywhere**:
   - `MigrationTest.flywayHeadIsV9UpdateChannelTrustedKeys` pins the Flyway
     head as a **literal** so a migration cannot be added silently. Renamed to
     `flywayHeadIsV10CatalogueExpansion`, head `"9"` → `"10"`. Updating it is
     the conscious step the pin exists to force. Nothing else in the class
     changed; what V10 inserts is asserted in `CatalogueExpansionTest`, not
     here (shared DB, no `@AfterEach`).
   - `MenuDisableGuardTest` twice asserted `GET /api/manager/menu-config`
     returns `hasSize(0)` to mean *"the rejected PUT changed nothing"*. The
     property scope now legitimately holds D5's eight opt-out rows. Changed to
     `findAll { it.menuItemKey == '<key under test>' }` → `hasSize(0)`, which
     states the same atomicity, names the key actually under test, and depends
     on no fixture key.
2. **`FirstRunSeedTest` step 3 does not list the eight fixture keys.** The
   plan's wording ("its exact five-key list → the 13-row catalogue") reads as
   *list all thirteen*, which would put eight fixture keys in a test file and
   break REQ-903's disposability — the thing T-21 greps for. It asserts
   `hasSize(13)` plus `key[0..4]` = the five real sections instead.
3. **T-03 was executed as two sub-steps** (fixture + the two catalogue-count
   amendments; then the four remaining `@AfterEach`), to stay inside the
   5-file-per-step cap. Same seven files, same result.
4. **T-05 validated by `DevDataSeederTest`, not by a manual `quarkus:dev`.**
   That class already boots a real dev-profile app against its own database,
   so REQ-906 is now a *test* rather than a manual observation, and its
   `reseedingIsAFullNoOp` case gained an assertion that the enablement sits
   inside the `devDeviceExists()` early return.
5. **T-01's "zero diff under `packages/api-client/`" is one file off.**
   `.openapi-generator/openapi.yaml-generate-ts-client.sha256` necessarily
   changes when the contract changes — it is the input hash. No generated
   *code* changed, and a second run is byte-identical. Expect that one line in
   the commit.

## Verified, so Phase 4+ need not re-derive it

- **REQ-903 disposability grep is clean today.** No fixture key appears in any
  `.ts`/`.html`/`.css`/`.java`/`.sql`/`.mjs` outside
  `V10__catalogue_expansion.sql`, `DevDataSeeder` and `MenuFixtures`. T-18 adds
  `measure.mjs`'s route list as the fourth sanctioned site; nothing else may.
- **`restoreCatalogueOptOut()` is `ON CONFLICT DO NOTHING`** — by design, so it
  never rewrites a row a test left deliberately. Consequence a future writer
  must know: it **cannot flip an `enabled = true` row back**. A test that
  enables a fixture must reset that row itself (`CatalogueExpansionTest`'s
  `disableAtPropertyScope` does).
- `%dev` delivers 13 menu items; `%test` delivers 5. The split is
  `@IfBuildProfile("dev")` at augmentation, and `CatalogueExpansionTest`'s
  five-key case is the proof.
- Sidebar consequence of the fixture set is as the PRD predicted: `home`,
  `info`, `umgebung`, `smart-home` + the overflow entry, with **`services`
  dropping into the popover** — so REQ-932 is live, not hypothetical.

## What must happen next

1. ~~Fix round on 🟠 1, 🟠 2, 🟠 3, 🟡 4, 🟡 5 and the deviation-2 plan
   correction.~~ **Done 2026-08-27** — see the fix-round table at the top. The
   lead commits; nothing is staged.
2. Phase 4 (T-10 → T-12: i18n keys, the parameterised route table,
   `SectionPlaceholderComponent`), Phase 5, Phase 6.
3. `T-12` needs `routeMatches()` from `menu-split.ts` (T-13) — land T-13 first
   or add the helper with T-12.
4. **One commit at the end (T-21); the user commits.** Nothing is staged. Stage
   **explicitly** — the tree also carries Part B/C planning work
   (`docs/design/screens-inhouse/connection.md`, the `modular-menu-catalogue`
   retirement, `docs/handoff.md`, `docs/features/index.md`).
5. **Until Phase 4 lands, `%dev` is mid-refactor** — 13 menu items against a
   router that knows four section paths. `%test`/`%prod` are unaffected.

## Still true from the planning handoff

- `n = 5` lives only in `SIDEBAR_SLOTS`; the popover has no capacity limit.
- `splitMenu` is five lines satisfying six requirements — do not branch it.
- REQ-932 is resolved by promotion ⇒ a popover tile is never active ⇒ no active
  variant in the panel.
- `CdkMenu` is 1-D and stays that way (`role="menu"`'s contract).
- The token is `--size-touch-min` (2.75 rem); `--size-touch-target` does not exist.
- Stop-and-report triggers: a `GuestModule` union or module registry (Part C);
  `guest-data.service.ts`, the two guest fetches or the ETag (Part B); a new
  dependency, a nested `CLAUDE.md` entry or a constitution amendment (propose
  exact wording and end the turn).
