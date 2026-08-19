# PRD: Backend Foundation
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** In Review

> First feature of the backend cut proposed in `docs/backend/specification.md`
> §12. Sanctioned by constitution **v1.5.0** (2026-08-19). The spec's §2
> decision log is **settled ground** — nothing in it is re-opened here.
> Scope discipline is the spec's own instruction: *"Start with
> `backend-foundation`, and do not let it grow."*

## Problem statement

The backend is decided (constitution v1.4.0/v1.5.0) but nothing of it exists:
no Quarkus project, no database, no migrations, no API contract, no generated
client. Four more backend features (`backend-domain-config`,
`device-pairing-and-guest-api`, `guest-frontend-integration`,
`manager-frontend`) are queued behind this one, and all of them assume a
working contract-first toolchain and the strict BCE conventions of spec §4.

**The feature's real job** (spec §12): prove that a change to
`api/openapi.yaml` propagates into both a Java interface and a TypeScript
client **in one commit**. If that chain does not work, everything after it is
built on a contract that is only aspirational.

## Goals / Non-goals

**Goals**

1. A building Quarkus scaffold (`backend/app/`, Maven, Java 21, current LTS
   platform) using exactly the approved dependency allow-list.
2. PostgreSQL wired via Quarkus Dev Services for dev and test — zero manual
   database work.
3. The Flyway mechanism established: a minimal `V1` migration, applied at
   startup.
4. The BCE package conventions of spec §4 established with one concrete
   reference package.
5. The OpenAPI toolchain **end to end**: `api/openapi.yaml` → generated JAX-RS
   interfaces + DTOs (implementations hand-written) → generated TypeScript
   client in `packages/api-client/`.
6. One trivial endpoint proving the whole chain, plus health/readiness
   endpoints so a person in the building can tell whether the box is the
   problem (spec §9).
7. `backend/compose.yaml` (app + Postgres, one named volume) as the deployment
   unit.
8. The fail-closed security posture (`quarkus-security`, deny-unannotated,
   `@RolesAllowed`/`@PermitAll` on Control methods) configured and proven by
   test — even though the auth endpoints themselves are later features.
9. Logging configured as a security surface from day one (no SQL parameters,
   no request bodies — constitution principle 3 verbatim).
10. A proposed `backend/CLAUDE.md` (exact text; written only after user
    approval).

**Non-goals** (owner decisions of 2026-08-19 — explicitly out of scope)

- Domain entities and the full first-run seed (Property row, menu catalogue,
  default room) → `backend-domain-config`.
- Manager CRUD API, manager login/logout, admin-password seeding and the
  refuse-to-start-if-unset enforcement in the app → later features. The
  password **model** (env var seeds on first start only; thereafter hashed in
  Postgres via JDK PBKDF2; every change drops all bearer tokens) is *recorded*
  in the design, not implemented.
- Device pairing, device tokens, TLS/CA generation, `/api/guest/**` →
  `device-pairing-and-guest-api`.
- Any frontend work: `frontend/` stays untouched; no consumption of the
  generated TS client; no Angular scaffolding under `backend/manager-ui/` and
  no Maven copy-step wiring for it (the copy-step *decision* is settled and
  recorded; wiring lands with `manager-frontend`, when there is a `dist` to
  copy).
- `packages/ui/` (shared Angular library) → `manager-frontend`.
- Native-image build configuration beyond Quarkus defaults.
- Spec §11 open items 6–9 (clock/NTP, backup responsibility, update process,
  sensitive-field list) — owner-level, not guessed here, and none blocks this
  feature.

## Assumptions

- Constitution v1.5.0 is ratified; the backend dependency allow-list and the
  infrastructure decisions (PostgreSQL, Flyway, Compose, Dev Services) are
  **already approved** — creating `backend/app/pom.xml` with exactly that list
  is covered and needs no fresh dependency approval.
- Spec §2 decision log and the two 2026-08-19 session decisions (manager-UI
  copy step; admin-password lifecycle) are settled; this PRD only plans within
  them.
- Docker is available on dev machines (Dev Services and Compose need it).
- Work happens on `feature/backend` (branch discipline).

## User stories

- As the **next backend feature** (`backend-domain-config`), I want a working
  scaffold, migration mechanism, BCE reference package and contract toolchain
  so that I can add domain entities and endpoints without inventing
  conventions.
- As a **developer changing the API**, I want a contract edit to regenerate
  the Java interface and the TS client in one build so that a contract breach
  fails the build, not the runtime.
- As a **person in the building**, I want health and readiness endpoints so
  that I can tell whether the box or the network is the problem.
- As the **project owner**, I want the fail-closed security posture and the
  no-sensitive-logging configuration in place from the first commit so that
  later features inherit safety instead of retrofitting it.

## Requirements

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-201 | The monorepo layout of spec §3 exists for this feature's slice: `api/openapi.yaml`, `backend/app/` (Quarkus Maven project), `backend/compose.yaml`, `packages/api-client/` (generated). `frontend/` is untouched (no file under it changes). | Must |
| REQ-202 | `backend/app/` builds green with Maven (wrapper committed), Java 21, the current Quarkus **LTS** platform BOM (pinned in the design), imperative stack (`quarkus-rest`, no reactive extensions). | Must |
| REQ-203 | The dependency set is **exactly** the constitution v1.5.0 allow-list — no Elytron, no smallrye-jwt, no mapping framework, no Lombok, no Testcontainers dependency, nothing else. Build-time plugins limited to Quarkus's own plus `openapi-generator-maven-plugin`. | Must |
| REQ-204 | Dev and test runs get PostgreSQL via **Quarkus Dev Services** with zero manual database setup; production configuration reads the datasource from environment variables. | Must |
| REQ-205 | Flyway is wired to **migrate at startup**; a minimal versioned `V1` migration exists, applies on boot and in tests, and no Hibernate schema generation is active in any profile. | Must |
| REQ-206 | `backend/compose.yaml` defines app + Postgres with **one named volume**; required environment variables (DB credentials, admin password) are declared with compose-level required interpolation, so a missing admin password fails `docker compose up` at once (app-level enforcement follows in the auth feature). | Must |
| REQ-207 | The Maven build generates JAX-RS **interfaces + DTOs** from `api/openapi.yaml` into non-committed generated sources; Resources implement the generated interfaces, so contract drift is a **compile failure**. | Must |
| REQ-208 | The same build generates the **TypeScript client** into `packages/api-client/`; the output is committed, deterministic (re-running generation with an unchanged contract produces no diff), never hand-edited, and requires no new npm dependency in any app. | Must |
| REQ-209 | The chain is **proven**: a change to `api/openapi.yaml` demonstrably propagates into both the Java interface (build breaks until implemented) and the TS client (diff in `packages/api-client/`) in one commit; the workflow is documented. | Must |
| REQ-210 | One reference feature package (`system`) follows spec §4 **strictly**: Boundary methods are one delegating line; `@Transactional` on the Control; the Control is the package's only public type and speaks generated DTOs; Entity, Repository (Panache repository), Mapper are package-private; the mapper is hand-written. | Must |
| REQ-211 | A trivial endpoint `GET /api/system/info` returns instance metadata of which at least one field is read **from PostgreSQL through the full BCE stack** (Flyway-seeded row → Entity → Repository → Mapper → DTO). | Must |
| REQ-212 | Health and readiness endpoints exist as hand-written, contract-declared endpoints (no new dependency): `GET /api/system/health` answers statically when the process serves HTTP; `GET /api/system/ready` verifies database connectivity and returns 503 when it is absent. | Must |
| REQ-213 | The security posture **fails closed**: `quarkus-security` with deny-unannotated configuration active; the system endpoints are explicitly `@PermitAll` on the Control; tests prove that (a) an unannotated method in an annotated Control is denied and (b) a `@RolesAllowed` method rejects anonymous callers. | Must |
| REQ-214 | Logging is configured as a security surface in committed configuration for **all** profiles: SQL statement and bind-parameter logging off, HTTP access/request-body logging off, with an in-file comment citing constitution principle 3. | Must |
| REQ-215 | Every implemented behavior has a test (`quarkus-junit5` + `rest-assured` against Dev Services): info content, health, readiness, fail-closed denial, and the applied `V1` migration. | Must |
| REQ-216 | A `backend/CLAUDE.md` is **proposed as exact text** in the design; the file is written only after the user approves the wording (constitution → Decisions Requiring Your Approval). | Must |
| REQ-217 | `backend/README.md` documents: first run (compose + required env vars), the contract-change workflow (REQ-209), and backup/restore as one `pg_dump`/restore (spec §9). `docs/system-context.md` gains the new real components after implementation. | Must |

## Acceptance criteria (testable)

- **REQ-201:** `api/openapi.yaml`, `backend/app/pom.xml`, `backend/compose.yaml`
  and `packages/api-client/` exist; `git status` shows no modification under
  `frontend/`.
- **REQ-202:** `./mvnw verify` in `backend/app/` exits 0 on Java 21; the
  platform BOM version matches the one pinned in `02-design.md`.
- **REQ-203:** `pom.xml` dependency list equals the allow-list — reviewable by
  diffing against constitution v1.5.0 → "Approved backend dependencies";
  `mvn dependency:tree` shows none of the named non-additions.
- **REQ-204:** with Docker running and no datasource URL configured,
  `./mvnw quarkus:dev` and `./mvnw verify` start Postgres automatically; no
  README step says "create a database".
- **REQ-205:** on boot, Flyway logs applying `V1`; a test asserts the seeded
  row/table exists; `quarkus.hibernate-orm.database.generation` is absent or
  `none` in every profile.
- **REQ-206:** `docker compose config` fails with a clear message when
  `GXP_ADMIN_PASSWORD` is unset and succeeds when set; exactly one named
  volume is defined; `docker compose up` yields a serving app whose
  `/api/system/ready` returns 200.
- **REQ-207:** deleting one operation from `api/openapi.yaml` and rebuilding
  fails compilation in `backend/app` (verified once during implementation,
  then reverted); generated Java sources are not committed.
- **REQ-208:** running generation twice in a row leaves `git status` clean in
  `packages/api-client/`; `frontend/package.json` is unchanged.
- **REQ-209:** the README documents the workflow; the feature's implementation
  history contains at least one commit where `api/openapi.yaml`, a hand-written
  Resource/Control and `packages/api-client/` change together.
- **REQ-210:** review check: every `SystemResource` method body is a single
  `return`/delegation statement; `@Transactional` appears only on the Control;
  `javap`-level visibility: only the Control is `public` besides the Boundary
  (the Boundary is the JAX-RS adapter and carries no logic).
- **REQ-211:** `GET /api/system/info` returns 200 with a name field whose
  value matches the Flyway-seeded row (asserted by test, not by fixture
  duplication in application code).
- **REQ-212:** `GET /api/system/health` → 200 with status `UP` even before the
  DB is used; `GET /api/system/ready` → 200 with the DB up (Dev Services) —
  the 503 path is covered by the Control unit behavior (connectivity failure →
  503), not by killing Docker mid-suite.
- **REQ-213:** test: anonymous call to the deliberately unannotated test
  method → 401/403; anonymous call to a `@RolesAllowed("admin")` test method
  → 401; the deny-unannotated property is set in committed config.
- **REQ-214:** committed `application.properties` contains the explicit
  logging settings and the principle-3 comment; grepping committed config for
  SQL/bind-parameter/access-log enablement finds none in any profile.
- **REQ-215:** `./mvnw verify` runs the tests above green; each of REQ-211,
  212, 213, 205 maps to at least one named test.
- **REQ-216:** `02-design.md` contains the full proposed text; `backend/CLAUDE.md`
  does not exist until the user has approved it.
- **REQ-217:** README sections "First run", "Changing the API contract",
  "Backup & restore" exist; `docs/system-context.md` lists the new components
  once they are real code.

## Technical constraints

Anchored to `docs/constitution.md` v1.5.0 (all binding here):

- Backend stack constraint: Java 21 + Quarkus + Maven, imperative, BCE per
  spec §4, Panache repositories, contract-first OpenAPI.
- Backend infrastructure constraint: PostgreSQL only, Flyway at startup,
  Docker Compose deployment, first run usable without manual DB work.
- Approved backend dependency allow-list — closed; named non-additions stay
  out.
- Auth & transport constraint: fail-closed `@RolesAllowed` +
  deny-unannotated posture (configured here; endpoints later).
- Principle 3 (no sensitive data in logs) applies to the backend verbatim —
  Hibernate SQL logging is the most likely accidental breach (spec §9).
- Principle 6 (tests accompany code), principle 7 (traceability), branch
  discipline, secrets never hardcoded, input validated at every boundary
  (`quarkus-hibernate-validator` is in the list for this; the foundation's
  endpoints take no input, so its first real use lands with domain-config).
- No dependency-manifest edits beyond the already-approved allow-list.

## Risks & open questions

- **No TLS yet.** HTTPS with the instance CA arrives with
  `device-pairing-and-guest-api` (spec decision 18). Until then the compose
  deployment is **dev/preview only** — the README must say so, and no real
  building deployment happens off this feature alone.
- **Quarkus LTS window.** The pinned LTS (see design) is supported ~12
  months; keeping instances current ties into spec §11 item 8 (update
  process, owner-level, open). Not blocking; recorded.
- **Generated TS client vs. Angular 22.** The `typescript-angular` generator's
  `ngVersion` support must be checked when the client is first *consumed*
  (`guest-frontend-integration`). Foundation only generates and commits it;
  a mismatch would surface there, not here. Recorded as a hand-off note.
- **Exact config property names** (deny-unannotated, bind-parameter logging)
  are version-sensitive and are verified against the pinned Quarkus release at
  scaffold time, per constitution; the design records intent + the expected
  names.
- Spec §11 items 6–9 remain open with the owner; none blocks this feature.
- **Pending approval (not a guess, not blocking implementation):** the exact
  `backend/CLAUDE.md` wording — proposed in `02-design.md`, written only after
  user confirmation (REQ-216).
