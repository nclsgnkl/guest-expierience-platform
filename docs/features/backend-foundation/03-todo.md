# TODO: Backend Foundation
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** In Progress

> Each task traces to a REQ-ID. Keep tasks small and sequential.
> Mark a task `[scaffolding]` if it bootstraps tooling that doesn't exist yet
> (project init, test runner setup) — `dev` relaxes its build/test and
> file-count guardrails for those, see `.claude/agents/dev.md`.
>
> All design decisions (versions, generator options, package names, config
> keys, migration content, compose shape) are pinned in
> [02-design.md](02-design.md) — do not re-decide them here or during
> implementation. Version-sensitive names marked *verify* are checked against
> Quarkus 3.33 docs, then used — same intent even if a name changed.
> Work on branch `feature/backend`.

## Setup

- [x] **T-01** `[scaffolding]` Create the Quarkus project `backend/app/` —
  Maven wrapper committed, Java 21, platform BOM `io.quarkus.platform:quarkus-bom:3.33.x`
  (latest micro), groupId `de.gxp`, artifactId `gxp-backend`. Dependencies:
  exactly the constitution v1.5.0 allow-list (`quarkus-rest`,
  `quarkus-rest-jackson`, `quarkus-hibernate-orm-panache`,
  `quarkus-jdbc-postgresql`, `quarkus-hibernate-validator`,
  `quarkus-smallrye-openapi`, `quarkus-flyway`, `quarkus-security`; test:
  `quarkus-junit5`, `rest-assured`). Delete generated sample code/tests. —
  _REQ-201, REQ-202, REQ-203_
  - Files: `backend/app/pom.xml`, `backend/app/mvnw*`, `backend/app/.mvn/`,
    `backend/app/src/**` (emptied of samples), Quarkus `src/main/docker/` files (kept for T-12)
  - Validate: `./mvnw verify` green (Docker running — Dev Services starts
    Postgres since the JDBC driver is present); `./mvnw dependency:tree | grep -Ei 'elytron|jwt|lombok|mapstruct|testcontainers'`
    finds nothing brought in directly (allow-list check by diff against
    constitution)
- [x] **T-02** `[scaffolding]` Repo hygiene: ensure `backend/app/target/` is
  git-ignored, `packages/api-client/` is **not** ignored; confirm no file
  under `frontend/` is touched by this feature at any point. — _REQ-201,
  REQ-208_
  - Files: `.gitignore` (root or `backend/app/.gitignore`)
  - Validate: `git status` after a build shows no `target/` noise and no
    `frontend/` changes
- [x] **T-03** Write the initial contract `api/openapi.yaml`: tag `system`;
  operations `getSystemHealth` (`GET /api/system/health` → 200 `SystemHealth`),
  `getSystemReadiness` (`GET /api/system/ready` → 200 `SystemReadiness`, 503
  `ApiError`), `getSystemInfo` (`GET /api/system/info` → 200 `SystemInfo
  {instanceName, applicationVersion}`); shared `ApiError {code, message}`.
  OpenAPI version per design (3.1, fall back to 3.0.x only on generator gaps —
  record the choice in the README). — _REQ-201, REQ-207, REQ-211, REQ-212_
  - Files: `api/openapi.yaml`
  - Validate: file parses (generation in T-04/T-05 is the real check)
- [x] **T-04** `[scaffolding]` Wire `openapi-generator-maven-plugin` **7.24.0**
  execution `generate-jaxrs`: generator `jaxrs-spec`, `interfaceOnly=true`,
  `useJakartaEe=true`, `returnResponse=false`, models+apis only, output
  `target/generated-sources/openapi`, registered as compile source root. —
  _REQ-207_
  - Files: `backend/app/pom.xml`
  - Validate: `./mvnw compile` generates `SystemApi` + DTOs; generated code is
    not in `git status`
- [x] **T-05** `[scaffolding]` Wire execution `generate-ts-client`: generator
  `typescript-angular`, output `../../packages/api-client/`,
  `hideGenerationTimestamp=true` (+ suppress version stamp — *verify* flag
  name in 7.24.0), no `npmName` (plain sources, no package.json churn).
  Commit the generated client. — _REQ-201, REQ-208_
  - Files: `backend/app/pom.xml`, `packages/api-client/**` (generated)
  - Validate: run generation twice → `git status` clean in
    `packages/api-client/`; `frontend/package.json` unchanged

## Implementation

- [x] **T-06** Flyway baseline: `V1__foundation_baseline.sql` exactly per
  design §Data model (`instance_info` single-row table + seed);
  `quarkus.flyway.migrate-at-start=true`; assert no
  `quarkus.hibernate-orm.database.generation` beyond `none`/absent in any
  profile. — _REQ-205_
  - Files: `backend/app/src/main/resources/db/migration/V1__foundation_baseline.sql`,
    `application.properties`
  - Validate: `./mvnw quarkus:dev` logs the applied migration; test in T-15
> **Amendment 2026-08-25 (owner decision D1, constitution v1.6.0) — read
> before any `de/gxp/system/...` path below.** Every file in that package
> gained one level: `boundary/SystemResource.java`,
> `control/{SystemControl,InstanceInfoRepository}.java`,
> `entity/{InstanceInfo,InstanceInfoMapper}.java`. `InstanceInfoRepository`
> is still package-private (that is why it sits in `.control`), and
> `InstanceInfo`'s fields are still package-private too; the TYPES
> `InstanceInfo` and `InstanceInfoMapper` had to become `public` because Java
> has no sub-package visibility. Test paths are unchanged — the three
> `de.gxp.system` tests drive HTTP and stayed at the feature root. See
> `02-design.md` for the full amended table.

- [x] **T-07** BCE reference package `de.gxp.system` per design table:
  `SystemResource` (implements generated `SystemApi`, every method one
  delegating line), `SystemControl` (public, `@Transactional` where DB is
  touched, `@PermitAll` on all three methods, speaks generated DTOs),
  package-private `InstanceInfo`, `InstanceInfoRepository`
  (`PanacheRepository`), hand-written `InstanceInfoMapper`. `info()` returns
  seeded name + `quarkus.application.version`. — _REQ-210, REQ-211_
  - Files: `backend/app/src/main/java/de/gxp/system/*.java`
  - Validate: build green; review check — Boundary bodies single-statement,
    `@Transactional` only on Control, only Resource+Control public
  - _Amended 2026-08-25 (D1, constitution v1.6.0):_ these five files moved into
    `de/gxp/system/{boundary,control,entity}/` — see `02-design.md` for the
    new table. The "only Resource+Control public" validation is superseded by
    `FeatureInternalsImportTest` + `EntityExposureTest`; the paths above are
    the record of what T-07 built, not the current layout.
- [x] **T-08** Readiness behavior in `SystemControl.ready()`: probe query via
  `InstanceInfoRepository` (`SELECT 1`); on failure throw
  `ServiceUnavailableException` (503) — the pinned non-200 pattern; success
  returns `SystemReadiness(READY)`. `health()` static `UP`, no DB. —
  _REQ-212_
  - Files: `de/gxp/system/SystemControl.java`, `InstanceInfoRepository.java`
  - Validate: tests in T-13
- [x] **T-09** Fail-closed security config:
  `quarkus.security.deny-unannotated-members=true` (*verify* exact property
  name for Quarkus 3.33) in `application.properties`, with a comment naming
  the convention (every public Control method carries exactly one security
  annotation; roles `admin`/`device` reserved). — _REQ-213_
  **Amended 2026-08-24 (D2):** that comment now describes the **Boundary**, and
  `quarkus.security.jaxrs.deny-unannotated-endpoints=true` sits beside the
  property this step added.
  - Files: `backend/app/src/main/resources/application.properties`
  - Validate: tests in T-14
- [x] **T-10** Logging as a security surface, all profiles:
  `quarkus.hibernate-orm.log.sql=false`, bind-parameter logging off (*verify*
  property name), `quarkus.http.access-log.enabled=false`, no
  `org.hibernate` DEBUG/TRACE category; in-file comment citing constitution
  principle 3. — _REQ-214_
  - Files: `application.properties`
  - Validate: grep committed config for sql/bind/access-log enablement → none;
    boot log of `quarkus:dev` shows no SQL statements when hitting `/api/system/info`
- [x] **T-11** Datasource profiles: dev/test carry **no** datasource URL (Dev
  Services provisions Postgres, image pinned to the compose major); `%prod`
  reads `quarkus.datasource.jdbc.url`/username/password from env vars matching
  `compose.yaml`. — _REQ-204_
  - Files: `application.properties`
  - Validate: `./mvnw verify` with no local Postgres and no URL configured
    passes (Dev Services); `quarkus:dev` same
- [x] **T-12** `backend/compose.yaml` per design: services `app` (image
  `gxp-backend:<tag>`, JVM Dockerfile from the scaffold, depends_on healthy
  `db`) + `db` (Postgres 17, exact tag *verified* at scaffold); **one** named
  volume `gxp-pgdata`; required interpolation for `${GXP_DB_PASSWORD:?}` and
  `${GXP_ADMIN_PASSWORD:?}` (passthrough only — app-level enforcement is the
  auth feature's); HTTP only, marked dev/preview-only pending TLS. —
  _REQ-206_
  - Files: `backend/compose.yaml`, `backend/app/src/main/docker/*` (as
    scaffolded)
  - Validate: `docker compose config` fails without `GXP_ADMIN_PASSWORD`,
    succeeds with it; `docker compose up` → `GET /api/system/ready` 200 →
    `docker compose down` (volume persists)

## Tests

- [x] **T-13** rest-assured tests for the system endpoints (Dev Services):
  `/api/system/health` → 200 `UP`; `/api/system/ready` → 200 `READY`;
  `/api/system/info` → 200 with `instanceName` equal to the V1-seeded value
  (asserted against the response, not duplicated app-side) and non-empty
  `applicationVersion`. Calls use the contract paths, so drift fails the
  suite. — _REQ-211, REQ-212, REQ-215, REQ-204_
  - Files: `backend/app/src/test/java/de/gxp/system/SystemResourceTest.java`
  - Validate: `./mvnw verify` green
- [x] **T-14** Fail-closed proof (test-only Control/Resource pair in
  `src/test`, never shipped): unannotated method in an annotated Control →
  401/403; `@RolesAllowed("admin")` method → 401 anonymous; `@PermitAll`
  sibling → 200. — _REQ-213, REQ-215_
  - Files: `backend/app/src/test/java/de/gxp/security/FailClosedTest.java`
    (+ test-only fixture classes)
  - Validate: `./mvnw verify` green
- [x] **T-15** Migration test: assert Flyway applied `V1` (e.g.
  `flyway_schema_history` row / `instance_info` present with exactly one row)
  in the Dev Services database. — _REQ-205, REQ-215_
  - Files: `backend/app/src/test/java/de/gxp/system/MigrationTest.java`
  - Validate: `./mvnw verify` green
- [x] **T-16** Chain-proof rehearsal (validation, then revert the scratch
  change): (a) add a temporary field to `SystemInfo` in `api/openapi.yaml` →
  build → confirm the generated DTO and `packages/api-client/` diff; (b)
  remove an operation → confirm **compile failure** in `SystemResource`;
  revert both. Keep the *final* state committing `api/openapi.yaml`, backend
  code and `packages/api-client/` together. — _REQ-209_
  - Files: none permanently (rehearsal); evidence noted in the implementation
    log / README
  - Validate: both observations reproduced; working tree back to intended
    state

## Docs & cleanup

- [x] **T-17** `backend/README.md`: **First run** (compose, required env vars,
  what refuses when unset, dev/preview-only-until-TLS warning), **Changing the
  API contract** (the REQ-209 workflow verbatim from design §Sequence),
  **Backup & restore** (one `pg_dump` of the named volume's DB; restore
  steps), Dev Services prerequisites (Docker), pinned versions and the OpenAPI
  version choice from T-03. — _REQ-217, REQ-209_
  - Files: `backend/README.md`
  - Validate: a reader can go from clean checkout to a serving instance with
    only this file
- [x] **T-18** ~~⏸ Approval-gated~~ **Done — approved and written 2026-08-19.**
  The user approved the design's proposed wording **verbatim** ("Yes you can
  add it exactly like this") in the `lead` session, and `lead` wrote
  `backend/CLAUDE.md` itself (nested `CLAUDE.md` files are orchestration-owned)
  while `dev` was still running. This is why `dev` — correctly, from its
  isolated context — flagged the file as an unexplained appearance at 10:47:
  it could not see the approval. The gate was honored: exact text proposed
  first, confirmed by the user, then written unchanged. — _REQ-216_
  - Files: `backend/CLAUDE.md`
  - Validate: file content equals the approved text ✅ (written from the
    approved text itself)
- [x] **T-19** Update `docs/system-context.md`: add the now-real components
  (`backend/app` system package, `api/openapi.yaml`, `packages/api-client/`,
  compose unit) to the component/integration tables — real code only, per that
  file's own rule — and update `docs/features/index.md` status. — _REQ-217,
  REQ-201_
  - Files: `docs/system-context.md`, `docs/features/index.md`
  - Validate: tables reflect the shipped slice; no decided-but-unbuilt item
    listed as real

---

## Implementation-environment note (2026-08-19, dev)

Checked tasks are **implemented and compile-verified**; the validations that
need a running Docker daemon could **not** be executed in the implementation
environment (Raspberry Pi; docker socket not accessible to the dev user, no
local JDK — a session-local Temurin 21 was provisioned for the build):

- T-01/T-11/T-13/T-14/T-15: `./mvnw verify` **with tests** (Dev Services
  cannot start Postgres — `DevServicesDatasourceProcessor: Please configure
  the datasource URL … or ensure the Docker daemon is up and running`).
  Test code is written and compiles; run `./mvnw verify` on a Docker-enabled
  machine before review sign-off.
- T-06/T-10: `quarkus:dev` boot-log observations (migration applied, no SQL
  statements logged) — same Docker dependency.
- T-12: `docker compose config` **was** validated (fails without the required
  vars, succeeds with them; exactly one named volume); `docker compose up`
  → `/api/system/ready` 200 was not runnable.

Everything not requiring the daemon was executed and passed: scaffold `verify`
(pre-tests) green, `compile`/`test-compile` green, dependency-tree allow-list
grep clean, TS-client generation byte-identical across runs, chain proof
(T-16) reproduced in both directions.
