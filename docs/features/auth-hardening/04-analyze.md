# Analyze: Authentication Hardening (remote-operations Part A)
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** PASS (2026-08-25, against constitution v1.8.0, spec §4/§12, `backend/CLAUDE.md`)

> `Status` below means "a task covers this", not "this works" — the gate
> proves the artifacts agree, nothing more (2026-08-18 lesson).

## Coverage matrix
| REQ-ID | Design section | TODO task(s) | Status |
|--------|----------------|--------------|--------|
| REQ-601 | A1 `LoginThrottle` (D2) | T-03, T-04 | ✅ |
| REQ-602 | A1 `LoginThrottle`, `ApiErrors.tooManyRequests` (D2, D3, D20) | T-03 | ✅ |
| REQ-603 | A1.4 `ClientAttribution`, proxy config (D4, D5) | T-02, T-04 | ✅ |
| REQ-604 | A1 wiring into `login` + `changePassword`, A3 enforcement table | T-03, T-10 | ✅ |
| REQ-605 | A1 cap + eviction, logs nothing (D21) | T-03 | ✅ |
| REQ-606 | Architecture decision → reopened REQ-326 | T-03 (code side); PRD note written by the planner 2026-08-25 | ✅ |
| REQ-607 | Data model V5 + carry-over proof (D16) | T-05 | ✅ |
| REQ-608 | A2 `FirstUserSeeder` (D15) | T-05 | ✅ |
| REQ-609 | A2 `verifyLogin` dummy hash; contract `LoginRequest.username` | T-05 | ✅ |
| REQ-610 | A2 `UserControl` admin ops, `manager-users` contract (D12) | T-07, T-09 (second-factor reset) | ✅ |
| REQ-611 | A2 `CredentialsInvalidated` event, `TokenStore.removeAllOf` (D13, D14) | T-06, T-07 | ✅ |
| REQ-612 | A2 principal = username, `LoginResponse.username` (D23) | T-05, T-06, T-12 (attribution proof) | ✅ |
| REQ-613 | A2 username pattern, mapper never leaks | T-05, T-07 | ✅ |
| REQ-614 | A3 `TotpVerifier`, `Base32` (D9, D25) | T-08 | ✅ |
| REQ-615 | A3 self-service endpoints (D11, D24) | T-09 | ✅ |
| REQ-616 | A3 `RecoveryCodes` (D10) | T-09 | ✅ |
| REQ-617 | A3 enforcement table + sequence (D6, D7, D8) | T-10 | ✅ |
| REQ-618 | A2/A3 admin reset of another user's factor | T-09 | ✅ |
| REQ-619 | A3 secrets never in logs/DTOs | T-09, T-17 | ✅ |
| REQ-620 | A4 `AuditEntry`, V7, no value column (D17, D18) | T-11 | ✅ |
| REQ-621 | A4 explicit `record` calls, actions/entities | T-12, T-13 | ✅ |
| REQ-622 | A4 `page` (D22), `manager-audit` contract | T-11 | ✅ |
| REQ-623 | A4 `AuditScheduler`, inventory edit | T-14 | ✅ |
| REQ-624 | A4 REQUIRED vs REQUIRES_NEW (D19) | T-11, T-12, T-13 | ✅ |
| REQ-625 | Contract changes; D25 no dependency | T-15 (+ every contract-touching task) | ✅ |
| REQ-626 | tests named per task | T-01, T-15, T-17 (+ every task's Tests block) | ✅ |
| REQ-627 | — (docs) | T-16 | ✅ |
| REQ-628 | device branch untouched; contract diff check | T-15 | ✅ |

Orphan check: T-01…T-17 each reference at least one of REQ-601…REQ-628; no task references a REQ outside that range. No unreferenced REQ.

## Checks
- [x] Coverage — every REQ-ID maps to ≥ 1 TODO task
- [x] No orphans — every TODO task references an existing REQ-ID
- [x] Design fidelity — the design satisfies every requirement; the one narrowing (own password change drops the owner's tokens, not all — D13) is recorded in the PRD (REQ-611) and the design consistently; the enforcement table matches REQ-617 line by line; the migration shape matches REQ-607
- [x] Constitution compliance (v1.8.0):
  - Principle 3 — audit rows hold names only (no value column by schema), throttle/TOTP/recovery log nothing, seeder messages name variables only
  - Principle 4 as amended — the application consumes one transport fact (`ClientAttribution`) for the bucket key and the factor requirement, nothing else; no second login path or token type; kiosks untouched (REQ-628)
  - Principle 6/7 — tests per task; REQ-IDs on every task; REQ-326 amended, not deleted
  - Backend constraint — BCE sub-packages for the two new features, one-line Boundaries, `@Transactional` on Controls, Boundary-only security annotations, five guards stay green, scheduled job only in `de.gxp.infrastructure.scheduler` with the pinned inventory edited deliberately
  - Auth & transport "Authentication hardening" paragraph — named accounts all `admin`, seed from the environment with refuse-to-start, deactivation drops tokens, per-address throttle before PBKDF2 never locking an account, proxy attribution from the configured proxy only, TOTP via JDK `Mac` with hashed recovery codes required only via the proxy, append-only audit with 365-day scheduler retention — every clause has a REQ
  - Dependency allow-list — `pom.xml` untouched (D25); `CurrentVertxRequest` and `quarkus.http.proxy.*` belong to the already-approved `quarkus-vertx-http` brought in by `quarkus-rest`
  - Contract-first — every operation/schema change in `api/openapi.yaml`; client regenerated
  - Branch discipline — stays on the current branch, no commits
- [x] No unresolved `[NEEDS CLARIFICATION]` markers in PRD, Design or TODO
- [x] No approval-gated decision still unconfirmed — the owner's §12 decisions are recorded under "Decisions confirmed by the user"; everything else was explicitly delegated and is recorded as D1–D25 with rejected alternatives. No new dependency, datastore, infrastructure or nested `CLAUDE.md` entry is proposed. (`ApiErrors.tooManyRequests` returns an existing pattern removed on 2026-08-24, not a new one.)

## Gaps
None.

Two notes for `dev`, not gaps:
- T-04 carries a verification note (whether Quarkus stamps
  `X-Forwarded-Trusted-Proxy` for a trusted peer without a `Forwarded`
  header). Either answer is compatible with the design; record it in the
  test's javadoc.
- The suite grows by roughly 60 tests and one extra Quarkus boot (the
  trusted-proxy profile). Plan the quiet window accordingly.

**Result: PASS** → proceed to `05_implement` (`dev`), starting at T-01.
