# Handoff — module-content (Part C)

**Last session:** 2026-08-27, `planner` — `01_prd` as a preservation document,
then a **document reconciliation** closing D10 and D12 (no design or TODO work)
**Branch:** `refactor/bce-subpackages-and-boundary-security` (owner instruction)
**Status:** **STILL NOT READY TO PLAN**, now on two grounds instead of four:
**D9 alone is open**, and **no module screen is designed** — the latter is the
real blocker and no decision can supply it. `02_design` must not start.

## Where this stands

- Part C of the three-way split of `modular-menu-catalogue` (owner D1,
  2026-08-27). REQ-961…REQ-978 moved across unchanged, so nothing is lost;
  REQ-979 was rewritten later the same day against constitution v1.9.0.
- **One gate is open and not approved: D9.** The lead stated explicitly to the
  owner that consent is not taken as given, and the owner's deferral is not it.
- **Two gates closed on 2026-08-27, in a later session:** **D10 approved** once
  it was re-framed correctly as a *frontend code file* beside the existing
  `menu_item_definition` table (the owner accepted that both sides then get
  maintained: *"denke okay, weil im Frontend muss ja sowieso entwickelt werden
  wenn ein neues Modul hinzu kommt, dann kann man auch immer die Datei
  mitziehen"*); **D12 settled** by constitution **v1.9.0**, whose wording the
  owner approved verbatim. **REQ-970 is unblocked and REQ-979 was rewritten**
  (its old text quoted a clause that no longer exists), A4/A5 were re-pointed,
  and the technical constraints now carry the amended three-bullet boundary.
- **Two further decisions were already settled** and constrain the block: **D8** — the manager may
  only choose which items are shown and, for the first four, in which order, so
  `docs/backend/specification.md` §3 decision 5 **stands uncontested** and
  REQ-965 is not authorised as written; **D11** — no per-room content override
  beyond the label/icon/sort override that already ships.

## What must happen before this can be planned

1. **D9 — the only open gate.** `oneOf`/`discriminator`. The owner deferred
   because he did not understand the question; that is not approval. Ask again **after** a generator
   spike (one throwaway two-member union, `./mvnw verify`, read both outputs).
   `jaxrs-spec` + `interfaceOnly` + `openApiNullable=false` has never emitted a
   `oneOf` in this repo. A new artifact would be a **second** gate.
2. **At least one actually designed module screen.** REQ-961 demands a fixed
   per-type shape while the Non-goals exclude designing any module screen — that
   is unresolvable, not a scheduling problem.

## Do not re-derive these

- Part A needs **nothing** from this Part — no union, no registry. If a Part A
  task appears to need one, that is Part A widening, not Part C being ready.
- REQ-962 (the kiosk never re-derives the delivered order) is where
  `guest-content.mapper.ts`'s redundant `sortIndex` re-sort gets resolved. Part A
  deliberately leaves it alone.
- Part A's eight placeholder catalogue rows are **disposable fixtures**, not
  candidate modules. REQ-974 must not adopt them by reflex.
- **D12's three options are withdrawn, all of them.** No per-field `sensitive`
  flag on the wire, no per-module-type classification in the registry, no
  "module content is never cached" condition at the write site. Under v1.9.0
  each costs offline availability to buy nothing an attacker did not already
  have — do not re-propose one as a safety improvement.
- **What survives is the credential line, not the sensitivity line.** A `wlan`
  module's password and an `anreise` module's key-box code may be cached and
  rendered offline; a token, key material or anything addressing the backend or
  another room may not. That is REQ-979, and Part B's REQ-951 is the precedent
  — including its warning that the test must **not** filter on names like
  `password` or `code`, which under v1.9.0 would assert close to the opposite
  of the rule.
- **D12 leaves one obligation behind, owned by nobody:** operator documentation
  must state that door, key-box and alarm codes are rotated when a kiosk goes
  missing (constitution v1.9.0's third cache bullet). It falls to whichever
  feature first ships a cacheable code — on current plans, this one.
- **D10's approval is not D9's.** The registry carries the type discriminant;
  how that discriminant is represented on the wire is still open. And the
  registry still has no consumer before this Part — approving the pattern did
  not create a reason to write the file.
