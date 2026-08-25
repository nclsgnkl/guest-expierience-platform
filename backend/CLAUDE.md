# Backend Conventions (`backend/`)

Java 21 + Quarkus (Maven), imperative/blocking. Governed by
`docs/constitution.md` and `docs/backend/specification.md` §4/§8 — this file
is the working summary, not a replacement.

## BCE — binding rules (spec §4)

- Package **by feature** under `de.gxp.<feature>`, split into three
  sub-packages: `boundary` (the `*Resource` JAX-RS adapters, and nothing
  else), `control` (the public `*Control` plus every package-private
  collaborator that is not a JPA type — `*Repository`, hashers, code
  generators, auth SPI adapters, filters, seeders), `entity` (the
  `@Entity` types and their `*Mapper`). Everything that is not a business
  capability lives under the single package **`de.gxp.infrastructure`** —
  `http` (hand-written `ApiErrors`, the exception mappers, the response
  filter), `web` (SPA fallback), `devseed` (dev-profile seeder),
  `testsupport` (test scope). `de.gxp.api` is the OpenAPI generator's output
  package and holds no hand-written source, so it exists only under
  `target/` and needs no exclusion.
  A package is a **feature** when it owns state (a table) and/or a contract
  surface; `de.gxp.auth` qualifies on both counts and stays one.
- **A Boundary method is one delegating line** — take the generated DTO, call
  one Control method, return the result. No `if`, no `try`, no loop, no
  mapping, no second call — not even for plain CRUD. Anything more belongs in
  the Control. Non-200 outcomes are thrown from the Control
  (`jakarta.ws.rs` exceptions), never branched on in the Boundary.
- `@Transactional` sits on the **Control**, never on the Boundary.
- The **Control is the only type another feature may reference.**
  Cross-feature access injects the other feature's Control. Types in `.entity`
  and non-Control types in `.control` are `public` only because Java has no
  sub-package visibility — referencing them from outside `de.gxp.<feature>` is
  a violation, caught by the architecture tests, not by the compiler.
  **Entities never leave their feature.** The four guards live in
  `de.gxp.architecture`: `FeatureInternalsImportTest` (cross-feature
  references into `.entity`/`.control`/`.boundary`, wildcard imports, layout
  present), `EntityExposureTest` (entity types in public signatures, including
  generics, supertypes and interfaces), `ControlPackageVisibilityTest`
  (only `*Control` public in `.control`, only `*Resource` in `.boundary`) and
  `PersistenceAccessTest` (who may reach the database — see below).
  They derive the feature list from disk, so a new feature is covered the day
  its directory exists.
- Controls accept and return **generated API DTOs** and call the hand-written
  Mapper themselves. No mapping framework, no Lombok.
- Data access uses Panache **repositories**; entities carry no persistence
  methods. Enforced by `PersistenceAccessTest` over `src/main/java`: only a
  class in `.entity` (`jakarta.persistence` only — an entity must not extend
  `PanacheEntity`) and a `*Repository` in `.control` (plus Panache) may
  reference JPA/Panache/Hibernate/JDBC. No Control and no Boundary may.
  `de.gxp.infrastructure.devseed.DevDataSeeder` is the one named exemption (dev-profile
  fixture, native SQL across features); test sources are out of scope, since
  reaching past the layering to arrange state is their job.
- A Boundary method carries **exactly one** of `@PermitAll` /
  `@RolesAllowed(...)` (method-level, or inherited from a single class-level
  annotation), and may additionally carry `@ResponseStatus` for a non-default
  success code (e.g. 201). It carries nothing else. The annotation must sit on
  the **implementing** method: Quarkus resolves the endpoint implementation
  first and the generated interface only as a fallback (verified against
  3.33.3.1).
- Controls build thrown non-2xx outcomes via `de.gxp.infrastructure.http.ApiErrors`, never
  ad-hoc `Response` construction — and **never a bare `jakarta.ws.rs`
  exception subclass** (`ServiceUnavailableException`, `NotFoundException`, …).
  Those carry no entity: their `String` constructor sets the Java message, not
  the response body, so the status goes out bodyless while the contract
  declares an `ApiError` for it, and `ApiErrorMappers` passes an entity-less
  `WebApplicationException` straight through. `ApiErrors` gained
  `serviceUnavailable(...)` on 2026-08-25 for exactly this reason (the
  readiness 503). The only legitimate entity-less throws are the ones where a
  bodyless response IS the contract — e.g. the pairing poll's `202`.

## Contract-first

- `api/openapi.yaml` is the source of truth. Generated JAX-RS
  interfaces/DTOs live under `target/generated-sources/` (never committed);
  the TypeScript client in `packages/api-client/` is generated, committed and
  **never hand-edited** — a diff there means the contract changed.
- Workflow: edit `api/openapi.yaml` → `./mvnw verify` (regenerates both
  sides; compile fails until Resources/Controls follow) → commit contract,
  backend code and regenerated client **together**.
- Contract tags map **one tag to one Boundary** (so the generator emits
  exactly one interface per Boundary). A package normally has one Boundary;
  it may host a second **only** when one consistency unit spans two auth
  surfaces (e.g. `pairing` + `manager-devices` in `de.gxp.device`) — every
  Boundary stays a one-line adapter over the package's single public Control.

## Security & logging

- **Fail closed:** `quarkus.security.jaxrs.deny-unannotated-endpoints=true`
  **and** `quarkus.security.deny-unannotated-members=true` both stay on, and
  **every public Boundary method carries exactly one of** `@PermitAll` /
  `@RolesAllowed(...)` — on the Boundary, never the Control. Roles: `admin`,
  `device`. Three tests keep this mechanical and must stay green: (1) every
  public `*Resource` method resolves to exactly one effective annotation;
  (2) no generated `de.gxp.api.*Api` interface method carries a security
  annotation (a generator upgrade must not start supplying one silently);
  (3) every operation marked `bearerAuth` in `api/openapi.yaml` answers 401 to
  an anonymous caller.
- **Class-level is allowed only for `@RolesAllowed`.** `@PermitAll` must sit on
  the method: a class-level `@PermitAll` is inherited by any method added
  later, so a forgotten endpoint becomes anonymously reachable with the audit,
  the contract test and `deny-unannotated-endpoints` all green. A class-level
  `@RolesAllowed` fails the other way — too restrictive, and loud.
- **Logging is a security surface** (constitution principle 3, verbatim for
  the backend): no SQL statement or bind-parameter logging, no access/request-
  body logging, no guest-sensitive value (door/key-box/alarm codes, WiFi
  passwords, tokens, password material) in any log message or exception —
  in every profile, including `%dev`. Never commit config that raises these.

## Dependencies, database, build

- The dependency allow-list in `docs/constitution.md` is **closed** — any
  `pom.xml` addition needs prior user approval. Named non-additions: Elytron
  (→ JDK PBKDF2), smallrye-jwt (→ opaque in-memory token), **Quartz**
  (→ core `quarkus-scheduler`), mapping frameworks/Lombok/utility libs
  (→ plain Java).
- **Scheduled work uses core `quarkus-scheduler`** (`@Scheduled`), approved
  constitution v1.7.0. In-JVM, no job store, no cluster coordination — one box
  needs none of that. A `@Scheduled` method belongs in a `.control` package
  like any other collaborator, and does its work through the feature's Control
  so the housekeeping and the request path share one code path.
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
- Shared test fixtures live in `de.gxp.infrastructure.testsupport` (test scope only); tests
  outside `de.gxp.device` seed kiosk devices via `DeviceFixtures` (hash-only
  DB insert), never the HTTP pairing flow — seeding a device is a
  fixture concern, and driving the real flow would couple unrelated suites to
  pairing's contract.
