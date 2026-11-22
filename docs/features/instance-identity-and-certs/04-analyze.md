# Analyze: Instance Identity & Certificates
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** PASS

> Gate run 2026-08-26 against constitution **v1.8.1**, `backend/CLAUDE.md`,
> `docs/backend/remote-operations.md` §12. `Status` below means "a task
> covers this", not "this works" (the 2026-08-18 lesson).

## Coverage matrix
| REQ-ID | Design section | TODO task(s) | Status |
|--------|----------------|--------------|--------|
| REQ-801 | §1 `InstanceIdentity`, §7, D1 | T-05 | ✅ |
| REQ-802 | §1 `InstanceIdentity` | T-05 | ✅ |
| REQ-803 | §1, API `identity` | T-05, T-11 | ✅ |
| REQ-804 | §1 (no consumer outside `instance`), D13 | T-05, T-11, T-19 | ✅ |
| REQ-805 | §1 `EnrollmentClient`, enroll sequence | T-07 | ✅ |
| REQ-806 | §3, D4 | T-02, T-06, T-13 | ✅ |
| REQ-807 | §1 enroll sequence, data model, API | T-03, T-04, T-11 | ✅ |
| REQ-808 | §1 reconcile sequence, D6, D7 | T-04, T-11 | ✅ |
| REQ-809 | §1 transactions table, D12 | T-03, T-04, T-11 | ✅ |
| REQ-810 | §1 audit, D20 | T-11 | ✅ |
| REQ-811 | API contract | T-03, T-11 | ✅ |
| REQ-812 | §4, D16, D17 | T-08 | ✅ |
| REQ-813 | §5, D2 | T-01, T-14, T-15 | ✅ |
| REQ-814 | §5 hook, D3 | T-13 | ✅ |
| REQ-815 | §6, D14 | T-09, T-14 | ✅ |
| REQ-816 | §5 step 8, §7, D8, D9 | T-14, T-17 | ✅ |
| REQ-817 | §1 `CertificateReconciler`, §2, D9, D10 | T-01, T-10, T-18 | ✅ |
| REQ-818 | §5 step 4, D15, D19 | T-03, T-11, T-14 | ✅ |
| REQ-819 | §2, D11 | T-01, T-10, T-16, T-18 | ✅ |
| REQ-820 | §2, §7 `instance-name`/`tls-sans` | T-11, T-16 | ✅ |
| REQ-821 | §1 sequences, §2 fallback, D12 | T-07, T-10, T-11, T-14, T-18 | ✅ |
| REQ-822 | §1, §5 logging rules | T-07, T-11, T-13, T-14, T-19 | ✅ |
| REQ-823 | §1 `InstanceScheduler`, D15 | T-12 | ✅ |
| REQ-824 | §4 double, §5 tests, D17, D18 | T-02, T-08, T-17 | ✅ |
| REQ-825 | (documentation) | T-20 | ✅ |
| REQ-826 | §1 `InstanceValidation`, §5 regex table | T-06, T-13, T-14, T-20 | ✅ |
| REQ-827 | §5 (`LEGO_CA_CERTIFICATES` rehearsal-only), PRD constraints | T-07 | ✅ |
| REQ-828 | PRD prerequisites table | T-20, T-21 | ✅ |

## Orphan check
Every task T-01…T-21 names at least one REQ-ID from the range REQ-801…828; no task references an unknown ID.

## Checks
- [x] Coverage — every REQ-ID maps to ≥1 TODO task
- [x] No orphans — every TODO task references an existing REQ-ID
- [x] Design fidelity — the Design satisfies every requirement; no PRD/Design contradiction found. Specifically re-checked: REQ-819 ("self-signed always served") vs D11 (SNI default alias) — consistent, with the compose smoke (T-18) as the proof and a named fallback; REQ-809 ("without a restart") vs the reconcile call at the end of `unenroll()` — consistent; REQ-820's restart-time effect is stated in both.
- [x] Constitution compliance —
  - principle 4 survival invariant: REQ-804/821, D13 (kiosk path unchanged); nothing waits on our infrastructure;
  - "remote access separately sold / never branches on origin": no request-origin logic is added; unenroll loses only the public certificate (REQ-809);
  - dependency allow-list closed: D18 — no `pom.xml` change; `quarkus-tls-registry` types are transitive of an approved extension; the host tool is OS-packaged;
  - `quarkus-scheduler` core cron, central package, pinned inventory: REQ-823, D15;
  - BCE: sub-packages, one-line Boundary, `@Transactional` on the Control/store, Controls speak DTOs, no entity leaves `de.gxp.instance`;
  - contract-first: T-03 edits `api/openapi.yaml` first; `api/enrollment-service.yaml` is explicitly outside the generator;
  - principle 3 / logging: REQ-822, the token never persisted (schema has no column);
  - Flyway SQL only: V10; PostgreSQL only;
  - host scripts per `backend/CLAUDE.md` (no `eval`, regex before command lines): REQ-813/826;
  - the A-3 rule (root never opens a path inside the app-owned directory for writing): REQ-815/816.
- [x] No unresolved `[NEEDS CLARIFICATION]` markers in any of the three artifacts
- [x] No approval-gated decision still unconfirmed — every choice in Design → "Decisions taken under delegated authority" falls under the owner's 2026-08-25 delegation (§12: "decide as much as possible yourself") and the lead's brief for this feature; the only reserved stop (a required `pom.xml` dependency) did not occur. Two items are worth the owner's *eyes* even though they need no approval: **D13** (the kiosk CA step stays — §5's claimed benefit is withdrawn) and **D2** (`lego` from apt, host-side).

## Gaps
None.

**Result: PASS** → hand off to `dev` for `05_implement`, starting at T-01.
