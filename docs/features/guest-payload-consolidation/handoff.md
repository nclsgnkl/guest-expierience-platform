# Handoff — guest-payload-consolidation (Part B)

**Last session:** 2026-08-27, `planner` — `02_design` → `03_todo` →
`04_analyze` (**PASS**), plus three corrections to `01-prd.md`
**Branch:** `refactor/bce-subpackages-and-boundary-security` (owner instruction)
**Status:** **Planned through the gate. Ready for `05_implement` at T-01 —
after Part A's commit.** No approval gate is open, no clarification marker
remains.

> ## Owner stop — lifted 2026-08-27
>
> Part B may be planned **and** implemented without a further check-in. The
> lift does **not** extend to approval gates: no dependency, no datastore/infra
> choice, no new architectural pattern, no nested `CLAUDE.md` entry and no
> constitution amendment without the owner approving the exact text. Nothing in
> this plan needs any of them — verified at `04_analyze` §4.

## Where this stands

All four documents exist: `01-prd.md` (REQ-941…952), `02-design.md`,
`03-todo.md` (**19 tasks, 6 phases**), `04-analyze.md` (**PASS**, 12/12
covered, 0 orphans).

**The four decisions `02_design` owned and took:**

1. **The endpoint is `GET /api/guest/content`** (`getGuestContent`,
   `GuestContent` + nested `GuestHome`). The `screens/` namespace is retired on
   purpose — it advertised a per-screen family the project has decided against.
   `session` was rejected: it names the auth relationship, not the payload.
2. **The ETag stamp is `sha256(bootId | max(audit_entry.id) | roomId | stay
   fingerprint | 300 s bucket)`** — no new table, no new bump call site. It
   leans on the constitutional audit obligation and the test that already
   enforces it (`AuditCoverageTest`), and the bucket bounds every
   under-invalidation at five minutes instead of forever.
3. **`If-None-Match` is declared in the contract** and the `304`/`200` are
   thrown `Response`s from the Control — the `getGuestAsset` pattern verbatim.
   `GuestEtagFilter` is **deleted**, which makes REQ-946 structurally true, and
   the frontend's `defaultHeaders` hack goes with it.
4. **The banner's spec is written, not deferred** —
   `docs/design/screens-inhouse/connection.md` → "The escalated staleness
   banner" (the Part A precedent: `menu-overflow.md` was written at
   `02_design`). `dev` implements that file and must not invent copy or
   geometry. The glyph is the already-registered `WifiSlash`, deliberately —
   no new icon, so `registered-icons.ts` stays Part A's, and the severity order
   stays honest.
5. **The banner is `level: 'NONE' | 'PILL' | 'BANNER'`** on a new
   `core/guest/staleness.service.ts`, driven by the existing minute-aligned
   `ClockService`. Tri-state, so "banner up ⇒ pill down" is true by
   construction. It escalates **in place** — the pill's own anchor, grown into
   a panel — and it is an absolute overlay, so it cannot grow the document.

## What must happen next

`dev` implements T-01 → T-19 in order. **Start condition: Part A
(`sidebar-catalogue-overflow`) must be committed** — see below.

## Do not re-derive these

- **Everything in the previous handoff still holds**: the withdrawn
  cross-module argument, the live `404` defect, `storedAt` is *not* dead
  (nothing compares it to the clock — that is the true half), nothing in the
  retry machine interferes with the hour, the clock measures **contact** not
  change, and the escalation is a presentation property of `STALE`, never a
  fourth state.
- **`max(id)` on the audit table, not `count(*)`.** `AuditScheduler` prunes
  daily, so the count is not monotonic; the sequence is. The one state where
  `max(id)` falls (an instance idle for a whole retention window) moves the
  stamp to a value no kiosk has held during that boot — an extra `200`, never a
  stale `304`.
- **The 300 s bucket is not padding.** It closes the commit-ordering hazard
  (`max(id)` is allocated at INSERT, not at COMMIT) and covers the structural
  hole that `NOT_A_MANAGER_MUTATION` legitimately contains guest-side writes.
  Deleting it re-opens an unbounded stale `304`.
- **Part A has NOT taken REQ-948.** `V10` inserts fixture rows only; it touches
  neither `home` nor `MenuControl`. Part B owns the mandatory-`home` guard, the
  resolution change and a small `V11`.
- **Part A and Part B share seven files, not one** (plus one near-miss: the
  two parts extend *different* measurement instruments — Part A `measure.mjs`,
  Part B `measure-states.mjs`). `02-design.md` §0 lists them with a resolution
  each. The PRD's old "no code file is touched by both" is corrected.
- **`GuestHomeScreenTest.java` is a trap.** Part A added the
  `MenuFixtures.restoreCatalogueOptOut` teardown to it; Part B deletes the
  file. T-07 carries the call into `GuestContentTest` — losing it leaves Part
  A's catalogue fixtures enabled for the next test class, green until the order
  changes.
- **Three PRD defects were found and fixed by the gate** (`04-analyze.md` §7):
  REQ-948's acceptance criterion was unconstructible under the owner's fix,
  REQ-952's no-scroll criterion named an instrument that cannot reach the
  banner (`measure-states.mjs` can), and the blocking map was factually wrong.
  All three were repaired in `01_prd`, not papered over in the gate.
- **No sixth architecture test.** The constitution counts the five by name;
  a sixth is an amendment, i.e. a gate. The REQ-946 seam is proven
  behaviourally instead: corrupt `property.timezone` by native SQL (no audit
  row ⇒ stamp unchanged), and the `304` still comes back while a `200` cannot
  be composed at all.
