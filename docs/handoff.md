# Session Handoff — 2026-08-27, `lead`

**Branch:** `refactor/bce-subpackages-and-boundary-security` — **owner
instruction: stay on it**, even though the name does not match the work
("Ich habe meine Gründe"). Do not create or switch branches.
**Commits:** the owner granted commit authority 2026-08-27 (*"doch committe
auch gerne"*). Commit at **coherent checkpoints** — a reviewed block, a
finished amendment — never mid-implementation while a `dev` agent is writing.
`docs/constitution.md` v1.9.0 is committed (`0ed4f03`); everything else is
still uncommitted.

## What this session did

The owner brought an **owner-written** PRD (`docs/features/modular-menu-catalogue/`)
and asked for it to be pressure-tested rather than accepted. It was rewritten
and **split into three features** (owner decision D1):

| Feature | Part | REQ | State |
|---|---|---|---|
| `sidebar-catalogue-overflow` | A — the owner's actual ask | 901…935 | Planned, `04_analyze` **PASS**. Implementation in progress |
| `guest-payload-consolidation` | B | 941…952 | PRD decision-complete, **not yet designed** |
| `module-content` | C | 961…979 | **Not ready to plan** — blocked on D9 and on a real module screen existing |

`modular-menu-catalogue/` is retired to a `README.md` pointer.

**`docs/constitution.md` is now v1.9.0** (amended this session, owner-approved
verbatim): the kiosk cache clause changed from an absolute prohibition to a
threat-model boundary, and stale rendering now escalates to a prominent banner
after a prolonged loss of contact (the threshold lives in Part B, not in the
constitution).

## Owner decisions, all 2026-08-27

D1 split A→B→C · D2 flat catalogue + popover, no hierarchy · D3 `n = 5` sidebar
slots (4 entries + overflow), popover unbounded · D4 parameterised placeholder
route + `**` fallback · D5 new catalogue rows `enabled = false`, `%dev` true ·
D6 popover designed in-repo · D7 merge **only** `session` + `screens/home` ·
D8 manager selects and orders, no content authoring — spec §3 decision 5 stands ·
D10 module registry approved (frontend code file *and* the existing table) ·
D11 no per-room content override · D12 settled by the v1.9.0 amendment ·
D13 escalating staleness banner, 60 min, also on the vacant-room standby screen.

**D9 (`oneOf`/`discriminator` on the wire) is the one open gate.** The owner
deferred to expertise without understanding it, which is **not** informed
approval. It needs the generator spike first — `jaxrs-spec` has never emitted a
`oneOf` in this repo.

## Autonomy grant — and its limits

The owner left for several hours (2026-08-27) with: *"Bitte fahre so autonom wie
möglich fort … falls doch was blockiert und es nichts großes darfst du auch
entscheiden."*

**In scope to decide alone:** small implementation choices, naming, test
structure, ordering of planned work, deferring open questions to a list.

**Still blocked, grant or no grant** — `docs/constitution.md` → "Decisions
Requiring Your Approval": a new dependency, a datastore/infra choice, a new
architectural pattern, a nested `CLAUDE.md` entry and a constitution amendment
(exact text approved **before** writing). "Nichts großes" does not cover a
gate; a gate is by definition the big kind. **Committing is no longer on this
list** — see above.

## Decisions the lead took under the autonomy grant

**2026-08-27 — Part A review 🟠 1: restore the kiosk-side menu bound at 50.**
The reviewer found REQ-901's acceptance criterion unmet — the contract now
declares `GuestMenu.items.maxItems: 50`, but nothing enforces it: response DTOs
are not runtime-validated (the generated `@Size(max=50)` is inert) and T-08
deleted the only kiosk-side check. The plan was internally inconsistent:
`02-design.md` §2 says the enforcement that matters is the kiosk's boundary
check, and §5 then removes it, justifying that with *"trusted for count the
same way `actionCards` is"* — which is factually wrong, `MAX_CARDS = 4` still
rejects at `guest-content.mapper.ts:184`.

Decided: **restore the guard at 50**, keep the criterion, correct §2/§5. Reasons
— it is the conservative half of the choice; it matches the sibling precedent
in the very same file; and it preserves what the original finding was *about*.
The old `MAX_MENU_ITEMS = 5` was wrong because it **invented** a bound and lied
about its source, not because bounds are wrong. At 50 the message *"exceeds the
contract's 0–50"* is finally true. Removing the check instead would drop
boundary validation of an untrusted wire, which `01-prd.md` A3 calls the
frontend's contract with that wire.

Not a constitution gate: no dependency, no infra choice, no new pattern, no
nested `CLAUDE.md` entry. **Owner may reverse it** — it is ~5 lines and one
acceptance criterion.

## Deferred questions — for the owner, none blocking

1. **REQ-ID space is exhausted.** `9xx` was the last free three-digit block.
   Feature nine needs a scheme (four digits, or a slug prefix).
2. **`PUT /api/manager/menu-config` is a whole-set replace** — a partial submit
   deletes D5's `enabled = false` rows, and absence resolves back to *enabled*.
   Pinned with a test for now; it becomes a real trap when `backend/manager-ui/`
   is built.
3. **Part A is invisible in production at first.** With `n = 5` and D5, a fresh
   instance shows today's five sections and no overflow entry until a manager
   enables something.
4. **Migration `V10` is contested** with the deferred `instance-identity-and-certs`
   plan. Part A takes it; that plan renumbers when picked up.
5. **Constitution wording vs. the standby banner.** v1.9.0 says the escalated
   banner directs *"the guest to their host"*, but the owner decided it also
   shows on a **vacant** room's standby screen, where it addresses staff. Part B
   judged that nothing is contradicted (the clause's own rationale is
   operational alerting) and proposed no amendment. If the owner wants the
   constitution to name the staff case explicitly, that is an amendment and
   needs his approval of the exact text. Non-blocking.
6. **Watch item for Part B implementation, not a question:** REQ-946's
   acceptance criterion reads *"given no content write between two polls, then
   `304`"*, but the version stamp carries a **300 s refresh bucket**, so the
   poll pair that straddles a bucket boundary legitimately returns `200`. The
   test must control the clock or avoid the boundary, or it will flake roughly
   1 run in 10. `02-design.md` reasons the bucket through and it should stay —
   the criterion is what needs to be read precisely.
7. **`docs/backend/specification.md:511–512`** still quotes the retired cache
   wording as a v1.4.0 amendment instruction. Owner decided to leave it as a
   dated log.
