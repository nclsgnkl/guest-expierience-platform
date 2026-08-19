# TODO: Backend Domain & Configuration
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** Implemented — suite execution + compose smoke pending (lead; dev sandbox has no Docker)

> Every task traces to a REQ-ID. All decisions (parameters, package layout,
> migration content, invariant algorithms, endpoint shapes) are pinned in
> [02-design.md](02-design.md) — do not re-decide. Items marked *verify* are
> mechanics checked against the pinned toolchain versions, same intent even
> if a name/type differs. Branch: `feature/backend`. Work **one task at a
> time, tests with the task** (constitution principle 6); `./mvnw verify`
> green after every task. **No `pom.xml` edits — if one seems needed, stop ⏸.**

## Contract (edit `api/openapi.yaml`, regenerate, keep client committed)

- [x] **T-01** Add `bearerAuth` security scheme + auth operations
  (`managerLogin`, `managerLogout`, `changeManagerPassword`) with
  `LoginRequest/LoginResponse/PasswordChangeRequest` schemas; extend
  `ApiError` additively with optional `details` array. Document the
  `GXP-Token-Expires` response header on `managerLogin` and in the API
  description. — _REQ-301, REQ-326, REQ-327, REQ-328, REQ-331_
  - Files: `api/openapi.yaml`, `packages/api-client/**` (generated)
  - Validate: `./mvnw verify` fails on missing implementations only after the
    matching impl tasks — so within this task run `generate-sources` and
    confirm both outputs; client regen deterministic (`git status` clean on
    second run)
- [x] **T-02** Add property + rooms operations (`getProperty`,
  `updateProperty`, rooms CRUD) with `PropertyDto` (localized maps, settings
  options, brand primitives, asset refs, raw templates) and `RoomDto`;
  contract-level constraints (hex pattern, required fields, enums) for bean
  validation. — _REQ-301, REQ-319, REQ-320, REQ-325_
  - Files: `api/openapi.yaml`, `packages/api-client/**`
  - Validate: as T-01
- [x] **T-03** Add menu catalogue (read-only — no write operation),
  menu-config `GET`/`PUT` for both scopes, action-cards `GET`/`PUT` for both
  scopes, with `MenuItemDefinitionDto`, `MenuItemConfigEntryDto`,
  `ActionCardDto` (localized template maps; `maxItems: 4`). —
  _REQ-301, REQ-306, REQ-321, REQ-322, REQ-312_
  - Files: `api/openapi.yaml`, `packages/api-client/**`
  - Validate: as T-01; grep contract: no `POST/PUT/DELETE` on catalogue path
- [x] **T-04** Add stays CRUD and asset operations (metadata list, binary
  upload with `kind` query param, binary get with `ETag`/`304`, delete);
  *verify* the `jaxrs-spec` parameter type generated for binary bodies and
  the `useBeanValidation` flag (design → verify-at-implementation). —
  _REQ-301, REQ-323, REQ-324, REQ-325_
  - Files: `api/openapi.yaml`, `packages/api-client/**`, `backend/app/pom.xml`
    is **not** touched (generator config only if a flag is needed — flags on
    the existing plugin are config, not dependencies)
  - Validate: as T-01; record the verified types in the handoff

## Schema

- [x] **T-05** `V2__domain_config.sql` exactly per design §Data model: all
  tables + checks + `UNIQUE NULLS NOT DISTINCT` constraints + seeds
  (catalogue 5 rows + `de`/`en` translations verbatim from
  `example-property.data.ts`, neutral property + `de` translation row,
  language/appearance options, default room `1`). `V1` untouched. —
  _REQ-302, REQ-303, REQ-304, REQ-305, REQ-306, REQ-307, REQ-308, REQ-309,
  REQ-310, REQ-311, REQ-334_
  - Files: `backend/app/src/main/resources/db/migration/V2__domain_config.sql`
  - Validate: `./mvnw verify` boots Flyway to V2; a temporary psql/Dev-Services
    check that a second property row and a duplicate NULL-scope config row are
    rejected (kept as tests in T-19/T-21)
- [x] **T-06** `V3__admin_credential.sql` (single-row table, no seed row —
  the seeder writes it). — _REQ-302, REQ-330_
  - Files: `backend/app/src/main/resources/db/migration/V3__admin_credential.sql`
  - Validate: boot reaches V3; table empty on fresh DB

## Auth (`de.gxp.auth`)

- [x] **T-07** `Pbkdf2PasswordHasher` (package-private): hash/verify with the
  pinned parameters and self-describing storage format; constant-time
  compare. Unit tests: roundtrip, wrong password, format parse, distinct
  salts. — _REQ-329_
  - Files: `backend/app/src/main/java/de/gxp/auth/Pbkdf2PasswordHasher.java`, test
  - Validate: `./mvnw verify`
- [x] **T-08** `TokenStore` (package-private, application-scoped):
  create (256-bit `SecureRandom` → base64url, store sha-256 hex + expiry),
  `slide()` on hit, expiry check, `remove()`, `clear()`, lazy purge. Unit
  tests incl. "store never contains the raw token" (API-level assertion) and
  expiry slide. — _REQ-327_
  - Files: `de/gxp/auth/TokenStore.java`, test
  - Validate: `./mvnw verify`
- [x] **T-09** `BearerAuthMechanism` + `TokenIdentityProvider` (SPI of
  `quarkus-security`/`quarkus-vertx-http`): `Authorization: Bearer` →
  identity with role `admin` via `TokenStore`; missing/unknown/expired →
  401 challenge (`WWW-Authenticate: Bearer`). **Revisit
  `FailClosedTest`**: with a mechanism installed, anonymous vs.
  `@RolesAllowed` now asserts `401` + challenge (update the 403 comment/
  assertion per the foundation's recorded revisit). — _REQ-332_
  - Files: `de/gxp/auth/BearerAuthMechanism.java`,
    `de/gxp/auth/TokenIdentityProvider.java`,
    `backend/app/src/test/java/de/gxp/failclosed/FailClosedTest.java`
  - Validate: `./mvnw verify`; FailClosedTest green with updated assertions
- [x] **T-10** `AuthControl` + `AuthResource` (one-line Boundary): `login`
  (`@PermitAll`; `LoginRateLimiter` 5-fail/30-s → 429 + `Retry-After`;
  generic 401; token issue), `logout` (`@RolesAllowed("admin")`, drops
  token, 204). Tests: success, wrong password generic 401, lockout window,
  logout kills token. — _REQ-326, REQ-328, REQ-332, REQ-333, REQ-335_
  - Files: `de/gxp/auth/AuthResource.java`, `AuthControl.java`,
    `LoginRateLimiter.java`, `AdminCredential(.java)+Repository`, tests
  - Validate: `./mvnw verify`; no log statement touches password/token (grep)
- [x] **T-11** `AdminPasswordSeeder` (startup observer): seed-once from
  `GXP_ADMIN_PASSWORD`; unset/blank + no row → boot failure with clear
  message (variable name only, never the value); row present → var ignored.
  Tests at component level (call the seeder with a config stub) + a
  `@QuarkusTest` proving the test profile seeds and login works. —
  _REQ-330, REQ-333, REQ-334, REQ-335_
  - Files: `de/gxp/auth/AdminPasswordSeeder.java`, tests,
    `application.properties` (test-profile `GXP_ADMIN_PASSWORD` supply)
  - Validate: `./mvnw verify`
- [x] **T-12** `TokenExpiryResponseFilter` (adds `GXP-Token-Expires` on
  authenticated responses) + sliding proof test (expiry header moves forward
  across two requests); `changeManagerPassword`: verify current (400 on
  mismatch), policy ≥ 8 (400), store new hash, `TokenStore.clear()` — test
  with **two** live sessions both dead afterwards, new password logs in. —
  _REQ-327, REQ-331, REQ-332, REQ-335_
  - Files: `de/gxp/auth/TokenExpiryResponseFilter.java`, `AuthControl.java`,
    `AuthResource.java`, tests
  - Validate: `./mvnw verify`

## Cross-cutting

- [x] **T-13** `de.gxp.api` error mapping: `ConstraintViolationException` (and
  the JSON-parse failure case) → `400` with `ApiError` body. Test: a
  contract-invalid payload returns the `ApiError` shape. — _REQ-325_
  - Files: `de/gxp/api/ApiErrorMappers.java`, test
  - Validate: `./mvnw verify`
- [x] **T-14** `de.gxp.text` `TemplateControl`: `validate(template,
  allowedPlaceholders)` → unknown/out-of-context placeholder list;
  `resolve(template, context)` with `|time`/`|date` formatting (property
  timezone, language locale). Unit tests: all four placeholders, unknown
  named in error, standby context rejects `stay.*`, `de`/`en` time+date
  formats, literal braces handling. — _REQ-317, REQ-318, REQ-335_
  - Files: `de/gxp/text/TemplateControl.java`, tests
  - Validate: `./mvnw verify`

## Domain CRUD

- [x] **T-15** `de.gxp.asset`: upload pipeline (cap→413 while reading,
  content-vs-declared verification→400, downscale >2560, sha/dims/size over
  stored bytes), metadata list via payload-free projection, binary get with
  strong ETag + `304`, delete. Tests: cap+1 byte, PNG-declared-JPEG, a
  >2560px fixture stored ≤2560 with recomputed sha, ETag/304 roundtrip,
  list SQL projection (no payload), kind stored. — _REQ-310, REQ-316,
  REQ-324, REQ-335_
  - Files: `de/gxp/asset/*` (Resource, Control, Asset, Repository, Mapper,
    ImageProcessor), test fixtures (generated in-test, not committed
    binaries), tests
  - Validate: `./mvnw verify`
- [x] **T-16** Asset delete guard: referenced by property logo/artwork or a
  room → `409` naming the referrer (Control pre-check; FKs `RESTRICT` as
  backstop). Test both referrer kinds + deletable case. — _REQ-324, REQ-335_
  - Files: `de/gxp/asset/AssetControl.java`, tests
  - Validate: `./mvnw verify`
- [x] **T-17** `de.gxp.property`: entities/repository/mapper +
  `GET`/`PUT /api/manager/property`. PUT validates: timezone (`ZoneId.of`),
  hex (bean validation), defaults ∈ offered sets, language codes `de`/`en`,
  asset kind via `AssetControl`, template fields via `TemplateControl`
  (greeting: all four placeholders; standby: `{property.name}` only).
  Tests: GET returns seed (raw templates), full PUT roundtrip, each 400
  path, seeded neutral row assertions. — _REQ-303, REQ-304, REQ-311,
  REQ-317, REQ-318, REQ-319, REQ-334, REQ-335_
  - Files: `de/gxp/property/*`, tests
  - Validate: `./mvnw verify`; DB-level second-row rejection asserted in test
- [x] **T-18** `de.gxp.room`: CRUD; code unique/non-empty (409/400 per
  design), overrides validated against property options + asset kind;
  delete: `409` naming stays when any exist, cascades own config rows.
  Tests: CRUD, duplicate code, delete-with-stay 409, cascade proof (config
  rows gone after delete). — _REQ-305, REQ-320, REQ-334, REQ-335_
  - Files: `de/gxp/room/*`, tests
  - Validate: `./mvnw verify`
- [x] **T-19** `de.gxp.menu` part 1 — catalogue + config: entities,
  `GET /api/manager/menu/catalogue` (asserts seed = the five frontend
  tuples), config `GET`/`PUT` both scopes with per-field inheritance
  semantics and NULL-scope uniqueness. Tests: catalogue equals frontend
  tuples (keys/routes/icons/labels), property + room override roundtrip,
  per-field inheritance (label-only room row inherits enabled/sort),
  unknown key 400, duplicate NULL-scope row rejected. — _REQ-306, REQ-307,
  REQ-311, REQ-321, REQ-335_
  - Files: `de/gxp/menu/*`, tests
  - Validate: `./mvnw verify`
- [x] **T-20** `de.gxp.menu` part 2 — action cards: `GET`/`PUT` both scopes,
  whole-set replace; invariant 1 (0–4, unique sort → 400), invariant 2
  (unknown target 400; disabled-in-scope target 409), template validation
  via `TemplateControl`; two cards → same target allowed (mirror of the
  shipped frontend data). — _REQ-308, REQ-311, REQ-312, REQ-313, REQ-317,
  REQ-322, REQ-335_
  - Files: `de/gxp/menu/MenuControl.java`, `MenuResource.java`, entities,
    mappers, tests
  - Validate: `./mvnw verify`
- [x] **T-21** `de.gxp.menu` part 3 — invariant 3 disable-guard, exactly the
  design's affected-scope algorithm. Tests (the design's matrix): property
  disable vs. property cards → 409 listing cards; property disable with a
  room-level enabled-override and room-own cards → allowed; room disable vs.
  inherited property cards → 409 with room scope named; 409 body lists
  **every** offender with code `menu-item-targeted-by-cards`. — _REQ-314,
  REQ-335_
  - Files: `de/gxp/menu/MenuControl.java`, tests
  - Validate: `./mvnw verify`
- [x] **T-22** `de.gxp.stay`: CRUD (+ `roomId` filter); check_in<check_out
  400; language/appearance ∈ offered; overlap via room-row lock →
  409 naming conflict; adjacent allowed; CANCELLED ignored; concurrency
  test (two overlapping creates in parallel transactions — one must fail).
  — _REQ-309, REQ-315, REQ-323, REQ-335_
  - Files: `de/gxp/stay/*`, tests
  - Validate: `./mvnw verify`

## Hardening & docs

- [x] **T-23** Security sweep: audit test asserting **every** public Control
  method in `de.gxp.*` carries exactly one of `@PermitAll`/`@RolesAllowed`
  (reflection over the deployment archive — makes the `backend/CLAUDE.md`
  convention mechanical); anonymous 401-with-challenge test against one
  endpoint per manager Resource; grep sweep: no
  password/token/payload logging anywhere in `src/main`, committed config
  unchanged from the foundation's logging block;
  `quarkus.http.limits.max-body-size=12M` added with a comment. —
  _REQ-316, REQ-332, REQ-333, REQ-335_
  - Files: new audit test, `application.properties`
  - Validate: `./mvnw verify`; boot log zero SQL statements (re-check)
- [x] **T-24** First-run proof: a `@QuarkusTest` asserting the complete seed
  (property, options, catalogue, room, credential after seeder) on a fresh
  Dev-Services DB; compose smoke on a fresh volume (`docker compose up` →
  login → `GET /api/manager/property` 200 → `down`), README instructions
  followed literally. — _REQ-334, REQ-335_
  - Files: test; no compose changes expected
  - Validate: documented smoke-run output in the handoff (environment
    caveats as in the foundation: may need `lead`/user for Docker)
- [x] **T-25** Docs: `backend/README.md` (login/logout/password-change
  workflow incl. `GXP-Token-Expires`, first-start password + rotation,
  asset limits/allow-list, backup section unchanged — still one `pg_dump`);
  `docs/system-context.md` (new packages/components table rows, Persistence/
  Auth sections updated to "implemented for manager"; hand-off notes:
  `einstellungen` fixed chrome, `/q/openapi` decision still open for the
  next feature); `docs/features/index.md` status. — _REQ-336_
  - Files: `backend/README.md`, `docs/system-context.md`,
    `docs/features/index.md`
  - Validate: review reads them against the code
- [x] **T-26** Final gate: full `./mvnw verify` green; regenerate client
  twice → no diff; `git status` shows nothing under `frontend/` and no
  `pom.xml` dependency change; contract + backend + `packages/api-client/`
  staged to move in one commit (the user commits). — _REQ-301, REQ-302,
  REQ-335_
  - Files: —
  - Validate: command outputs recorded in the handoff
