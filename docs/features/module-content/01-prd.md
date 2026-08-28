# PRD: Module Content
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md) | **Spike:** [d9-spike.md](d9-spike.md)
**Status:** Draft — **STILL NOT READY TO PLAN**, now for **one** reason instead
of two: **at least one designed module screen still does not exist.** That was
always the real blocker, it is the owner's to produce, and no decision, spike or
document can supply it. **Every approval gate is now closed** — **D9 settled
2026-08-28** (Variant B, on the evidence in [`d9-spike.md`](d9-spike.md)), after
**D10 was approved and D12 settled on 2026-08-27**. One approval is still
outstanding but does **not** block planning: the single `pom.xml`
generator-option line D9 needs, which lands with this Part's first real union
and not before — see "Pending, owner-gated". `02_design` must not start. This
document exists so nothing is lost, not because the feature is ready.

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
> **Governing:** `docs/constitution.md` **v1.9.0**, `docs/backend/specification.md`
> §3 decision 5 / §5, `frontend/CLAUDE.md`, `backend/CLAUDE.md`.

---

## Why this Part is not plannable yet

**One reason now, and it is not a scheduling preference.**

**It is schema design for content nobody has specified.** REQ-961 requires
*"one member per module type, each with a fixed shape"* while the Non-goals
exclude *"designing the individual module screens"*. You cannot fix a per-type
schema before deciding what the type shows. **At least one real, designed
module screen must exist first** — and that is the owner's to produce.

**No approval gate is open any more.** All three are under Settled decisions:
**D9**, `oneOf`/`discriminator`, **settled 2026-08-28** — the owner approved
Variant B after the generator spike answered what the toolchain actually emits;
**D10**, the module registry, **approved 2026-08-27** once it was re-framed
correctly as a frontend code file; **D12**, module content in the
last-known-good cache, **settled by constitution amendment v1.9.0**, whose
wording the owner approved verbatim. The one approval still outstanding — the
single `pom.xml` generator-option line — is deliberately not asked now and does
not block planning; it blocks the commit that first ships a union. See
"Pending, owner-gated" below.

Part A deliberately needs nothing from this document — no `GuestModule` union
and no module registry — which is what makes deferring it free.

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

---

## Goals / Non-goals

**Goals**

1. **A module content model, once a module screen exists to model.** With
   per-module failure isolation: one malformed module is dropped, only a
   structurally invalid envelope is fatal. Twenty modules must not mean twenty
   ways to blank a kiosk.
2. **Bounds that are declared and enforced at the boundary.**

**Non-goals**

- **Designing the individual module screens.** Per-module design work, planned
  separately — and it is a *prerequisite*, not an exclusion of convenience.
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
  frontend's contract with an untrusted wire.
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
| REQ-961 | `api/openapi.yaml` defines a `GuestModule` schema as a **discriminated union** (`oneOf` + `discriminator: type`), one member per module type, each with a fixed shape. **Each member declares its `type` as a plain `string` (`type: { type: string }`) — never a single-value `enum`.** The allowed values are pinned by `discriminator.mapping` in the contract and by `@JsonTypeName` on the generated Java class, not by the member's own schema. **The spelling is load-bearing and the intuitive one is the broken one:** with `type: { type: string, enum: [text] }` the build either compiles and puts `"type":"GuestModule"` on the wire instead of `"text"` — silently, with nothing failing on the Java side — or, once the generator flag below is on, fails to compile outright (`TypeEnum getType()` cannot implement the generated interface's `String getType()`). Writing the `enum` "for safety" is the trap. This shape additionally requires **one generator option**, `<useOneOfInterfaces>true</useOneOfInterfaces>`, on the `jaxrs-spec` execution in `backend/app/pom.xml` — **not a dependency** — which is owner-gated and lands in the same commit as this Part's first union, not before (see "Pending, owner-gated"). *(**D9 settled 2026-08-28**, Variant B, on the evidence in [`d9-spike.md`](d9-spike.md). The "blocked by D9" note this requirement carried since 2026-08-27 is spent. The contract still uses no `oneOf`, `anyOf`, `allOf` or `discriminator` anywhere today — this Part introduces the first.)* | Must |
| REQ-962 | The guest payload carries the **resolved, enabled, ordered** module list; the kiosk performs no enablement resolution and never re-derives the delivered order. *(Presentation ordering inside the popover — Part A's REQ-924 — is a view concern over the same delivered set. Note that `guest-content.mapper.ts` re-sorts `menu.items` by `sortIndex` today even though the contract says the server ordered them; that redundancy is resolved here, deliberately not in Part A.)* | Must |
| REQ-963 | Every module member declares explicit bounds: array `maxItems`, text `maxLength`. No unbounded array or string reaches the wire. *(Note 2026-08-28: REQ-961's Variant B is what makes this land where it is written — each member generates its own Java class holding **only its own** fields, so a member's bounds and `@NotNull`s belong to that member. The rejected `enum` spelling flattened every member's fields into one class, which would have put every module type's constraints on every module.)* | Must |
| REQ-964 | Localised module text follows the existing `LocalizedText` convention and is **resolved per offered language server-side**, exactly as `GuestHomeScreen` fields already are — never a raw template on the wire. | Must |
| REQ-965 | Manager-side CRUD for module selection, ordering and content exists in the contract under the existing `manager*` tags and auth scheme. **⚠ Constrained by D8 (2026-08-27): `docs/backend/specification.md` §3 decision 5 stands uncontested, so manager-authored module *content* is NOT authorised.** Selection and ordering already ship (`PUT /api/manager/menu-config`). The owner named per-module placeholder values as a *future* wish explicitly outside this feature; whenever it is taken up it needs an amendment to decision 5 first, not an implementation. | Must |
| REQ-966 | The generated TypeScript client regenerates **byte-identically** from the amended contract. **Regeneration alone does not achieve this when a schema is removed or renamed:** the generator does **not** delete the file it previously wrote under `packages/api-client/model/`, so the orphan `.ts` survives and the committed tree stops being what a clean checkout would produce. **Any commit in this Part that removes or renames a schema must delete the orphaned generated files in the same commit.** *(Added 2026-08-28 from the D9 spike, which left three orphan `spike*.ts` files behind after a full revert and had to remove them by hand — `git status` was clean only afterwards. Without this the criterion is asserted against a tree regeneration would not reproduce, and quietly stops meaning anything.)* | Must |
| REQ-967 | Cross-module values rendered on the overview are resolved **server-side from the owning module's data**; the value is never stored twice. *(Smaller than the parent draft claimed — it does not require an aggregated payload.)* | Must |
| REQ-968 | Persistence for module content follows the existing BCE package rules and the migration conventions of `backend-domain-config`; no package exists without a Control. | Must |
| REQ-969 | **Every manager mutation introduced by this Part writes an append-only audit row** — who, when, from which address, which entity, which field — and **never a value** of a guest-sensitive field (`docs/constitution.md` v1.8.1, principle 3). `AuditCoverageTest` derives `@Transactional public` Control methods from source and fails the build on an unclassified one. | Must |
| REQ-970 | A **module registry** under `core/guest/` (or `core/modules/`) is the single registration point per module type, carrying at minimum: wire type discriminant, validator/mapper, and the component that renders it. *(**D10 approved** by the owner 2026-08-27 — see Settled decisions. The pattern is authorised; the requirement still waits on the rest of this Part, and the registry has no consumer until a module screen exists.)* **Consequence of D9/Variant B, 2026-08-28:** the registry's per-type branch is where the lost TypeScript narrowing gets paid for. With the member `type` typed as `string`, `switch (m.type)` no longer narrows the generated union. **Narrowing stays cast-free** either way — one 1-line predicate per module type keyed on the discriminant (`const isText = (m: GuestModule): m is GuestTextModule => m.type === 'text'`) or structural (`'body' in m`); both typecheck under the project's strict flags. The loss lands in exactly **one file**, `guest-content.mapper.ts`, because REQ-977 and `frontend/CLAUDE.md` let nothing else import the generated models — and that file was going to branch per type anyway. **Exhaustiveness is a separate loss that this requirement does not cover — see REQ-980.** | Must |
| REQ-971 | Wire validation of module types is **derived from the registry**, not from a hand-maintained parallel list — the `registered-icons.ts` precedent, whose comment states the reason: *"Deriving the validation set from the registration map is what keeps the two from drifting."* *(Note 2026-08-28: this closes registry↔validator drift, not registry↔**contract** drift. Deriving the validation set from the registration map cannot notice a contract that gained a union member the registry never learned about — and under D9's Variant B nothing at compile time notices it either. That gap is REQ-980.)* | Must |
| REQ-972 | An unknown or structurally invalid **single** module is dropped from the mapped result; the rest of the payload renders. Only an invalid envelope throws `GuestContentMappingError`. | Must |
| REQ-973 | The frontend renders a module type it does not know **without crashing** — forward compatibility, so a backend that gains a module before the kiosk is updated degrades to "not shown". *(Note 2026-08-28, D9/Variant B: this graceful path is also what **hides a missing registration**. A member the build was meant to support but that nobody registered is, at runtime, indistinguishable from a genuinely newer module type — both degrade to "not shown". Under the rejected Variant A the compiler separated the two for free; under Variant B only REQ-980's guard does.)* | Should |
| REQ-974 | The four existing placeholder sections become catalogue modules or are explicitly retired; `SectionPlaceholderComponent` survives only if a delivered-but-unauthored module is a real state. *(Note 2026-08-27: Part A adds eight further placeholder rows that are **disposable fixtures** — removing them is one migration plus a `registered-icons.ts` edit. This Part must not adopt them as real modules by reflex.)* | Must |
| REQ-975 | Existing properties migrate without manual intervention, consistently with Part A's D5 (a newly introduced catalogue row arrives **property-disabled**; the manager opts it in). | Must |
| REQ-976 | The `%dev` seeder produces a property with at least two authored module types, so the registry path is exercised in development rather than only in tests. | Should |
| REQ-977 | Components read `PropertyContentService`, never `@gxp/api-client` models; no user-visible string is written in a component or template. *(**Consequence of D9/Variant B, 2026-08-28.** This boundary is what confines REQ-970's lost narrowing to a single file, and it now carries a second weight. A `m is GuestTextModule` predicate is an **assertion TypeScript trusts**, not a proof: it holds only because the backend put the right fields next to the right `type`. The mapper hand-validates every field regardless and drops/throws per REQ-972, so runtime behaviour is unchanged — but **the type system is no longer the thing proving the payload's shape; the mapper is.** Do not thin out the mapper's field validation on the grounds that "the types already say so". Under Variant B they do not.)* | Must |
| REQ-978 | Every module screen satisfies the no-scroll constraint at 1024×768 and 1280×832, in both themes and every offered language, measured with `node tools/measure.mjs`. | Must |
| REQ-979 | **No module payload — live or cached — carries anything that extends reach beyond the device**: no device or session token, no key material, no credential that could address the backend, and nothing scoped to another room or another guest. Guest-facing module content, **the WiFi password and door/key-box codes included, may be cached and rendered offline** — that is the point of caching it. *(Rewritten 2026-08-27 against `docs/constitution.md` **v1.9.0**; **D12 is settled**. The old text — "guest-sensitive module content never enters the last-known-good cache" — restated the absolute prohibition the amendment removed. Part B's REQ-951 pins the same line for the merged guest payload and is the precedent to follow, including its warning that the test must not filter on names like `password` or `code`.)* | Must |
| REQ-980 | **The module registry carries a compile-time exhaustiveness guard** — a `never` check that fails the build when the generated `GuestModule` union contains a member the registry does not handle. *(**New 2026-08-28, and it exists only because of D9.** The rejected Variant A got this for free: TypeScript checks a `switch (m.type)` over a properly discriminated union for exhaustiveness, so adding a member turned every such switch into a compile error. Variant B's predicate chain does not — an unhandled member simply falls through to REQ-973's "unknown → not shown" path, silently and in production. **No existing requirement covered this:** REQ-971 derives validation from the registry and so catches registry↔validator drift only; REQ-972/973 act at runtime and by design cannot distinguish "newer than this build" from "somebody forgot to register it". The guard is therefore hand-written or it does not exist. **How it is written is design work and is deliberately not decided here.**)* | Must |

**Acceptance criteria** are written when a module screen exists. *(Until
2026-08-28 this read "when the gates close and a module screen exists"; the
gates are closed now, the screen is not.)* Writing them now would pin behaviour
to a schema nobody has agreed to.

### Open items surfaced by the D9 fold (2026-08-28) — recorded, not resolved

Two things above are in tension now that Variant B is settled. Both are
**requirement-level questions for whoever plans this Part**, and neither is
answered here — answering either is design work, and the Part is not plannable
yet.

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
   question for planning.

---

## Technical constraints

- **`docs/constitution.md` v1.9.0 governs**, in particular the **amended**
  cache constraint (2026-08-27, three bullets under Constraints): the
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
  uncontested** (owner, 2026-08-27).
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

---

## Settled decisions (owner, 2026-08-27 and 2026-08-28)

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
   before any contract or schema work.
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
  nobody yet.

---

## Open gates — **none**

**All three are closed:** D9 settled 2026-08-28, D10 approved and D12 settled
2026-08-27, all under Settled decisions above. Nothing in this document is
waiting on an owner decision any more. What it waits on is a **designed module
screen**, which is not a decision and cannot be answered by one.

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
database, build" gates **any** `pom.xml` change, dependency or not. So: propose
this exact line to the owner as part of the commit that first introduces the
`GuestModule` union, and do not write it before then.

---

## Blocking map

| Blocks / blocked by | Detail |
|---|---|
| **Blocked by — decisions** | **Nothing.** All three gates are closed: **D9 settled 2026-08-28** (Variant B, after the generator spike), **D10 approved** and **D12 settled** 2026-08-27. |
| **Blocked by — prerequisites** | At least one **actually designed module screen** — **the only remaining blocker**, the one no decision can supply, and the owner's to produce. Without it, REQ-961/963/964 are schema design for unspecified content. |
| **Pending approval — does not block planning** | The single `pom.xml` line `<useOneOfInterfaces>true</useOneOfInterfaces>`. A generator option, not a dependency; asked when this Part's first union is committed, not before. |
| **Blocked by — features** | Part A (routes: every catalogue route must already resolve). Part B is a convenience, not a hard dependency. |
| **Constrained by** | **D8** — decision 5 stands, so REQ-965 is not authorised as written. **D11** — no per-room content override. |
| **Blocks** | Nothing. It is the end of the chain. |
