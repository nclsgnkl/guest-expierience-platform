# Analyze: Backend Domain & Configuration
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** PASS

> Gate run 2026-08-19 against constitution **v1.5.1**, `backend/CLAUDE.md`,
> `docs/backend/specification.md` §4/§5/§6/§9/§12. First round (no
> correction), so no reverse defect trace applies. Status ✅ below means
> "a task covers this", not "this works" — proof is `06_review`'s job.

## Coverage — every REQ ↔ tasks (36/36, 0 orphans)

| REQ-ID | Design section | TODO task(s) | Status |
|---|---|---|---|
| REQ-301 | API contract; Scope decisions | T-01–T-04, T-26 | ✅ |
| REQ-302 | Data model (V2/V3); `instance_info` kept | T-05, T-06, T-26 | ✅ |
| REQ-303 | Data model `property`; Architecture (PropertyControl) | T-05, T-17 | ✅ |
| REQ-304 | Data model option tables; Pinned seed defaults | T-05, T-17 | ✅ |
| REQ-305 | Data model `room` | T-05, T-18 | ✅ |
| REQ-306 | Data model seeds (5 frontend tuples); catalogue read-only | T-03, T-05, T-19 | ✅ |
| REQ-307 | Data model `menu_item_config` (nullable-scope unique, per-field) | T-05, T-19 | ✅ |
| REQ-308 | Data model `action_card_config` (no target unique; wholesale room set) | T-05, T-20 | ✅ |
| REQ-309 | Data model `stay`; Scope decisions (derived active) | T-05, T-22 | ✅ |
| REQ-310 | Data model `asset` | T-05, T-15 | ✅ |
| REQ-311 | Data model translation tables; language `CHECK`s | T-05, T-17, T-19, T-20 | ✅ |
| REQ-312 | Data model constraints (sort 0..3 unique/scope); invariant algorithm | T-03, T-20 | ✅ |
| REQ-313 | Invariant 3 algorithm (symmetric check) | T-20 | ✅ |
| REQ-314 | Invariant 3 algorithm (affected scopes) | T-21 | ✅ |
| REQ-315 | Stay overlap (room-row lock) | T-22 | ✅ |
| REQ-316 | Asset pipeline; Pinned parameters (cap, downscale, ETag) | T-15, T-23 | ✅ |
| REQ-317 | TemplateControl (validate, per-field context) | T-14, T-17, T-20 | ✅ |
| REQ-318 | TemplateControl (resolve); manager reads raw | T-14, T-17 | ✅ |
| REQ-319 | API contract (property ops) | T-02, T-17 | ✅ |
| REQ-320 | API contract (rooms); room deletion semantics | T-02, T-18 | ✅ |
| REQ-321 | API contract (catalogue, menu-config) | T-03, T-19 | ✅ |
| REQ-322 | API contract (action-cards) | T-03, T-20 | ✅ |
| REQ-323 | API contract (stays) | T-04, T-22 | ✅ |
| REQ-324 | API contract (assets); delete guard | T-04, T-15, T-16 | ✅ |
| REQ-325 | API contract (constraints); `de.gxp.api` mappers | T-02, T-04, T-13 | ✅ |
| REQ-326 | Auth flow; Pinned (token, lockout) | T-01, T-10 | ✅ |
| REQ-327 | Auth flow; Pinned (idle 600 s, header) | T-01, T-08, T-12 | ✅ |
| REQ-328 | Auth flow (logout) | T-01, T-10 | ✅ |
| REQ-329 | Pinned (PBKDF2 600k/format) | T-07 | ✅ |
| REQ-330 | Auth flow (`AdminPasswordSeeder`); V3 | T-06, T-11 | ✅ |
| REQ-331 | Auth flow (password change → `clear()`) | T-01, T-12 | ✅ |
| REQ-332 | Architecture (mechanism/provider); FailClosedTest revisit | T-09, T-10, T-12, T-23 | ✅ |
| REQ-333 | Pinned + Auth flow (generic errors, no value logging) | T-10, T-11, T-23 | ✅ |
| REQ-334 | Data model seeds; Auth seeder | T-05, T-11, T-17, T-18, T-24 | ✅ |
| REQ-335 | (cross-cutting — per-behavior tests) | T-07…T-24, T-26 | ✅ |
| REQ-336 | (docs) | T-25 | ✅ |

**No orphans:** every task T-01…T-26 cites only REQ-301…336; verified task by
task.

## Design fidelity

- Every PRD requirement has a HOW: entities/constraints (Data model),
  invariants 1–6 (constraints by construction + two pinned algorithms +
  pipeline), templates (TemplateControl with per-field context), CRUD + auth
  (API contract, Auth flow), ops/seed (V2 seeds + seeder). No contradiction
  found between PRD and Design; the PRD's acceptance criteria all map onto
  designed mechanisms (spot-checked: the REQ-314 matrix ↔ the affected-scope
  algorithm; REQ-316's projection criterion ↔ the payload-free metadata
  query; REQ-327's slide proof ↔ `GXP-Token-Expires`).
- Spec §5 fidelity: all listed entities present except `Device`/
  `PairingRequest` — **correctly** absent (next feature, per the routing
  brief); translations typed per-entity, no key/value table; the no-unique-
  on-target rule is carried into the DDL with the frontend evidence cited.
- Spec §12 cut respected: preview endpoint and guest API deferred with
  recorded rationale; nothing forecloses the `/q/openapi` decision.

## Constitution compliance (v1.5.1)

- **Closed allow-list:** design confirms zero `pom.xml` changes (JDK
  features + approved-extension SPIs + core Postgres SQL); TODO repeats the
  ⏸-stop rule. Named non-additions respected (PBKDF2 not Elytron; opaque
  in-memory token not JWT; hand-written mappers).
- **Auth & transport:** implements the recorded model verbatim — password
  only at login, 10-min sliding in-memory hashed token, 256-bit
  `SecureRandom`, PBKDF2, `@RolesAllowed` + deny-unannotated fail-closed,
  v1.5.1 password lifecycle (seed-once, UI-changeable, change drops all
  tokens). TLS is the next feature; the plain-HTTP window is a PRD risk with
  the dev/preview-only caveat maintained, matching the constitution's
  sequencing (transport constraint lands with pairing).
- **BCE strictness:** one-line Boundaries, `@Transactional` + security on
  Controls, package-private Entities/Repos/Mappers, Control-only cross-
  feature injection; the menu+cards single-package call avoids circular
  injection rather than bending the rules. Infrastructure adapters (auth
  SPI, exception mappers) documented as platform adapters with zero business
  logic — consistent with the foundation's pinned exception-mapping
  precedent, not a new pattern requiring approval.
- **Principle 1:** property content arrives as data (neutral editable seed);
  the catalogue is release data by owner decision 5 — compliant by design.
- **Principle 3 / logging:** REQ-333 + T-23 sweep; foundation config
  untouched.
- **Principles 6/7:** tests ride each task; every task cites REQ-IDs.
- **Branch discipline:** `feature/backend` stated in PRD/TODO.
- **Approval gates:** nothing in the three artifacts adds a dependency,
  infrastructure, metered request, token-realisation change, new
  cross-cutting convention, or nested `CLAUDE.md` edit. No ⏸ pending.

## No unresolved markers

`grep -n "NEEDS CLARIFICATION" 01-prd.md 02-design.md 03-todo.md` → 0 hits.
All planner-level calls are recorded as decisions with rationale (design →
"Scope decisions"), and delegated explicitly by the routing brief.

## Gaps

None.

**Result: PASS → proceed to `05_implement` (`dev`), one task at a time,
tests with each task.**
