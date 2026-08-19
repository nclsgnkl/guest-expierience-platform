# Review: Backend Foundation
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Reviewed:** 2026-08-19 · against constitution **v1.5.1**, `backend/CLAUDE.md`, spec §4/§8
**Reviewer:** `reviewer` (06_review)

## Verdict

**PASS ✅** — 🔴 0 · 🟠 0 · 🟡 2 (1 fixed in-review) · 🟢 6

Build and full suite green (`./mvnw verify`, 8/8 tests — verified against
`/tmp/gxp-verify.log`); compose cycle green (`/tmp/gxp-compose-check.log`:
ready 200, info 200 from the seeded row, volume persists). All named design
checks were re-executed mechanically, not taken from the handoff — see
"Independent verification" below.

## Findings

| # | Severity | Category | File | Finding | Fix |
|---|----------|----------|------|---------|-----|
| 1 | 🟡 | Security / exposure | `backend/app/pom.xml` (quarkus-smallrye-openapi), `application.properties` | `/q/openapi` is served **unauthenticated in `%prod`** by default (no `%prod.quarkus.smallrye-openapi.*` setting; Swagger UI itself is dev/test-only). Today it discloses only the three public system operations — harmless. But as admin/guest operations enter the contract, the entire API surface becomes enumerable by any device on the LAN. Serving the contract is the extension's *approved purpose* (spec §8), so this may well be intended — it just has never been decided explicitly. | **Track** — an explicit decision (keep serving, or `%prod.quarkus.smallrye-openapi.enable=false`) belongs to `device-pairing-and-guest-api` at the latest, when the contract first contains authenticated operations. No code change now. |
| 2 | 🟡 | Docs | `backend/README.md:4-6` | Stale: pointed to `02-design.md` for conventions "until a `backend/CLAUDE.md` is approved" — the file is approved and written (T-18). | **Fixed in-review** (doc-only): now references `backend/CLAUDE.md` directly. |
| 3 | 🟢 | Security / exposure | `api/openapi.yaml`, `SystemControl.info()` | `/api/system/info` exposes `applicationVersion` unauthenticated. On unpatchable boxes (principle 4), version fingerprinting is a small gift to an attacker. Contract-pinned by the design (REQ-211) and currently `1.0.0-SNAPSHOT` — accepted. | Reconsider whether version stays in the public payload when TLS/auth land. |
| 4 | 🟢 | Tests | `FailClosedTest.java:38-50` | The 403-vs-401 fix is **sound**: with no authentication mechanism installed (none exists in this feature), Quarkus resolves an anonymous caller against `@RolesAllowed` as a plain 403 denial — a 401 challenge requires a mechanism that can issue one. The security property actually claimed ("anonymous is denied") is fully proven; the comment explains the reasoning and flags the revisit when auth lands. The PRD's acceptance-criterion text for REQ-213 ("→ 401") is superseded by this — recorded here rather than editing the PRD retroactively. | None. Revisit the assertion in `device-pairing-and-guest-api` (already noted in the test). |
| 5 | 🟢 | Process | (git) | REQ-209's "one commit where `api/openapi.yaml`, backend code and `packages/api-client/` change together" is pending — nothing is committed yet (the user commits). All three move together in the working tree, so the user's single commit satisfies it. | User: commit contract + backend + `packages/api-client/` together. |
| 6 | 🟢 | Tests / environment | `application.properties:34-35` | The Dev Services path itself was never executed on this machine — validation used an explicit datasource + throwaway Postgres (test run) and compose (prod path). The config is the standard mechanism (no dev/test URL → Dev Services, image pinned `postgres:17.11`) and the handoff records the limitation honestly. | First `./mvnw verify` on a Docker-enabled dev machine confirms it; no action here. |
| 7 | 🟢 | Repo hygiene | `packages/api-client/git_push.sh`, `README.md`, `.gitignore` | Generator boilerplate ships inside the committed client. Part of the deterministic generated output (`.openapi-generator/FILES` lists them) — hand-pruning would break the proven no-diff-on-regeneration invariant. | Leave as generated. If unwanted, the sanctioned lever is `.openapi-generator-ignore`, via a normal change, not a hand edit. |
| 8 | 🟢 | Convention | `FailClosedControl.java:22` | `unannotated()` deliberately violates the every-method-annotated convention — correct: it *is* the proof fixture for `deny-unannotated-members`, test-only (`src/test`, never shipped), and documented as deliberate. | None. |

## Independent verification (design's named checks, re-executed)

1. **BCE strictness (REQ-210)** — mechanically checked, file by file:
   - `SystemResource`: 3 methods, each body exactly one `return systemControl.x();` — no `if`/`try`/loop/mapping/second call; no security or transaction annotations. Implements generated `SystemApi`.
   - `@Transactional` only on `SystemControl.ready()`/`info()` (the two DB-touching methods); `health()` correctly bare.
   - Visibility: `SystemControl` public (+ its adapter `SystemResource`); `InstanceInfo`, `InstanceInfoRepository`, `InstanceInfoMapper` all package-private. Entity carries no persistence methods; repository is `PanacheRepository`; mapper hand-written, invoked by the Control only. 503 thrown from the Control (`ServiceUnavailableException`) — the pinned non-200 pattern.
   - Test fixture `FailClosedResource` also one-line-per-method.
2. **Fail-closed (REQ-213)** — `quarkus.security.deny-unannotated-members=true` in committed config with the convention comment; every public `SystemControl` method carries exactly one `@PermitAll`; `FailClosedTest` proves `@PermitAll` → 200, unannotated → denied (401/403), `@RolesAllowed("admin")` anonymous → 403 (finding #4).
3. **Exposure on a shared LAN** — unauthenticated surface: the three `@PermitAll` system endpoints (payloads: `UP`/`READY`/product name + version — nothing property- or guest-specific; the seeded `name` is platform metadata, principle 1 intact) plus `/q/openapi` (finding #1). No secrets in compose (required interpolation, no defaults), config, or any committed file. `GXP_ADMIN_PASSWORD` appears **only** in `compose.yaml` (passthrough) — never read, logged or echoed by app code; there are zero log/`System.out`/`printStackTrace` statements in `src/main/java` at all. Plain-HTTP compose unit prominently marked dev/preview-only in both `compose.yaml` and README.
4. **Logging (REQ-214)** — committed config: `log.sql=false`, `log.bind-parameters=false` (non-deprecated name), `access-log.enabled=false`, no `org.hibernate` categories, principle-3 comment present; no profile raises any of them. `/tmp/gxp-verify.log`: Flyway applied V1; **zero** SQL statements/bind parameters (the single grep hit is a generator info line about `ngVersion`).
5. **Dependency allow-list (REQ-203)** — `dependency:tree` re-run during review: direct dependencies are exactly the 8 runtime extensions + `quarkus-junit` (v1.5.1 corrected name, comment in pom) + `rest-assured`; grep for elytron/smallrye-jwt/lombok/mapstruct/testcontainers/jackson-databind-nullable over the full tree: **0 hits** (`openApiNullable=false` did its job). Only build plugin beyond Quarkus's own: `openapi-generator-maven-plugin` 7.24.0. `frontend/` — **zero** working-tree changes.
6. **Contract-first integrity (REQ-207/208)** — regenerated during review (`./mvnw generate-sources`): `packages/api-client/` **byte-identical** (sha256 over all 23 files) — proven purely generated, deterministic, never hand-edited. Generated Java only under `target/` (ignored via `backend/app/.gitignore`); `git check-ignore` confirms the root `.gitignore` re-include tracks `packages/**` while `target/` stays ignored. `openapi.yaml` (3.1.0) matches the implementation: 3 operations, 4 schemas, paths asserted 1:1 by `SystemResourceTest` against the literal contract paths.
7. **`backend/CLAUDE.md` (REQ-216)** — diffed against the design's proposed block: **verbatim**, as approved.
8. **Docs (REQ-217)** — README covers first run (+ dev/preview-only TLS warning), contract workflow, `pg_dump` backup/restore, pins, OpenAPI-3.1 choice; `docs/system-context.md` additions describe real code and correctly mark decided-but-unbuilt items as such.

## REQ coverage — 17/17 implemented, 0 orphans

| REQ | Evidence | ✔ |
|---|---|---|
| REQ-201 | `api/openapi.yaml`, `backend/app/` (wrapper incl. `mvnw`/`mvnw.cmd`), `backend/compose.yaml`, `packages/api-client/`; `frontend/` untouched | ✅ |
| REQ-202 | `./mvnw verify` BUILD SUCCESS; Java 21; `quarkus-bom:3.33.3.1` (3.33 LTS per design pin) | ✅ |
| REQ-203 | Exact allow-list; tree grep clean (re-run at review) | ✅ |
| REQ-204 | No dev/test datasource URL; Dev Services image pinned; `%prod` from env vars (finding #6 note) | ✅ |
| REQ-205 | `V1__foundation_baseline.sql`; `migrate-at-start=true`; `database.generation=none`; `MigrationTest` (2) | ✅ |
| REQ-206 | Compose: 1 named volume `gxp-pgdata`; required interpolation for both vars; up→ready 200 (log) | ✅ |
| REQ-207 | Generated Java under ignored `target/`; Resources implement generated interfaces; removal-breaks-compile rehearsed (T-16) | ✅ |
| REQ-208 | Client committed; byte-identical regeneration re-proven at review; `frontend/package.json` unchanged | ✅ |
| REQ-209 | Chain proof both directions (handoff); README workflow section; one-commit criterion pending the user's commit (finding #5) | ✅ |
| REQ-210 | Strict BCE mechanically verified (section above, check 1) | ✅ |
| REQ-211 | `info()` reads seeded row via Entity→Repository→Mapper→DTO; test asserts seeded value from the response | ✅ |
| REQ-212 | Hand-written contract-declared `/api/system/health` + `/ready`; 503 via Control exception; tests | ✅ |
| REQ-213 | deny-unannotated in committed config; `FailClosedTest` (3) proves denial; 403 rationale sound (finding #4) | ✅ |
| REQ-214 | Logging block + principle-3 comment; zero SQL in boot log (re-checked) | ✅ |
| REQ-215 | 8/8 named tests map to REQ-205/211/212/213; suite green | ✅ |
| REQ-216 | `backend/CLAUDE.md` verbatim to the approved proposed text (diffed) | ✅ |
| REQ-217 | README sections present; `docs/system-context.md` + `docs/features/index.md` updated, real code only | ✅ |

## Top 3 recommendations

1. **Decide `/q/openapi` exposure in `%prod` explicitly** (finding #1) no later
   than `device-pairing-and-guest-api` — the moment authenticated operations
   enter the contract, an enumerable API surface on the LAN is a real choice,
   not a default.
2. **Commit contract + backend + `packages/api-client/` in one commit**
   (finding #5) — that closes the last REQ-209 criterion.
3. Carry the two recorded hand-offs forward: `ngVersion` ceiling check on first
   client consumption (`guest-frontend-integration`); `instance_info` fate and
   the `FailClosedTest` 401-revisit (`device-pairing-and-guest-api` /
   `backend-domain-config`).
