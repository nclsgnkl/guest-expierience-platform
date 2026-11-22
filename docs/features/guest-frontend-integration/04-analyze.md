# Analyze: Guest Frontend Integration
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** PASS — round 2, 2026-08-20 (planner), after the user answered the full D1–D9 batch (D1–D6/D8/D9 as recommended, **D7 changed to in-scope** → REQ-526–528 + TODO Phase 4b added, then re-gated). Round 1 (same day) ran GAPS with the open batch as the sole gap class.

> Not a correction round — no reverse defect trace applies. `Status` in the
> table means "a task covers this", never "this works".

## Coverage (REQ → Design → TODO)

| REQ-ID | Design section | TODO task(s) | Status |
|--------|----------------|--------------|--------|
| REQ-501 | §1, §2 (client wiring, alias), PRD Assumptions (ngVersion resolved) | T-05, T-06, T-21 | ✅ covered |
| REQ-502 | §7 (proxy, no-CORS posture) | T-08 | ✅ covered |
| REQ-503 | §7, §10 D2 (resolved) | T-02 ✔, T-03 | ✅ covered |
| REQ-504 | §7 (inertness proof obligations) | T-04, T-24, T-29 | ✅ covered |
| REQ-505 | §7, §10 D3 (resolved) | T-02 ✔, T-07, T-08, T-24 | ✅ covered |
| REQ-506 | §3 (state machine), screens-inhouse/pairing.md | T-15, T-16, T-22 | ✅ covered |
| REQ-507 | §3 (pins: 3 s, backoff, ~~429~~ — **amended 2026-08-24:** the 429/cooldown leg is withdrawn with the backend throttle; every create failure is the offline+backoff path, expiry renew) | T-16 | ✅ covered |
| REQ-508 | §2 (token store), §10 D4 (resolved: localStorage) | T-07, T-24 | ✅ covered |
| REQ-509 | §4 (401 wipe), §5 | T-10, T-14, T-22 | ✅ covered |
| REQ-510 | §1, §2, §6 (mapper/validation), §8, §10 D6 (resolved: delete) | T-09, T-12, T-13 | ✅ covered |
| REQ-511 | §6 (server-resolved strings, 0–4 cards) | T-19 | ✅ covered |
| REQ-512 | §6 (brand-hex boundary check) | T-09 | ✅ covered |
| REQ-513 | §2, §6, verify-at-impl (a) | T-11, T-19 | ✅ covered |
| REQ-514 | §6 (localized maps, zero fetch on switch) | T-12, T-20 | ✅ covered |
| REQ-515 | §6 (menu mapping, settings chrome) | T-12, T-19 | ✅ covered |
| REQ-516 | §4 (30 s ETag loop) | T-10, T-22 | ✅ covered |
| REQ-517 | §4 (occupancy), screens-inhouse/standby.md | T-10, T-15, T-17, T-22 | ✅ covered |
| REQ-518 | §2, §5, §10 D8 (resolved) | T-13, T-14 | ✅ covered |
| REQ-519 | §4 (cache pins), §10 D5 (resolved), screens-inhouse/connection.md (pill) | T-10, T-18, T-22, T-24 | ✅ covered |
| REQ-520 | §4, §5, screens-inhouse/connection.md | T-14, T-15, T-18, T-22 | ✅ covered |
| REQ-521 | §9, §10 D1 (resolved); specs **written** | T-01 ✔ | ✅ covered & executed |
| REQ-522 | §9 + the three specs (no-scroll by construction), PRD baselines | T-23 | ✅ covered |
| REQ-523 | §9/specs (i18n/voice), PRD offline sweep | T-20, T-24 | ✅ covered |
| REQ-524 | per-task Validate lines | T-09–T-19, T-28 tests, T-21 | ✅ covered |
| REQ-525 | §10/§11 (records), TODO Phase 5 | T-25, T-26 | ✅ covered |
| REQ-526 | §11 (path layout, copy step, SPA fallback + exclusions) | T-27, T-28 | ✅ covered |
| REQ-527 | §11 (auto-activated profile, Node-free backend builds, workflow) | T-27 | ✅ covered |
| REQ-528 | §11 (smoke assertions) | T-29 | ✅ covered |

**No orphans:** every TODO task (T-01…T-29) references ≥1 existing REQ-ID.
T-01/T-02 are done (planner, 2026-08-20); T-03…T-29 are open for `dev`.

## Design fidelity

- PRD ↔ Design ↔ screen specs agree: same-origin/proxy posture, token
  store, state machines, cache scope, deletion list, screen inventory, seed
  proof obligations, and the D7 path layout (`/` guest, `/api` + `/q`
  untouched, `/manage/` reserved). No contradiction found.
- One refinement made at spec-writing time is recorded in §9 rather than
  silently applied: the status colour is decorative-only (contrast), status
  *text* stays on `--color-text-secondary`.
- The 4xx contract is consumed unchanged; every mapped operation exists in
  the generated client; no guest op needs a scope parameter.
- D7 fidelity: REQ-526–528 ↔ §11 ↔ T-27/28/29 align on mechanism (copy
  profile on the verified `frontend/dist/frontend/browser` path), fallback
  exclusion rules, inert-without-bundle behavior, and smoke scope.

## Constitution compliance (v1.5.1)

| Check | Result |
|---|---|
| No new dependency; no manifest *dependency* edit (the D7 `pom.xml` change is build-plugin configuration of an already-bound core plugin; tsconfig/angular.json are config; client consumed by path as recorded). Standing rule: any actual new artifact at implementation → stop ⏸ | ✅ |
| Principle 2: swap inside services; components read `PropertyContentService`/`PreferencesService` | ✅ |
| Principle 3 + kiosk-cache clause: cache = non-sensitive v1 payloads only; token/secret/code never logged; cache cleared on 401 | ✅ |
| Principle 4: offline degradation designed; no external runtime request; room scoping untouched; D7 adds one unauthenticated static surface, analysed in §11's exposure note (no secret in the bundle — grep-proven; security remains a review category for `06_review`) | ✅ |
| Principle 5: new screens reference semantic tokens only; the one new role went through `tokens.md` → In-house tokens per the approved D1 convention | ✅ |
| Principles 6/7: per-task tests; every REQ has a stable ID + task trace | ✅ |
| Design-tool metering: zero MCP requests; `docs/design/**` edited only per the approved D1 convention (three specs + 📋 rows + one token row — diff-reviewable) | ✅ |
| Approval gates: **none open** — the batch is answered and recorded verbatim in 02-design §10; the manager-UI serving mechanism was already constitution-decided (v1.5.0) and D7 mirrors it by explicit user decision | ✅ |
| Branch discipline: feature branch per PRD assumptions | ✅ |

## No unresolved markers

Grep-verified across 01/02/03: zero live `[NEEDS CLARIFICATION]` markers
(the string appears only in prose *stating* that none remain). The remaining
`⏸` strings are the standing process rules (stop on an unexpected
dependency; propose `frontend/CLAUDE.md` text before writing) — the same
characterization the 4xx gate recorded.

## Gaps

None.

**Result: PASS** — proceed to `05_implement` (`dev`). Suggested order =
TODO phases: dev seed → client wiring/topology → data layer → screens →
D7 packaging → verification/measure/docs. Environment notes: the backend
suite needs Docker/Postgres; the compose smoke (T-29, folding in T-04's
assertions) needs Docker and a frontend production build first.
