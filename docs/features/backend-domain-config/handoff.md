# Handoff: backend-domain-config

**Last session:** 2026-08-19 (reviewer, 06_review round 2 — **PASS ✅**,
ready for the single commit)

## 06_review round 2 (2026-08-19, reviewer) — PASS ✅

Both open items verified resolved — see [06-review.md](06-review.md) → "Round 2":

- **🟠 #1 fixed:** `AuthControl` throws via `ApiErrors` only (new
  `ApiErrors.unauthorized`); no wire-shape change, zero test edits; the
  `backend/CLAUDE.md` convention holds. `LoginRateLimiter`'s 429 stays ad-hoc
  by design (infrastructure, needs `Retry-After`) — accepted.
- **🟡 #2 resolved (user's ceiling decision) and implemented faithfully:**
  all four rules verified in `de.gxp.menu` — ceiling resolution used by ALL
  guards, applied-then-validated transaction kept, 409 detail shapes match
  invariant 3, `roomEnableRowsOf` package-private, rewritten guard test pins
  the new semantics (old setup, 200 → 409). No contract change (verified:
  `api/` + all 46 `packages/api-client/` files sha-identical, `frontend/`
  untouched).
- **Independent suite:** fresh Postgres 17, one clean quiet-window run:
  **123/123, 0 failures/errors/skips, BUILD SUCCESS**; log hygiene clean.
  Two earlier attempts were poisoned by load-25+ spikes (immich) — all their
  errors were timeouts/boot failures, **zero assertion failures**; recorded
  as the environment note (this box needs a quiet window +
  `-Dquarkus.datasource.jdbc.acquisition-timeout=60` for suite runs).
- **Spec drift closed by reviewer:** `01-prd.md` REQ-307 amended (ceiling,
  marked as user decision) + superseded REQ-314 acceptance bullet struck;
  `docs/system-context.md` `de.gxp.menu` row now records ceiling resolution
  and that **the guest API must resolve `enabled` the same way** (hand-off to
  `device-pairing-and-guest-api`).
- **Final counts:** 🔴 0 · 🟠 0 (1 fixed) · 🟡 0 (1 resolved) · 🟢 7 tracked,
  non-blocking (list in 06-review.md "Final verdict").

**Next:** the single commit — contract + backend + `packages/api-client/` +
docs together (user granted commit authority to lead 2026-08-19). Nothing
else open in this feature.

---

## Previous: dev, round 2 — 🟡 #2 ceiling amendment
implemented; **full suite 123/123 green, BUILD SUCCESS**, one run

## Round 2: 🟡 #2 resolved-by-user ceiling amendment (2026-08-19, dev)

Implemented exactly the design amendment in 02-design.md ("RESOLVED by user
decision (2026-08-19)") — *the property is the global ceiling*:

- `de/gxp/menu/MenuControl.java`:
  1. `enabled(room, k)` is now `enabled(property, k) AND room-override(k,
     default true)` — property disable wins over any room enable
     (new `propertyEnabled(k)` helper; class + method javadoc updated).
  2. Room menu-config PUT with `enabled=true` for a property-disabled item →
     **409 `item-disabled-at-property`** (details name the item;
     collected in `validateConfigEntries`, now taking the scope).
  3. Property menu-config PUT disabling `k` while explicit room-level
     enabled rows exist → **409 `menu-item-enabled-by-rooms`** listing the
     rooms (`scope=room:<id>`, `reference=k`; checked after the invariant-3
     stranded-card pass, same applied-then-validated transaction).
  4. Property card PUT additionally validated against every INHERITING room
     (no own card rows) via `collectStrandedCards` → **409 `target-disabled`**
     in the invariant-3 detail shape (card + `room:<id>`). Room-scope card
     PUT unchanged.
- `de/gxp/menu/MenuRepository.java`: new `roomEnableRowsOf(key)` query
  (rule 3). No contract change — all four PUT endpoints already declare 409
  with the `ApiError` schema (verified in `api/openapi.yaml`); client
  untouched.
- **Tests (118 → 123):** `MenuDisableGuardTest` —
  `propertyDisableIsAllowedWhenOnlyAnOverridingRoomStillTargetsIt` REWRITTEN
  to `propertyDisableBeatsARoomEnableAndStrandsTheRoomsOwnCards` (the old
  room-wins 200 is now a 409, pinning the ceiling inside invariant 3) + new
  `propertyDisableOfARoomEnabledItemIs409ListingTheRooms` (rule 3).
  `MenuCatalogueAndConfigTest` — new
  `roomEnableOfAPropertyDisabledItemIs409NamingTheItem` (rule 2) and
  `aRoomEnableRowIsInertUnderTheCeiling` (rule 1: the forbidden state is
  forged via native SQL and proven inert — a room card targeting the item is
  still 409). `ActionCardTest` — new
  `aPropertyCardStrandedInAnInheritingRoomIs409ListingCardAndRoom` (rule 4)
  and `aRoomWithOwnCardsIsNotCheckedOnAPropertyCardPut` (rule 4 scope pin).
- **Suite vs. fresh throwaway Postgres 17,
  `-Dquarkus.datasource.jdbc.acquisition-timeout=60`, load avg ~2.0 at
  start: 123 run / 123 passed / 0 failed / 0 skipped, BUILD SUCCESS — one
  run, no flake** (also closes round 1's missing all-green run; both
  previously flaking profile-restart classes green). Container + volume
  removed afterwards.

**Next:** reviewer re-check (round-1 🟠 fix + this round) → PASS → user
commits.

---

## Previous: round 1 (dev) — 🟠 #1 fixed; suite functionally green,
blocked from a single all-green run only by the known env flake (superseded:
round 2's 123/123 run covers it)

## 🟠 #1 fix applied (2026-08-19, dev)

- `de/gxp/api/ApiErrors.java`: added `unauthorized(code, message)` (→ 401 via
  the existing `withStatus` builder — same `ApiError` wire shape, placed
  between `badRequest` and `notFound`).
- `de/gxp/auth/AuthControl.java`: `login` now throws
  `ApiErrors.unauthorized("invalid-credentials", "Login failed")`;
  `changePassword` throws `ApiErrors.badRequest(...)` for both 400s; the two
  private ad-hoc builders (`genericUnauthorized`, `badRequest`) deleted, dead
  imports removed. **No wire-shape change, zero test edits** (exactly the
  reviewer's prescription; 🟡 #2 untouched — still awaiting the user).
- `./mvnw -o test-compile` — EXIT 0.
- **Full suite vs. fresh throwaway Postgres 17 (port 55433), three runs:**
  1. `acquisition-timeout=60`: 118 run, **117 passed**, 1 error —
     `LoginLockoutTest` startup (Flyway/Agroal *acquisition timeout* at the
     `@TestProfile` restart, review 🟢 #9 verbatim).
  2. same flags: 118 run, **117 passed**, 1 error — `OpenApiDisabledTest`
     startup, same acquisition timeout (LoginLockoutTest green this run).
  3. `acquisition-timeout=120`, run under sustained load-avg 15–22 (immich
     background jobs on this Pi): both profile-restart tests hit the same
     startup timeout; 116 passed, 30:45 min total.
  **Zero assertion failures in any run; every one of the 118 tests passed in
  at least one run; every error is the documented pre-test env flake, never
  app code.** Dev stopped per guardrail (repeated env failures) rather than
  keep hammering the loaded box. Container `gxp-fix-pg` + its anonymous
  volume removed after the runs.

**Next:** re-run the suite once when the machine is idle (expect 118/118 with
`-Dquarkus.datasource.jdbc.acquisition-timeout=60`), user decides 🟡 #2, then
reviewer re-check → PASS.

---

## Previous: reviewer, 06_review — **NEEDS CHANGES ❌**, one
🟠 mechanical fix away from PASS

## 06_review result (2026-08-19, reviewer) — see [06-review.md](06-review.md)

- **Counts: 🔴 0 · 🟠 1 · 🟡 1 · 🟢 7.** All 36 REQs (301–336) implemented,
  0 orphans. All 9 recorded deviations ratified; spec drift folded into
  02-design.md ("Implementation deviations" addendum).
- **Independently re-verified by the reviewer:** full suite vs. fresh
  Postgres 17 → **118/118 green, BUILD SUCCESS** (needed
  `-Dquarkus.datasource.jdbc.acquisition-timeout=60` on this Pi — two runs hit
  Agroal's default 5 s at a `@TestProfile` restart boundary; environment, not
  code); log sweep 0 hits (source, config, test logs); `dependency:tree` 0
  forbidden hits, pom = allow-list exactly; client regeneration byte-identical
  twice (46 files); contract security/tags/ops parsed and confirmed; V2 seeds
  verbatim vs. frontend data; BCE import graph = design's exactly. Compose
  smoke NOT re-run (taken from lead's same-day results above).
- **🟠 #1 (blocks PASS, mechanical):** `AuthControl` builds its thrown
  400/401 ad-hoc instead of via `de.gxp.api.ApiErrors` — contradicts the
  user-approved `backend/CLAUDE.md` entry. Fix: use `ApiErrors.badRequest`,
  add `ApiErrors.unauthorized(code, message)`, delete the private builders.
  No wire-shape change, no test moves. (`LoginRateLimiter`'s 429 is
  infrastructure + needs a `Retry-After` header — align or comment.)
- **🟡 #2 (owner decision):** property-scope card PUT validates only the
  property scope (design's pinned letter) — a room that disabled the target
  and has no own cards inherits a stranded card (dead tile). Extend the check
  to inheriting scopes, or accept + guest API skips defensively. Recorded in
  the design addendum.
- 🟢 notes: latent ResponseStatusFilter/thrown-200 interaction; room-code
  TOCTOU (DB backstop); password-change endpoint outside the login lockout
  (note for the per-user swap); `HttpHeaders` in `AssetControl` (forced,
  don't spread); `ZoneId.of` looser than "IANA"; EXIF orientation dropped on
  downscale (note for `manager-frontend`); acquisition-timeout flag for slow
  test hardware (optional README line).

**Next:** dev (or lead) applies the 🟠 #1 fix + user decides 🟡 #2 → reviewer
re-checks (trivial) → PASS → the user commits (contract + backend +
`packages/api-client/` together; commit authority already granted to lead
2026-08-19 per the section below).

---

## Previous: lead, final validation — ALL GREEN

## ✅ Final validation (2026-08-19, lead-orchestrated, after the fix round)

- `./mvnw verify` against a fresh Postgres 17.11: **118 run / 118 passed /
  0 failed, BUILD SUCCESS** (all three root-cause fixes confirmed).
- Prod-profile compose smoke (image rebuilt from `Dockerfile.jvm`,
  `compose up` with env-seeded admin password): `/api/system/ready` **200**;
  `/q/openapi` **404** and `/q/swagger-ui` **404** (the user's `%prod`
  lockdown, executable proof); `/api/system/info` **200**; unauthenticated
  `GET /api/manager/rooms` **401**; `POST /api/manager/login` with the
  seeded env password **200** → authenticated rooms CRUD **200**; app log:
  **0** error lines, **0** occurrences of the admin password (principle 3).
  Teardown clean; the throwaway test volume was removed afterwards.
- Also approved and written today by lead (user confirmation verbatim):
  the three `backend/CLAUDE.md` convention additions dev proposed
  (one-tag-per-package, `@ResponseStatus` on Boundary, `ApiErrors` for
  non-2xx). The `@ResponseStatus` entry survives the fix round unamended —
  `de.gxp.api.ResponseStatusFilter` is its enabling infrastructure.

**Next:** `06_review` (reviewer), then lead commits (user granted commit
authority 2026-08-19; contract + backend + regenerated client together).

## Previous: dev, suite-failure fixes (25 failures + 1
error from lead's first Postgres run, diagnosed and fixed)

## Follow-up 2026-08-19 (2): first suite run fixed (118 run, 25 F + 1 E)

Lead executed the suite against Postgres; three root causes, all fixed in
code (no test weakened, no dependency touched, no CLAUDE.md edit):

1. **`@ResponseStatus(201)` ignored → 46× "expected 201 was 200".**
   Root cause (verified against the 3.33.3.1 bytecode, not guessed):
   Quarkus REST resolves `@ResponseStatus` at BUILD time via
   `ResponseStatusMethodScanner`, which receives the `MethodInfo` of the
   method carrying the JAX-RS annotations — for our contract-first
   Boundaries that is the GENERATED interface method
   (`EndpointIndexer.createEndpoints` recurses into the interface when the
   impl class has no `@POST` etc.), so the annotation on the override is
   invisible. Fix: new `de.gxp.api.ResponseStatusFilter`
   (package-private `@Provider ContainerResponseFilter`, sits beside
   `ApiErrorMappers`): honors the RUNTIME-retained annotation via
   `ResourceInfo#getResourceMethod()`, which resolves against the ACTUAL
   resource class (verified: `RuntimeResourceDeployment` builds
   `ResteasyReactiveResourceInfo` from `ResourceClass.getClassName()` — the
   impl). Only rewrites a default 200; thrown `Response`s (binary 200/304,
   all errors) untouched. The Boundaries keep their annotation — the
   `backend/CLAUDE.md` convention stays TRUE as worded, no amendment needed.
   Rejected alternative (for the record): throwing a 201 `Response` from the
   Control — a `WebApplicationException` is a RuntimeException and would
   mark the `@Transactional` create ROLLBACK-ONLY, silently undoing the
   insert; `dontRollbackOn` workarounds were judged uglier than the filter.
2. **Malformed JSON → bare 400 without content-type (1 error).**
   Root cause: `ServerJacksonMessageBodyReader#readFrom` (verified in
   3.33.3.1) wraps `StreamReadException`/`DatabindException`/
   `StreamConstraintsException` in an ENTITY-LESS
   `WebApplicationException(cause, 400)` — only MismatchedInput/
   InvalidDefinition are rethrown raw (which is why that mapper worked). An
   `ExceptionMapper<JacksonException>` can never see parse failures. Fix:
   new `ApiErrorMappers.WrappedJacksonExceptionMapper`
   (`ExceptionMapper<WebApplicationException>`): cause `instanceof
   JacksonException` → shared malformed-body `ApiError`; anything else →
   `exception.getResponse()` unchanged. Safe because entity-carrying WAEs
   (all `ApiErrors` outcomes) never reach mappers at all —
   `RuntimeExceptionMapper.mapException` short-circuits on `hasEntity()`;
   entity-less ones (framework 404/405…) pass through byte-identical.
   `JsonParseMapper` kept for raw `JacksonException`s, both share one
   response builder.
3. **Guard collection sizes (4 failures) — real logic gap, NOT downstream
   of #1.** `collectStrandedCards` reported each stranded card once PER
   affected scope (card × property + every seed room). The design says
   "every offending card … and its scope" — per card. Fix: dedup by card id
   across affected scopes (`Set<Long> reportedCardIds`, first-scope-wins,
   which keeps `details[0].scope` semantics the tests pin).

Verified in-sandbox (no Docker, JDK `/tmp/gxp-jdk/jdk-21.0.12+8`):
`./mvnw -o test-compile` EXIT 0; `./mvnw -o verify -DskipTests`
**BUILD SUCCESS** (full ArC augmentation incl. the two new `@Provider`
beans); DB-free unit tests **31 run, 0 failures**. The fixed suite itself
needs lead's Postgres re-run.

## Follow-up 2026-08-19: `/q/openapi` closed in `%prod` (user decision)

User decided explicitly: `/q/openapi` must NOT be served in `%prod`, and no
dev-time surface may be active in prod. This **resolves backend-foundation
06-review finding #1** (was: decide by `device-pairing-and-guest-api`).

- `application.properties` gained a commented `%prod` block:
  `%prod.quarkus.smallrye-openapi.enabled=false` (runtime config — **not**
  the review's candidate `.enable`, which is deprecated since Quarkus 3.26
  forRemoval; verified against the 3.33.3.1 jars: false registers the
  extension's not-found handler → 404, build-time generation and dev/test
  serving untouched), `%prod.quarkus.swagger-ui.always-include=false`
  (default-false pinned) and `%prod.quarkus.devservices.enabled=false`
  (Dev Services are launch-mode-gated anyway; intent made reviewable).
  Dev UI needs no property — dev-mode-only by construction, its deployment
  module is not on the prod classpath.
- New test `de.gxp.security.OpenApiDisabledTest` (own `@TestProfile`):
  proves the property name + 404 semantics on this Quarkus version
  (`/q/openapi` → 404, `/api/system/ready` → 200 with
  `quarkus.smallrye-openapi.enabled=false`). The `%prod.` prefix wiring
  itself is NOT testable from `%test` — it is config-review plus the compose
  smoke below. Not yet executed (no Docker in dev's sandbox).
- `./mvnw verify -DskipTests` after the change: **BUILD SUCCESS**.

## State

**T-01…T-26 all implemented** on `feature/backend`. Everything compiles and
packages; the client regenerates byte-identically. **The @QuarkusTest suite
has NOT been executed** — dev's sandbox has no Docker (no Dev Services); see
"What could not be executed" below. All 31 pure-unit tests (no DB) ran green
in-sandbox.

### Build results (exact, in dev's sandbox, JDK `/tmp/gxp-jdk/jdk-21.0.12+8`)

- `./mvnw -o test-compile` — **EXIT 0** after every task.
- `./mvnw -o verify -DskipTests` — **BUILD SUCCESS** (full Quarkus
  augmentation; validates ArC wiring incl. the auth SPI beans).
- Client determinism: `generate-sources` twice → `sha256sum` over every file
  in `packages/api-client/` — **byte-identical**.
- `dependency:tree` grep for
  elytron/smallrye-jwt/lombok/mapstruct/testcontainers/jackson-databind-nullable —
  **0 hits**.
- Unit tests executed:
  `Pbkdf2PasswordHasherTest(6) TokenStoreTest(9) TemplateControlTest(10)
  AdminPasswordSeederTest(5) SecurityAnnotationAuditTest(1)` →
  **31 run, 0 failures** (plus the earlier partial runs, same classes).
- `git status`: nothing under `frontend/`; `backend/app/pom.xml` carries **no
  dependency change** (one generator *config flag* added — see below).
- Grep sweep: **zero** logger/`System.out`/`printStackTrace` usage anywhere in
  `src/main/java`; the foundation's logging config block is unchanged.

## Verify-at-implementation findings (design's three open items)

| Item | Finding |
|---|---|
| `jaxrs-spec` binary body type | Request body `format: binary` generates a **`java.io.File` parameter** (Quarkus REST materializes the body to a temp file — the 10 MiB cap is checked on `File.length()` before any read, same intent as cap-while-reading). Binary **response** also generates `File` as return type. |
| `useBeanValidation` in 7.24.0 | **On by default** — no flag needed: generated params carry `@Valid @NotNull`, models carry `@Pattern`/`@Size`/`@Min`/`@Max` from contract constraints. `ConstraintViolationException` is mapped to the shared `ApiError` by `de.gxp.api.ApiErrorMappers` (REQ-325). |
| ArC visibility for auth SPI beans | **Package-private works.** `BearerAuthMechanism`, `TokenIdentityProvider` (`@ApplicationScoped`) and `TokenExpiryResponseFilter` (`@Provider`) are all package-private in `de.gxp.auth`; full Quarkus augmentation (`verify -DskipTests`) passes. Nothing had to be made public. |

## Deviations from the design (with rationale)

1. **Tags `manager-auth|property|rooms|menu|stays|assets` instead of one
   `manager` tag.** With `useTags=true` one tag = one generated interface =
   one Boundary implementing *everything*, which would contradict the
   design's own one-Resource-per-BCE-package architecture. One tag per
   package keeps each Boundary a thin adapter. Paths/operations are exactly
   the design's table.
2. **Binary GET serves via a thrown `Response`.** `returnResponse` is
   global-only in jaxrs-spec; a `File` return type cannot carry
   `ETag`/`Cache-Control`/304. `AssetControl.binary()` therefore ALWAYS
   throws the fully built `Response` (200-with-ETag or 304) — the same
   "outcomes are thrown from the Control" channel every non-2xx already
   uses; the Boundary stays one line; no temp files (bytes stream from the
   entity). Flagged for review.
3. **`@ResponseStatus(201)` on the three create Boundaries**
   (`uploadAsset`, `createRoom`, `createStay`) — with `returnResponse=false`
   a POST returning a DTO would answer 200; the contract says 201. RESTEasy
   Reactive's annotation on the override is a pure HTTP-adapter concern
   (JAX-RS annotation inheritance is unaffected — it is not a JAX-RS
   annotation).
4. **One generator config flag added to the existing plugin**
   (`<dateLibrary>java8</dateLibrary>`): default was legacy
   `java.util.Date` for `date-time`; now `OffsetDateTime`. Config, not a
   dependency (explicitly allowed by T-04's note).
5. **Referrer checks via native queries on foreign tables.**
   `AssetRepository.findReferrers` reads `property`/`room` tables and
   `RoomRepository.staysOf` reads `stay` — injecting the owning Controls
   would be circular (they already inject `AssetControl`/`RoomControl`).
   Entities never leave their packages; FKs `ON DELETE RESTRICT` are the DB
   backstop.
6. **Test-profile-only tuning knobs**: `gxp.auth.pbkdf2-iterations`
   (default 600000, `%test` 1000 — the self-describing hash format makes
   this behavior-neutral), `gxp.auth.idle-timeout-seconds` (default 600),
   `gxp.auth.lockout-seconds` (default 30; `LoginLockoutTest` overrides to
   2 s via its own `@TestProfile` so a tripped lock can't poison the shared
   app instance). Production defaults are the pinned values.
7. **`de.gxp.api.ApiErrors`** — one shared infrastructure helper building
   the thrown non-2xx `ApiError` responses (sits beside `ApiErrorMappers`;
   not a Control/Boundary/Entity). Avoids seven copies of the same builder.
8. **Seed brand colors** are a neutral slate placeholder
   (`#5b6472`/`#3f4652`/`#8892a0`) — the design pinned no values; nothing
   property-specific (principle 1), flagged (like all neutral copy) for
   `manager-frontend` to surface as "please configure me".
9. **`GXP-Token-Expires` on the login response** is taken from the
   `LoginResponse` body by the filter (login is `@PermitAll`, so no
   identity attribute exists there).

## What was built (files by package)

- **Contract:** `api/openapi.yaml` (auth, property, rooms, menu catalogue/
  config, action cards, stays, assets; `bearerAuth`; `ApiError.details` +
  `ApiErrorDetail`), `packages/api-client/**` regenerated (7 services,
  22 models).
- **Migrations:** `V2__domain_config.sql` (13 tables, checks,
  `UNIQUE NULLS NOT DISTINCT` scopes, catalogue+property+options+room
  seeds), `V3__admin_credential.sql` (no seed row).
- **`de.gxp.auth`:** `AuthResource`, `AuthControl`, `AdminCredential`,
  `AdminCredentialRepository`, `TokenStore`, `Pbkdf2PasswordHasher`,
  `LoginRateLimiter`, `AdminPasswordSeeder`, `BearerAuthMechanism`,
  `TokenIdentityProvider`, `TokenExpiryResponseFilter`.
- **`de.gxp.text`:** `TemplateControl`.
- **`de.gxp.property`:** `PropertyResource`, `PropertyControl`, `Property`,
  `PropertyTranslation`, `PropertyLanguageOption`,
  `PropertyAppearanceOption`, `PropertyRepository`, `PropertyMapper`.
- **`de.gxp.room`:** `RoomResource`, `RoomControl`, `Room`,
  `RoomRepository`, `RoomMapper`.
- **`de.gxp.menu`:** `MenuResource`, `MenuControl`, `MenuItemDefinition`
  (+Translation), `MenuItemConfig` (+Translation), `ActionCardConfig`
  (+Translation), `MenuRepository`, `MenuMapper`.
- **`de.gxp.stay`:** `StayResource`, `StayControl`, `Stay`,
  `StayRepository`, `StayMapper`.
- **`de.gxp.asset`:** `AssetResource`, `AssetControl`, `Asset`,
  `AssetMetaRow`, `AssetRepository`, `AssetMapper`, `ImageProcessor`.
- **`de.gxp.api`:** `ApiErrorMappers`, `ApiErrors`.
- **Config:** `application.properties` (+`max-body-size=12M` with comment,
  `%test` auth block), `backend/compose.yaml` (comment updated: password
  lifecycle is real now).
- **Tests (19 classes, ~101 methods + 1 parameterized×7):**
  `Pbkdf2PasswordHasherTest`, `TokenStoreTest`, `AdminPasswordSeederTest`,
  `AuthResourceTest`, `LoginLockoutTest` (own `@TestProfile`),
  `ApiErrorMappersTest`, `TemplateControlTest`, `AssetResourceTest`,
  `AssetDeleteGuardTest`, `PropertyResourceTest`, `RoomResourceTest`,
  `StayResourceTest` (incl. the parallel-create race proof),
  `MenuCatalogueAndConfigTest`, `ActionCardTest`, `MenuDisableGuardTest`
  (the full invariant-3 matrix), `SecurityAnnotationAuditTest`,
  `ManagerSurfaceDeniedTest`, `FirstRunSeedTest`; `FailClosedTest` revised
  (401 + `WWW-Authenticate: Bearer` now that a mechanism exists, per
  REQ-332), `MigrationTest` extended (V2/V3 applied, credential seeded by
  code).
- **Docs:** `backend/README.md` (auth workflow, first-start password +
  rotation, asset limits; backup unchanged — still one `pg_dump`),
  `docs/system-context.md` (new component rows, Auth/Persistence updated,
  hand-off notes), `docs/features/index.md`.

## What could NOT be executed (lead: please run)

1. **`./mvnw verify` with the test suite** — needs a Postgres (Dev Services
   or an explicit datasource as in the foundation run:
   `-Dquarkus.datasource.jdbc.url=… -Dquarkus.datasource.username=…
   -Dquarkus.datasource.password=…`). Tests assume the credential seed came
   from `%test.gxp.admin.password` (`test-admin-password-1`) — a **fresh**
   throwaway DB is required (a DB carrying a different credential row will
   fail every auth-dependent test; the seeder ignores the variable then).
   Tests are otherwise data-independent / self-cleaning and were written to
   run under either Dev Services or an external datasource.
2. **Boot-log check** — zero SQL statements/bind values (re-check while
   running the suite; committed config unchanged, so this should hold).
3. **Compose smoke (T-24)** — fresh volume: `docker compose up` → login →
   `GET /api/manager/property` 200 → `down`; README steps followed
   literally. Not runnable without Docker. **Add one check** (follow-up
   above): `GET /q/openapi` → **404** while `/api/system/ready` → 200 —
   this is the only executable proof of the `%prod.` wiring, since tests
   run in `%test`.
4. Flyway V2/V3 have not touched a real database yet — any SQL typo
   surfaces on the first suite run (`MigrationTest` +
   `FirstRunSeedTest` then prove the seeds).

## For review to check closely

- **Jackson-required vs. bean validation:** the generated models bind
  required fields through a `@JsonCreator` constructor, so a missing
  required property raises `MismatchedInputException` BEFORE bean
  validation. `ApiErrorMappers.MismatchedInputMapper` (exact exception
  type) exists so the shared `ApiError` shape answers instead of Quarkus'
  built-in mapper. If the built-in still wins on this Quarkus version
  (priority tie-break), the fix is a `@Priority(Priorities.USER - 1)` on
  that one mapper — `ApiErrorMappersTest`/`AuthResourceTest.loginRejects…`
  will show it immediately on the first suite run.
- The thrown-200 `Response` in `AssetControl.binary()` (deviation 2).
- `TemplateControl` methods are `@PermitAll` (stateless, no HTTP surface) —
  keeps the annotation audit exactly-one; confirm that reading of the
  convention.
- The invariant-3 matrix in `MenuDisableGuardTest` against the design's
  affected-scope algorithm.
- Locale-exact assertions in `TemplateControlTest` ("19 Aug 2026",
  "19.08.2026", "16:42") are pinned to JDK 21 CLDR — verified green on
  `/tmp/gxp-jdk/jdk-21.0.12+8`.
- `ManagerSurfaceDeniedTest` uses `@ParameterizedTest`
  (junit-jupiter-params, part of the jupiter aggregate quarkus-junit
  brings) — no new dependency.

## Carried forward (unchanged)

- ~~`/q/openapi` `%prod` exposure~~ — **RESOLVED 2026-08-19** (user
  decision, see follow-up section above): disabled in `%prod`, foundation
  review 🟡 #1 closed. Nothing carries to `device-pairing-and-guest-api`.
- Plain HTTP until TLS lands next feature; compose stays dev/preview-only.
- `ngVersion` ceiling on first client consumption
  (`guest-frontend-integration`).
- `einstellungen` = fixed chrome for the guest API (hand-off note recorded
  in system-context).
- Neutral seed copy + placeholder brand colors → `manager-frontend`
  ("please configure me").

## Next

1. `lead`: **re-run the full suite** (same fresh-Postgres setup as the first
   run) — expectation: the 46× 201s, the malformed-JSON shape and the four
   guard-size assertions now pass. Then the compose smoke (section above),
   record results here.
2. `06_review` (`reviewer`) with the named checks: invariant matrix, auth
   lifecycle, annotation audit, log sweep, allow-list `dependency:tree`
   re-run, byte-identical client regeneration, BCE strictness of the seven
   new packages.
3. Nothing committed — the user commits contract + backend +
   `packages/api-client/` together.

## Addendum 2026-08-24 — `backend-bce-refactor` block A

Appended to a closed record. `LoginRateLimiter` (the 5-failure / 30 s global
lockout) and `PairingRateLimiter` (5 creates / 60 s global) have been replaced
by `LoginConcurrencyGate` / `PairingConcurrencyGate` — both pace instead of
denying, per the owner's amendment to REQ-326. `gxp.auth.lockout-seconds` is
gone; `AdminPasswordSeeder` now enforces the same ≥ 8 minimum as a password
change and refuses the first start below it. Nothing else in this handoff
changes.

## Addendum 2026-08-24 (second, same day) — throttling removed entirely

Appended to a closed record; the addendum above is superseded in its
conclusion, not in its history. Owner decision: **no throttling anywhere in
the backend for now.** `LoginConcurrencyGate` and `PairingConcurrencyGate`
(and, before them, `LoginRateLimiter` / `PairingRateLimiter`) are all gone,
together with `ApiErrors.tooManyRequests`, the `gxp.auth.gate-*` /
`gxp.pairing.*` config keys and the login `429` in the contract. Rationale:
the production hardware is not chosen. **Kept:** the `AdminPasswordSeeder`
≥ 8 policy and its tests — a password policy is not a rate limit, and with no
throttle it is the only thing carrying brute-force resistance. Revisit once
the deployment target is known.


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
