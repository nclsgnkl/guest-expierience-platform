# Backend Conventions (`backend/`)

Java 21 + Quarkus (Maven), imperative/blocking. Governed by
`docs/constitution.md` and `docs/backend/specification.md` §4/§8 — this file
is the working summary, not a replacement.

## BCE — binding rules (spec §4)

- Package **by feature** under `de.gxp.<feature>`: Boundary (`*Resource`),
  Control (`*Control`), Entities (+ package-private `*Repository`, `*Mapper`).
- **A Boundary method is one delegating line** — take the generated DTO, call
  one Control method, return the result. No `if`, no `try`, no loop, no
  mapping, no second call — not even for plain CRUD. Anything more belongs in
  the Control. Non-200 outcomes are thrown from the Control
  (`jakarta.ws.rs` exceptions), never branched on in the Boundary.
- `@Transactional` sits on the **Control**, never on the Boundary.
- The **Control is the package's only public type** (the Boundary is just its
  HTTP adapter). Cross-feature access injects the other feature's Control;
  Entity, Repository and Mapper stay package-private, and Entities never
  leave their package.
- Controls accept and return **generated API DTOs** and call the hand-written
  Mapper themselves. No mapping framework, no Lombok.
- Data access uses Panache **repositories**; entities carry no persistence
  methods.
- A Boundary override may carry `@ResponseStatus` for a non-default success
  code (e.g. 201) — the only annotation allowed on a Boundary method;
  everything else stays on the Control.
- Controls build thrown non-2xx outcomes via `de.gxp.api.ApiErrors`, never
  ad-hoc `Response` construction.

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

- **Fail closed:** `quarkus.security.deny-unannotated-members=true` stays on,
  and **every public Control method carries exactly one of** `@PermitAll` /
  `@RolesAllowed(...)` — on the Control, never the Boundary. Roles: `admin`,
  `device`. A test asserts unannotated methods are denied; keep it green.
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
