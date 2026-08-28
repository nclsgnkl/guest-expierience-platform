# Handoff — module-content (Part C)

**Last session:** 2026-08-28, `planner` — a **narrow document fold** closing
**D9** into `01-prd.md` after the owner approved Variant B on the evidence in
`d9-spike.md`. No design, no TODO, no analyze gate; Part C was deliberately not
planned further.
*(Previous: 2026-08-27, `planner` — `01_prd` as a preservation document, then a
document reconciliation closing D10 and D12.)*
**Branch:** `refactor/bce-subpackages-and-boundary-security` (owner instruction)
**Status:** **STILL NOT READY TO PLAN**, now on **one** ground: **no module
screen is designed.** That is the real blocker, it is the owner's to produce,
and no decision can supply it. **No approval gate is open any more** — D9, the
last one, closed 2026-08-28. `02_design` must not start.

## Where this stands

- Part C of the three-way split of `modular-menu-catalogue` (owner D1,
  2026-08-27). REQ-961…REQ-978 moved across unchanged, so nothing is lost;
  REQ-979 was rewritten later the same day against constitution v1.9.0.
  **On 2026-08-28 the block moved twice more: REQ-961 was amended** to pin D9's
  spelling (it could previously be read as requiring the `enum` that breaks),
  and **REQ-980 was added** — new, and the only ID this Part has gained.
- **D9 is settled (owner, 2026-08-28): Variant B.** *"ja mach mal variante B bei
  D9"*, given after reading `d9-spike.md` and a worked code example — asked
  again only once the spike had answered what the generators actually emit, so
  it is informed approval in the way the 2026-08-27 deferral was not.
  **`oneOf` + `discriminator: type`, each member's `type` a plain `string`,
  never a single-value `enum`** — the values are pinned by
  `discriminator.mapping` and `@JsonTypeName`. **The `enum` is the trap:** with
  the generator flag off it compiles and puts `"type":"GuestModule"` on the wire
  instead of `"text"`, silently; with the flag on it does not compile at all.
  The full reasoning is in `01-prd.md` → Settled decisions → D9, and the
  evidence in `d9-spike.md`; do not re-run the experiment.
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

1. **At least one actually designed module screen.** The only remaining
   blocker. REQ-961 demands a fixed per-type shape while the Non-goals exclude
   designing any module screen — that is unresolvable, not a scheduling problem,
   and it is the owner's to produce.

That is the whole list. **No gate is open.**

## Owner-gated, but not blocking: the one `pom.xml` line

`<useOneOfInterfaces>true</useOneOfInterfaces>` on the `jaxrs-spec` execution in
`backend/app/pom.xml`. **Do not add it now** — `api/openapi.yaml` has no `oneOf`
today, so it is a no-op, and adding it early puts configuration for something
that does not exist into an approval-gated file with nothing to review it
against. It lands in **the same commit as this Part's first real union**, and
the owner is asked for it **then**. It is a **generator option, not a
dependency** — the spike confirmed nothing new is pulled in, so D9's reserved
"second dependency gate" never opens — but `backend/CLAUDE.md` gates every
`pom.xml` change regardless. See `01-prd.md` → "Pending, owner-gated".

## Two open items the D9 fold surfaced — for planning, not for now

Recorded in `01-prd.md` → "Open items surfaced by the D9 fold", deliberately
unanswered because answering either is design work.

1. **REQ-963 vs. the discriminator property.** Variant B's member `type` is an
   unbounded `string`, and REQ-963 forbids unbounded strings on the wire. Either
   REQ-963 carves the discriminator out, or `maxLength` goes on it — and the
   second is **untested and suspect**: a `@Size`-annotated `getType()` is the
   same shape that made the `enum` fail to implement the generated interface.
2. **REQ-980 does not catch a wrong discriminant *string*.** It catches an
   unhandled union member. With `type` typed as `string`, a registry keyed on a
   typo'd or renamed literal typechecks fine — the rejected Variant A got that
   check for free from the generated `TypeEnum`. Third drift axis; nothing
   currently requires anything of it.

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
- **D10's approval was never D9's** — they were answered separately, and D9 is
  now answered too. The registry still has **no consumer before this Part**;
  approving the pattern did not create a reason to write the file, and settling
  D9 does not either.
- **Three consequences of Variant B are already folded into the requirements
  that own them — do not re-derive them and do not "fix" them by reverting to
  the `enum`:** (a) `switch (m.type)` no longer narrows in TypeScript, bought
  back cast-free by one 1-line `m is GuestTextModule` predicate per type (or
  `'body' in m`), and confined to `guest-content.mapper.ts` alone by REQ-977 —
  noted on **REQ-970**; (b) compile-time exhaustiveness is gone and is now
  **REQ-980, a new requirement**, because REQ-971 catches only
  registry↔validator drift and REQ-972/973 cannot tell "newer than this build"
  from "nobody registered it"; (c) a type predicate is an assertion TypeScript
  trusts, not a proof — the mapper's hand-validation is what actually proves the
  shape now, so do not thin it out — noted on **REQ-977**.
- **REQ-966 gained a real hole the spike found:** removing or renaming a schema
  does **not** delete the file the generator previously wrote under
  `packages/api-client/model/`. Three orphans had to be deleted by hand.
  Any commit here that removes or renames a schema must sweep the orphans in the
  same commit, or "byte-identical regeneration" is asserted against a tree a
  clean checkout would not produce.
