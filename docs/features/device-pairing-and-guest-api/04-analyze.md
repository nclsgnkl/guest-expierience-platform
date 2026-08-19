# Analyze: Device Pairing & Guest API
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** PASS — round 2 (2026-08-19), after the user approved all five open decisions verbatim ("All five recommended go ahead"); round 1 (same day) was GAPS with the decision batch as the sole gap class

> Gate run against constitution **v1.5.1** and `backend/CLAUDE.md` **as
> amended 2026-08-19** (the approved one-tag/two-Boundary clause). `Status`
> in the table means "a task covers this", not "this works" (the 2026-08-18
> lesson). This is not a correction round — no defects exist; round 1's only
> gap was the deliberately withheld decision batch, now resolved and folded
> into PRD/design/TODO (all `[⏸]` markers stripped; verified by grep).

## Coverage — every REQ maps to ≥1 task

| REQ-ID | Design section | TODO task(s) | Status |
|--------|----------------|--------------|--------|
| REQ-401 | API contract table; resolved decision #5 (tags) | T-01, T-02, T-06, T-26 (done) | ✅ covered |
| REQ-402 | Data model (V4) | T-03 | ✅ covered |
| REQ-403 | Pairing flow; pinned params (code/secret/TTL/rate) | T-04, T-05, T-06, T-20 | ✅ covered |
| REQ-404 | Pairing flow (claim-once, indistinguishable 404) | T-05, T-06, T-20 | ✅ covered |
| REQ-405 | Pairing flow (confirm) | T-05, T-06, T-20 | ✅ covered |
| REQ-406 | Token minted at claim; pinned params | T-05, T-20 | ✅ covered |
| REQ-407 | Contract table (manager-devices); DeviceControl | T-05, T-06, T-20 | ✅ covered |
| REQ-408 | Data model (room deletion); cross-feature notes | T-07, T-21 | ✅ covered |
| REQ-409 | Pinned params; no-scheduler decision | T-04, T-05, T-20 | ✅ covered |
| REQ-410 | Device authentication (auth extension) | T-08, T-21 | ✅ covered |
| REQ-411 | Guest read model step 1; contract note | T-01, T-10, T-21 | ✅ covered |
| REQ-412 | Device authentication (revocation immediacy) | T-08, T-21 | ✅ covered |
| REQ-413 | Pinned params (last-seen throttle) | T-05, T-21 | ✅ covered |
| REQ-414 | Architecture (annotation rules) | T-08, T-21, T-23 | ✅ covered |
| REQ-415 | Architecture; logging posture | T-05, T-23 | ✅ covered |
| REQ-416 | Guest read model (session) | T-10, T-22 | ✅ covered |
| REQ-417 | Scope decision "resolution never duplicated" | T-09, T-10, T-22 | ✅ covered |
| REQ-418 | Pinned params (settings chrome constants) | T-10, T-22 | ✅ covered |
| REQ-419 | Vacancy decision; session/homeScreen flows | T-10, T-11, T-22 | ✅ covered |
| REQ-420 | Guest read model (homeScreen) | T-11, T-22 | ✅ covered |
| REQ-421 | Pinned params (screen keys); homeScreen flow | T-11, T-22 | ✅ covered |
| REQ-422 | Asset reachability decision; asset flow | T-12, T-23 | ✅ covered |
| REQ-423 | ETag section; pinned cache params | T-13, T-23 | ✅ covered |
| REQ-424 | Preference-write decision; flow | T-09, T-14, T-23 | ✅ covered |
| REQ-425 | Guest read model; test strategy | T-21 | ✅ covered |
| REQ-426 | Localized-maps decision | T-10, T-22 | ✅ covered |
| REQ-427 | TLS — decided mechanics (keytool entrypoint, `gxp-tls`) | T-15, T-24 | ✅ covered |
| REQ-428 | TLS — config posture (`insecure-requests=disabled`) | T-16, T-24 | ✅ covered |
| REQ-429 | TLS — CA distribution | T-17, T-24, T-25 | ✅ covered |
| REQ-430 | TLS — certificate identity (`GXP_TLS_HOST`, EC P-256, 20y/10y) | T-15, T-24 | ✅ covered |
| REQ-431 | TLS — compose/backup (443→8443, `gxp-tls` volume) | T-18, T-24, T-25 | ✅ covered |
| REQ-432 | Resolved decision #4 (`@RolesAllowed({"admin","device"})`) | T-19, T-24 | ✅ covered |
| REQ-433 | TLS refuse-to-start; ops | T-15, T-16, T-24 | ✅ covered |
| REQ-434 | Test strategy throughout | T-20–T-24 | ✅ covered |
| REQ-435 | — (docs) | T-25, T-27 | ✅ covered |

## No orphans

Every TODO task (T-01…T-27) cites at least one existing REQ-4xx; T-26
(already done — the approved `backend/CLAUDE.md` amendment, written
2026-08-19) traces to REQ-401 via the tag convention it amends. ✅

## Design fidelity

- PRD and design agree on every flow (two-credential pairing, claim-once,
  ceiling parity via `MenuControl`, vacancy `409`s, localized maps, ETag
  semantics, reachability-restricted assets, stay-anchored preference) and
  now on every TLS/system-surface parameter — the five resolutions appear
  identically in PRD REQ-401/427/428/430/432/433, the design's TLS section
  and resolved-decisions record, and TODO T-01/T-15–T-19/T-24/T-26. ✅
- No contradiction with the settled ground: spec decisions 3/7/9/11/18, the
  ceiling rule (referenced, not re-decided), the `einstellungen`-as-chrome
  hand-off, the `/q/openapi` `%prod` posture, the §6-preview deferral, and
  the `TemplateControl` `@PermitAll` precedent. ✅
- The written `backend/CLAUDE.md` amendment matches the design's quoted
  approved text word for word (diff-checked at this gate). ✅

## Constitution compliance (v1.5.1)

- **Principle 4 / auth & transport:** implements the recorded model verbatim
  (device token = identity + authentication, hashed, revocable, room derived
  from it; HTTPS with instance CA distributed at pairing — now with decided
  mechanics). Room-scoping is a named, tested requirement (REQ-411/425). ✅
- **Dependency allow-list:** nothing added — the decided `keytool` path
  stays inside the JDK/base image; `dev` still stops with ⏸ if
  implementation contradicts this. ✅
- **Kiosk cache constraint:** nothing served is guest-sensitive yet;
  recorded, with the §11-item-9 hand-off for the first sensitive screen. ✅
- **Principle 3 / logging:** REQ-415 + T-23 sweep; TLS keys explicitly
  included. ✅
- **Principles 6/7:** every behavior has a named test task; every REQ has an
  ID and tasks trace to them. ✅
- **Approval gates honored:** all five decisions were withheld, batched with
  recommendations, and approved verbatim by the user 2026-08-19; the
  nested-`CLAUDE.md` amendment was written only after that approval
  (T-26). ✅

## Unresolved markers

None. `grep '⏸' / '[NEEDS CLARIFICATION' ` over PRD/design/TODO: the only
remaining occurrences are the standing rule "dev stops with ⏸ on an
unexpected dependency" — a process rule, not an open marker. ✅

## Gaps

None.

**Result: PASS** → proceed to `05_implement` (hand off to `dev`). Named
checks for review carried in the design: verify-at-implementation items
(Quarkus 3.33 TLS property names, `insecure-requests` semantics, ETag
interception point, identity attributes in response filters), the
indistinguishable-404 matrix, the claim-once race, and ceiling parity.
