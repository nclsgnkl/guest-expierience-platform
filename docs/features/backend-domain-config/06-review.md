# Review: Backend Domain & Configuration
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** PASS ✅ (round 2, 2026-08-19) — round 1's 🟠 fixed, 🟡 resolved by
user decision + implemented; final counts **🔴 0 · 🟠 0 (1 fixed) · 🟡 0
(1 resolved) · 🟢 7 (tracked, non-blocking)**. See "Round 2" at the end.
**Reviewed:** 2026-08-19 (reviewer), against constitution **v1.5.1**, `backend/CLAUDE.md`
(incl. the three entries approved 2026-08-19), spec §4/§5/§6/§9, and the
`code-review` skill checklist.

> Step 0 (visual check) skipped explicitly: backend-only change — no rendered
> surface, `frontend/` and `docs/design/**` untouched (verified via `git status`).

## What was verified independently vs. taken from the handoff

**Re-executed by the reviewer (independent):**

- **Full suite** against a fresh throwaway Postgres 17: **118 run / 118
  passed / 0 failed / 0 skipped, BUILD SUCCESS.** Note: two prior attempts on
  this hardware (Raspberry Pi) failed at a Quarkus test-profile restart
  boundary with an Agroal *acquisition timeout* (default 5 s) while Postgres
  sat at 6 connections — an environment limit, not app code; raising
  `-Dquarkus.datasource.jdbc.acquisition-timeout=60` for the run made all 118
  green. See 🟢 #7.
- **Log sweep:** zero `Logger`/`System.out`/`System.err`/`printStackTrace` in
  `src/main/java`; test-run output has **0** SQL statements, **0** bind
  values, **0** occurrences of the test admin password. The only two `ERROR`
  strings are Hibernate WARN lines from tests that *deliberately* provoke DB
  constraint rejections (unique-scope, property `CHECK (id=1)`) — no values leaked.
- **Allow-list:** `dependency:tree` (294 lines) — **0 hits** for
  elytron / smallrye-jwt / lombok / mapstruct / testcontainers /
  jackson-databind-nullable. `backend/app/pom.xml` carries exactly the
  constitution's allow-list; the only generator change is the config flag
  `<dateLibrary>java8</dateLibrary>` (config, not a dependency — allowed).
- **Client determinism:** `generate-sources` twice; sha256 over all 46 files
  in `packages/api-client/` — **byte-identical** with the checked-in state and
  across both regenerations (also proves no hand edits).
- **Contract:** parsed `api/openapi.yaml` — all 28 manager operations except
  `managerLogin` carry `bearerAuth`; no property `POST`/`DELETE`; no catalogue
  write op; tags are one `manager-*` tag per BCE package (approved convention);
  stays list carries the `roomId` filter.
- **Seed fidelity:** V2's catalogue seed compared line-by-line against
  `frontend/src/app/core/content/example-property.data.ts` — keys, routes,
  icons, `de`/`en` labels verbatim; `einstellungen` excluded; language/
  appearance options match the shipped frontend config.
- **BCE strictness:** grep of all cross-package imports — the only
  cross-package types used are public Controls, exactly the design's injection
  graph. Every Entity/Repository/Mapper is package-private (verified for all
  seven packages); Boundaries are one delegating line each (all six read).
- **Code reading:** every file in `de.gxp.{auth,text,property,room,menu,stay,
  asset,api}` (main) plus V2/V3, `application.properties`, and the key test
  classes.

**Taken from the handoff (lead-run 2026-08-19, not re-executed — cost):**

- The prod-profile **compose smoke**: ready 200, `/q/openapi` + `/q/swagger-ui`
  404 in `%prod`, unauth 401, login + rooms CRUD 200, 0 error lines, 0 password
  occurrences in the app log. (Image rebuild on this hardware is expensive; the
  results are from today and internally consistent with everything re-verified.)

## Named checks (from the handoff)

| # | Check | Result |
|---|---|---|
| 1 | Invariant-3 matrix in `MenuDisableGuardTest` vs. design algorithm | ✅ All five design cases covered: property-disable vs. property cards (409, atomic rollback proven), property-disable allowed when only an overriding room re-enables + own cards, room-disable vs. inherited property cards (409 naming `room:<id>`), every offender listed, property-disable checked against every room scope. Because the seed room exists, the `hasSize(1)`/`hasSize(2)` assertions also pin the **dedup** (details per card, not per scope — first-scope-wins keeps `details[0].scope`) |
| 2 | Auth lifecycle end to end | ✅ Seeder (V3 has no seed row; refuses start on unset/blank naming only the variable; ignores the var once a row exists), PBKDF2 (`PBKDF2WithHmacSHA256`, 600 000 iter default, 16-byte salt, 256-bit key, self-describing format, `MessageDigest.isEqual` constant-time, malformed stored value → false), `TokenStore` (32-byte `SecureRandom` → base64url no padding, SHA-256 hex only, atomic `computeIfPresent` slide, lazy purge), lockout (5 fails → 30 s, 429 + `Retry-After`, own `@TestProfile`), logout (drops exactly the presented token via the identity's hash attribute), password change (verifies current, ≥ 8 policy, `clear()` drops **all** tokens incl. caller's — two-session test proves it, restores state), mechanism/provider fail closed (no/garbage token → anonymous → deny-unannotated + `@RolesAllowed` → 401 `WWW-Authenticate: Bearer`), `TokenExpiryResponseFilter` takes login expiry from the body (login is `@PermitAll`, no identity) and the slid expiry from the identity attribute otherwise |
| 3 | `TemplateControl` `@PermitAll` reading | ✅ Confirmed as the right reading: stateless pure functions, no Boundary/HTTP surface, no secrets — and forward-correct, since the guest API (device role) must call `resolve()` next feature; `@RolesAllowed("admin")` would be wrong there. Keeps `SecurityAnnotationAuditTest` exactly-one (the audit walks compiled `*Control` classes in the deployment archive — main classes only, so the test-only `FailClosedControl` is out of scope by construction) |
| 4 | Log sweep | ✅ Re-run, zero hits (see above); committed config unchanged from the foundation's posture (`log.sql=false`, `bind-parameters=false`, access log off) |
| 5 | `dependency:tree` + pom | ✅ Re-run, 0 hits; no new dependency, `dateLibrary` is config |
| 6 | Byte-identical client regeneration | ✅ Re-run, 46 files byte-identical twice |
| 7 | BCE strictness of the seven packages | ✅ (see above). The two native-query deviations (`AssetRepository.findReferrers`, `RoomRepository.staysOf`) judged **acceptable as recorded**: both are parameterized (no injection), both exist to avoid a circular Control injection, both are backed by `ON DELETE RESTRICT` FKs, and entities still never leave their package — the queries read foreign *tables*, not foreign *entities* |
| 8 | Thrown-200 in `AssetControl.binary()` + cap | ✅ The 10 MiB cap is checked on `File.length()` **before** any read; 304 short-circuits before touching the payload; the thrown 200 carries strong ETag + `Cache-Control: private, max-age=3600` and reuses the pinned thrown-outcome channel; `getAssetBinary` carries **no** `@ResponseStatus`, so `ResponseStatusFilter` cannot touch it (see 🟢 #1 for the latent interaction) |
| 9 | `MismatchedInputMapper` priority | ✅ Resolved empirically: `ApiErrorMappersTest.beanValidationFailureReturnsTheApiErrorShape` sends `{}` (missing required creator property) and asserts `code == "validation-failed"` — the built-in Quarkus mapper would answer a different body, so the green test **is** the proof the exact-type mapper wins on 3.33.3.1. No `@Priority` needed |
| 10 | All 9 recorded deviations | ✅ Each judged against PRD/design/constitution — see table below |

### Deviation judgments (all 9 accepted)

| # | Deviation | Judgment |
|---|---|---|
| 1 | One tag per BCE package | ✅ Matches the user-approved `backend/CLAUDE.md` entry; keeps Boundaries thin |
| 2 | Binary GET via thrown `Response` | ✅ Only channel that carries ETag/304 under `returnResponse=false`; consistent with the pinned thrown-outcome pattern |
| 3 | `@ResponseStatus(201)` on the three create Boundaries | ✅ User-approved convention; `ResponseStatusFilter` is sound (rewrites only a default 200, resolves the method against the actual resource class) |
| 4 | `<dateLibrary>java8</dateLibrary>` | ✅ Config flag, explicitly allowed by T-04's note |
| 5 | Native queries on foreign tables | ✅ See named check 7 |
| 6 | Test-profile tuning knobs | ✅ Behavior-neutral (self-describing hash format), production defaults are the pinned values, `%test`-only overrides |
| 7 | `de.gxp.api.ApiErrors` | ✅ Good infrastructure — but the auth package doesn't use it, which is 🟠 #1 |
| 8 | Neutral slate seed colors | ✅ Principle 1 clean; flagged to `manager-frontend` |
| 9 | `GXP-Token-Expires` from the login body | ✅ Correct — no identity attribute exists on a `@PermitAll` request |

## Findings

| # | Severity | Category | File | Finding | Fix |
|---|----------|----------|------|---------|-----|
| 1 | 🟠 | Conventions | `backend/app/src/main/java/de/gxp/auth/AuthControl.java:88-102` | `AuthControl` builds its thrown non-2xx outcomes **ad-hoc** (`genericUnauthorized()`, `badRequest()`) — contradicting the `backend/CLAUDE.md` entry approved 2026-08-19: *"Controls build thrown non-2xx outcomes via `de.gxp.api.ApiErrors`, never ad-hoc `Response` construction."* The private `badRequest` is a byte-for-byte duplicate of `ApiErrors.badRequest`. (`LoginRateLimiter`'s 429 also builds ad-hoc, but it is package-private auth infrastructure, not a Control, and needs a `Retry-After` header `ApiErrors` cannot express — lesser concern, align or comment.) | Mechanical: use `ApiErrors.badRequest(...)` in `changePassword`; add a one-line `ApiErrors.unauthorized(code, message)` and use it in `login`; delete the two private builders. Optionally add a header-capable variant for the 429. No behavior change — the wire shape is already identical, so no test moves |
| 2 | 🟡 | Correctness (design-level gap) | `backend/app/src/main/java/de/gxp/menu/MenuControl.java:127` (`putCards`) | A **property-scope card PUT is validated against the property scope only** (the design's pinned "symmetric check … for scope S"). Sequence that strands a card: room *r* disables item *k* (passes invariant 3 — no card targets *k* yet) → property-level card PUT adds a card targeting *k* (passes invariant 2 — *k* is enabled at property scope) → room *r* has no own cards, inherits the property set → dead tile in *r*. Faithful to spec §5 invariant 2's letter, but it defeats the PRD user story "not ship a dead tile to a kiosk". Not fixed in-review — changing it changes pinned design behavior | Either extend invariant-2 validation on a property-scope card PUT to all inheriting room scopes (reuses `collectStrandedCards`, mirrors invariant 3), or record the gap as a known limitation for `device-pairing-and-guest-api` (the guest API could skip cards with disabled targets defensively) + `manager-frontend`. Owner's call; recorded in 02-design.md's addendum |
| 3 | 🟢 | Correctness (latent) | `de/gxp/api/ResponseStatusFilter.java:44` | The filter rewrites *any* 200 from an annotated method — including a thrown 200 `Response`, which its javadoc claims stay untouched. No annotated method throws a 200 today (`getAssetBinary` carries no annotation), so this is latent only | If a create ever needs a thrown 200/`Response`, revisit; or note it in the javadoc |
| 4 | 🟢 | Correctness (race) | `de/gxp/room/RoomControl.java:46,66` | Room-code uniqueness is check-then-insert; two concurrent creates with the same code would surface as a 500 from the DB `UNIQUE` instead of 409. One-admin instance, DB integrity holds | Acceptable; catch the constraint violation → 409 if it ever matters |
| 5 | 🟢 | Security (accepted) | `de/gxp/auth/AuthControl.java:76` | `PUT /api/manager/password` verifies `currentPassword` **outside** the login lockout — a stolen-token holder can guess the current password without the 429 choke. Mitigations: requires a live token (10-min idle), and every guess costs a full 600 k-iteration PBKDF2 verify | Note for the per-user auth swap (spec decision 4); optionally route failures through `LoginRateLimiter` |
| 6 | 🟢 | Conventions (accepted) | `de/gxp/asset/AssetControl.java:50` | A Control injects JAX-RS `HttpHeaders` (HTTP concern) — forced by the generated `uploadAsset(kind, File)` signature carrying no content-type parameter, and the one-line-Boundary rule forbidding the Boundary from reading it. Recorded so the pattern does not spread beyond this forced case | None needed |
| 7 | 🟢 | Correctness (nit) | `de/gxp/property/PropertyControl.java:106` | `ZoneId.of` accepts non-IANA forms (`UTC+5`, `Z`, offsets), so "IANA-validated" (REQ-303) is slightly looser than worded; every accepted value still formats correctly in the resolver | Accept, or restrict to `ZoneId.getAvailableZoneIds()` later |
| 8 | 🟢 | Correctness (quality) | `de/gxp/asset/ImageProcessor.java:69` | EXIF orientation is dropped on the downscale re-encode: a > 2560 px phone JPEG with orientation metadata is stored rotated. Within-cap uploads are stored byte-exact and unaffected | Note for `manager-frontend` (its preview will surface it); ImageIO cannot read EXIF without a new dependency — any fix is a JDK-only transform on the orientation tag, later |
| 9 | 🟢 | Environment (info) | test runs on slow hardware | Two suite runs on this Pi failed at a `@TestProfile` restart boundary with Agroal's default 5 s acquisition timeout (Postgres was healthy, 6 connections). `-Dquarkus.datasource.jdbc.acquisition-timeout=60` → 118/118. Nothing committed configures pools; purely a test-runner environment note | Optionally mention the flag in `backend/README.md`'s test section |

**Counts: 🔴 0 · 🟠 1 · 🟡 1 · 🟢 7** — nothing fixed in-review (the single 🟠 is
above the in-review fix threshold; both actionable findings are recorded for dev).

## REQ coverage (REQ-301…REQ-336) — 36/36 implemented, 0 orphans

| REQ | Verified by | ✓ |
|---|---|---|
| 301 | Contract parsed (bearerAuth on all but login), both generators green, client byte-identical + committed, `frontend/` untouched (git status) | ✅ |
| 302 | V2/V3 versioned SQL, `MigrationTest` (V1 baseline + V2/V3 applied, `instance_info` untouched), `database.generation=none` unchanged | ✅ |
| 303 | V2 `CHECK (id=1)` + `secondPropertyRowIsRejectedByTheDatabase`; no create/delete op in contract; `invalidTimezoneIs400`, `malformedHexColorIs400` | ✅ |
| 304 | Option tables + `defaultLanguageOutsideTheOfferedSetIs400`, `unsupportedOfferedLanguageIs400` (contract enum + DB CHECK + Control) | ✅ |
| 305 | `room` DDL + RoomResourceTest (`emptyCodeIs400`, `duplicateCodeIs409…`, artwork-kind + offered-set checks) | ✅ |
| 306 | Seed compared verbatim against frontend data (reviewer); `catalogueEqualsTheFiveFrontendTuples`; no write op; `einstellungen` out | ✅ |
| 307 | `UNIQUE NULLS NOT DISTINCT` + `duplicateNullScopeRowIsRejectedByTheDatabase`; per-field resolution in `MenuControl.enabled()` + `labelOnlyRoomRowLeavesEnabledAndSortInherited` | ✅ |
| 308 | No unique on target key (DDL comment + `theShippedFrontendCardSetSaves…` with two cards → `info`); wholesale replace + `aRoomsOwnSetReplacesWholesaleAndCanBeCleared` | ✅ |
| 309 | `ck_stay_range` + `checkInMustBeBeforeCheckOut`; status CHECK; derived-active by construction | ✅ |
| 310 | `asset` DDL (mime CHECK, sha256, BYTEA, kind CHECK) | ✅ |
| 311 | Four typed translation tables, language CHECKs, seeded `de`+`en` catalogue labels | ✅ |
| 312 | DB `CHECK 0..3` + per-scope unique; `aFifthCardIs400`, `duplicateSortIndexIs400`, `invalid-sort-index` in Control | ✅ |
| 313 | `unknownTargetIs400NamingTheKey`, `cardTargetingAnItemDisabledInItsScopeIs409` (item + scope in details) | ✅ |
| 314 | `MenuDisableGuardTest` full matrix (named check 1), applied-then-validated in one transaction, rollback proven | ✅ |
| 315 | Room-row `FOR UPDATE` before range check; `overlapIs409AdjacentPassesCancelledNeverBlocks`, `concurrentOverlappingCreatesCannotBothSucceed`, `updateExcludesItselfFromTheOverlapCheck` | ✅ |
| 316 | Cap-before-read 413, content-vs-declared 400, undecodable 400, downscale + sha-over-stored, ETag/304, payload-free projection (`listReturnsMetadataThroughThePayloadFreeProjection`) | ✅ |
| 317 | `findInvalidPlaceholders` + per-field allowed sets; `unknownPlaceholderInAGreetingTemplateIs400NamingIt`, `stayPlaceholderInAStandbyTemplateIs400`, card-template validation in `putCards` | ✅ |
| 318 | `TemplateControl.resolve` + 10 unit tests (locale/timezone pinned, green on the target JDK); manager reads return raw (`seededNeutralPropertyIsReturnedWithRawTemplates`) | ✅ |
| 319 | `GET`/`PUT /api/manager/property` only; full-replace roundtrip test | ✅ |
| 320 | Rooms CRUD tests; `deletingARoomWithStaysIs409NamingThem`; `deletingARoomCascadesItsOwnConfigRows` | ✅ |
| 321 | Catalogue GET + menu-config GET/PUT both scopes, atomic replace + inherit tests | ✅ |
| 322 | Action-cards GET/PUT both scopes, whole-set 0–4, invariants atomically | ✅ |
| 323 | Stays CRUD + `roomId` filter (`crudRoundtripAndRoomFilter`) | ✅ |
| 324 | Assets list/upload/binary/delete; `AssetDeleteGuardTest` names property + room referrers | ✅ |
| 325 | Generated bean validation + `ConstraintViolationMapper`/`MismatchedInputMapper`/`JsonParseMapper`/`WrappedJacksonExceptionMapper`; `ApiErrorMappersTest` | ✅ |
| 326 | Login-only password; 256-bit token; generic 401 (constant-shape, PBKDF2-dominated timing); lockout 429 + `Retry-After` (`LoginLockoutTest`) | ✅ |
| 327 | In-memory SHA-256-hashed store, 600 s slide, header on every authenticated response, `expiryHeaderSlidesForwardAcrossRequests`, `storeNeverContainsTheRawToken` | ✅ |
| 328 | `logoutKillsExactlyThePresentedToken` (204, server-side drop) | ✅ |
| 329 | JDK PBKDF2, pinned params, self-describing format, 6 hasher unit tests, no Elytron (dependency:tree re-run) | ✅ |
| 330 | Seeder tests: unset/blank + no row → refuse naming the variable only; row present → var ignored; hash verifiable, never plaintext | ✅ |
| 331 | Two-session drop test incl. caller's token, old password dead, new logs in | ✅ |
| 332 | `SecurityAnnotationAuditTest` (exactly-one on every public Control method), `FailClosedTest` revised (401 + `WWW-Authenticate: Bearer`), `ManagerSurfaceDeniedTest` (one endpoint per Resource + logout) | ✅ |
| 333 | Log sweep re-run (zero hits, zero SQL/binds/password in test logs); generic auth errors; seeder message names the variable only | ✅ |
| 334 | `aFreshInstanceIsCompletelyUsable` + compose smoke (lead, taken from handoff) | ✅ |
| 335 | 118 tests / 22 classes, re-run green by the reviewer; every acceptance criterion has a named test | ✅ |
| 336 | `backend/README.md` (auth workflow, first password + rotation, asset limits, one-`pg_dump` backup), `docs/system-context.md` (new rows, hand-off notes incl. `einstellungen`), `docs/features/index.md` | ✅ |

**Orphan check:** no unimplemented REQ; no implemented behavior without a REQ —
the three fix-round additions trace to REQ-301/325/314, `OpenApiDisabledTest`
traces to the user's recorded `/q/openapi` `%prod` decision (closes
backend-foundation review 🟡 #1).

## Spec drift

The nine deviations + the three fix-round infrastructure pieces are now
recorded in [02-design.md](02-design.md) → "Implementation deviations"
addendum (added by this review), so the design stays the source of truth.
🟡 #2 is recorded there as an open design question.

## Top 3 recommendations

1. **Fix 🟠 #1** — route `AuthControl`'s 400/401 through `de.gxp.api.ApiErrors`
   (add `unauthorized(code, message)`); delete the duplicated private builder.
   Five-minute change, no test moves. Then this review is a PASS.
2. **Decide 🟡 #2** — either extend the property-scope card PUT check to
   inheriting rooms (small, reuses `collectStrandedCards`) or explicitly accept
   the gap and let the guest API skip disabled-target cards defensively.
3. Carry 🟢 #5 (password-change lockout) and 🟢 #8 (EXIF orientation) as
   hand-off notes into the per-user-auth swap and `manager-frontend`
   respectively — both are one-line notes, not code today.

## Escalation check

No `architect` escalation needed: the auth exposure model is exactly the one
the constitution records (v1.5.x), the two security-relevant findings (🟢 #5,
🟡 #2) don't alter what an unauthenticated device on the LAN can reach, and no
new architectural pattern beyond the user-approved conventions was introduced.

---

# Round 2 (2026-08-19) — re-check of the two open items → PASS ✅

## Round-1 🟠 #1 fix — VERIFIED ✅

`AuthControl` now throws via `de.gxp.api.ApiErrors` exclusively: the login 401
uses the new `ApiErrors.unauthorized(code, message)` (house style, sits between
`badRequest` and `notFound`, delegates to the same `withStatus` builder), both
`changePassword` 400s use `ApiErrors.badRequest`; the private ad-hoc builders
and their dead imports are gone (grep for `Response.status` / `new
WebApplicationException` in `*Control.java`: only `AssetControl.binary()`'s
thrown 200/304 remains — ratified deviation 2; a 304 must not carry an
`ApiError` body, so it structurally cannot go through `ApiErrors`).
**No wire-shape change** (same codes/messages/JSON), zero test edits — the
`backend/CLAUDE.md` convention holds again. `LoginRateLimiter`'s 429 stays
ad-hoc: it is package-private auth infrastructure (not a Control, outside the
convention's letter) and needs a `Retry-After` header `ApiErrors` cannot
express — accepted.

## Round-2 🟡 #2 resolution — VERIFIED FAITHFUL ✅

The user superseded both proposed options with a hierarchy principle (*the
property is the global ceiling — a room can only offer what the property
offers*), amended in 02-design.md ("RESOLVED by user decision (2026-08-19)" +
the amended `enabled()` definition in the pinned invariant-3 section).
Implementation in `de.gxp.menu` checked rule by rule (the user's *choice* is
not re-litigated — faithfulness only):

| Rule | Code | Verdict |
|---|---|---|
| 1 — ceiling resolution | `MenuControl.enabled()` = `propertyEnabled(k) AND room-override(default true)`; room rows consulted only when the property allows the item; `propertyEnabled` stays property row → catalogue default | ✅ and used by **all** guards: invariant 3 (`collectStrandedCards`), invariant 2 (`validateCards`), rule 4 — no caller bypasses it |
| 2 — room-enable of property-disabled → 409 | `validateConfigEntries` collects per item, throws `item-disabled-at-property` with details (message + `scope` + `reference`) | ✅ pre-apply check is sound: a room PUT cannot change property state in the same transaction |
| 3 — property-disable over explicit room enables → 409 | `putConfig` after the invariant-3 pass, via new `MenuRepository.roomEnableRowsOf(key)`; `menu-item-enabled-by-rooms` listing `room:<id>` + item key | ✅ applied-then-validated in the same transaction (bulk delete + persist precede both validations; any 409 rolls everything back — atomicity asserted by tests) |
| 4 — property card PUT vs. inheriting rooms | `putCards` (property scope only): every room with **no own card rows** runs `collectStrandedCards` → 409 `target-disabled` in the invariant-3 detail shape (card + `room:<id>`); room-scope card PUT unchanged | ✅ closes the original 🟡 #2 hole; wholesale-override semantics (REQ-308) respected — rooms with own cards are deliberately not checked |

- **409 detail shapes** consistent across all three new conflicts
  (`ApiErrorDetail` message + `scope` + `reference`, same as invariant 3).
- **BCE strictness preserved:** `roomEnableRowsOf` is package-private,
  parameterized JPQL, in-package; no new public Control methods
  (`SecurityAnnotationAuditTest`'s exactly-one audit unaffected); no new
  cross-package imports.
- **No contract change needed** — verified: all four PUT endpoints already
  declare 409/`ApiError`; `api/openapi.yaml` and all 46 files of
  `packages/api-client/` are sha256-identical to the round-1-reviewed state;
  `frontend/` untouched.
- **Tests (118 → 123):** the rewritten
  `propertyDisableBeatsARoomEnableAndStrandsTheRoomsOwnCards` keeps the old
  test's exact setup and flips the expectation 200 → 409 with room-scope
  details — it **pins the new semantics**, nothing was weakened. The five
  added tests cover every rule including atomicity, the forged-row inertness
  proof (native SQL creates the API-unreachable pre-amendment state; ceiling
  resolution keeps the room-enable row inert → card still 409), and the
  inheriting-rooms-only scoping of rule 4.

## Round-2 suite evidence (independent)

Three runs by the reviewer against fresh throwaway Postgres 17 containers
(`-Dquarkus.datasource.jdbc.acquisition-timeout=60`):

1. Load avg spiked to **25+** mid-run (immich jobs; transactions reaped as
   zombies while blocked in `Net.poll`): 18 errors — **all** client
   read-timeouts/boot failures, **0 assertion failures**.
2. Load spiked again (~34): first 15 classes / 105 tests **green including
   the entire round-2 delta** (all 3 menu classes 23/23), then one boot
   failure; run cut off by session loss. Union of runs 1+2: 122/123 tests
   green at least once, 0 assertion failures anywhere.
3. **Quiet window (load 2.78 at start): 123 run / 123 passed / 0 failures /
   0 errors / 0 skipped, BUILD SUCCESS — single clean run.** Log hygiene: 0
   SQL statements/bind values/password occurrences; the only ERROR strings
   are the two expected constraint-violation WARNs from tests that provoke DB
   rejections. Container + volume removed afterwards.

Confirms dev's recorded 123/123 single-run result. Runs 1–2 sharpen 🟢 #9:
this box cannot reliably host the suite while immich et al. are active —
environment, not code.

## Spec drift closed in round 2 (by reviewer)

- `01-prd.md` REQ-307 amended with the ceiling decision (marked as user
  decision 2026-08-19) and the superseded REQ-314 acceptance bullet
  ("overriding room → allowed") struck through with the supersession note.
- `docs/system-context.md` `de.gxp.menu` row updated: ceiling resolution for
  `enabled` (with the explicit hand-off that **the guest API must resolve the
  same way**) + the two new guard codes.

## Final verdict

**PASS ✅** — counts across both rounds: **🔴 0 · 🟠 0 (1 fixed in round 2) ·
🟡 0 (1 resolved by user decision + implemented) · 🟢 7** (all tracked,
non-blocking: latent `ResponseStatusFilter`/thrown-200 interaction, room-code
TOCTOU, password-change outside the login lockout → per-user-auth swap, forced
`HttpHeaders` in `AssetControl`, `ZoneId.of` looseness, EXIF orientation →
`manager-frontend`, acquisition-timeout flag for slow test hardware). All 36
REQs implemented, 0 orphans; every `backend/CLAUDE.md` convention holds; spec
chain (PRD → design → code → tests → system-context) consistent.
**Ready for the single commit** (contract + backend + `packages/api-client/` +
docs — the user/lead commits per the granted authority).

---

## Addendum 2026-08-24 — superseded by `backend-bce-refactor` (block A)

Appended, not rewritten: this file is the dated record of what was true on
review day. Two findings above have since changed status.

- **🟢 #5 (password change outside the login lockout) — resolved.**
  `AuthControl.changePassword` now runs both of its PBKDF2 operations inside
  the same `LoginConcurrencyGate` as `login`, so a stolen-token holder no
  longer guesses the current password outside the choke point. The optional
  remedy this row suggested ("route failures through `LoginRateLimiter`") is
  moot in its literal form — the limiter itself is gone.
- **The "login lockout" every row above refers to no longer exists.** It was
  found to be an unauthenticated denial of service (one **global** failure
  counter and lock, checked *before* verification, so the manager's correct
  password was answered `429`, re-armable forever with five requests per
  30 s). REQ-326 was amended by the owner and the mechanism replaced with a
  fair single-permit concurrency gate that paces and never denies. See
  `01-prd.md` REQ-326, `02-design.md` (pinned parameters), `03-todo.md` T-10.
- Unchanged by this addendum: 🟢 #1/#3/#4/#6/#7/#8/#9 and the PASS verdict.

---

## Addendum 2026-08-24 (second, same day) — throttling removed entirely

Appended, not rewritten. The addendum directly above records the concurrency
gate that replaced the login lockout. The owner then decided to **remove
throttling from the backend altogether, for now**: no lockout, no rate limit,
no concurrency gate on login or password change. The production hardware is
not chosen, and a throttle sized for the wrong machine is worse than none.

- **🟢 #5 (password change outside the login lockout) — withdrawn, not
  resolved.** The gate that closed it is gone. `changePassword` is again
  unthrottled, and that is now the intended behaviour; it is admin-only, so it
  is not an unauthenticated surface. Do not re-open this row without reading
  REQ-326 first.
- **Every "login lockout" reference in the rows above is doubly historical.**
  The lockout was an unauthenticated DoS (global counter, checked before
  verification, correct password answered `429`); its replacement paced rather
  than denied but still shed arriving callers throughout a flood; both are
  removed. `LoginConcurrencyGate`, `LoginConcurrencyGateTest`,
  `LoginBusyWireShapeTest`, `ApiErrors.tooManyRequests`, the `gxp.auth.gate-*`
  keys and the login `429` in `api/openapi.yaml` no longer exist.
- **Explicitly accepted and recorded** (so nobody "fixes" it unknowingly): an
  unauthenticated caller on the LAN can spend the server's CPU at will through
  PBKDF2, so sustained abuse degrades the whole instance, kiosks included.
  Brute-force resistance rests solely on the ≥ 8 password policy — which is
  **kept**, on both the change endpoint and the first-start seed.
- Unchanged: the PASS verdict and every other finding.

---

## Addendum 2026-08-24 (third, same day) — the annotations moved to the Boundary

Appended, not rewritten. Owner decision **D2** (constitution **v1.6.0**,
`backend/CLAUDE.md`, spec §4): authorization hangs off the **Boundary**, not
the Control, because the trust boundary is HTTP and an internal Java call is
not one. Rows above that read "on the Control" are historical from now on.

- **REQ-332's substance is unchanged: still exactly one of `@PermitAll` /
  `@RolesAllowed(...)` per public method, still fail-closed — only the carrier
  moved.** Every `*Resource` method resolves to exactly one annotation, taken
  from the method or from a single class-level one; every `*Control` carries
  none.
- **`SecurityAnnotationAuditTest` (row 141, 218) no longer exists.** It could
  not simply be re-pointed at the Boundaries and left at that:
  `quarkus.security.deny-unannotated-members` is **class-scoped**, so once the
  Controls have zero annotations it goes inert *for them* — the move alone
  would have been strictly weaker than the model this review passed. It
  therefore landed together with
  **`quarkus.security.jaxrs.deny-unannotated-endpoints=true`** (global,
  build-time), which closes a hole that existed while this review was written:
  a new Boundary over a new Control with no annotation anywhere was wide open,
  and the old audit caught it only if the class name ended in `Control`.
  Three tests replace the one: `BoundaryAnnotationAuditTest` (the `*Resource`
  sweep, ten Boundaries pinned by name, **plus** a guard that no generated
  `de.gxp.api.*Api` interface carries a security annotation — Quarkus resolves
  the implementation first and the interface as a *fallback*, so a generator
  upgrade emitting them would silently become the fallback), and
  `ContractSecuredOperationsDeniedTest` (every `bearerAuth` operation in
  `api/openapi.yaml` answers 401 anonymously — contract-derived, so a new
  secured operation cannot be forgotten).
- **🟢 row 66 (`TemplateControl` `@PermitAll`) — withdrawn, not reversed.** The
  reading was right for the model in force; the model changed. `TemplateControl`
  now carries no annotation at all, which is the same conclusion by a shorter
  route: it has no HTTP surface, so it was never a place where authorization
  belonged. The same is true of the four other `@PermitAll`-on-internal warts
  and of `DeviceControl.verifyToken`, which is called from
  `TokenIdentityProvider` **during authentication, before any identity exists**.
- **Kept deliberately:** `deny-unannotated-members` stays on as the second line
  for beans that do mix annotated and unannotated members, and the test-only
  `FailClosedControl`/`FailClosedResource` pair still proves it. One endpoint
  in that fixture stays annotated **nowhere** on purpose — with the global deny
  on, it is the only evidence the flag bites.
- Unchanged: the PASS verdict, the roles (`admin`, `device`), every path and
  every status code on the wire.


## Addendum 2026-08-25 — the sub-package split (`backend-bce-refactor`, block C)

Appended, not rewritten. Owner decision **D1** (constitution **v1.6.0**) split
every feature package into `de.gxp.<f>.boundary` / `.control` / `.entity`.
Two verified findings above are superseded in their MECHANISM, not in their
substance:

- *"Every Entity/Repository/Mapper is package-private (verified for all seven
  packages)"* — the **Repositories are all still package-private**, now inside
  each feature's `.control` package, which is the whole reason they live there
  rather than beside the entity. **Entities and Mappers are now `public`**,
  because Java has no sub-package visibility and the Control has to name them
  across the split. Entity FIELDS were widened one at a time and only where a
  Control genuinely touches one — most stayed package-private (`Room` and
  `Property` gave up one field each; `Stay` six of eight; `Device`,
  `PairingRequest` and `AdminCredential` all of theirs, because their Controls
  drive the whole row and they have no or nearly no mapper).
- *"BCE strictness preserved: … no new cross-package imports"* — still true,
  and no longer only by review: `FeatureInternalsImportTest` fails the build
  on any reference from outside a feature to its `.entity`/`.control` types
  other than the feature's own `*Control`.

The compiler's role is taken by three dependency-free architecture tests in
`de.gxp.architecture` (`FeatureInternalsImportTest`, `EntityExposureTest`,
`ControlPackageVisibilityTest`), all re-proven by injected violation at full
scope on 2026-08-25. ArchUnit was rejected — a new dependency on an
unpatchable box, principle 4.
