# Handoff: backend-foundation

**Last session:** 2026-08-19 (reviewer, 06_review)

## Review result (2026-08-19)

**PASS ✅** — 🔴 0 · 🟠 0 · 🟡 2 (1 fixed in-review) · 🟢 6; all
REQ-201…REQ-217 implemented, 0 orphans. Full findings:
[06-review.md](06-review.md). Independently re-verified at review (not taken
from this handoff): BCE strictness file-by-file, allow-list via a fresh
`dependency:tree` (0 banned hits), byte-identical client regeneration
(sha256 over all 23 files after `./mvnw generate-sources`),
`backend/CLAUDE.md` verbatim to the approved text, zero SQL in the verify
boot log, `frontend/` untouched.

Findings summary:
- 🟡 `/q/openapi` is served unauthenticated in `%prod` by default — harmless
  today (3 public system ops), but decide explicitly (keep vs.
  `%prod.quarkus.smallrye-openapi.enable=false`) no later than
  `device-pairing-and-guest-api`.
  **RESOLVED 2026-08-19** (user decision, implemented in
  `backend-domain-config`): no dev-time surface in `%prod` —
  `%prod.quarkus.smallrye-openapi.enabled=false` (the `.enable` spelling
  above is deprecated since Quarkus 3.26) plus explicit Swagger-UI/Dev
  Services guards; see `docs/features/backend-domain-config/handoff.md`.
- 🟡 (fixed in-review, doc-only) `backend/README.md` intro still pointed at
  `02-design.md` "until a `backend/CLAUDE.md` is approved" — now references
  the approved file.
- 🟢 notes: public `applicationVersion` fingerprinting (revisit with auth/TLS);
  403-vs-401 fix confirmed sound (PRD's "401" text superseded, recorded in
  06-review); REQ-209's one-commit criterion closes with the user's commit;
  Dev Services path itself untested on this machine (config is standard);
  generated client boilerplate stays (no hand edits); `FailClosedControl`'s
  unannotated method is the deliberate proof fixture.

**Next action:** user commits everything on `feature/backend` — contract +
backend + `packages/api-client/` together (REQ-209). Then `07_improve` or the
next feature (`backend-domain-config`).

---

## Previous session (dev, 05_implement)

## State

**T-01…T-19 all implemented** (T-18 landed via `lead` after user approval —
see the resolved section below).
All code compiles; scaffold `./mvnw verify` (pre-tests) and full
`./mvnw verify -DskipTests` packaging are green. **The test suite has now
been executed** (2026-08-19, outside dev's sandbox) — see the validation
section below.

### What was built

- `backend/app/` — Quarkus **3.33.3.1** (3.33 LTS, latest micro), Java 21,
  Maven wrapper (wrapper 3.3.4 → Maven 3.9.12), groupId `de.gxp`,
  artifactId `gxp-backend`. Dependencies exactly the constitution v1.5.0
  allow-list; `dependency:tree` grep for
  elytron/jwt/lombok/mapstruct/testcontainers/jackson-databind-nullable: clean.
- `api/openapi.yaml` — OpenAPI **3.1.0**; tag `system`, three operations +
  shared `ApiError`.
- Contract toolchain in `backend/app/pom.xml`
  (openapi-generator-maven-plugin **7.24.0**, two executions):
  `generate-jaxrs` (jaxrs-spec, interfaceOnly, jakarta, returnResponse=false,
  `sourceFolder=src/gen/java` registered as compile source root,
  `openApiNullable=false`) and `generate-ts-client` (typescript-angular →
  `packages/api-client/`, `hideGenerationTimestamp=true`, no npmName).
- `packages/api-client/` — generated, committed; byte-identical across runs
  (sha256 over every file compared).
- Flyway `V1__foundation_baseline.sql` (single-row `instance_info` + seed,
  exactly per design), `quarkus.flyway.migrate-at-start=true`,
  `quarkus.hibernate-orm.database.generation=none`.
- BCE reference package `de.gxp.system`: `SystemResource` (public Boundary,
  every body one delegating line), `SystemControl` (public Control,
  `@PermitAll` ×3, `@Transactional` on the two DB-touching methods, 503 via
  `ServiceUnavailableException` from the Control), package-private
  `InstanceInfo`, `InstanceInfoRepository` (Panache, hosts `SELECT 1` probe),
  `InstanceInfoMapper` (hand-written, static).
- `application.properties` — fail-closed
  (`quarkus.security.deny-unannotated-members=true` + convention comment),
  logging-as-security-surface block (sql/bind-parameters/access-log all
  false, principle-3 comment), Dev Services image pin `postgres:17.11`,
  `%prod` datasource from `GXP_DB_JDBC_URL`/`GXP_DB_USERNAME`/`GXP_DB_PASSWORD`.
- `backend/compose.yaml` — app (`gxp-backend:1.0.0-SNAPSHOT`, Dockerfile.jvm)
  + db (`postgres:17.11`, healthcheck), one named volume `gxp-pgdata`,
  required interpolation for both `GXP_DB_PASSWORD` and `GXP_ADMIN_PASSWORD`,
  dev/preview-only warning (no TLS).
- Tests written (quarkus-junit + rest-assured): `SystemResourceTest` (3),
  `FailClosedTest` (3, with test-only `FailClosedControl`/`FailClosedResource`
  fixtures in src/test), `MigrationTest` (2). All compile.
- `backend/README.md` (first run, contract workflow, backup/restore, pins,
  OpenAPI-version choice), root `.gitignore` fix (the VS template ignored
  `**/packages/*`; re-include appended and proven with `git check-ignore`),
  `docs/system-context.md` + `docs/features/index.md` updated.
- Chain proof (T-16) rehearsed and reverted: field add propagated into the
  generated Java DTO **and** `packages/api-client/model/systemInfo.ts`;
  operation removal broke compilation at `SystemResource.java:32`
  ("method does not override..."); final tree restored and re-verified.

### Verify-at-scaffold findings (design's open name checks)

| Item | Finding |
|---|---|
| Quarkus 3.33 latest micro | **3.33.3.1** (platform metadata, Maven Central) |
| `quarkus-junit5` | **Renamed to `quarkus-junit` in Quarkus 3.31**; `quarkus-junit5` is now only a relocation pom (Migration Guide 3.31). Pom uses the corrected name with a comment — same artifact, allow-list intent unchanged |
| `quarkus.security.deny-unannotated-members` | Exists as expected (verified in `quarkus-security-deployment-3.33.3.1.jar` javadoc properties) |
| Bind-parameter logging | `quarkus.hibernate-orm.log.bind-parameters` exists as expected (a deprecated `log.bind-param` also exists; the non-deprecated name is used) |
| Schema generation property | Still `quarkus.hibernate-orm.database.generation` in 3.33 (no `schema-management.strategy` here) — set to `none` |
| Postgres 17 tag | **`postgres:17.11`** (current 17.x on Docker Hub, 2026-08-19) — used in compose **and** Dev Services |
| OpenAPI 3.1 vs 3.0 | **3.1 kept.** Generator 7.24.0 prints an "OpenAPI 3.1 support is still in beta" warning but generated both sides correctly for this contract (chain proof passed both directions). No fallback needed; recorded in `backend/README.md` |
| TS version-stamp flag | **No extra flag needed in 7.24.0**: no timestamp/generator-version appears in any generated `.ts` file; the only version record is `.openapi-generator/VERSION`, which is stable while the plugin is pinned. Determinism proven byte-identical across runs |
| Unplanned: `openApiNullable=false` | jaxrs-spec models otherwise import `org.openapitools.jackson.nullable` (jackson-databind-nullable — **not** on the allow-list). The generator option removes the import; no dependency added |

## Validation status

**Test suite executed 2026-08-19** (by the user, outside dev's sandbox:
transient JDK at `/tmp/gxp-jdk`, throwaway Postgres via `sudo docker`, Dev
Services bypassed by passing the datasource explicitly): **8 run, 7 passed,
1 failed**. The one failure was
`FailClosedTest.rolesAllowedMethodRejectsAnonymousCaller` expecting 401 where
Quarkus correctly returns **403**: with no authentication mechanism installed
(none exists in this feature — device/admin auth come later), an anonymous
caller hitting `@RolesAllowed` is a plain denial; a 401 challenge requires a
mechanism that can issue one. **Fix applied** (2026-08-19): the test now
asserts 403 with a comment explaining why, and noting it should be revisited
when an auth mechanism lands in `device-pairing-and-guest-api`. No other
assertion weakened.

**Re-run after the fix (2026-08-19, `lead`-orchestrated): `./mvnw verify`
BUILD SUCCESS, 8 run / 8 passed / 0 failed.** The remaining boot-log and
compose checks were executed the same way and all passed:

1. ✅ Boot log (from the verify run, which boots the real app): Flyway
   *"Successfully applied 1 migration … now at version v1"*; **zero** SQL
   statements or bind parameters anywhere in the log (principle 3).
2. ✅ Compose cycle: image built from `Dockerfile.jvm`, `docker compose up`
   → `/api/system/ready` **200** and `/api/system/info` **200** (real JSON
   from the seeded row) → `down`; named volume `gxp_gxp-pgdata` persists.

Earlier environment note (still true for dev sessions on this machine):
Raspberry Pi (aarch64), no permanent JDK/Maven, Docker socket not accessible
to the dev user — `lead` ran the above via a transient JDK (`/tmp/gxp-jdk`)
and `sudo docker`.

## ✅ Resolved: the `backend/CLAUDE.md` "anomaly" was the approval landing

The file `dev` saw appear at 10:47 was written by **`lead`**, in the main
session, **after the user approved the proposed wording verbatim** ("Yes you
can add it exactly like this", 2026-08-19). `dev` could not see that approval
from its isolated context and flagged it correctly. The gate was honored in
full: exact text proposed by `planner`, relayed by `lead`, confirmed by the
user, written unchanged. **T-18 is done; REQ-216 satisfied.** The file is
authoritative.

## Open / pending

- Recorded for `guest-frontend-integration`: generated client's `ngVersion`
  ceiling vs. Angular 22 — check on first consumption.
- Recorded for `backend-domain-config`: `instance_info` may be absorbed or
  dropped in V2+.
- Nothing committed — branch `feature/backend` carries all work as
  uncommitted changes (including pre-existing uncommitted planner docs and
  the constitution v1.5.0 text). The user commits.

## Next

- `06_review` by `reviewer`, with the design's named checks: one-line
  Boundaries, exactly one security annotation per public Control method,
  no SQL/access logging in committed config, allow-list diff — **after**
  the user's re-run of the suite confirms 8/8 green with the 403 fix.
