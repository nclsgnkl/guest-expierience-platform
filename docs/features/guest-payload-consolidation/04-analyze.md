# Analyze: Guest Payload Consolidation

**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** PASS
**Run:** 2026-08-27, first round (not a correction round — no defect trace applies)
**Governing:** `docs/constitution.md` **v1.9.0**

> **What `Status: ✅` means below:** a task exists that covers the requirement.
> **It does not mean the requirement is met** — that is `06_review`'s job. On
> 2026-08-18 three shipped defects violated `REQ-ID`s already ticked in a
> matrix exactly like this one.

---

## 1. Coverage — 12 requirements, 19 tasks

| REQ-ID | Design section | TODO task(s) | Status |
|---|---|---|---|
| REQ-941 | §1, §3.1, §8 | T-01, T-04, T-07, T-18, T-19 | ✅ |
| REQ-942 | §1.2, §5 | T-04, T-10, T-11 | ✅ |
| REQ-943 | §5 (untouched list), §6.1, §11 (re-adopt row) | T-10, T-13 | ✅ |
| REQ-944 | §1.2 | T-04, T-07 | ✅ |
| REQ-945 | §1.2 | T-07, T-12 | ✅ |
| REQ-946 | §2.1, §3.2, §3.3 | T-02, T-03, T-04, T-08, T-10 | ✅ |
| REQ-947 | §2.1–§2.3 | T-02, T-03, T-08 | ✅ |
| REQ-948 | §1.2, §4, §7 | T-01, T-04, T-05, T-06, T-07, T-09, T-11 | ✅ |
| REQ-949 | §5 | T-10, T-19 | ✅ |
| REQ-950 | §9 | T-12, T-19 | ✅ |
| REQ-951 | §5 (cache record) | T-12 | ✅ |
| REQ-952 | §6.1–§6.6 + the written spec in `docs/design/screens-inhouse/connection.md` | T-13, T-14, T-15, T-16, T-17, T-18 | ✅ |

**12 / 12 covered. 0 uncovered.**

## 2. Orphans — none

Every task carries a `— *REQ-…*` line and every ID it names is in the PRD's
table (941…952). Task→REQ, checked one by one:

T-01 → 941/948 · T-02 → 946/947 · T-03 → 946/947 · T-04 → 941/942/944/946/948 ·
T-05 → 948 · T-06 → 948 · T-07 → 941/944/945/948 · T-08 → 946/947 ·
T-09 → 948 · T-10 → 942/943/946/949 · T-11 → 942/948 · T-12 → 950/945/951 ·
T-13 → 952/943 · T-14 → 952 · T-15 → 952 · T-16 → 952 · T-17 → 952 ·
T-18 → 941/942/952 · T-19 → 941/949/950.

**0 orphans.**

## 3. Acceptance criteria — each mapped to the task that asserts it

| PRD acceptance criterion | Asserted by |
|---|---|
| REQ-942/949 line count + five greps | T-10 (validate), T-19 |
| REQ-945 property half survives vacancy, in RAM and in `localStorage` | T-07 (wire), T-12 (cache) |
| REQ-946 `304` without serialising, **at the seam, not by timing** | T-08 (a) — the unresolvable-payload seam of §9 |
| REQ-947 check-in between polls → `200`; two rooms independent | T-08 (b), T-08 (c) |
| REQ-948 (restated) refused write; legacy row inert; no `STALE` | T-05, T-09 |
| REQ-950 the 13 cases, same observable behaviour | T-12 |
| REQ-951 allow-list, token absent, **no name-based filter** | T-12 |
| REQ-952 threshold 59/60 min | T-13 |
| REQ-952 contact-not-change (two hours of `304`s) | T-13 |
| REQ-952 cold boot already escalated | T-13, and again in the browser in T-17 |
| REQ-952 machine untouched (three members) | T-13 |
| REQ-952 no-scroll | T-17 |

## 4. Constitution compliance (v1.9.0)

| Clause | Verdict |
|---|---|
| Approved dependencies (both allow-lists **closed**) | ✅ Nothing added. `pom.xml`/`package.json` untouched, pinned as a T-19 check. The stamp uses the JDK, the DB and the existing audit feature; the banner's clock is the existing `ClockService` |
| Kiosk cache — may hold guest-facing content | ✅ REQ-951 as rewritten; the merged record is the same three fields written once instead of twice |
| Kiosk cache — **never** anything extending reach beyond the device | ✅ T-12's allow-list + token-absence test, explicitly **not** name-based |
| Stale rendering escalates after prolonged loss of contact | ✅ REQ-952 at 60 min; the threshold lives here, as v1.9.0 requires. Content keeps rendering — the banner is an overlay, and `DISCONNECTED` is untouched |
| Escalation is **operational**, not security | ✅ Design §6 carries the rationale in those terms; nothing in the design treats it as a security control |
| Nothing scrolls (document scope, v1.8.0) | ✅ The banner is an absolute overlay taking no flow space (§6.3), so it cannot grow the document — and it is still measured (T-17). No `overflow: hidden` anywhere in the plan |
| Append-only audit (v1.8.1) | ✅ No new manager-visible mutation. `AuditCoverageTest`'s only change is a two-entries-for-one swap in the exemption list (T-09), which the test's own contract requires when a Control method is renamed. The stamp **reads** the audit trail and never writes it |
| Principle 3 — logging is a security surface | ✅ No task adds logging. The stamp is a hash of ids and timestamps and never reaches a log |
| Principle 4 — self-hosted, offline, untrusted LAN | ✅ No external call, no new runtime fetch. The stamp is in-JVM plus one indexed query |
| Contract-first | ✅ T-01 edits `api/openapi.yaml` first; the client is regenerated and T-19 proves it is byte-identical on a second build |
| BCE / `backend/CLAUDE.md` | ✅ Boundary stays one delegating line; the 304/200 are thrown from the Control, the pattern `AssetControl.guestBinary` already ships. `GuestContentVersion` is package-private in `.control`; `AuditControl` is crossed through its public Control only |
| The **five** architecture guards | ✅ Unchanged, and the design explicitly rejects adding a sixth (§9) — that would need a constitution amendment, i.e. a gate |
| REQ-411 (no room/stay/device/property parameter on a guest operation) | ✅ The added parameter is the `If-None-Match` **header**, precedented by `getGuestAsset`. The room still comes exclusively from the device identity |
| Decisions Requiring Your Approval | ✅ **None triggered.** No dependency, no datastore/infra choice, no new architectural pattern, no nested `CLAUDE.md` entry, no constitution amendment |

## 5. Design fidelity — contradictions actively looked for

Six places where this plan could have contradicted the PRD, each checked:

1. **REQ-943 vs. REQ-952.** The banner adds no state, no transition and no
   timer: `StalenessService` is a derived signal over `staleSince()` and the
   existing `ClockService` (§6.1), and D13 pinned that shape in the PRD itself.
   `ConnectionState` keeps three members, asserted in T-13. **No contradiction.**
2. **REQ-943 vs. the refresh bucket.** Every ~5 minutes a `200` carries content
   equal to what is held, so `adopt()` re-maps and re-sets `_config`. Checked:
   no state, transition, timer, cadence, backoff or observable behaviour
   changes — only how often an already-exercised path runs (§11). **Not a
   violation**, and recorded rather than discovered later.
3. **REQ-946 vs. REQ-947.** The bucket is what keeps the cheap `304` honest:
   §2.3 names the commit-ordering hazard of `max(id)` and bounds it at 300 s,
   and it also covers the structural hole that `NOT_A_MANAGER_MUTATION`
   legitimately contains guest-side writes. The two named failure modes
   (check-in across a poll, two rooms) are tested directly, not argued.
4. **REQ-944/945 vs. D7.** Vacancy stays enforced by the server **omitting**
   fields (`home`, `guestDisplayName`, `preference`), never by a second
   endpoint — which is what D7 said the split never bought.
5. **REQ-949 vs. REQ-952.** The banner adds no line to `guest-data.service.ts`:
   `StalenessService` is its own file (§6.1). The estimate is ~136 removed
   against ~20 added.
6. **REQ-951 vs. the merge.** One record instead of two, same three fields;
   the allow-list is pinned on the payload's top-level keys so a new wire field
   fails the test until classified.

## 6. Markers

`grep -n "NEEDS CLARIFICATION"` across `01-prd.md`, `02-design.md`,
`03-todo.md`: **none**. Two items are explicitly *verify-at-implementation*
(§3.2's thrown JSON `Response`, with the §3.4 fallback that changes no
requirement; and the banner's exact panel geometry, which T-17 measures) —
neither is an unresolved decision and neither can open a gate.

## 7. Findings — resolved before this gate ran, and recorded

This gate did not start clean. Two PRD acceptance criteria were stale, and
both were fixed **in their owning phase (`01_prd`)** before the result below
was issued, per the rule that this gate detects and does not repair:

| # | Finding | Owning phase | Resolution |
|---|---|---|---|
| F-1 | **REQ-948's acceptance criterion was unconstructible.** It reads *"Given `home` resolves disabled for a room…"*, but the owner's fix — decided **after** the PRD was written and living only in `docs/handoff.md` — makes `home` mandatory, so that state cannot be reached. The criterion would have passed vacuously | `01_prd` | PRD amended: the owner's fix is now recorded as a settled decision, and the criterion restated to the strongest reachable form (the write is refused; a pre-existing row is inert; the kiosk does not go `STALE`). T-05/T-09 assert exactly that |
| F-2 | **REQ-952's no-scroll criterion named the wrong instrument.** `tools/measure.mjs` measures a route grid; the banner is a kiosk-*state* screen it cannot reach | `01_prd` | PRD amended to name `tools/measure-states.mjs` — which already drives the STALE pill the same way. The assertion (`scrollWidth <= clientWidth`, `scrollHeight <= clientHeight`) is unchanged |
| F-3 | **The PRD's blocking map was factually wrong.** *"`api/openapi.yaml` only … no code file is touched by both"* — Part A has since touched four files this Part must also change, with one more still open | `01_prd` | PRD's Blocking map corrected and pointed at `02-design.md` §0, which lists every shared file with a resolution each — including the one that is **not** a collision (the two parts extend different measurement instruments) |

Three further observations, none of them gaps, recorded so `06_review` does not
have to rediscover them:

- **O-1.** Two of REQ-950's thirteen cases test `409 room-vacant` on
  `screens/home`, a status this Part deletes. They are converted into
  "the server answered vacant" cases; the transition they protect — the
  previous guest's content leaving the screen at once, rather than sitting
  behind an untruthful pill — is preserved and still asserted (T-12).
- **O-2.** REQ-951's *"no manager bearer token"* is trivially true: a kiosk
  never holds one. The load-bearing half is the device token and the
  allow-list, and that is what T-12 asserts.
- **O-3.** The constitution's escalation clause speaks of *"directing the guest
  to their host"*; the vacant-room variant addresses staff, because a vacant
  room has no guest. The clause's own rationale — operational alerting on a box
  with no remote access — is what the vacant case serves, so **no amendment is
  proposed** (§6.6).

## 8. Start condition — this is a gate too

**Part A must be committed before T-01 begins.** One of its open tasks (T-14,
`shell.component.html`) touches a file this Part also touches — its T-18
extends `measure.mjs` while this Part extends `measure-states.mjs`, so those
two do **not** collide — and one of its *finished* tasks is a trap: `GuestHomeScreenTest.java`
carries the `MenuFixtures.restoreCatalogueOptOut` teardown that keeps the
shared backend database honest, and Part B **deletes that file**. T-07 carries
the call across explicitly; losing it would leave Part A's catalogue fixtures
enabled for whatever test class runs next, with a green suite until the order
changes.

---

## Result

**PASS** — 12/12 requirements covered by 19 tasks in 6 phases, 0 orphans, 0
unresolved markers, no constitution conflict, no approval gate open, and the
three PRD defects found by this gate fixed in `01_prd` before it issued.

**Next:** `05_implement` (`dev`), starting at T-01 — **after Part A's commit**.
