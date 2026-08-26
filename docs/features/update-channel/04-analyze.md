# Analyze: Update Channel (remote-operations Part C)
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** PASS (2026-08-26, against constitution v1.8.0)

> `Status` below means "a TODO task covers this", nothing more — it is not
> evidence the requirement is met (the 2026-08-18 lesson). This is a first
> round, not a correction round, so there is no defect table.

## Coverage matrix
| REQ-ID | Design section | TODO task(s) | Status |
|--------|----------------|--------------|--------|
| REQ-701 | §1 `ManifestClient`, config block; §3 (no listener) | T-07, T-10, T-13, T-18 | ✅ |
| REQ-702 | §2 fixed manifest layout, regex table; §4 `sign-manifest.sh`; D4 | T-01, T-02, T-08 | ✅ |
| REQ-703 | §1 `SigningKeyLoader`/`ManifestVerifier`, `runCheck` order (verify before parse); D7 | T-06, T-10 | ✅ |
| REQ-704 | §1 `SigningKeyLoader` fail-closed; §4 `SigningKeyResourceTest` | T-02, T-06 | ✅ |
| REQ-705 | §1 `ManifestParser`, `SemanticVersion`; D6 | T-05, T-08, T-10 | ✅ |
| REQ-706 | §1 `ManifestClient` timeouts/cap/no redirects; Risks | T-07, T-10 | ✅ |
| REQ-707 | §1 `UpdateScheduler`, jitter; D10 | T-10, T-12 | ✅ |
| REQ-708 | §1 `reconcileDesiredUpdate` algorithm; D11 | T-10, T-12 | ✅ |
| REQ-709 | Data model V8; D8 | T-04 | ✅ |
| REQ-710 | API contract `getUpdateStatus`, `status()` | T-03, T-11 | ✅ |
| REQ-711 | API contract `updateUpdateSettings`, transactions/audit table; D20 | T-03, T-11 | ✅ |
| REQ-712 | API contract `applyUpdateNow`, `applyNow()`; D13 | T-03, T-11 | ✅ |
| REQ-713 | API contract `checkForUpdateNow`, `checkNow()` non-tx + `EXPLICIT_ENTRIES`; D9, D16 | T-03, T-11 | ✅ |
| REQ-714 | `reconcileDesiredUpdate` (auto disabled → no file, available kept) | T-10, T-11 | ✅ |
| REQ-715 | §2 `desired-update.json` layout, `ExchangeFiles`, shared fixture; D3 | T-09, T-10, T-15 | ✅ |
| REQ-716 | §2 `state.json` layout, tolerant read; D21 | T-09, T-15 | ✅ |
| REQ-717 | §2 Volume; compose/Dockerfile; live proof in the smoke script | T-09, T-13, T-16 | ✅ |
| REQ-718 | §3 updater steps 0–4, regex validation, replay guard; D2, D19 | T-14, T-15 | ✅ |
| REQ-719 | §3 step 6; D18 | T-14, T-16, T-17 | ✅ |
| REQ-720 | §3 steps 7–8; compose `GXP_IMAGE`; D5 | T-13, T-14, T-16 | ✅ |
| REQ-721 | §3 step 9 + "Why ready + the Flyway row"; D1, D14 | T-14, T-16, T-17 | ✅ |
| REQ-722 | §3 steps 10–11; D15 | T-14, T-17 | ✅ |
| REQ-723 | §3 window check + "Timing and the kiosk" (code-verified) | T-14, T-16, T-17, T-19 | ✅ |
| REQ-724 | §3 test files | T-16, T-17 | ✅ |
| REQ-725 | §4 keys and tooling; D17 | T-01, T-02, T-06, T-14, T-18 | ✅ |
| REQ-726 | API contract (no raw manifest), logging rules in §1, D22 | T-03, T-11, T-18 | ✅ |
| REQ-727 | `ManifestParser` (app) + §3 step 4 (updater); D6 | T-05, T-08, T-10, T-14 | ✅ |
| REQ-728 | §4 rotation note, Risks, the verbatim §6 consequence | T-19, T-20 | ✅ |
| REQ-729 | §1 `ManifestClient` javadoc/no identity imports | T-07 | ✅ |

## Checks
- [x] Coverage — every REQ-701…729 maps to ≥1 TODO task (29/29)
- [x] No orphans — every TODO task T-01…T-21 references existing REQ-IDs (T-21 references the full range)
- [x] Design fidelity — each requirement has a Design section; the three PRD amendments made at Design time (REQ-709 manual-request columns, REQ-713 "smoke script drives check-now" removed, REQ-717 acceptance re-phrased to the live `state.json` proof + the shared fixture) are recorded in the PRD itself, so PRD and Design agree. Verified against the code: `/api/system/ready` exists and `quarkus-smallrye-health` does not (D1); `AuditControl.record` needs a request scope (hence the transactions/audit table); `AuditCoverageTest` pins 25 and scans `@Transactional public` on `*Control.java` only (hence `EXPLICIT_ENTRIES` for `checkNow`); `ScheduledJobInventoryTest` pins three jobs today; `PropertyDto.getTimezone()` is the property's zone; `GuestDataService` sets `STALE` on a poll failure with a held config and wipes only on `HttpErrorResponse.status === 401` (REQ-723 claim holds); `.gitignore` already ignores `*.env`, so the compose `.env` the updater rewrites is never committed.
- [x] Constitution compliance — principle 4 (outbound only, key in the image, updater beside the app, rollback, survival invariant: every failure of the update server is a status code, never a guest/manager effect); dependency posture (D22: nothing added to `pom.xml`, the image, or the host); principle 3 (no secret exists on the path; class-name-only logs; no manifest bytes in logs or DTOs); principle 6/7 (every task has a test or a script and a REQ-ID); BCE + contract-first + `AuditControl.record` on every manager mutation + scheduling only in `de.gxp.infrastructure.scheduler` with Quartz crons and `SKIP`; no `/api/guest/**` or `/api/pairing/**` change; no `pom.xml` edit; branch discipline (feature branch).
- [x] No unresolved `[NEEDS CLARIFICATION]` markers in PRD, Design or TODO
- [x] No approval-gated decision still unconfirmed — no new dependency, datastore or infrastructure choice is introduced; the updater's form (host script + systemd) is a deployment detail inside the compose-unit decision already approved, and the owner delegated every remaining decision (Design table D1–D22). The one decision that *touches* an approval gate — the production signing key's custody (D17) — adds nothing to any manifest and is routed to `architect` (T-20) by REQ-728 before `06_review`.

## Gaps
None. Two things are deliberately **not** gaps but are recorded so `06_review` does not mistake them for omissions:
- The update **server** and the registry choice are non-goals (PRD); `artifactRef` is pull-agnostic and the rehearsal uses local images.
- Key **rotation** is out of scope by design (§4) and is the first item of the architect review.

**Result: PASS** → hand off to `dev` for `05_implement`, starting at T-01.
