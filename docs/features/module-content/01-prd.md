# PRD: Module Content
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md) | **Analyze:** [04-analyze.md](04-analyze.md) | **Spike:** [d9-spike.md](d9-spike.md) | **Screen:** [screens-inhouse/info.md](../../design/screens-inhouse/info.md)
**Status:** In Review — **PLANNED THROUGH `04_analyze` (2026-08-28).**
*(It read "Draft — PLANNABLE" until the fourth fold of the same day, which wrote
the design, the TODO and the gate.)* **As of the third fold the three
product calls that stood between this Part and a clean gate were answered.** The
one blocker this document ever carried — *"at least one real, designed module
screen must exist first"* — was discharged earlier the same day by
[`docs/design/screens-inhouse/info.md`](../../design/screens-inhouse/info.md),
and the two decisions that screen surfaced were closed in the second fold: **D14
— bespoke per type**, with the cost named back to the owner, and **D15 — a
module rides inside its menu item** (`menu.items[].module`), decided by the lead
on the owner's explicit delegation, which **answered REQ-974**
(`SectionPlaceholderComponent` survives, demoted to the empty state inside the
section host). **Every approval gate stays closed** — **D9 settled 2026-08-28**
(Variant B, on the evidence in [`d9-spike.md`](d9-spike.md)), after **D10 was
approved and D12 settled on 2026-08-27**.

**Third fold, 2026-08-28 — four owner decisions, D16…D19.** They close the three
items this document listed under "What stood in the way" and reclassify a
fourth:

- **D16 — REQ-965 is descoped from Part C.** Manager-authored module content
  becomes an explicit **Non-goal**; `docs/backend/specification.md` §3 decision 5
  **stands unamended**. Module content in this Part comes from the `%dev` seed
  and nowhere else. **Be plain about the consequence: Part C is not a shippable
  manager feature.** It proves the transport, the per-type wire shape, the
  registry and one rendered module against seeded content — genuinely useful, and
  not something a real property can be handed, because a WiFi password is
  per-property and somebody has to type it. The successor feature is **named** in
  the Non-goals and is deliberately not planned here.
- **D17 — one authored module type in `%dev`, not two.** The registry's
  multi-type path is proven by a **fabricated second type in a test**, never by a
  second designed screen. REQ-976 is amended to say so.
- **D18 — an unresolvable placeholder is refused at the write; the frontend does
  nothing about it.** The server rejects content carrying a placeholder it cannot
  resolve; if one reaches the kiosk anyway the **raw placeholder text simply
  renders**, which is already `TemplateControl.resolve`'s behaviour. Under D16
  the write half belongs to the successor feature — **Part C carries only the
  do-nothing fallback**, and deliberately writes no validation requirement it
  cannot implement.
- **D19 — empty states are per module type.** This **reclassifies** the old open
  question rather than answering it: it is a line on the checklist for designing
  any module type, not a question owed to some future one.

**Fourth fold, 2026-08-28 — the document is now complete.**
[`02-design.md`](02-design.md), [`03-todo.md`](03-todo.md) and
[`04-analyze.md`](04-analyze.md) were written in one pass, and this document
gained the two things it still lacked: **an `Acceptance criteria (testable)`
section**, which the PRD template requires and `04_analyze` reads, and the
**answers to the three open items** the design owed it (§1.3, §8, §7 there;
"Open items" 1, 2 and 5 here). **Nothing in this document is outstanding.**
One approval sits outside it and is unchanged: the single `pom.xml`
generator-option line, now planned as **`03-todo.md`'s T-01, a stop-and-ask
task that comes before the contract edit** — see "Pending, owner-gated", whose
reasoning the design sharpens.

> **Part C of three.** The 2026-08-27 owner decision **D1** split
> `modular-menu-catalogue` into three sequenced features. This is the third and
> last, and it cannot move earlier. Siblings:
> [`sidebar-catalogue-overflow`](../sidebar-catalogue-overflow/01-prd.md)
> (Part A, REQ-901…935, planned) and
> [`guest-payload-consolidation`](../guest-payload-consolidation/01-prd.md)
> (Part B, REQ-941…952, decision-complete). Retired parent:
> [`modular-menu-catalogue/README.md`](../modular-menu-catalogue/README.md).
>
> **Requirement IDs are stable.** REQ-961…REQ-978 mean here exactly what they
> meant in the parent PRD; that block moved unchanged, including the ones the
> settled decisions below constrain. **One exception, 2026-08-27: REQ-979 was
> rewritten** against constitution **v1.9.0** — its old text restated an
> absolute prohibition the amendment removed, so it now pins the line that
> survives (no credential, nothing that reaches past the device). Part B's
> REQ-951 was rewritten the same way and is the precedent. **REQ-970 is
> unblocked** — D10 is approved.
>
> **Second exception, 2026-08-28: REQ-961 was amended** to pin the exact `oneOf`
> spelling D9 settled on — the member's `type` is a plain `string`, never a
> single-value `enum` — because the pre-2026-08-28 text could be read as
> requiring the `enum`, and that reading is now known to be wrong. **REQ-980 is
> new**: the compile-time exhaustiveness guard Variant B costs and that no
> existing requirement carried. REQ-963, REQ-966, REQ-970, REQ-971, REQ-973 and
> REQ-977 gained inline consequences of the same decision.
>
> **Third exception, 2026-08-28 (second fold of the day): REQ-974 is answered,
> not amended.** Its own conditional — *"`SectionPlaceholderComponent` survives
> **only if** a delivered-but-unauthored module is a real state"* — is now
> resolved to **yes**, by D15's optional field and by there being eleven
> section routes with no designed screen. The text stands; the condition is
> discharged. **No new ID was minted in this fold.** REQ-961, REQ-962, REQ-963,
> REQ-970, REQ-972, REQ-973, REQ-976, REQ-978 and REQ-979 gained inline
> consequences of D14/D15. **REQ-976 is the one this fold breaks** — see the
> note on it. *(**Repaired 2026-08-28 by D17**, one fold later.)*
>
> **Fourth fold, 2026-08-28: REQ-965 is descoped and REQ-976 is amended.**
> **D16** takes REQ-965 **out of Part C's scope** — the ID keeps its meaning, it
> takes **no TODO task**, and `04_analyze` records it as a **declared exclusion,
> not a coverage gap**. **D17** rewrites REQ-976 to **one** authored module type
> in `%dev` plus a **fabricated** second type in a test. **D18** and **D19** mint
> no ID: D18 lands as inline notes on REQ-964, REQ-967 and REQ-972 — its
> server-side half lands on the successor feature, not here — and D19 is a
> reclassification recorded in "Open items", 4. **No new ID was minted in this
> fold**, and none was retired.
>
> **Governing:** `docs/constitution.md` **v1.10.0** *(reconciled 2026-08-28 —
> this document said v1.9.0 until the amendment landed the same day. **v1.10.0
> binds nothing here**: its new clause requires a shorter `%dev` value for a
> threshold that gates an otherwise unreachable state, and this Part introduces
> no threshold of any kind. The clause that governs this Part is still
> **v1.9.0's** cache boundary, unchanged by the amendment.)*,
> `docs/backend/specification.md`
> §3 decision 5 / §5, `frontend/CLAUDE.md`, `backend/CLAUDE.md`.

---

## The blocker is discharged

**What blocked this Part was never a decision.** It was: *"you cannot fix a
per-type schema before deciding what the type shows"* — REQ-961 requires *"one
member per module type, each with a fixed shape"* while the Non-goals exclude
*"designing the individual module screens"*. That is unresolvable from inside
this document, and it needed a screen.

**The screen exists.**
[`docs/design/screens-inhouse/info.md`](../../design/screens-inhouse/info.md),
2026-08-28 — the fifth in-repo surface, written to the owner's own brief
(*"mach da nur nen Platzhalter rein. einfach 2 Stichpunkte einmal Check[-out]
Zeit und dann nen fiktiven wlan code"*). Two rows: a check-out time resolved
from the shipped `{stay.checkOut|date}` / `{stay.checkOut|time}` placeholders,
and a fictional WiFi password. It is **design only** — nothing is implemented
until this Part puts the values on the wire, because building it against
hardcoded content means building it twice.

That screen settled the frame, the row, provenance, ordering, absent values and
the no-scroll budget on its own authority. What it could **not** settle it
handed up, and the ledger is exact:

- **One recommendation, needing the owner** — is a module a generic row list, or
  bespoke per type? → **answered: D14, bespoke per type.**
- **Three open questions, all of them this Part's — and all three are now
  closed** — *(1)* nothing binds a module to a section route → **answered:
  D15**, and REQ-974 falls out of it; *(2)* an unresolvable placeholder reaches
  the guest as literal braces → **answered: D18** — refused at the write (which
  is the successor feature's, not Part C's), raw text at the read, and the
  mapper does nothing; *(3)* a module type that can deliver zero rows needs an
  empty state → **reclassified: D19** — it is a per-type design-checklist line,
  not a question, and `info` cannot be empty, so designing none was right.

**No approval gate is open.** All three gates that ever existed are under
Settled decisions: **D9**,
`oneOf`/`discriminator`, **settled 2026-08-28** — the owner approved Variant B
after the generator spike answered what the toolchain actually emits; **D10**,
the module registry, **approved 2026-08-27** once it was re-framed correctly as
a frontend code file; **D12**, module content in the last-known-good cache,
**settled by constitution amendment v1.9.0**, whose wording the owner approved
verbatim.

Part A deliberately needs nothing from this document — no `GuestModule` union
and no module registry — which is what made deferring it free, and that is
unchanged.

## What stood in the way — **all three closed, 2026-08-28**

**Nothing owner-gated remains.** The three items this section listed are
answered by **D16**, **D17** and **D18**. They are kept below **with their
resolutions** rather than deleted, because "this was once a blocker and here is
how it was answered" is the part that stops it being rediscovered as new.

| # | What it was | Resolution |
|---|---|---|
| 1 | **REQ-965 was a Must with no authorised form.** D8 settled that `docs/backend/specification.md` §3 decision 5 stands, so manager-authored module *content* had no sanctioned path. | **D16 (owner, 2026-08-28): descoped.** REQ-965 is **out of scope for Part C**, takes **no TODO task**, and decision 5 stands unamended. `04_analyze` must record it as a **declared exclusion, not a coverage gap** — see the requirement row and the Blocking map. |
| 2 | **REQ-976 could not be met.** It asked `%dev` for *"at least two authored module **types**"*; D14 makes a second type cost a second designed screen — the very blocker that had just cleared. | **D17 (owner, 2026-08-28): one authored type.** `%dev` seeds `info` only; the registry's multi-type behaviour is proven by a **fabricated second type in a test**. REQ-976 is amended to that, with the reason the substitution is legitimate written into it. |
| 3 | **The unresolvable placeholder.** A placeholder the template engine cannot resolve reaching the guest as literal `{…}`; `TemplateControl.resolve` leaves it by design and the kiosk cannot detect it. *(**With today's placeholder set that outcome is unreachable on `/info`, not merely unlikely** — `stay.check_out` is `NOT NULL` and a null `checkOut` means a vacant room, which is on `/standby`. See D18's correction.)* | **D18 (owner, 2026-08-28): refused at the write, nothing clever at the read.** The server refuses to *store* it — and that half belongs to the successor feature D16 names, because Part C has no write path. **Part C carries only the frontend's do-nothing fallback**, which is existing behaviour and costs no code. |

**Two things remain outside that list, and neither blocks `02_design`:**

- **The single `pom.xml` generator-option line.** Owner-gated and outstanding,
  but it blocks the **commit** that first ships a union, not the plan — see
  "Pending, owner-gated".
- ~~**This document has no Acceptance criteria section**, which the PRD template
  requires and `04_analyze` reads.~~ **Written 2026-08-28** — see "Acceptance
  criteria (testable)". The three that waited on `02_design` are written
  against its answers, not deferred a fourth time.

---

## Problem statement

### P3 — Module content has no model, and the manager may not author it today

A catalogue entry carries a label, an icon and a route. There is no model for
what a section *shows*, and every section route renders
`SectionPlaceholderComponent`. But the shape of the answer is **already
constrained**: `docs/backend/specification.md` §3 decision 5 and §5 state that
`MenuItemDefinition` is *"the release catalogue, seeded by Flyway and **not
writable through any API**"* and that a new menu item is *"a seed row plus a
frontend screen — not a feature"*. Manager-authored module content overturns
that — and as of **2026-08-27 the owner confirmed decision 5 stands**. See D8.

**Update 2026-08-28 (D16): it stays that way in this Part, by choice.** REQ-965
is **descoped**, not deferred-with-a-task — Part C's module content comes from
the `%dev` seed and from nowhere else. **The second half of this problem
statement is therefore not solved here.** It is the successor feature
**`module-content-authoring`**, whose first act is an amendment to decision 5,
proposed to and approved by the owner. Nothing in the rest of this document
should be read as closing P3.

---

## Goals / Non-goals

**Goals**

1. **A module content model.** *(Until 2026-08-28 this read "once a module
   screen exists to model"; `screens-inhouse/info.md` is that screen.)* With
   per-module failure isolation: one malformed module is dropped, only a
   structurally invalid envelope is fatal. Twenty modules must not mean twenty
   ways to blank a kiosk.
2. **Bounds that are declared and enforced at the boundary.**
3. **One module type actually rendering — `info`.** Added 2026-08-28. Under
   **D14** a Part that delivers only union machinery delivers nothing testable:
   the shape is per type, so the first type is the only proof the shape works.
   `/info` is that type, and it is the *whole* of this Part's rendered scope —
   see the note on REQ-976 for what that costs, and **D17** for how the
   registry's second branch is proven without a second screen. **Its content
   comes from the `%dev` seed and from nowhere else (D16).**

**Non-goals**

- **Manager-authored module content — explicitly out of scope (D16, owner,
  2026-08-28).** Module content in Part C comes from the `%dev` seed and from
  nowhere else. There is **no manager write path for a module's values** — not in
  the contract, not in the backend, not in a TODO task;
  `docs/backend/specification.md` §3 decision 5 stands **unamended**, and REQ-965
  is **descoped** rather than deferred with a task against it.
  **The hole this leaves is real and is stated rather than hidden:** a WiFi
  password is per-property and somebody has to type it, so a Part whose content
  can only come from release seed data **cannot serve a real customer**. What
  Part C is worth on its own is the transport, the per-type wire shape, the
  registry and one rendered module, proven against seeded content — a real
  foundation for the twenty types that follow, and **not a shippable manager
  feature**.
  **Successor feature — named here, neither created nor planned:**
  **`module-content-authoring`** (manager-authored per-module values — the owner's
  own *"in Zukunft soll er platzhalter werte pro modul pflegen können"*, D8).
  **Its precondition is not code:** an owner-approved **amendment to
  `docs/backend/specification.md` §3 decision 5**, proposed and approved *before*
  any contract or schema work. That amendment is the decision that makes this
  platform **partly a CMS**, which is why it is the owner's. That feature also
  owns **D18's server-side refusal**. Tracked in
  [`docs/features/index.md`](../index.md) → "Tracked, not blocking"; **no
  directory exists for it and none should be created here.**
- **Designing the module screens *other than* `info`.** Per-module design work,
  planned separately. *(Reworded 2026-08-28. It was *"the individual module
  screens … a prerequisite, not an exclusion of convenience"* — and it stays a
  prerequisite, but **D14 makes it a recurring one**: the blocker that stopped
  this Part is now paid once per module type, roughly twenty times. `info` is
  paid. Nothing else is.)*
- **A manager frontend.** `backend/manager-ui/` does not exist. Manager-side
  work here is contract + backend only.
- **Lazy/per-route fetching.**
- **A second cache tier.**

---

## Assumptions

- **A1.** Module content is **text and asset references**. Binary content stays
  behind `GET /api/guest/assets/{id}`; nothing is inlined, base64 or otherwise.
  Under this assumption a full 20-module payload is ~40–60 kB JSON (~10 kB
  gzip), and the 30 s poll costs a `304` (~200 B).
- **A2.** Modules are **property-scoped content**, not stay-scoped. WLAN, house
  rules and surroundings do not identify a guest and need not be erased at
  check-out. **A2 does not extend to secrecy** — see A5.
- **A3.** Every module type has a **fixed, known-at-build-time shape**. The
  catalogue grows by adding types, not by allowing free-form JSON: a free-form
  payload cannot be boundary-validated, and boundary validation is the
  frontend's contract with an untrusted wire. *(**Confirmed by the owner
  2026-08-28 — D14**, and no longer an assumption in the ordinary sense. He
  chose bespoke-per-type in his own words and named the cost back: the contract
  changes for every new module type. A3 is now the shape of the product, not a
  working hypothesis about it.)*
- **A4.** The kiosk continues to hold and render whatever it last had when the
  backend is unreachable. *(Until 2026-08-27 this read "subject to A5, which
  overrides it where the two conflict". Constitution v1.9.0 removed the
  conflict: A5 no longer overrides A4 — see D12, settled.)*
- **A5.** Some module content **is** guest-sensitive: a `wlan` module holds the
  WiFi password, a `smart-home` or `anreise` module may hold a key-box or door
  code. **Constitution v1.9.0 (2026-08-27) settles what that means for the
  cache:** such a value **may** be cached and rendered offline, because the
  manager deliberately put it on a guest-facing screen — a guest who can read it
  can photograph it, so purging it afterwards removes nothing an attacker
  already holds, while withholding it denies the WiFi password to exactly the
  guest who needs it when the backend is unreachable. A5 therefore **no longer
  overrides A4**; it constrains a different thing: no module payload may carry a
  token, key material or any credential reaching the backend or another room.
  See **D12 (settled)** and REQ-979.

---

## User stories

- As a **property manager**, I want to choose which of the available modules my
  property shows and in which order, so that a small apartment is not forced to
  display the same sections as a hotel. *(Already satisfied by the shipped
  model — see D8.)*
- As a **guest**, I want each section to actually show something.
- As the **operator**, I want one badly-authored module never to take the kiosk
  down, so a content mistake is a missing tile and not a site visit.

---

## Requirements

> Preserved verbatim from the parent PRD, **with three exceptions.**
> **REQ-979 was rewritten on 2026-08-27** because the constitution clause it
> quoted no longer exists (v1.9.0; D12). **REQ-961 was amended on 2026-08-28**
> to pin the spelling D9 settled on, because its earlier text could be read as
> requiring the one spelling that provably breaks. **REQ-980 is new, 2026-08-28**
> — a guard that only became necessary because of that decision. Several other
> requirements are **constrained or contradicted** by the settled decisions
> below, or carry a consequence of them; each such requirement carries an inline
> note. Nothing is deleted — an ID never changes meaning — but a note here is
> binding on whoever plans this Part.

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-961 | `api/openapi.yaml` defines a `GuestModule` schema as a **discriminated union** (`oneOf` + `discriminator: type`), one member per module type, each with a fixed shape. **Each member declares its `type` as a plain `string` (`type: { type: string }`) — never a single-value `enum`.** The allowed values are pinned by `discriminator.mapping` in the contract and by `@JsonTypeName` on the generated Java class, not by the member's own schema. **The spelling is load-bearing and the intuitive one is the broken one:** with `type: { type: string, enum: [text] }` the build either compiles and puts `"type":"GuestModule"` on the wire instead of `"text"` — silently, with nothing failing on the Java side — or, once the generator flag below is on, fails to compile outright (`TypeEnum getType()` cannot implement the generated interface's `String getType()`). Writing the `enum` "for safety" is the trap. This shape additionally requires **one generator option**, `<useOneOfInterfaces>true</useOneOfInterfaces>`, on the `jaxrs-spec` execution in `backend/app/pom.xml` — **not a dependency** — which is owner-gated and lands in the same commit as this Part's first union, not before (see "Pending, owner-gated"). *(**D9 settled 2026-08-28**, Variant B, on the evidence in [`d9-spike.md`](d9-spike.md). The "blocked by D9" note this requirement carried since 2026-08-27 is spent. The contract still uses no `oneOf`, `anyOf`, `allOf` or `discriminator` anywhere today — this Part introduces the first.)* **Consequence of D15, 2026-08-28: the union is reached through `GuestMenuItem.module`** — an **optional** property on the existing `GuestMenuItem` schema, whose `required: [key, label, icon, route, sortIndex]` is unchanged — **not** through a top-level `modules[]` array. There is no second array and no join. **The first and only member this Part defines is `info`**, whose content need is specified in prose by [`screens-inhouse/info.md`](../../design/screens-inhouse/info.md) → Decision 3: per row a **label** and a **value**, both `LocalizedText`, both server-resolved per offered language; and explicitly **not** an icon, **not** an ordering field, **not** a row id, **not** a `sensitive` flag (D12 closed it), and **not** a title on the module — the screen's `<h1>` is `GuestMenuItem.label`, and a module never restates the name of the section it fills. | Must |
| REQ-962 | The guest payload carries the **resolved, enabled, ordered** module list; the kiosk performs no enablement resolution and never re-derives the delivered order. *(Presentation ordering inside the popover — Part A's REQ-924 — is a view concern over the same delivered set. Note that `guest-content.mapper.ts` re-sorts `menu.items` by `sortIndex` today even though the contract says the server ordered them; that redundancy is resolved here, deliberately not in Part A.)* **Consequence of D15, 2026-08-28: there is no separate module list to deliver.** `menu.items[]` **is** the list, and each item optionally carries its own module. That is what makes this requirement structural rather than careful: enablement resolves server-side through the ceiling rule, so a disabled section has no `menu.items` entry — and therefore **its module is not in the payload at all**. The rejected `modules[]`-joined-on-catalogue-key shape permitted an entry whose menu item had been filtered out, i.e. a disabled section's content sitting on the kiosk, invisible but present. See **D15**. | Must |
| REQ-963 | Every module member declares explicit bounds: array `maxItems`, text `maxLength`. No unbounded array or string reaches the wire. *(Note 2026-08-28: REQ-961's Variant B is what makes this land where it is written — each member generates its own Java class holding **only its own** fields, so a member's bounds and `@NotNull`s belong to that member. The rejected `enum` spelling flattened every member's fields into one class, which would have put every module type's constraints on every module.)* **Consequence of D15, 2026-08-28: the module *count* needs no bound of its own.** There is no `modules[]` array to bound — modules ride inside `menu.items[]`, which already declares `maxItems: 50`, so at most 50 modules can reach the wire by construction. What this requirement still owns is the **inside** of each member: for `info`, the row array's `maxItems` and the label/value `maxLength`s. `info.md` is explicit that a value is property-authored and its length is not ours to assume — whatever bound is chosen, the renderer wraps (`overflow-wrap: anywhere`) rather than trusting it. **Answered 2026-08-28 by `02-design.md` §1.2–§1.3:** `rows` `maxItems: 10`, module text bounded at `maxLength: 240` through a new `ModuleText` schema (`LocalizedText` itself stays unbounded — bounding it would put `@Size` on every existing text field in the contract), and **the discriminator property is explicitly carved out** rather than given a `maxLength` it would not be helped by. Neither number is a no-scroll guarantee; REQ-978's measurement is. | Must |
| REQ-964 | Localised module text follows the existing `LocalizedText` convention and is **resolved per offered language server-side**, exactly as `GuestHomeScreen` fields already are — never a raw template on the wire. *(Note 2026-08-28, D18: server-side resolution is what this requirement already required. **The refusal to *store* an unresolvable placeholder is its write-side counterpart, and it is not Part C's** — Part C has no module-content write path (D16), so no requirement here may assert it. The rule, its already-shipping implementation and the feature that inherits it are recorded under **D18**.)* | Must |
| REQ-965 | Manager-side CRUD for module selection, ordering and content exists in the contract under the existing `manager*` tags and auth scheme. **⛔ DESCOPED FROM PART C — D16 (owner, 2026-08-28).** The ID is **not deleted and its meaning does not change**; it is **out of scope for this Part**, takes **no TODO task**, and **`04_analyze` must record it as a declared exclusion rather than a coverage gap** — the one carve-out in this Part's coverage check, and the only one. Selection and ordering **already ship** (`PUT /api/manager/menu-config`); module **content** does not, and D8 left `docs/backend/specification.md` §3 decision 5 standing, so it has no sanctioned path. Whenever it is taken up — as the successor feature **`module-content-authoring`**, named under Non-goals — it needs an **amendment to decision 5 first, not an implementation**. **That feature also carries D18's server-side half:** refusing to store content whose placeholders cannot be resolved is a property of the write path, and Part C has no write path. | **Out of scope (was Must)** |
| REQ-966 | The generated TypeScript client regenerates **byte-identically** from the amended contract. **Regeneration alone does not achieve this when a schema is removed or renamed:** the generator does **not** delete the file it previously wrote under `packages/api-client/model/`, so the orphan `.ts` survives and the committed tree stops being what a clean checkout would produce. **Any commit in this Part that removes or renames a schema must delete the orphaned generated files in the same commit.** *(Added 2026-08-28 from the D9 spike, which left three orphan `spike*.ts` files behind after a full revert and had to remove them by hand — `git status` was clean only afterwards. Without this the criterion is asserted against a tree regeneration would not reproduce, and quietly stops meaning anything.)* | Must |
| REQ-967 | Cross-module values rendered on the overview are resolved **server-side from the owning module's data**; the value is never stored twice. *(Smaller than the parent draft claimed — it does not require an aggregated payload.)* *(Note 2026-08-28: `info.md` found a live instance of the duplication this requirement is about. The `%dev` seed hardcodes the literal **"Bis 11:00 Uhr am Abreisetag"** into a Home action card, while `{stay.checkOut\|time}` has shipped in `TemplateControl.ALL_PLACEHOLDERS` all along — two files away. `/info` row 1 uses the placeholder. Whether the Home card is changed to use it too is this requirement's call, and it is the smallest available demonstration of "the value is never stored twice".)* *(Note 2026-08-28, D16/D18: under D16 the `%dev` seed is the **only** author of module content in this Part, so this fix is a **seed edit** — `backend/app/src/main/java/de/gxp/infrastructure/devseed/DevDataSeeder.java:97`, which passes the literal as a Home action-card description — and not a manager action. Changing it to `{stay.checkOut\|time}` is safe under D18's own rule, because that placeholder **is** in `TemplateControl.ALL_PLACEHOLDERS` and would pass the write-time validation that already ships for property text and menu-card templates.)* | Must |
| REQ-968 | Persistence for module content follows the existing BCE package rules and the migration conventions of `backend-domain-config`; no package exists without a Control. | Must |
| REQ-969 | **Every manager mutation introduced by this Part writes an append-only audit row** — who, when, from which address, which entity, which field — and **never a value** of a guest-sensitive field (`docs/constitution.md` v1.8.1, principle 3). `AuditCoverageTest` derives `@Transactional public` Control methods from source and fails the build on an unclassified one. | Must |
| REQ-970 | A **module registry** under `core/guest/` (or `core/modules/`) is the single registration point per module type, carrying at minimum: wire type discriminant, validator/mapper, and the component that renders it. *(**D10 approved** by the owner 2026-08-27 — see Settled decisions. The pattern is authorised; the requirement still waits on the rest of this Part, and the registry has no consumer until a module screen exists.)* **Consequence of D9/Variant B, 2026-08-28:** the registry's per-type branch is where the lost TypeScript narrowing gets paid for. With the member `type` typed as `string`, `switch (m.type)` no longer narrows the generated union. **Narrowing stays cast-free** either way — one 1-line predicate per module type keyed on the discriminant (`const isText = (m: GuestModule): m is GuestTextModule => m.type === 'text'`) or structural (`'body' in m`); both typecheck under the project's strict flags. The loss lands in exactly **one file**, `guest-content.mapper.ts`, because REQ-977 and `frontend/CLAUDE.md` let nothing else import the generated models — and that file was going to branch per type anyway. **Exhaustiveness is a separate loss that this requirement does not cover — see REQ-980.** **Consequence of D15, 2026-08-28: the route→module half needs no new machinery, and the registry stays exactly what this requirement always said it was — *type → renderer*.** The section host calls `layout/sidebar/menu-split.ts`'s `itemForRoute(items, url)` — already the documented convention in `frontend/CLAUDE.md` (rewritten 2026-08-28) and already shared by `SidebarComponent` and `SectionPlaceholderComponent`, so a route normalises identically everywhere — and reads `.module` off the result. No lookup service, no join, no second normalisation. **Consequence of `02-design.md` §4.4, 2026-08-28 — read this before assuming a single file:** the registry lives at `core/guest/module-registry.ts` (the only location permitted to import `@gxp/api-client`, so `core/modules/` would have needed a `frontend/CLAUDE.md` amendment), and it carries the discriminant, the validator/mapper and the language resolution — but **the component is registered in the section host instead**, because a registry importing a feature component would be the app's first `core → features` import. The split does not re-open the drift this requirement exists to close: the host's map is `satisfies Record<ModuleKind, Type<unknown>>`, total over the same key set, so a type with no renderer and a renderer with no type both fail the build. The registry remains the single **source** of the type set. | Must |
| REQ-971 | Wire validation of module types is **derived from the registry**, not from a hand-maintained parallel list — the `registered-icons.ts` precedent, whose comment states the reason: *"Deriving the validation set from the registration map is what keeps the two from drifting."* *(Note 2026-08-28: this closes registry↔validator drift, not registry↔**contract** drift. Deriving the validation set from the registration map cannot notice a contract that gained a union member the registry never learned about — and under D9's Variant B nothing at compile time notices it either. That gap is REQ-980.)* | Must |
| REQ-972 | An unknown or structurally invalid **single** module is dropped from the mapped result; the rest of the payload renders. Only an invalid envelope throws `GuestContentMappingError`. *(Note 2026-08-28, D15/REQ-974: **"dropped" now has a defined landing place.** Dropping a module does **not** drop its menu item — the section stays in the sidebar, its route still resolves, and the host renders `SectionPlaceholderComponent`'s `section.comingSoon`, exactly as a section with no authored content does. Guest-visible result: one consistent "nothing here yet", never a blank screen and never a missing nav entry. **The drop must not quote the offending content** — not the value, not the label (principle 3; `info.md` names the WiFi row as precisely where somebody will be tempted, and notes that a console warning is not exempt).)* *(Note 2026-08-28, D18: **a value that still contains `{…}` is not a drop trigger.** Option (a) of the old "Open items", 3 — the mapper drops a value carrying unresolved braces — was **put to the owner and rejected**: it silently hides an authoring error and false-positives on legitimately braced authored text. This requirement's drop path is for **structural** invalidity — a missing required field, a wrong type, an unknown discriminant — and **the mapper contains no placeholder-shaped logic at all**. An unresolved placeholder renders as authored, visibly. See D18.)* | Must |
| REQ-973 | The frontend renders a module type it does not know **without crashing** — forward compatibility, so a backend that gains a module before the kiosk is updated degrades to "not shown". *(Note 2026-08-28, D9/Variant B: this graceful path is also what **hides a missing registration**. A member the build was meant to support but that nobody registered is, at runtime, indistinguishable from a genuinely newer module type — both degrade to "not shown". Under the rejected Variant A the compiler separated the two for free; under Variant B only REQ-980's guard does.)* *(Note 2026-08-28, D15/REQ-974: **"not shown" on a dedicated section route is not a blank screen.** It is `SectionPlaceholderComponent` rendering `section.comingSoon` inside the section host, which is now the defined empty state — so this requirement's degradation path finally has somewhere to land. Its second arm, `section.unavailable` for a route the delivered menu does not claim, is untouched.)* | Should |
| REQ-974 | The four existing placeholder sections become catalogue modules or are explicitly retired; `SectionPlaceholderComponent` survives only if a delivered-but-unauthored module is a real state. *(Note 2026-08-27: Part A adds eight further placeholder rows that are **disposable fixtures** — removing them is one migration plus a `registered-icons.ts` edit. This Part must not adopt them as real modules by reflex.)* **✅ ANSWERED 2026-08-28 — the conditional resolves to *yes*, and the text is discharged rather than amended.** A delivered-but-unauthored module is a real state, twice over: **D15 makes `module` an optional field, and absent means "no content is authored for this section yet"**; and the catalogue has **thirteen rows, twelve of them section routes, against one designed screen** — the other eleven need the placeholder and will for a long time. **So: `SectionPlaceholderComponent` survives, demoted.** It stops being *the* component behind `{ path: ':sectionKey' }` and becomes **the empty state inside the section host**. No route change, no new chrome key, no component deleted — and specifically **no `{ path: 'info' }` route**, which would re-enumerate the catalogue the route table deliberately stopped enumerating (the `einstellungen` precedent is app chrome, not a catalogue row). **The first half of this requirement is now bounded by D14:** with one designed module type, only `info` can become a module in this Part; the other three original placeholder sections stay placeholders and are **not** retired — retiring them would delete a route a guest can still reach. | Must |
| REQ-975 | Existing properties migrate without manual intervention, consistently with Part A's D5 (a newly introduced catalogue row arrives **property-disabled**; the manager opts it in). | Must |
| REQ-976 | The `%dev` seeder produces a property with **one authored module type — `info` — carried by at least one enabled catalogue section**, so the route → menu item → module → registry → renderer path is exercised in development rather than only in tests. **The registry's multi-type behaviour is proven separately, by a *fabricated* module type that exists only inside a test** — a discriminant and a registry entry defined in the test file, never in `api/openapi.yaml`, never in the seed, never as a designed screen. **⚠ AMENDED 2026-08-28 by D17 (owner).** It previously asked for *"at least two authored module **types**"*, which D14 made unsatisfiable: under bespoke-per-type a second type costs a second **designed screen**, which is the exact blocker that had just cleared for the first. **Why the substitution is legitimate, and not a requirement quietly weakened:** what this requirement exists to prove is that the registry **dispatches on the discriminant** — that a second entry is reachable and that the first does not swallow it. A fabricated type proves exactly that, and proves *more* than a seed does, because the same test can also drive the **miss** path (REQ-973, a type nothing registered) and the **drop** path (REQ-972, a structurally invalid one), neither of which a well-formed second seed ever reaches. Designing a module screen to satisfy a seed requirement would be **work for the requirement rather than for the product** — and D14 is precisely what makes that expensive, because the screen is not the CSS, it is *deciding what the type shows*, and that decision is owed to nobody yet. **What the substitution loses, stated:** the `%dev` property will never show two different module layouts side by side, so a developer clicking through development sees one. That is a demo affordance, not a correctness property, and it is the whole cost. *(The pre-D17 note claimed constitution **v1.10.0** *"leans towards"* seeding a deliberately unknown type in `%dev`. **That reading is withdrawn:** v1.10.0's clause is about a **threshold** that gates an otherwise unreachable state, and this Part introduces no threshold — Technical constraints says so in as many words. Do not cite it here.)* **Note that the declined generic-rows option would have made this requirement free** — see D14. | Should |
| REQ-977 | Components read `PropertyContentService`, never `@gxp/api-client` models; no user-visible string is written in a component or template. *(**Consequence of D9/Variant B, 2026-08-28.** This boundary is what confines REQ-970's lost narrowing to a single file, and it now carries a second weight. A `m is GuestTextModule` predicate is an **assertion TypeScript trusts**, not a proof: it holds only because the backend put the right fields next to the right `type`. The mapper hand-validates every field regardless and drops/throws per REQ-972, so runtime behaviour is unchanged — but **the type system is no longer the thing proving the payload's shape; the mapper is.** Do not thin out the mapper's field validation on the grounds that "the types already say so". Under Variant B they do not.)* *(Note 2026-08-28, D15: the per-type branch lands in the **menu** mapping of that same one file, because the module now arrives inside `menu.items[]`. `MenuItemShape<T>` in `core/content/property.model.ts` gains one optional field and the mapper gains one branch; nothing else in the app sees a generated model, and the section host reads the resolved shape like every other component.)* | Must |
| REQ-978 | Every module screen satisfies the no-scroll constraint at 1024×768 and 1280×832, in both themes and every offered language, measured with `node tools/measure.mjs`. *(Note 2026-08-28: `info.md` → "No-scroll and the banner" already carries the figures for `/info` — panel bottom y254.8 / y318.0, **≈ 490 px of headroom at both sizes** — and states plainly that **they are computed, not measured**, and must be verified on the built screen. One figure needs the measurement rather than the arithmetic: the escalated staleness banner's bottom edge is y118 / y146 against a panel top of y117.8 / y147.2, so `/info` **clears by 1.2 px at 1280 and overlaps by 0.2 px at 1024**. The design argues that is acceptable — the banner is an overlay by construction, the 0.2 px is against its padding not its copy, and the rows are left-aligned so the bar lands on the panel's empty right-hand side. It is still a fifth of a pixel on the wrong side of zero and this requirement is where it gets checked.)* **Instrument correction, 2026-08-28 (found at `04_analyze`, F-2):** the route half is `node tools/measure.mjs`, whose grid **already contains `/info`** — no instrument change is needed for it. But the banner figure this requirement's own note demands **cannot be reached by that file**: the escalated staleness banner is a kiosk *state*, not a route, so it is measured with **`tools/measure-states.mjs`**, which already drives the banner and gains a `/info` variant of Part B's BANNER cell. Both instruments, one assertion — `scrollWidth <= clientWidth` and `scrollHeight <= clientHeight` — plus the banner/panel boxes reported. *(Part B's REQ-952 carried the identical defect and was corrected the same way at its own gate.)* | Must |
| REQ-979 | **No module payload — live or cached — carries anything that extends reach beyond the device**: no device or session token, no key material, no credential that could address the backend, and nothing scoped to another room or another guest. Guest-facing module content, **the WiFi password and door/key-box codes included, may be cached and rendered offline** — that is the point of caching it. *(Rewritten 2026-08-27 against `docs/constitution.md` **v1.9.0**; **D12 is settled**. The old text — "guest-sensitive module content never enters the last-known-good cache" — restated the absolute prohibition the amendment removed. Part B's REQ-951 pins the same line for the merged guest payload and is the precedent to follow, including its warning that the test must not filter on names like `password` or `code`.)* **Consequence of D15, 2026-08-28 — a real hole, recorded rather than closed.** Module content now enters the cache **nested under a top-level key that is already classified**. Part B's compile-time half of the allow-list (`frontend/src/app/core/guest/guest-data.service.spec.ts`: `type PayloadKey = keyof Required<GuestContent>` plus `const _allowListIsExhaustive: Record<PayloadKey, true>`) enumerates **top-level `GuestContent` keys only**, and `menu: true` is already in it. So adding `GuestMenuItem.module` **will not break that build** — the guard written precisely to make an unclassified new wire field fail the compile stays silent for this one. **This is honestly a point against D15 and in favour of the rejected top-level `modules[]` array, which would have tripped it.** It is recorded, not traded: D15's leak-closure property is structural and permanent, this is a test-coverage gap with a known fix. Two things follow — REQ-979's runtime test must reach into `menu.items[].module` explicitly rather than trusting the top-level sweep, and **whether the compile-time guard should recurse into nested schemas is an open question** (see "Open items"). *(This does not change what may be cached: `info`'s WiFi row is guest-facing content the manager deliberately displays, so v1.9.0 permits it, REQ-979 permits it, and `info.md` requires the screen to render **identically from cache** — a guest whose backend is unreachable is exactly the guest who still needs the WiFi password.)* | Must |
| REQ-980 | **The module registry carries a compile-time exhaustiveness guard** — a `never` check that fails the build when the generated `GuestModule` union contains a member the registry does not handle. *(**New 2026-08-28, and it exists only because of D9.** The rejected Variant A got this for free: TypeScript checks a `switch (m.type)` over a properly discriminated union for exhaustiveness, so adding a member turned every such switch into a compile error. Variant B's predicate chain does not — an unhandled member simply falls through to REQ-973's "unknown → not shown" path, silently and in production. **No existing requirement covered this:** REQ-971 derives validation from the registry and so catches registry↔validator drift only; REQ-972/973 act at runtime and by design cannot distinguish "newer than this build" from "somebody forgot to register it". The guard is therefore hand-written or it does not exist. **How it is written is design work and is deliberately not decided here.**)* | Must |

### Open items — **all closed as of 2026-08-28**

**Items 1, 2 and 5 — the three that belonged to `02_design` — are answered by
[`02-design.md`](02-design.md) §1.3, §8 and §7 respectively**, and the
Acceptance criteria above assert each answer. Their entries below are kept as
written, with the answer added, because "this was open and here is how it was
closed" is the part that stops it being rediscovered as new. **Nothing in this
section is outstanding.** *(Six were listed on 2026-08-28. **Item 3 is answered by D18**, **item
4 is reclassified by D19**, and **item 6 is reassigned out of this Part**
entirely. Their entries are kept below with the resolution written in, because
each is exactly the kind of thing that gets rediscovered and re-argued as new.
Numbering is unchanged so that references from `handoff.md` and
`docs/features/index.md` still land.)*

*(The two scope problems that used to be excluded from this list — REQ-965
unauthorised, REQ-976 unsatisfiable — are closed by **D16** and **D17**; see
"What stood in the way".)*

#### From the D9 fold (2026-08-28)

1. **REQ-963 vs. the discriminator property itself.** REQ-963 says *"No
   unbounded array or string reaches the wire"*. Variant B's member `type` **is**
   an unbounded `string` in the contract — the rejected `enum` spelling bounded
   it implicitly, which is one thing that spelling genuinely bought. In practice
   the value is pinned three ways (`discriminator.mapping`, `@JsonTypeName`, and
   REQ-971's registry-derived wire validation), so nothing arbitrary is *served*;
   what is missing is the schema-level bound REQ-963 asks for. The choice is
   between an explicit carve-out in REQ-963 for the discriminator property, and
   adding `maxLength` to it — and **the second option is untested**: the D9 spike
   never ran a bounded discriminator, and a `@Size`-annotated `getType()` is
   exactly the shape that made the `enum` fail to implement the generated
   interface's `String getType()`. Do not add `maxLength` there on instinct;
   it is the same class of trap.
   **✅ ANSWERED 2026-08-28 by [`02-design.md`](02-design.md) §1.3: the explicit
   carve-out.** The decisive reason is not the risk but the semantics — a
   length bound would still admit `"nfo"` and `"Info"`, so it bounds the wrong
   dimension. What pins the value is a **set**, pinned three times already
   (`discriminator.mapping`, `@JsonTypeName`, REQ-971's registry-derived
   validation). The carve-out is written into the contract next to the property
   rather than only here.
2. **The registry's discriminant literals are no longer compile-checked against
   the contract, and REQ-980 does not cover that.** REQ-980 catches a union
   *member* the registry does not handle. It cannot catch a member handled under
   a **wrong or stale discriminant string**: with `type` typed as `string`,
   `m.type === 'txet'` typechecks, and so does a registry keyed on a literal the
   contract renamed. Under the rejected Variant A this was free — the generated
   `TypeEnum` made `'text' | 'list'` the only assignable values. This is a third
   drift axis, distinct from REQ-971's (registry↔validator) and REQ-980's
   (contract↔registry membership), and **nothing currently requires anything of
   it.** Whether it needs its own guard, or is accepted as covered by the
   mapper's runtime validation plus REQ-976's `%dev` exercise, is an open
   question for planning. *(Note 2026-08-28, D17: REQ-976 now stands again, but
   **it no longer helps here.** It seeds **one** authored type, so the `%dev`
   exercise cannot surface a wrong or stale discriminant literal on a *second*
   type — by construction there is no second type in `%dev`. If this drift axis
   is to be covered at all, the **fabricated-type test D17 introduces** is the
   only place in this Part where it could be, and `02_design` should say so
   explicitly rather than assume the seed covers it.)*
   **✅ ANSWERED 2026-08-28 by [`02-design.md`](02-design.md) §8 — and the
   answer is *not* the fabricated-type test.** That test cannot cover this axis
   at all: its discriminant is defined by the test file, so test and registry
   agree by construction and there is no contract for them to disagree with.
   The cover is a **pinned contract test on the Java side**
   (`de.gxp.guest.GuestModuleContractTest`, the `ScheduledJobInventoryTest`
   precedent), which reads `@JsonSubTypes` off the generated union and compares
   it with a hand-pinned map, so a contract rename becomes a red build with
   both literals on screen. **It lives outside `de.gxp.architecture`** — a
   sixth architecture guard would need a constitution amendment. The residual
   is stated in §8: a loud failure with a pointer, not a proof that the kiosk
   agrees.

#### From the `info.md` fold (2026-08-28)

3. **An unresolvable placeholder reaching the guest as literal braces —
   ✅ ANSWERED 2026-08-28 by D18 (owner).** Kept, because the answer has **two
   halves that land in two different features**, and because the original framing
   of this item contained a factual error worth correcting.
   **In short:** the server refuses to *store* one — validation at the **write**,
   not at the read — and the frontend does **nothing** if one slips through: the
   raw placeholder text simply renders. **Under D16 the write half is not Part
   C's**, because Part C has no module-content write path; writing it as a
   requirement here would reproduce exactly the REQ-965 shape D16 just removed.
   **What Part C carries is the do-nothing fallback, and it carries it by writing
   no code** — `TemplateControl.resolve` already behaves that way by design.
   Full reasoning, the already-shipping validation it reuses, the rejected option
   (a), the `{…}`-not-`${…}` syntax note and the factual correction are all under
   **D18**. **The correction's conclusion, carried here so it is not chased:
   the outcome this item describes is *unreachable* on `/info` today, not
   merely unlikely.** `stay.check_out` is `NOT NULL`, and a null `checkOut`
   reaches `resolveLocalized` only when `stay == null` — a vacant room, which
   is on `/standby`. The original framing (*"an `OCCUPIED` stay with no
   check-out timestamp"*) describes a state that cannot exist.

4. **A module type that can legitimately deliver zero rows needs an empty
   state — ✅ RECLASSIFIED 2026-08-28 by D19 (owner). It is not an open
   question.** It was recorded as a question owed to *"the first module type that
   can deliver zero rows"*. **It is not a question at all: it is a line on the
   checklist for designing any module type**, alongside layout, fields and copy —
   because what "empty" means, and what belongs in its place, depend on what the
   module shows. **There is no shared generic empty state.** `info` has none, and
   that is a **finding rather than an omission**: its check-out row is a stay fact
   that is always present under `OCCUPIED`, and `/info` never renders otherwise
   (a vacant kiosk is on `/standby`; `screens/*` answer `409 room-vacant`), so
   **`info` cannot be empty**. `info.md` was right to design none and right to
   say none should be invented. **D19 also pins the distinction that must stay
   visible** — a menu item carrying *no module* means "nothing authored yet" and
   renders `SectionPlaceholderComponent`, while a module delivering *zero
   entries* means "authored and empty right now" and renders that type's own
   empty state. The two look alike and mean opposite things.

5. **Part B's compile-time cache classification guard does not see nested
   fields, and D15 puts module content there.** Full detail in the note on
   **REQ-979**. In short: `Record<keyof Required<GuestContent>, true>` in
   `guest-data.service.spec.ts` enumerates **top-level** payload keys, `menu` is
   already classified, and `GuestMenuItem.module` therefore arrives without
   breaking the build the guard exists to break. The rejected top-level
   `modules[]` array **would** have tripped it — recorded honestly as the one
   thing D15 costs. **Open:** whether the guard should recurse into nested
   schemas (real work, and it would benefit every future nested field), or
   whether REQ-979's runtime test reaching explicitly into `menu.items[].module`
   is enough. `02_design`'s to weigh.
   **✅ ANSWERED 2026-08-28 by [`02-design.md`](02-design.md) §7: both, not
   either.** The guard gains two nested tables in the same spec file and the
   same shape — `Record<keyof Required<GuestMenuItem>, true>` and
   `Record<keyof Required<GuestInfoModule>, true>` — **and** the runtime case
   reaches explicitly into `menu.items[].module`. Neither implies the other:
   the compile-time table fails when a wire field is added and not classified,
   the runtime one proves what the written cache record actually holds.
   **Not** general recursion: a future nested type still needs its own table,
   recorded there as a known gap with a five-line fix.

6. **D12's disclosure obligation — ✅ REASSIGNED 2026-08-28, out of this Part.
   No ID was minted for it, deliberately.** Constitution **v1.9.0**'s third cache
   bullet makes physical loss of a kiosk *"an operator responsibility the product
   **discloses**"*: the operator documentation must state that door, key-box and
   alarm codes are to be rotated when a device goes missing. It is a
   **documentation deliverable, not a code change**, and REQ-961…REQ-980 contains
   nothing that would produce it. **Under D16 it is not yet due in practice:** no
   real property carries real module content in this Part — the `%dev` seed is
   the only author — so the trigger D12 named (*"the first feature to ship a
   cacheable guest-facing code"*) has **not** fired. A fictional
   `Meeresrauschen-77` on a development box is not a code anybody rotates.
   **It is now an owner task with an explicit trigger**, recorded in
   [`docs/features/index.md`](../index.md) → "Tracked, not blocking":
   **before the first property authors real module content.** In practice that
   makes it a **precondition of the successor feature
   `module-content-authoring`**, which is the first thing that can put a real
   code on a real kiosk. **It has an owner and a trigger now, which is exactly
   what it lacked the two times it was recorded and lapsed.**

---

## Acceptance criteria (testable)

**Written 2026-08-28, in the same fold as [`02-design.md`](02-design.md)** —
which is why the three that used to wait on design (REQ-963's carve-out,
discriminant-literal drift, REQ-979's nested guard) are here rather than
deferred again. *(The sentence this section replaces had been walked forward
three times: "when the gates close and a module screen exists", then "when a
module screen exists", then "REQ-965 and REQ-976 need the owner and `/info`
waits on a product call". Every one of those conditions was met on 2026-08-28.)*

**REQ-965 gets none, and that is correct** — it is descoped (D16), so a
criterion for it would be a criterion for something this Part does not do.
`04_analyze` records it as a **declared exclusion, not a coverage gap**.

- **REQ-961:** Given `api/openapi.yaml` after the contract task, when
  `GuestModule` is read, then it is a `oneOf` over exactly one member
  (`GuestInfoModule`) with `discriminator: {propertyName: type, mapping:
  {info: …}}`; the member declares `type: {type: string}` **with no `enum`**;
  `GuestMenuItem.module` is optional and `GuestMenuItem.required` is unchanged;
  there is **no** top-level `modules[]`; and the member carries no `title`, no
  per-row icon, no per-row sort key, no row id and no `sensitive` flag.
  On the generated side: `GuestModule` is a Java **interface** declaring
  `String getType()`, `GuestInfoModule implements` it and carries
  `@JsonTypeName("info")`, and the serialised discriminant is `"info"` — the
  three facts the `enum` spelling breaks silently.
- **REQ-962:** Given a payload whose `menu.items` arrive in server order, when
  `mapGuestContent` runs, then the delivered order is preserved verbatim and
  `guest-content.mapper.ts` contains **no sort over `menu.items`**. Given a
  section carrying module content that is disabled at property scope, when the
  guest payload is served, then **neither the item nor its module appears
  anywhere in it**.
- **REQ-963:** Every array in the union declares `maxItems` and every string
  **except the discriminator** resolves to a schema declaring `maxLength`; the
  discriminator property declares **neither `enum` nor `maxLength`**, and the
  carve-out's reason is written next to it in the contract *(`02-design.md`
  §1.3 — the choice "Open items" 1 left open)*. On the kiosk: 11 rows are
  dropped, a 241-character value is dropped, and 10 rows with a 240-character
  value map.
- **REQ-964:** Given `%dev` with `de` and `en` offered, when
  `GET /api/guest/content` is read, then the `info` module's row 1 value
  contains **no `{`** and equals the active stay's check-out formatted in the
  property timezone for each language, resolved by the same
  `resolveLocalized` that already serves `GuestHome`.
- **REQ-966:** Given the committed tree, when `./mvnw verify` runs twice in a
  row, then `packages/api-client/` is byte-identical on the second build and
  `git status --porcelain` is empty. **This Part removes and renames no
  schema**, so the orphan sweep has nothing to delete — the criterion is that
  `packages/api-client/model/` holds no file whose schema is absent from
  `api/openapi.yaml`, checked, not assumed.
- **REQ-967:** Given a fresh `%dev` database, when the Home overview renders,
  then the check-out card's description resolves from `{stay.checkOut|time}`
  and shows the same time `/info` row 1 shows; and `grep -rn "Bis 11:00"
  backend/app/src/main` is empty.
- **REQ-968:** `V12` creates the module tables with no DDL outside them; the
  entities live in `de.gxp.menu.entity` and the repository in
  `de.gxp.menu.control`; the **five** architecture guards stay green with **no
  sixth added and no exemption list edited**.
- **REQ-969:** No manager mutation is introduced by this Part, so no audit call
  is; `AuditCoverageTest` passes with **exactly one** added entry in
  `NOT_A_MANAGER_MUTATION` (the new read) and no other change, and a guest
  content read carrying a module writes no audit row.
- **REQ-970:** `core/guest/module-registry.ts` is the single source of the
  module type set. Adding a kind to the domain union **without** a registry
  entry fails `ng build` (the registry's mapped type); adding one without a
  renderer fails `ng build` (the section host's `satisfies Record<ModuleKind,
  Type<unknown>>`). Both are verified once by hand during implementation, with
  the compiler's error text recorded.
  *(Amended by `02-design.md` §4.4: the **component** is registered in the
  section host rather than in the registry object, because the alternative is
  the app's first `core → features` import. The compile-time totality above is
  what keeps that split from re-opening the drift this requirement exists to
  close.)*
- **REQ-971:** The wire-validation set is `Object.hasOwn(MODULE_REGISTRY, …)` —
  there is **no second list** anywhere; and the wire discriminant literal
  `'info'` appears in exactly two adjacent places in `frontend/src/` (the
  registry key and its one-line predicate) and nowhere else.
- **REQ-972:** Given a payload in which one module is unknown, structurally
  invalid, or over a declared bound, when it is mapped, then **that module
  alone** becomes `null`, every other item and the whole envelope map
  unchanged, `mapGuestContent` does **not** throw, and **nothing is logged** —
  asserted with a `console` spy, because the drop must never quote a label or a
  value (principle 3).
- **REQ-973:** Given a payload carrying a module type this build does not
  register, when its route renders, then `SectionPlaceholderComponent`'s
  `section.comingSoon` is shown, the sidebar entry is intact, and no error
  surfaces.
- **REQ-974:** `SectionPlaceholderComponent` still exists, is **unmodified**
  (its spec passes without edits), and is rendered by the section host as the
  empty state; `app.routes.ts` gains **no** `{ path: 'info' }`; the other
  original placeholder sections still resolve and still render the placeholder.
- **REQ-975:** Given a database at `V11` with no module content, when the app
  starts, then `V12` applies with no manual step, `info` needs no config
  change, the module tables are empty, the payload carries no `module`, and
  `/info` renders exactly what it rendered before the upgrade.
- **REQ-976:** Given `%dev`, `/info` renders two seeded rows through
  route → menu item → module → registry → renderer. Separately, a spec drives
  a **fabricated** second module type — present in no contract, no seed and no
  component — through the same dispatch (**hit**), a third unregistered type
  (**miss**, REQ-973) and a structurally invalid one (**drop**, REQ-972).
- **REQ-977:** `grep -rn "@gxp/api-client" frontend/src/app` lists only
  `core/guest/` and `core/api/`; the info component's template contains no
  user-visible string; and the mapper validates **every** module field by hand
  rather than leaning on the predicate — under Variant B the predicate is an
  assertion, not a proof.
- **REQ-978:** `node tools/measure.mjs` reports `scrollWidth <= clientWidth`
  and `scrollHeight <= clientHeight` for `/info` at **1024×768 and 1280×832**,
  in both themes and both offered languages. Additionally, a `/info` variant of
  the BANNER cell in `tools/measure-states.mjs` **measures** the escalated
  staleness banner against the panel — `info.md` computes a 1.2 px clearance at
  1280 and a 0.2 px overlap at 1024, and says plainly those are arithmetic.
  A partial overlay is the banner's designed behaviour; the criterion is that
  the bar lands on the panel's empty right-hand side and no value is covered.
- **REQ-979:** The cache record's payload is classified **at three levels** —
  Part B's top-level table plus `Record<keyof Required<GuestMenuItem>, true>`
  and `Record<keyof Required<GuestInfoModule>, true>` — so a new wire field
  fails the build until classified; and the runtime case reaches **explicitly**
  into `menu.items[].module`. `/info` renders identically from cache. The test
  asserts the absence of the device token, `bearer` and `authorization`, and
  **must not filter on names like `password` or `code`** *(the choice "Open
  items" 5 left open: both halves, not either — `02-design.md` §7)*.
- **REQ-980:** Removing a registry entry while the contract keeps its member
  fails `ng build` with `TS2344: … does not satisfy the constraint 'never'`,
  verified once by hand. **The third drift axis — a registry keyed on a stale
  or typo'd discriminant *literal* — is covered separately** by a pinned
  contract test on the Java side (`de.gxp.guest.GuestModuleContractTest`, the
  `ScheduledJobInventoryTest` precedent), which turns a contract rename into a
  red build. **The fabricated-type test cannot cover that axis and does not
  claim to** *(the question "Open items" 2 left open — `02-design.md` §8, which
  also states the residual: a red Java build with a pointer, not a proof that
  the kiosk agrees)*.

---

## Technical constraints

- **`docs/constitution.md` v1.10.0 governs**, in particular the **v1.9.0**
  cache constraint (2026-08-27, three bullets under Constraints), which
  v1.10.0 did not touch. *(**v1.10.0 adds nothing this Part must satisfy.** Its
  new clause — a threshold gating an otherwise unreachable state carries a
  shorter `%dev` value, side by side in configuration and never as one constant
  edited to test and edited back — has no subject here: Part C introduces no
  threshold. It does bear on **REQ-978** indirectly, since `/info` must be
  measured with the escalated staleness banner up and that banner's threshold is
  Part B's, now `%dev`-shortened by this clause. That makes the measurement
  easier, not different.)* The cache constraint says: the
  last-known-good cache **may** hold any guest-facing content the current guest
  is entitled to see, bounded by **what the manager deliberately chose to
  display**; it must **never** hold anything that extends reach beyond the
  device — device token, session token, key material, any credential reaching
  the backend, another room's or another guest's data; and physical loss of a
  kiosk is an **operator responsibility the product discloses** (codes are
  rotated when a device goes missing) rather than one it defends against
  speculatively. *(This bullet previously carried a standing prohibition on
  writing or designing against an amended clause. The amendment landed with the
  owner's verbatim approval, so that prohibition is spent — see D12.)*
- **`docs/backend/specification.md` §3 decision 5 and §5 are in force and
  uncontested** (owner, 2026-08-27), and **stay unamended through this Part**
  (**D16**, owner, 2026-08-28). Amending decision 5 is the **first act of the
  successor feature `module-content-authoring`**, not of this one.
- **Write-time placeholder validation already ships and is the model D18 adopts.**
  `TemplateControl.findInvalidPlaceholders` is called by
  `PropertyControl.validateTemplates` (property text, against `ALL_PLACEHOLDERS`;
  the two standby fields against the narrower `STANDBY_PLACEHOLDERS`) and by
  `MenuControl.collectInvalidTemplates` (card templates), both raising
  **`400 unknown-placeholder`** with the offending field and placeholder named.
  **Part C adds no call to it**, because Part C has no module-content write path
  (D16) — this is recorded so the successor feature does not reinvent it.
- **`openapi-generator-maven-plugin` 7.24.0** with `jaxrs-spec` +
  `interfaceOnly` + `openApiNullable=false`, and `typescript-angular`. Neither
  has ever emitted a `oneOf` in this repo; **the D9 spike established exactly
  what happens when one is added** and the answer is now settled — see
  **D9 (settled)** and [`d9-spike.md`](d9-spike.md). Two facts bind any work
  here: the `jaxrs-spec` execution needs `<useOneOfInterfaces>true</useOneOfInterfaces>`
  (owner-gated, added with the first union — see "Pending, owner-gated"), and a
  union member's `type` must be a plain `string`, never a single-value `enum`.
- Backend: Quarkus, BCE sub-packages, Flyway. Frontend: Angular 22, standalone,
  zoneless, signals, plain CSS with the two-layer token system.
- **[`docs/design/screens-inhouse/info.md`](../../design/screens-inhouse/info.md)
  is a binding input, 2026-08-28.** It is the designed screen this Part was
  blocked on, and its *"What generalises"* table is the contract every later
  module screen inherits: the screen frame (`<h1>` = the section's catalogue
  label, `--space-9` top inset, panel width `3 × --size-panel + 2 × --space-4`),
  the row (stacked label over value, `--text-body`/secondary over
  `--text-title`/primary, `overflow-wrap: anywhere`, no icon, not interactive),
  **absent value ⇒ absent row**, **a module never restates the section's name**,
  **delivered array order with no per-row sort key**, **provenance is invisible**
  (a stay-resolved and a property-authored value render identically), and
  **values stay left-aligned** because the escalated staleness banner and the
  content are on screen together by design. It introduces **zero new tokens and
  zero new icons**. `/info` is also the first screen in the app whose content
  area contains **no focusable element at all**.

---

## Settled decisions (2026-08-27 and 2026-08-28)

*(D8, D9, D10, D11, D12, **D14** and the third fold's **D16, D17, D18, D19** are
the owner's. **D15** is the lead's, taken under an explicit delegation and
written out so the owner can reverse it — see its own section.)*

### D8 — May the manager author module content at all? — **SETTLED: no; decision 5 stands**

The owner scoped the manager's authority exactly: *"Der manager darf nur
auswählen welche Items angezeigt werden sollen (und bei den ersten 4 in welcher
Reihenfolge). In Zukunft soll er platzhalter werte pro modul pflegen können.
Aber das ist Zukunft und hat mit diesem feature nichts zu tun."*

Therefore **`docs/backend/specification.md` §3 decision 5 stands, uncontested**:
the menu catalogue is release data, `MenuItemDefinition` is not writable through
any API, and a new menu item is a seed row plus a frontend screen. Everything the
owner listed as in-scope already ships — enable/disable, reorder, label override
via `menu_item_config_translation`, icon override.

Two consequences bind this Part:

1. **REQ-965 is not authorised as written.** Manager-authored module *content*
   requires an amendment to decision 5, proposed to and approved by the owner,
   before any contract or schema work. **Resolved 2026-08-28 by D16: the owner
   chose to descope rather than amend.** Decision 5 stands untouched, REQ-965 is
   out of Part C's scope, and the amendment becomes the first act of the
   successor feature `module-content-authoring`.
2. **The ordering rule is Part A's** and is repeated here so it is not
   re-derived: resolved `sortIndex` orders everything, but only ranks 1–4 are
   visible as an order (the sidebar); everything below rank 4 lands in the
   popover, sorted alphabetically. A manager reordering ranks 5+ changes nothing
   a guest can see.

### D9 — `oneOf` + `discriminator` on the wire? — **SETTLED (owner, 2026-08-28): yes, Variant B**

**Approved:** *"ja mach mal variante B bei D9"*, after reading the spike's
findings and a worked code example. This is informed approval in the way the
2026-08-27 deferral explicitly was not: the question was put again only **after**
[`d9-spike.md`](d9-spike.md) had answered what the two generators actually emit,
and the owner was shown the code each variant produces.

**What was decided.** `oneOf` + `discriminator: type`, with **each member's
`type` declared as a plain `string`**, its allowed values pinned by
`discriminator.mapping` in the contract and by `@JsonTypeName` on the generated
Java class — plus the one `pom.xml` generator option below.

**Why `string` and not `enum` — the part that must survive without re-running
the experiment.** The single-value `enum` (`type: { type: string, enum: [text] }`)
is the intuitive spelling, it is what REQ-961 could be read as requiring before
2026-08-28, and it is the one that breaks. The spike ran it, on the repo's exact
generator configuration. *(The spike's throwaway union was named `SpikeModule`
with members `SpikeTextModule`/`SpikeListModule`; read those as `GuestModule`
and its members below.)*

- **With `useOneOfInterfaces` off it compiles and ships the wrong bytes.**
  `jaxrs-spec` does not degrade to `Object` and does not fail the build. It
  emits a **concrete class flattening every member's fields together**, plus
  member classes that implement nothing: the union is not a supertype of its
  members (`isAssignableFrom = false`), so a Control cannot return a member as
  the union at all, and the only constructible thing carries **every** member's
  `@NotNull` fields at once — A3's fixed-shape-per-type is not represented
  anywhere. Worse, the generated `@JsonTypeName("SpikeModule")` makes Jackson
  write `{"type":"SpikeModule",…}` where the contract promised `{"type":"text",…}`:
  a discriminant that is in no `mapping`, that no kiosk `switch` can match, and
  **nothing on the Java side fails while it happens**. Reading such a payload
  back throws `InvalidTypeIdException`. `./mvnw -o compile` succeeds on all of
  it. That is the dangerous failure mode — not a red build, a green one.
- **With the flag on it fails to compile.** The generated interface declares
  `String getType()`; a member whose `type` is an `enum` generates
  `TypeEnum getType()` — four `javac` errors. **The single-value `enum` on the
  member is precisely and only what breaks it.**

Drop the `enum` and every property this Part needs holds, verified against the
generated output: `public interface GuestModule { String getType(); }` with each
member `implements` it, `javac` exit 0, the correct discriminant on the wire
(`{"type":"text","body":{"de":"hallo"}}`), each member keeping **only its own**
fields, correct deserialisation, and Java 21 pattern-matching `switch` over the
members.

**Why not the alternatives.** Variant C (`allOf` inheritance) needs no `pom.xml`
change and works on the Java side, but emits **no union type in TypeScript** —
the mapper must cast — and leaves the base class concrete and instantiable, so
nothing stops a typeless module reaching the wire. A free-form key/value bag has
no generator risk but cannot be boundary-validated at all (A3), and makes every
renderer a cast.

**The second dependency gate this question reserved never opens.** The
2026-08-27 wording said that if the Java side *"wants a new artifact (e.g.
`jackson-databind-nullable`), that is a second, separate dependency gate"*. The
spike checked for exactly that: `useOneOfInterfaces` is a `<configOptions>`
line and pulls in **nothing**. There is no dependency decision here — only a
`pom.xml` one, which `backend/CLAUDE.md` gates regardless.

**Three consequences are recorded in the requirements they affect, not here:**
lost `switch` narrowing → **REQ-970** (bought back cast-free by a 1-line
predicate per type, and confined to one file by REQ-977); lost compile-time
exhaustiveness → **REQ-980, which is new because nothing else carried it**;
weakened soundness → **REQ-977**. REQ-961 now pins the spelling, REQ-963 records
where the per-member bounds land, and REQ-966 gained the spike's unrelated
orphan-file finding.

**What this does not unblock.** Nothing about the real blocker. A designed
module screen is still required before REQ-961/963/964 can be written; the spike
removed the generator uncertainty and nothing else.

### D10 — The module registry as a cross-cutting frontend convention — **APPROVED (owner, 2026-08-27)**

The gate was raised because the owner had read the registry as a **database
table**. Re-framed correctly — it is a **frontend code file** (the
`core/icons/registered-icons.ts` pattern) that is simultaneously the
wire-validation set, the mapping table and the render dispatch, and it lives
**alongside** the existing `menu_item_definition` seed table rather than
replacing it — he accepted that both sides then get maintained, and approved it:
*"denke okay, weil im Frontend muss ja sowieso entwickelt werden wenn ein neues
Modul hinzu kommt, dann kann man auch immer die Datei mitziehen."*

That is informed approval: the cost he was asked to accept — two places to touch
per new module type — is the exact cost he named back, and it lines up with
`docs/backend/specification.md` §5's existing rule that a new menu item is *"a
seed row plus a frontend screen"*. The `docs/constitution.md` → "Decisions
Requiring Your Approval" gate for *"a new architectural pattern or cross-cutting
convention"* is therefore **closed for this pattern**.

Two things it does **not** license:

- It was **not** approval for D9. The registry carries the type discriminant,
  but how that discriminant is represented on the wire was a separate question —
  **answered separately, on 2026-08-28, by D9 above.** The two remain distinct
  decisions; neither was ever evidence for the other.
- It still has **no consumer before this Part**. Nothing may be built on it
  until a module screen exists to register — approving the pattern did not
  create a reason to write the file.

### D11 — Per-room module *content* override? — **SETTLED: no**

No per-room override beyond the label/icon/sort override that **already ships**.
`menu_item_config` carries `icon_override` and `sort_index`, and
`menu_item_config_translation` carries a per-scope **label**, resolved room →
property → catalogue in `MenuControl.guestResolvedMenu`. Extending that to a
module *body* would double the manager's authoring surface and the persistence
model for a case (per-room WiFi) a property can express today with a room-scoped
label — and it is precisely the kind of scope whose deployment context is not
yet known.

### D12 — Guest-sensitive module content in the kiosk cache — **SETTLED (owner, 2026-08-27): it may be cached**

**Closed by constitution amendment v1.9.0**, whose wording the owner approved
verbatim before it was written. The clause this gate was raised against — *"The
kiosk's last-known-good cache never holds guest-sensitive values — no door
codes, key-box codes, alarm codes or WiFi passwords, ever"* — **no longer
exists**. It is replaced by the three-bullet threat-model boundary quoted under
Technical constraints above.

The owner's reasoning, preserved because it is the load-bearing part and not the
conclusion:

> A value shown on a guest-facing screen can be photographed or written down, so
> purging it from the cache afterwards removes nothing an attacker already
> holds — while withholding the WiFi password or the door code denies them to
> exactly the guest who needs them when the backend is unreachable.

What this settles, and what it does not:

- **Settled:** a `wlan` module's password and an `anreise` module's key-box code
  may be written to the last-known-good cache and rendered offline. **All three
  options this gate listed are withdrawn** — no per-field `sensitive` flag on
  the wire, no per-module-type classification in the registry, no "module
  content is never cached" condition at the write site. None should be
  re-proposed as a safety improvement: each cost real offline availability to
  buy nothing an attacker did not already have.
- **Still binding:** the credential line. No module payload may carry a token,
  key material, or anything addressing the backend, another room or another
  guest — **REQ-979**, rewritten above.
- **Not a licence for logging.** Principle 3 is untouched: these values still
  never reach a log, an analytics event or a captured stack trace. A cache is
  not a log.
- **It carries a disclosure obligation, not a code change.** The constitution
  now states that the product *discloses* physical loss as an operator
  responsibility — operator documentation must say that door, key-box and alarm
  codes are rotated when a kiosk goes missing. That is a documentation
  deliverable of whichever feature first ships a cacheable code, which on
  current plans is this one. Recorded here so it is not lost; it is owned by
  nobody yet. **Update 2026-08-28, first pass: it looked due** — `info.md`
  designs the WiFi row this Part will ship. **Update 2026-08-28, second pass
  (D16): it is not due yet, and it now has an owner and a trigger.** Under D16
  no real property carries real module content in this Part — the `%dev` seed is
  the only author, and a fictional `Meeresrauschen-77` on a development box is
  not a code anybody rotates. It is recorded as an **owner task** in
  `docs/features/index.md` → "Tracked, not blocking", triggered **before the
  first property authors real module content**, which in practice makes it a
  precondition of `module-content-authoring`. See "Open items", 6.

*(There is no D13 in this document. D13 is the escalating staleness banner, a
Part B decision recorded in `docs/handoff.md`; the decision numbering is shared
across the three Parts of the split, so this document's next free ID was D14.)*

### D14 — Is a module a generic row list, or bespoke per type? — **SETTLED (owner, 2026-08-28): bespoke per type**

**Approved in his own terms:** *"ja, jedes Modul hat ein eigenen Typ. Jedes
Modul ist sein eigener Spezialtyp. Das ist das modulare design was ich stets
verfolge. Wenn dann jedes mal die api angepasst werden muss, ist das doof, aber
werden wir so machen."*

**Note what he accepted.** He **named the cost back before choosing** — *"wenn
dann jedes mal die api angepasst werden muss, ist das doof"* — and chose it
anyway, because per-type modularity is the design he is pursuing. That is
informed approval of the D10 kind, and it is recorded that way: the contract
changes for every new module type, and he knows.

**What was decided.** Each module type gets its **own layout and its own union
member**. `info` is simply the type whose layout happens to be a list of
label/value rows. The row is a shared **component**, never the module model.

**Why** (the argument from `info.md` → Decision 1, which stands):

- **The alternative is one step from the thing D9 already rejected.** A single
  generic `rows: [{label, value}]` member for every module type collapses A3's
  *"fixed, known-at-build-time shape per type"* into one shape for everything.
  It is not literally the free-form key/value bag — it is bounded, so it *can*
  be boundary-validated — but it validates the **container** and never the
  content, and REQ-963's per-member bounds would have nothing type-specific
  left to bind to.
- **The rest of the catalogue is visibly not rows.** `umgebung` and
  `gastronomie` are places with distances, `smart-home` is control tiles,
  `hausordnung` is prose, `aktivitaeten` is cards. Forcing those into
  label/value rows pushes the structure into the value string, where nothing
  can validate it and every property invents its own convention.
- **Rows still generalise, as a component.** Any type may render a row list —
  `info` is the first, `wlan` and `anreise` are the obvious next. What does not
  generalise is the wire shape.

**The price, stated honestly and not softened by the approval:**

1. **Every module type needs a designed screen before its schema can be
   written. The blocker that stopped this Part recurs once per type — roughly
   twenty times.** It is not paid once. This is the single most consequential
   sentence in this decision.
2. Each type costs a `oneOf` member, a registry entry (D10's two places), a
   validator/mapper branch and a component.
3. **The mitigation is real but partial.** `info.md` settles the **screen
   frame** and the **row**, so screen #2 is a fraction of screen #1. What is
   never free is **deciding what a type shows** — and that is the part that
   blocks, not the CSS.

**A third option was offered and declined — record it so it is not
re-proposed as a discovery.** The lead put a middle path to the owner: a
**generic `GuestRowsModule` as one member of the union**, sitting beside the
bespoke ones — it would have covered the row-shaped half of the catalogue
immediately (`info`, `wlan`, `anreise`, `hausordnung` in a plain reading) with
bespoke members added later for the shapes that need them, and it would have
made **REQ-976 free**. **He declined it in favour of per-type.** Anyone who
rediscovers this idea should read it as already answered, not as new.

**Consequences folded into this document:** a third Goal (one module type must
actually render, or the Part proves nothing); the Non-goal reworded from a
one-off prerequisite to a recurring one; **REQ-976 contradicted** — a second
module *type* now costs a second designed screen, **resolved 2026-08-28 by
D17**, which keeps one authored type in `%dev` and moves the second into a test;
and REQ-974's first half bounded — only `info` can become a module in this Part.

### D15 — How does a module bind to its section route? — **DECIDED (the lead, 2026-08-28, on the owner's explicit delegation)**

`info.md`'s first open question was a **requirement-level gap, not a design
choice**: REQ-962 delivers the module list, REQ-970's registry maps *type →
renderer*, and **nothing answered "which module fills `/info`?"** The section
host cannot render without that link, so it blocked REQ-961 as hard as the
missing screen did.

**The owner delegated it and asked for the reasoning to be written down so he
can reverse it later.** That is what this section is for — the conclusion alone
would not be reversible.

**Decided: the menu item carries its module, as an optional field.**

```
menu.items[] = { key, label, icon, route, sortIndex, module? }
```

**Three reasons, in the order that decided it:**

1. **It closes a leak by construction.** Enablement resolves server-side
   through the ceiling rule, so a disabled entry is **absent from
   `menu.items`**. With the module *inside* the item, a disabled module's
   content is therefore **not in the payload at all**. The alternative — a
   separate `modules[]` array joined on the catalogue key — permits an entry
   whose menu item was filtered out, i.e. a disabled section's content sitting
   on the kiosk: invisible, but present. **That is a property to make
   structurally impossible rather than one to protect by care.**
2. **It reuses machinery that already ships.** Part A's `itemForRoute(items,
   url)` in `layout/sidebar/menu-split.ts` is already the documented convention
   (`frontend/CLAUDE.md`, rewritten 2026-08-28) and is already used by
   `SidebarComponent` and `SectionPlaceholderComponent`. The module host calls
   the same function and reads `.module`. **No lookup, no join, no new
   pattern** — and therefore no "new architectural pattern" gate.
3. **The join creates two failure modes that this shape does not have** — a
   module whose key matches no menu item, and a menu item with no module — each
   needing a rule, a test, and a decision about what the guest sees.

**Rejected, and worth recording: the module carrying its own route.** That puts
the route in **two places**, and two sources for one fact is exactly what Part
B's consolidation removed. It would also make the route the join key, so a
renamed `route` silently orphans content.

**The consequence that answers `info.md`'s other finding: the field is
optional, and absent means no content is authored for that section yet** —
which is precisely when `SectionPlaceholderComponent` renders as the empty
state. **REQ-974 is therefore answered**, in the form `info.md` proposed: the
placeholder survives, demoted from *"the component for every section route"* to
*"the empty state inside the section host"*.

**What this decision costs, stated rather than buried:** module content now
enters the last-known-good cache **nested under `menu`, a top-level key Part B
already classified**, so Part B's compile-time allow-list guard will not fire
when it arrives. The rejected `modules[]` array would have tripped that guard.
See the note on **REQ-979** and "Open items", 5.

**Reversibility, since the owner asked for it.** Reversing D15 means moving
`module` out of `GuestMenuItem` into a top-level `modules[]` keyed by catalogue
key, adding the join in `guest-content.mapper.ts`, and writing the two rules
reason 3 names. It stays cheap for as long as `info` is the only type; it stops
being cheap once several types ship, because every mapper branch moves with it.

### D16 — May Part C author module content at all? — **SETTLED (owner, 2026-08-28): no. REQ-965 is descoped**

**The question was forced by D8.** D8 settled that
`docs/backend/specification.md` §3 decision 5 stands, which left **REQ-965 — a
Must — with no authorised form**. Two ways out were put to the owner: **(a)**
descope REQ-965 out of this Part, or **(b)** amend decision 5. **He chose (a).**

**What was decided.** Part C delivers **the wire shape, the rendering path and
the registry**. **Module content comes from the `%dev` seed only.** The manager
**cannot author module content in this Part**, and **decision 5 stands
unamended**.

**The reasoning is recorded because the hole is real and somebody will
rediscover it.** A WiFi password is per-property. Somebody has to type it. A
Part C whose content can only come from **release seed data** therefore **cannot
serve a real customer** — not "is unpolished for one", *cannot*. That is not an
oversight being papered over: **the owner anticipated it himself at D8** —
*"In Zukunft soll er platzhalter werte pro modul pflegen können. Aber das ist
Zukunft und hat mit diesem feature nichts zu tun."* — and has now confirmed it
stays future work.

**So be plain about what this Part is worth on its own.** It proves **the
transport, the per-type wire shape, the registry and one rendered module**,
against seeded content. That is genuinely useful: every one of those four is
inherited by the twenty module types that follow, and none of them can be proven
without a first one. **It is not a shippable manager feature**, and nothing
elsewhere in this document should be read as claiming otherwise.

**The successor feature, named and deliberately not planned:
`module-content-authoring`.** Manager-authored per-module values — the owner's
own *"platzhalter werte pro modul"*. **Its precondition is not code.** It must
**first** propose an amendment to `docs/backend/specification.md` §3 decision 5
and have the owner approve it; only then does contract or schema work begin.
**That amendment is the decision that makes this platform partly a CMS**: today
the catalogue is release data and a new section is *"a seed row plus a frontend
screen — not a feature"*; afterwards a manager authors content that guests read
off a wall. That is a change in the shape of the product, not a feature detail,
which is exactly why it is the owner's and why it is not being taken here.
**No directory is created and nothing is planned for it**; it is recorded in
`docs/features/index.md` → "Tracked, not blocking".

**Two things D16 also relocates, and both matter:**

1. **D18's server-side refusal** belongs to that successor feature, because a
   **write-time** rule needs a write path and this Part has none.
2. **D12's disclosure obligation is not due in this Part**, because no real
   property carries real module content in it — see "Open items", 6.

**What D16 does *not* do.** It does not touch REQ-961…REQ-964 or REQ-966…REQ-980:
the union, the bounds, the server-side localisation, the registry, the failure
isolation and the `/info` screen are all unaffected. The seed is simply the only
thing that fills them.

### D17 — Two authored module types in `%dev`? — **SETTLED (owner, 2026-08-28): one, and the second is fabricated in a test**

**The question was forced by D14.** REQ-976 asked `%dev` for *"at least two
authored module **types**"*; bespoke-per-type makes a second type cost a second
**designed screen** — the exact blocker that had just cleared for the first.
**The owner chose not to design a second module.**

**What was decided.** `%dev` seeds **one** authored module type, `info`. **The
registry's multi-type behaviour is proven with a fabricated second type in a
test** — a discriminant and a registry entry that exist only inside the test
file: never in `api/openapi.yaml`, never in the seed, never as a screen.

**Why the substitution is legitimate, and not the requirement quietly
weakened.** What REQ-976 exists to prove is that the registry **dispatches on
the discriminant** — that a second entry is reachable, and that the first does
not swallow it. A fabricated type proves that directly, and proves *more* than a
seeded one does: the same test can drive the **miss** path (REQ-973, a type
nothing registered) and the **drop** path (REQ-972, a structurally invalid one),
neither of which a well-formed second seed ever reaches.

**Designing a module screen to satisfy a seed requirement would be work for the
requirement rather than for the product.** D14 is precisely what makes that
expensive: the cost of a module type is not its CSS, it is **deciding what the
type shows** — and that decision is owed to nobody yet.

**What it loses, stated rather than glossed.** The `%dev` property will never
show two different module layouts side by side, so a developer clicking through
development sees one. That is a **demo affordance, not a correctness property**,
and it is the whole cost.

**One earlier claim is withdrawn.** The pre-D17 note on REQ-976 said constitution
**v1.10.0** *"leans towards"* seeding a deliberately unknown type in `%dev`. It
does not: v1.10.0's clause is about a **threshold** that gates an otherwise
unreachable state, and this Part introduces no threshold — Technical constraints
already says so. Do not cite it here.

**One thing it does not fix.** With a single seeded type, `%dev` cannot surface a
wrong or stale **discriminant literal** — "Open items", 2 can no longer lean on
the seed, and the fabricated-type test is the only place in this Part where that
axis could be covered.

### D18 — What happens when a placeholder cannot be resolved? — **SETTLED (owner, 2026-08-28): refused at the write; nothing clever at the read**

Three options had been recorded and none chosen: **(a)** the mapper drops a value
still containing `{…}`; **(b)** the server refuses to *serve* such a module;
**(c)** it is accepted as a visible authoring error. **The owner's answer is a
sharper form of (b) with (c) as the fallback — and it explicitly rejects (a).**

**The rule — validation at the write.** A manager may **not save** module content
containing a placeholder the template engine cannot resolve. The refusal happens
at the **write**, not at the read: the person who can fix it is the person doing
the typing, and they are told immediately instead of a guest discovering it on a
wall — and unlike option (b)-at-serve-time it never turns one authoring slip into
a missing section for every guest at once.

**This is not new machinery — it already ships** for every field that accepts a
template today:

- `TemplateControl.findInvalidPlaceholders(template, allowed)` returns every
  offending placeholder, braces included;
- `PropertyControl.validateTemplates` calls it for `heroSubtitle`, `headline`
  and `paragraph` against `ALL_PLACEHOLDERS`, and for the two standby fields
  against the narrower `STANDBY_PLACEHOLDERS`;
- `MenuControl.collectInvalidTemplates` calls it for card templates;
- both raise **`400 unknown-placeholder`**, naming the offending field *and* the
  placeholder.

Extending that same call to a module-content write path is the **entire**
server-side change. **Under D16 there is no module-content write path in Part
C**, so **this half belongs to `module-content-authoring`** and **must not be
written as a Part C requirement**: a Must this Part cannot implement is exactly
the REQ-965 shape D16 just removed.

**The fallback — the frontend does nothing, deliberately.** If a placeholder
slips through anyway, **the raw placeholder text simply appears**. No mapper
logic, no row-dropping, no substitution, no marker, no console noise. That is
already `TemplateControl.resolve`'s documented behaviour — *"A placeholder whose
context value is absent stays as-is rather than rendering `null`"* — so **the
fallback costs nothing to implement** and stays a **last-resort visible error**
rather than a second mechanism that can itself be wrong. **This is what Part C
actually carries, and it carries it by writing no code.**

**Option (a) is rejected, and it is the one that will be re-proposed.** Dropping
a value because it contains `{…}` silently hides an authoring error and
false-positives on legitimately braced authored text. Recorded in **REQ-972**:
that requirement's drop path is for **structural** invalidity — a missing
required field, a wrong type, an unknown discriminant — and **the mapper contains
no placeholder-shaped logic at all**.

**Syntax, because the informal spelling will be copied.** The placeholders are
`{stay.checkOut|time}` — **curly braces, no dollar sign**. `${…}` was informal
shorthand in the conversation, not the grammar. The rule is *"show the raw
placeholder text as authored"*.

**The live instance this bears on.** `%dev` hardcodes the literal *"Bis 11:00 Uhr
am Abreisetag"* into a Home action card
(`backend/app/src/main/java/de/gxp/infrastructure/devseed/DevDataSeeder.java:97`)
while `{stay.checkOut|time}` has shipped in `TemplateControl.ALL_PLACEHOLDERS`
all along, two files away. **`info.md`'s row 1 is where that gets fixed** — it
uses the placeholder. Whether the Home card is changed to match is **REQ-967**'s
call, and under D16 it is a **seed edit**, not a manager action. It is safe by
D18's own rule: `{stay.checkOut|time}` is a *known* placeholder and would pass
the write-time validation that already ships.

**A correction that came out of checking this decision against the code.** This
question was framed — here and in `info.md` → Decision 2 — as *"an `OCCUPIED`
stay with no check-out timestamp"*. **That state does not exist.**
`stay.check_out` is `timestamptz NOT NULL` (`V2__domain_config.sql`), and
`GuestControl.resolveLocalized` passes a null `checkOut` into the template
context **only when `stay == null`** — which for `/info` means a vacant room, and
a vacant kiosk is on `/standby` while `screens/*` answer `409 room-vacant`. So
with **today's** placeholder set the raw-braces outcome on `/info` is
**unreachable**, not merely unlikely. That **strengthens** the decision rather
than undermining it: the do-nothing fallback is free, correct, and already in
place for the day somebody adds a placeholder whose context value may legitimately
be absent — and nobody has to revisit the question then.

### D19 — Is there a shared empty state for modules? — **SETTLED (owner, 2026-08-28): no. Every module type designs its own**

Consistent with **D14**: **a module type designs its own empty state**, because
what "empty" means and what belongs in its place depend on what the module
shows. A list of places with nothing nearby, a list of activities out of season
and a grid of smart-home controls with no devices do not want the same sentence,
and probably do not want the same shape. **There is no shared generic empty
state.**

**This reclassifies the old open question rather than answering it.** It was
recorded as a question owed to *"the first module type that can deliver zero
rows"*. It is **not a question at all** — it is **a line on the checklist for
designing any module type**, alongside layout, fields and copy. `info` has none,
and that is a **finding rather than an omission**: its check-out row is a stay
fact that is always present under `OCCUPIED`, so **`info` cannot be empty**.
`info.md` was right to design none and right to say none should be invented.

**Keep the distinction visible. The two cases look alike and mean opposite
things:**

| case | meaning | renders |
|---|---|---|
| menu item carries **no module** | nothing authored for this section yet | `SectionPlaceholderComponent` — "coming soon" |
| module delivers **zero entries** | authored, and empty **right now** | **that module type's own empty state** |

The first is **D15's optional field being absent**, and it is answered for the
whole catalogue at once: it is REQ-974's answer, and it is why
`SectionPlaceholderComponent` survives. The second is **per type**, and it is
answered once per designed screen. Collapsing them would either tell a guest
"coming soon" about a section the property has already filled in, or invent a
bespoke empty state for eleven sections that simply have no content yet.

**Where the checklist line belongs — flagged, not written.** `info.md`'s *"What
generalises"* table is the contract every later module screen inherits, and
**this line is missing from it**. It is recorded here rather than added there,
because editing a design document is design work and this fold does none.
Whoever next designs a module screen adds it: *"an empty state — or a stated
reason this type cannot be empty."*

---

## Open gates — **none**, and the three owner calls are now answered

**No `docs/constitution.md` → "Decisions Requiring Your Approval" gate is
open.** All three that ever were are closed: **D9** settled 2026-08-28, **D10**
approved and **D12** settled 2026-08-27, all under Settled decisions above.
**D14 and D16…D19 opened none** — they are the owner's own answers — and **D15**
introduces **no new architectural pattern** (it reuses `itemForRoute`, which
`frontend/CLAUDE.md` already documents), **no dependency**, **no infrastructure**
and **no nested `CLAUDE.md` entry**. **The third fold introduces nothing gated
either:** descoping a requirement, relaxing a seed requirement, reusing an
already-shipping validation and declining to invent a shared empty state are none
of them a dependency, a datastore, an architectural pattern or a `CLAUDE.md`
entry.

**The three ordinary product calls this section used to list are answered:**
the scope of REQ-965 → **D16, descoped**; the relaxation of REQ-976 → **D17, one
authored type plus a fabricated one in a test**; what a guest sees when a
placeholder cannot be resolved → **D18, refused at the write, raw text at the
read**. The fourth item — D12's disclosure obligation, which needed an assignee
rather than a decision — is **reassigned** to the owner-task register with an
explicit trigger; it is not Part C's. See "Open items", 6.

**The prerequisite that was never a decision is discharged.** A designed module
screen exists.

**One approval remains, and it gates a commit rather than a plan:** the single
`pom.xml` generator-option line — next section.

## Pending, owner-gated — the one `pom.xml` line

One approval is still outstanding, and it is **deliberately not being asked
now**. It does not block planning; it blocks the commit that first ships a
union.

D9's Variant B needs exactly one line in the `jaxrs-spec` execution's
`<configOptions>` in `backend/app/pom.xml`:

```xml
<useOneOfInterfaces>true</useOneOfInterfaces>
```

**Why it is not added today.** `api/openapi.yaml` contains no `oneOf` anywhere,
so the flag is currently a **no-op**: adding it now is configuration for
something that does not exist, landing in an approval-gated file with nothing to
review it against. It belongs in **the same commit as this Part's first real
union**, where a reviewer can see what it does and what it changes.

**What it is, precisely.** A **generator option, not a dependency.** The spike
confirmed it pulls in nothing — no `jackson-databind-nullable`, no other
artifact — and the entire flag-on/flag-off difference in the generated Java tree
was the three throwaway spike files. Blast radius on today's contract is zero,
because there is no other `oneOf` for it to re-shape.

**It still needs the owner's word.** `backend/CLAUDE.md` → "Dependencies,
database, build" gates **any** `pom.xml` change, dependency or not, and
`docs/constitution.md` → Constraints says the same in its own words (*"No
dependency-manifest edits … without explicit approval"*). So: propose this exact
line to the owner as part of the commit that first introduces the `GuestModule`
union, and do not write it before then.

**Sharpened 2026-08-28 by [`02-design.md`](02-design.md) §1.5: it must land in
the same *build*, not merely the same commit — so the approval comes *before*
the contract edit, as [`03-todo.md`](03-todo.md)'s T-01.** With a `oneOf` in
the contract and the flag off, `./mvnw verify` does not fail: it emits a
flattened class with `@JsonTypeName("GuestModule")` and goes **green** while
the wrong discriminant goes on the wire. Anyone who regenerates between the
contract edit and the approval gets a green build they can believe.

---

## Blocking map

| Blocks / blocked by | Detail |
|---|---|
| **Blocked by — decisions** | **Nothing.** All gates are closed: **D9 settled 2026-08-28** (Variant B, after the generator spike), **D10 approved** and **D12 settled** 2026-08-27, **D14 settled** and **D15 decided** 2026-08-28, and **D16, D17, D18, D19 settled** by the owner 2026-08-28. |
| **Blocked by — prerequisites** | **Nothing. ✅ Discharged 2026-08-28.** The one prerequisite — at least one actually designed module screen — is met by [`docs/design/screens-inhouse/info.md`](../../design/screens-inhouse/info.md). *(It is design only. It is deliberately **not** built, because building it against hardcoded content before this Part puts the values on the wire means building it twice.)* |
| **Blocks a clean `04_analyze`** | **Nothing, as of 2026-08-28.** The Acceptance criteria section is written, including the three criteria that waited on `02_design`'s answers to "Open items" 1, 2 and 5. The gate ran and its result is [`04-analyze.md`](04-analyze.md). |
| **`04_analyze` must not flag** | **REQ-965 is a declared exclusion, not a coverage gap** (D16) — it is descoped from Part C and takes no TODO task by decision. It is the **only** such carve-out; every other REQ-961…REQ-980 still needs a task. |
| **No longer blocking anything** | The two scope problems (**REQ-965** unauthorised → D16; **REQ-976** unsatisfiable → D17) and the **unresolvable-placeholder product call** (→ D18) are closed. **D19** reclassified the empty-state question out of this Part's open list. |
| **Pending approval — blocks the commit, not the plan** | The single `pom.xml` line `<useOneOfInterfaces>true</useOneOfInterfaces>`. A generator option, not a dependency; asked when this Part's first union is committed, not before. |
| **Blocked by — features** | Part A (routes: every catalogue route must already resolve). Part B is a convenience, not a hard dependency. |
| **Constrained by** | **D8 + D16** — decision 5 stands **unamended**, and REQ-965 is **descoped** rather than deferred: this Part's module content comes from the `%dev` seed only, and there is no manager write path. **D11** — no per-room content override. **D14** — bespoke per type, so `info` is the only rendered type. **D17** — one authored type in `%dev`; the second is fabricated in a test. |
| **Blocks** | Nothing. It is the end of the chain. |
