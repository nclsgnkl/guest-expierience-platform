# Design: Backend Foundation
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** In Review

> HOW for the requirements in [01-prd.md](01-prd.md). Settled ground:
> `docs/backend/specification.md` §2–§9 and constitution v1.5.0 — decisions
> recorded there are referenced, not re-made.

## Pinned versions (verified 2026-08-19)

| What | Pin | Verification |
|---|---|---|
| Quarkus platform | **3.33 (LTS)** — latest micro at scaffold time (`io.quarkus.platform:quarkus-bom:3.33.x`) | endoflife.date/quarkus, 2026-08-19: 3.33 LTS released 2026-03-25, supported to 2027-03-25. The older 3.27 LTS expires 2026-09-24 → rejected. Non-LTS 3.38 rejected: spec decision 12 prefers the LTS stream |
| Java | **21** (LTS) | Constitution constraint; spec decision 12 |
| `openapi-generator-maven-plugin` | **`org.openapitools:openapi-generator-maven-plugin:7.24.0`** | Maven Central, 2026-08-19 (latest release) |
| PostgreSQL image | `postgres:17-<current minor>` in `compose.yaml`; Dev Services default image aligned to the same major | Verify exact tag at scaffold time; major 17 chosen as current stable. Postgres itself is approved infrastructure (constitution v1.4.0) — the image tag is an implementation detail |

Version-sensitive **config property names** (deny-unannotated, bind-parameter
logging) are verified against Quarkus 3.33 docs at scaffold time; expected
names are listed below with that caveat. `dev` must not silently substitute a
*different mechanism* if a name changed — same intent, corrected name.

## Architecture decision

### Repository slice (REQ-201)

```
api/
  openapi.yaml                  # the contract — source of truth
backend/
  app/                          # Quarkus, Maven (wrapper committed), Java 21
    src/main/java/de/gxp/...
    src/main/resources/application.properties
    src/main/resources/db/migration/V1__foundation_baseline.sql
    src/test/java/de/gxp/...
  compose.yaml
packages/
  api-client/                   # GENERATED TypeScript client — committed, never hand-edited
frontend/                       # untouched
```

Maven coordinates: groupId `de.gxp`, artifactId `gxp-backend`, base package
`de.gxp` (spec §4 uses `de.<org>.gxp.<feature>`; with no org name in play the
shorter `de.gxp.<feature>` is the pinned convention — a naming choice, fixed
before first commit, not approval-gated). Single Maven module; the TS-client
generation writes outside the module tree to `../../packages/api-client/`
(deliberate: spec §3 forbids shared packages living under `backend/`).

### Contract toolchain (REQ-207, REQ-208, REQ-209)

Two executions of `openapi-generator-maven-plugin` bound to
`generate-sources`, both reading `../../api/openapi.yaml`:

| Execution | Generator | Output | Committed? |
|---|---|---|---|
| `generate-jaxrs` | `jaxrs-spec`, `interfaceOnly=true`, `useJakartaEe=true`, `returnResponse=false`, models + apis only (no supporting scaffold) | `target/generated-sources/openapi` (added to compile source root) | **No** — regenerated every build; `.gitignore`d via `target/` |
| `generate-ts-client` | `typescript-angular`, `hideGenerationTimestamp=true` (plus whatever flag suppresses the generator-version stamp in 7.24 — verify) | `packages/api-client/` | **Yes** — a diff there means the contract changed |

Why this split is the design (not merely convenient):

- **Java side not committed:** the backend cannot compile against a stale
  interface — every build regenerates, so drift between contract and
  implementation is a **compile failure** (REQ-207). Committing it would allow
  the contract and the interfaces to diverge silently.
- **TS side committed:** the frontends must consume the client **without
  running Maven**; and the visible diff is the review signal the spec demands
  ("a diff there means the contract changed"). Determinism
  (`hideGenerationTimestamp`) is what makes "no diff" meaningful (REQ-208).
- `typescript-angular` output uses only `@angular/common/http` + `rxjs` —
  already present in `frontend/` — so **no npm manifest changes anywhere**
  (spec §8's "pleasant consequence"). No `npmName` is configured: plain
  sources, consumed by path later, not a publishable npm package.
- Angular-version note for the hand-off: the generator's `ngVersion` ceiling
  vs. Angular 22 is checked when the client is first consumed
  (`guest-frontend-integration`) — recorded in the PRD risks.

**Chain proof (REQ-209):** during implementation, one rehearsed contract
change (add a field → build regenerates both sides; remove an operation →
compile failure until the Resource follows) is performed and reverted; the
final feature commit(s) contain `api/openapi.yaml` + backend code +
`packages/api-client/` moving together. The workflow is documented in
`backend/README.md`.

### BCE reference package `de.gxp.system` (REQ-210, REQ-211, REQ-212)

The one feature package this feature ships — the concrete template every later
package copies. Strict spec §4 shape:

| Type | Role | Visibility |
|---|---|---|
| `SystemResource` | Boundary — implements the generated `SystemApi` interface; every method body is **one delegating line** to the Control | public (JAX-RS adapter only) |
| `SystemControl` | Control — all behavior; `@Transactional` on DB-touching methods; security annotations live here; speaks generated DTOs; the package's entry point | **public** |
| `InstanceInfoRepository` | Control (data access) — `PanacheRepository<InstanceInfo>`; also hosts the readiness probe query (`SELECT 1`) | package-private |
| `InstanceInfo` | Entity — no persistence methods | package-private |
| `InstanceInfoMapper` | Entity → `SystemInfoDto`, hand-written | package-private |

**Amended 2026-08-25 (owner decision D1, constitution v1.6.0).** The table
above is the ORIGINAL flat shape and is kept as the record of what was built
on 2026-08-19. `de.gxp.system` was the first package migrated to the
`boundary` / `control` / `entity` sub-package layout — it was chosen to go
first precisely because it is the reference package, it is five files, and
nothing else in the codebase imports it, so the scheme could be proven at
runtime before the nine harder packages follow. The shape now is:

| Type | Sub-package | Role | Visibility |
|---|---|---|---|
| `SystemResource` | `boundary` | unchanged — the JAX-RS adapter, and the only thing in `boundary` | public |
| `SystemControl` | `control` | unchanged, except that security annotations moved to the Boundary (D2) | **public** — the only type another feature may reference |
| `InstanceInfoRepository` | `control` | unchanged | **package-private** — it lives in `.control` rather than beside the Entity for exactly this reason |
| `InstanceInfo` | `entity` | unchanged | **public** type, package-private FIELDS (see below) |
| `InstanceInfoMapper` | `entity` | unchanged | **public** |

Two notes that will matter when the remaining packages migrate:

- Entity and Mapper are `public` only because Java has no sub-package
  visibility. Nothing about the boundary being enforced changed in intent —
  only who enforces it. The compiler no longer can; `FeatureInternalsImportTest`
  and `EntityExposureTest` (`de.gxp.architecture`) do, and the constitution
  makes them part of the definition of done. **A third guard,
  `ControlPackageVisibilityTest`, was added 2026-08-25** before the
  collaborator-heavy packages migrated: only a `*Control` may be public inside
  a `.control` package, so `InstanceInfoRepository` — and the two dozen
  repositories, hashers, SPI adapters and helpers that followed it into
  `.control` — cannot be quietly widened. **Widened to `.boundary` later the
  same day**, after the migration review walked a public
  `StayView extends Stay` in `de.gxp.stay.boundary` past all three guards:
  the source scan now covers the `.boundary.` prefix with no exception, the
  visibility guard admits only a `*Resource` there, and `EntityExposureTest`
  now also reads the supertypes of every public type, so an entity that is
  inherited rather than declared is caught too. See `01-prd.md` → REQ-210.
- `InstanceInfo`'s **fields stayed package-private**. The accepted cost of D1
  is ~97 public entity fields across the whole migration, because Controls
  elsewhere mutate entity state directly across the new sub-package line
  (**actual figure once all ten packages had migrated on 2026-08-25: 58 of the
  17 entities' 97 fields — 39 stayed package-private**, because the per-field
  rule below was applied throughout rather than widening whole entities; the
  ~97 estimate was an upper bound from static analysis, not a target) — but
  `SystemControl` does not touch a single `InstanceInfo` field (only the
  Mapper does, and the Mapper lives in `.entity` beside it). Widening those
  three fields would have been widening for nothing. Widen per field, where a
  Control actually needs it.

Behavior:

- `info()` — loads the single `instance_info` row, maps to the generated DTO,
  adds `applicationVersion` from `quarkus.application.version` config. Proves
  Flyway seed → Entity → Repository → Mapper → DTO → generated interface → TS
  client, i.e. **the entire stack** (REQ-211).
- `health()` — static `UP` DTO; no DB touch, so it answers whenever the
  process serves HTTP.
- `ready()` — runs the probe query; on failure throws
  `jakarta.ws.rs.ServiceUnavailableException` (503). Throwing from the
  **Control** keeps the Boundary one line — exception mapping is the JAX-RS
  runtime's job, not Boundary logic. This is the pinned pattern for
  non-200 outcomes under the one-line rule (later features reuse it, e.g. the
  409 of spec §5 invariant 3).

**Health without `quarkus-smallrye-health`:** the allow-list has no health
extension, deliberately — constitution principle 4 prefers platform features
over dependencies, and these two endpoints are a handful of lines. They are
**contract-declared** under `/api/system/…` (not `/q/…`), which both proves
the toolchain on real paths and gives kiosks/operators a stable, documented
URL. This follows from already-approved constraints (closed allow-list); it is
not a new pattern requiring approval.

### Security posture (REQ-213)

- Config (names to verify against 3.33): `quarkus.security.deny-unannotated-members=true`
  — a class carrying at least one security annotation gets `@DenyAll` on every
  unannotated member.
- Convention pinned here and enforced at review (and proposed for
  `backend/CLAUDE.md`): **every public Control method carries exactly one of
  `@PermitAll` / `@RolesAllowed(...)`** — annotations sit on the Control, per
  constitution ("authorization hangs off `@RolesAllowed` on Control methods"),
  never on the Boundary. `SystemControl`'s three methods are `@PermitAll`
  (they are the box-diagnosis endpoints; nothing sensitive crosses them).
  - **Amended 2026-08-24 (owner decision D2, constitution v1.6.0 — this
    convention is reversed):** the annotation carrier is the **Boundary**. The
    constitution sentence quoted above was rewritten with it; Controls now carry
    none, and `quarkus.security.jaxrs.deny-unannotated-endpoints=true` (global,
    build-time) makes an unannotated *endpoint* fail closed — which the
    class-scoped `deny-unannotated-members` cannot do once a Control has no
    annotations at all. `SystemResource` (not `SystemControl`) now carries
    `@PermitAll` on `/health` and `/ready`, `@RolesAllowed({"admin","device"})`
    on `/info` (REQ-432). This also **supersedes the "honest residual gap"
    bullet below**: the Control with zero security annotations is no longer
    hypothetical, it is every Control, so `deny-unannotated-members` is inert
    and the every-method Control convention that used to close the gap no
    longer exists — `quarkus.security.jaxrs.deny-unannotated-endpoints=true`
    is what closes it now, globally and at the endpoint, and the review-time
    grep is replaced by `BoundaryAnnotationAuditTest` over the Boundaries.
- **Honest residual gap, recorded:** `deny-unannotated-members` covers classes
  with ≥1 annotation; a hypothetical Control with *zero* security annotations
  would not be auto-denied. The every-method convention above closes it and is
  mechanically checkable at review (grep Controls for public methods lacking an
  annotation). Role names pinned now for later features: `admin`, `device`.
- Proof by test (in `src/test`, so the classes never ship): a test-only
  Control/Resource pair with (a) one `@PermitAll` method, (b) one unannotated
  method → expect denial, (c) one `@RolesAllowed("admin")` method → expect 401
  for anonymous. No identity provider is configured yet — that is exactly the
  fail-closed point.

### Logging as a security surface (REQ-214)

Committed `application.properties`, applying to **every profile including
`%dev`** (a comment cites constitution principle 3):

- `quarkus.hibernate-orm.log.sql=false` (explicit, though default)
- bind-parameter logging off (expected `quarkus.hibernate-orm.log.bind-parameters=false`
  — verify name) and no `org.hibernate` DEBUG/TRACE category in any committed
  profile
- `quarkus.http.access-log.enabled=false` (explicit) — no request logging, no
  body logging
- No committed config may raise these; a developer flipping them locally keeps
  that uncommitted.

### Datasource & Dev Services (REQ-204)

No datasource URL in dev/test config → Quarkus Dev Services provisions
Postgres (Docker) automatically, for `quarkus:dev` and for `@QuarkusTest` —
no Testcontainers dependency (allow-list). `%prod` reads
`quarkus.datasource.jdbc.url` / username / password from environment variables
(the ones `compose.yaml` supplies). Dev Services image pinned to the same
Postgres major as compose so tests run against what ships.

### Compose deployment unit (REQ-206)

`backend/compose.yaml`:

- Services: `app` (image `gxp-backend:<tag>`, built from the
  Quarkus-generated JVM Dockerfile; depends_on `db` with healthcheck) and `db`
  (pinned Postgres image).
- **One named volume** (`gxp-pgdata`) for the database — the whole instance
  backs up as one `pg_dump` (spec decision 10 / §9).
- Env vars with **required interpolation**: `POSTGRES_PASSWORD`
  (`${GXP_DB_PASSWORD:?...}`) and `GXP_ADMIN_PASSWORD`
  (`${GXP_ADMIN_PASSWORD:?set the initial admin password}`) — so first run
  fails fast at the compose level when unset. The *app-level* seeding +
  refuse-to-start logic is the auth feature's (model recorded below); passing
  the variable through now means that feature adds code, not deployment
  surface.
- Ports: HTTP only for now — **no TLS until `device-pairing-and-guest-api`**;
  README marks the compose unit dev/preview-only until then (PRD risk).

### Manager-UI copy step (recorded, not wired)

Owner decision 2026-08-19 (constitution v1.5.0): the manager UI enters the
artifact via a **plain Maven copy step** of its built `dist` into
`META-INF/resources`; Quinoa rejected. **No wiring in this feature** — there
is no `dist` to copy, and dead build config would rot. The decision is
recorded here and in the proposed `backend/CLAUDE.md`; the copy step lands
with `manager-frontend`.

### Admin-password model (recorded, not implemented)

Settled 2026-08-19 (constitution v1.5.0): env var seeds the password on
**first start only** — the app refuses to start if the var is unset AND no
password row exists yet; thereafter the hash (JDK PBKDF2,
`PBKDF2WithHmacSHA256`) lives in Postgres and is changed via an authenticated
manager-UI endpoint; every change drops all active bearer tokens. Nothing of
this is scoped into the foundation beyond the compose env passthrough above.
Even the credential *table* waits: the feature that implements seeding owns
its migration, so schema and code land together.

## Data model (REQ-205, REQ-211)

`V1__foundation_baseline.sql` — deliberately minimal, but real enough to
prove the mechanism end to end (DDL + seed + a consumer):

```sql
CREATE TABLE instance_info (
    id          smallint PRIMARY KEY CHECK (id = 1),  -- single row, spec §5 style
    name        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
INSERT INTO instance_info (id, name) VALUES (1, 'Guest Experience Platform');
```

- `name` is **platform** metadata (the product name), not the property's name
  — the `Property` entity and its seed belong to `backend-domain-config`
  (constitution principle 1 untouched: nothing property-specific is code or
  seed here).
- Recorded for the successor feature: `backend-domain-config` may absorb or
  drop `instance_info` in its own migration (`V2+`) once `Property` exists.
- Config: `quarkus.flyway.migrate-at-start=true`; no
  `quarkus.hibernate-orm.database.generation` beyond `none`/absent, in any
  profile (spec decision 15).

## API contract (`api/openapi.yaml`, initial)

OpenAPI 3.1 (fall back to 3.0.x only if generator 7.24 has 3.1 gaps —
verify at scaffold; pin whichever is chosen in the file's `openapi:` field and
note it in the README). Tag `system`, three operations — full shapes live in
the YAML; summarized:

| Operation | Path | Responses |
|---|---|---|
| `getSystemHealth` | `GET /api/system/health` | 200 `SystemHealth { status: "UP" }` |
| `getSystemReadiness` | `GET /api/system/ready` | 200 `SystemReadiness { status: "READY" }`; 503 `ApiError` when DB unreachable |
| `getSystemInfo` | `GET /api/system/info` | 200 `SystemInfo { instanceName: string, applicationVersion: string }` |

`ApiError { code: string, message: string }` is introduced now as the shared
error shape later features extend (the 409-with-offending-cards of spec §5
will need it). No auth in the contract yet; `securitySchemes` (bearer) are
added by the features that add authenticated endpoints.

## Sequence: contract-change workflow (the thing REQ-209 proves)

```
edit api/openapi.yaml
        │
   ./mvnw verify  ──► generate-jaxrs ──► SystemApi.java changes ──► compile fails
        │                                                            until Resource/Control follow
        ├────────────► generate-ts-client ──► packages/api-client/ diff
        │
implement/adjust Resource + Control (+ Mapper, migration if needed)
        │
one commit: api/openapi.yaml + backend code + packages/api-client/
```

## Risks & trade-offs

- **Hand-written health vs. `quarkus-smallrye-health`:** loses the `/q/health`
  ecosystem convention; gains one dependency fewer on unpatchable boxes and
  contract-declared, documented paths. Aligned with principle 4; reversible
  later by an approval if orchestration ever needs the MP Health format.
- **`instance_info` is scaffolding-ish data.** Accepted: the BCE reference
  package must exercise a real Entity/Repository/Mapper, and the successor
  feature owns its fate (recorded above). The alternative — a DB-free info
  endpoint — would leave the persistence conventions undemonstrated.
- **Controls coupled to generated DTOs** — deliberate spec §4 trade-off,
  restated here so review doesn't flag it: in exchange the Boundary is trivial
  and entities cannot escape.
- **503-via-exception from the Control** keeps the one-line Boundary but puts
  HTTP semantics in the Control's vocabulary. Accepted and pinned — the
  alternative (Boundary branching on a result) is exactly what spec §4
  forbids.
- **No TLS yet** — see PRD risks; compose unit is dev/preview-only until
  `device-pairing-and-guest-api`.

## Open decisions

- **⏸ Awaiting user approval — `backend/CLAUDE.md` wording** (REQ-216): the
  exact proposed text is below. Blocks **only** its own write-task (TODO
  T-18), not implementation. Per constitution, the file is written only after
  the user confirms the text (verbatim or amended).

*(No `[NEEDS CLARIFICATION]` markers — every other decision this feature needs
is settled in constitution v1.5.0, spec §2, or pinned above.)*

## Decisions confirmed by the user

| Decision | Chosen | Date |
|----------|--------|------|
| Backend exists; stack Java 21 + Quarkus + Maven, imperative, BCE, Panache repositories, contract-first | Constitution v1.4.0 | 2026-08-19 |
| Datastore | PostgreSQL (assets as BYTEA later) | 2026-08-19 |
| Migrations | Flyway, at startup, versioned SQL | 2026-08-19 |
| Deployment unit | Docker Compose, app + Postgres, one named volume | 2026-08-19 |
| Dependency allow-list (incl. Dev Services, no Testcontainers dep; non-additions: Elytron, smallrye-jwt, mappers, Lombok) | Constitution v1.5.0 list, closed | 2026-08-19 |
| Fail-closed authz: `@RolesAllowed` on Controls + deny-unannotated | Constitution v1.4.0 | 2026-08-19 |
| Manager UI → artifact | Plain Maven copy step into `META-INF/resources`; Quinoa rejected | 2026-08-19 |
| Admin password lifecycle | Env var seeds first start only; hashed (JDK PBKDF2) in Postgres; change drops all bearer tokens | 2026-08-19 |
| Session scope | Backend only; `guest-frontend-integration` & `manager-frontend` out of scope | 2026-08-19 |

## Proposed `backend/CLAUDE.md` (REQ-216 — exact text, write only after approval)

```markdown
# Backend Conventions (`backend/`)

Java 21 + Quarkus (Maven), imperative/blocking. Governed by
`docs/constitution.md` and `docs/backend/specification.md` §4/§8 — this file
is the working summary, not a replacement.

## BCE — binding rules (spec §4)

- Package **by feature** under `de.gxp.<feature>`: Boundary (`*Resource`),
  Control (`*Control`), Entities (+ package-private `*Repository`, `*Mapper`).
  **Amended 2026-08-25 (D1):** each feature package is split into the
  sub-packages `boundary` (the `*Resource` adapters, nothing else), `control`
  (the public `*Control` plus every package-private non-JPA collaborator,
  `*Repository` included) and `entity` (the `@Entity` types and their
  `*Mapper`). `de.gxp.api`, `de.gxp.web`, `de.gxp.devseed` and
  `de.gxp.testsupport` are not feature packages and are excluded by name.
- **A Boundary method is one delegating line** — take the generated DTO, call
  one Control method, return the result. No `if`, no `try`, no loop, no
  mapping, no second call — not even for plain CRUD. Anything more belongs in
  the Control. Non-200 outcomes are thrown from the Control
  (`jakarta.ws.rs` exceptions), never branched on in the Boundary.
- `@Transactional` sits on the **Control**, never on the Boundary.
- The **Control is the package's only public type** (the Boundary is just its
  HTTP adapter). Cross-feature access injects the other feature's Control;
  Entity, Repository and Mapper stay package-private, and Entities never
  leave their package. **Amended 2026-08-25 (D1):** the Repository still stays
  package-private, inside `.control`; Entity and Mapper become `public`
  because Java has no sub-package visibility. The rule itself is unchanged —
  the **Control is the only type another feature may reference**, and
  **entities never leave their feature** — but it is now enforced by
  `FeatureInternalsImportTest` (no file outside `de.gxp.<feature>` names a
  `.entity` or `.control` type other than that feature's `*Control`; wildcard
  imports forbidden) and `EntityExposureTest` (no public signature outside a
  `.entity` package mentions an entity type, generics and arrays included),
  not by the compiler. ArchUnit was rejected for this: a new dependency on an
  unpatchable box (principle 4).
- Controls accept and return **generated API DTOs** and call the hand-written
  Mapper themselves. No mapping framework, no Lombok.
- Data access uses Panache **repositories**; entities carry no persistence
  methods.

## Contract-first

- `api/openapi.yaml` is the source of truth. Generated JAX-RS
  interfaces/DTOs live under `target/generated-sources/` (never committed);
  the TypeScript client in `packages/api-client/` is generated, committed and
  **never hand-edited** — a diff there means the contract changed.
- Workflow: edit `api/openapi.yaml` → `./mvnw verify` (regenerates both
  sides; compile fails until Resources/Controls follow) → commit contract,
  backend code and regenerated client **together**.

## Security & logging

- **Fail closed:** `quarkus.security.deny-unannotated-members=true` stays on,
  and **every public Control method carries exactly one of** `@PermitAll` /
  `@RolesAllowed(...)` — on the Control, never the Boundary. Roles: `admin`,
  `device`. A test asserts unannotated methods are denied; keep it green.
  **Amended 2026-08-24 (D2):** read `backend/CLAUDE.md` → "Security & logging"
  for the current rule — both flags are on, and the exactly-one requirement
  applies to public **Boundary** methods. Roles unchanged.
- **Logging is a security surface** (constitution principle 3, verbatim for
  the backend): no SQL statement or bind-parameter logging, no access/request-
  body logging, no guest-sensitive value (door/key-box/alarm codes, WiFi
  passwords, tokens, password material) in any log message or exception —
  in every profile, including `%dev`. Never commit config that raises these.

## Dependencies, database, build

- The dependency allow-list in `docs/constitution.md` is **closed** — any
  `pom.xml` addition needs prior user approval. Named non-additions: Elytron
  (→ JDK PBKDF2), smallrye-jwt (→ opaque in-memory token), mapping
  frameworks/Lombok/utility libs (→ plain Java).
- Schema changes are **Flyway versioned SQL** in
  `src/main/resources/db/migration/` — no Hibernate schema generation, in any
  profile. Migrations run at startup and must leave a fresh instance usable.
- Dev/test Postgres comes from **Quarkus Dev Services** (Docker required) —
  no manual DB setup, no Testcontainers dependency.
- The manager UI (later feature) enters the artifact via a **plain Maven copy**
  of its built `dist` into `META-INF/resources` — Quinoa stays rejected.
- Shared packages (`packages/`) never live under `backend/`.

## Tests

- `quarkus-junit5` + `rest-assured`; every implemented behavior has a test
  (constitution principle 6). HTTP tests call the real contract paths, so
  contract drift also fails the suite.
```
