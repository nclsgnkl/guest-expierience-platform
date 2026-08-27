# PRD: Module Content
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Draft — **STILL NOT READY TO PLAN**, but for two reasons now
instead of four. **One approval gate remains open — D9** (`oneOf` /
`discriminator`, and it needs a generator spike before it can honestly be asked)
— and **at least one designed module screen still does not exist**, which is the
real blocker and which no decision can supply. **D10 was approved and D12 was
settled on 2026-08-27** (see Settled decisions); neither blocks anything any
more. `02_design` must not start. This document exists so nothing is lost, not
because the feature is ready.

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
> **Governing:** `docs/constitution.md` **v1.9.0**, `docs/backend/specification.md`
> §3 decision 5 / §5, `frontend/CLAUDE.md`, `backend/CLAUDE.md`.

---

## Why this Part is not plannable yet

Two independent reasons, and neither is a scheduling preference.

1. **One approval gate is still open — D9** (`oneOf` / `discriminator` on the
   wire). The owner deferred it on 2026-08-27 saying he did not understand the
   question; that is not informed approval and it is not treated as one. Ask it
   again only **after** the generator spike — see "Open gate" below.
   *(Two gates closed on 2026-08-27 and are recorded under Settled decisions:
   **D10**, the module registry, **approved** once it was re-framed correctly as
   a frontend code file; **D12**, module content in the last-known-good cache,
   **settled by constitution amendment v1.9.0**, whose wording the owner
   approved verbatim.)*
2. **It is schema design for content nobody has specified.** REQ-961 requires
   *"one member per module type, each with a fixed shape"* while the Non-goals
   exclude *"designing the individual module screens"*. You cannot fix a
   per-type schema before deciding what the type shows. **At least one real,
   designed module screen must exist first.**

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

> Preserved verbatim from the parent PRD, **with one exception: REQ-979 was
> rewritten on 2026-08-27** because the constitution clause it quoted no longer
> exists (v1.9.0; D12). Several others are **constrained or contradicted** by
> the settled decisions below; each such requirement carries an inline note.
> Nothing is deleted — an ID never changes meaning — but a note here is binding
> on whoever plans this Part.

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-961 | `api/openapi.yaml` defines a `GuestModule` schema as a **discriminated union** (`oneOf` + `discriminator: type`), one member per module type, each with a fixed shape. *(Blocked by **D9** — the contract uses no `oneOf`, `anyOf`, `allOf` or `discriminator` anywhere today; verified 2026-08-27.)* | Must |
| REQ-962 | The guest payload carries the **resolved, enabled, ordered** module list; the kiosk performs no enablement resolution and never re-derives the delivered order. *(Presentation ordering inside the popover — Part A's REQ-924 — is a view concern over the same delivered set. Note that `guest-content.mapper.ts` re-sorts `menu.items` by `sortIndex` today even though the contract says the server ordered them; that redundancy is resolved here, deliberately not in Part A.)* | Must |
| REQ-963 | Every module member declares explicit bounds: array `maxItems`, text `maxLength`. No unbounded array or string reaches the wire. | Must |
| REQ-964 | Localised module text follows the existing `LocalizedText` convention and is **resolved per offered language server-side**, exactly as `GuestHomeScreen` fields already are — never a raw template on the wire. | Must |
| REQ-965 | Manager-side CRUD for module selection, ordering and content exists in the contract under the existing `manager*` tags and auth scheme. **⚠ Constrained by D8 (2026-08-27): `docs/backend/specification.md` §3 decision 5 stands uncontested, so manager-authored module *content* is NOT authorised.** Selection and ordering already ship (`PUT /api/manager/menu-config`). The owner named per-module placeholder values as a *future* wish explicitly outside this feature; whenever it is taken up it needs an amendment to decision 5 first, not an implementation. | Must |
| REQ-966 | The generated TypeScript client regenerates **byte-identically** from the amended contract. | Must |
| REQ-967 | Cross-module values rendered on the overview are resolved **server-side from the owning module's data**; the value is never stored twice. *(Smaller than the parent draft claimed — it does not require an aggregated payload.)* | Must |
| REQ-968 | Persistence for module content follows the existing BCE package rules and the migration conventions of `backend-domain-config`; no package exists without a Control. | Must |
| REQ-969 | **Every manager mutation introduced by this Part writes an append-only audit row** — who, when, from which address, which entity, which field — and **never a value** of a guest-sensitive field (`docs/constitution.md` v1.8.1, principle 3). `AuditCoverageTest` derives `@Transactional public` Control methods from source and fails the build on an unclassified one. | Must |
| REQ-970 | A **module registry** under `core/guest/` (or `core/modules/`) is the single registration point per module type, carrying at minimum: wire type discriminant, validator/mapper, and the component that renders it. *(**D10 approved** by the owner 2026-08-27 — see Settled decisions. The pattern is authorised; the requirement still waits on the rest of this Part, and the registry has no consumer until a module screen exists.)* | Must |
| REQ-971 | Wire validation of module types is **derived from the registry**, not from a hand-maintained parallel list — the `registered-icons.ts` precedent, whose comment states the reason: *"Deriving the validation set from the registration map is what keeps the two from drifting."* | Must |
| REQ-972 | An unknown or structurally invalid **single** module is dropped from the mapped result; the rest of the payload renders. Only an invalid envelope throws `GuestContentMappingError`. | Must |
| REQ-973 | The frontend renders a module type it does not know **without crashing** — forward compatibility, so a backend that gains a module before the kiosk is updated degrades to "not shown". | Should |
| REQ-974 | The four existing placeholder sections become catalogue modules or are explicitly retired; `SectionPlaceholderComponent` survives only if a delivered-but-unauthored module is a real state. *(Note 2026-08-27: Part A adds eight further placeholder rows that are **disposable fixtures** — removing them is one migration plus a `registered-icons.ts` edit. This Part must not adopt them as real modules by reflex.)* | Must |
| REQ-975 | Existing properties migrate without manual intervention, consistently with Part A's D5 (a newly introduced catalogue row arrives **property-disabled**; the manager opts it in). | Must |
| REQ-976 | The `%dev` seeder produces a property with at least two authored module types, so the registry path is exercised in development rather than only in tests. | Should |
| REQ-977 | Components read `PropertyContentService`, never `@gxp/api-client` models; no user-visible string is written in a component or template. | Must |
| REQ-978 | Every module screen satisfies the no-scroll constraint at 1024×768 and 1280×832, in both themes and every offered language, measured with `node tools/measure.mjs`. | Must |
| REQ-979 | **No module payload — live or cached — carries anything that extends reach beyond the device**: no device or session token, no key material, no credential that could address the backend, and nothing scoped to another room or another guest. Guest-facing module content, **the WiFi password and door/key-box codes included, may be cached and rendered offline** — that is the point of caching it. *(Rewritten 2026-08-27 against `docs/constitution.md` **v1.9.0**; **D12 is settled**. The old text — "guest-sensitive module content never enters the last-known-good cache" — restated the absolute prohibition the amendment removed. Part B's REQ-951 pins the same line for the merged guest payload and is the precedent to follow, including its warning that the test must not filter on names like `password` or `code`.)* | Must |

**Acceptance criteria** are written when the gates close and a module screen
exists. Writing them now would pin behaviour to a schema nobody has agreed to.

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
  has ever emitted a `oneOf` in this repo — see D9.
- Backend: Quarkus, BCE sub-packages, Flyway. Frontend: Angular 22, standalone,
  zoneless, signals, plain CSS with the two-layer token system.

---

## Settled decisions (owner, 2026-08-27)

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

- It is **not** approval for D9. The registry would carry the type
  discriminant, but how that discriminant is represented on the wire is a
  separate, still-open question.
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

## Open gate — **D9, and it is not approved**

> The lead stated explicitly to the owner that consent to this is **not** taken
> as given. It must be answered when this Part is planned, not before. *(D10 and
> D12 stood here until 2026-08-27; both are now under Settled decisions.)*

### D9 — **GATE** — `oneOf` + `discriminator` on the wire?

**Status: deferred, explicitly not approved.** The owner said he does not
understand the question and defers it; that is not informed approval, and it will
not be treated as one.

When it is asked again, ask it *after* a generator spike, not before: add one
throwaway two-member union to `api/openapi.yaml`, run `./mvnw verify`, and read
both generated outputs. `jaxrs-spec` + `interfaceOnly` + `openApiNullable=false`
has never emitted a `oneOf` in this repo, and `jaxrs-spec`'s polymorphism support
is historically weaker than `typescript-angular`'s. If the Java side degrades to
`Object` or wants a new artifact (e.g. `jackson-databind-nullable`), **that is a
second, separate dependency gate.**

The alternative (a generic key/value bag) has no generator risk but cannot be
boundary-validated at all (A3), and every module renderer becomes a cast.

---

## Blocking map

| Blocks / blocked by | Detail |
|---|---|
| **Blocked by — decisions** | **D9 alone.** The owner deferred it without understanding it, which is not approval; ask again only after the generator spike. **D10 (approved) and D12 (settled by constitution v1.9.0) no longer block anything** — both moved to Settled decisions on 2026-08-27. |
| **Blocked by — prerequisites** | At least one **actually designed module screen** — **the real blocker**, and the one no decision can supply. Without it, REQ-961/963/964 are schema design for unspecified content. |
| **Blocked by — features** | Part A (routes: every catalogue route must already resolve). Part B is a convenience, not a hard dependency. |
| **Constrained by** | **D8** — decision 5 stands, so REQ-965 is not authorised as written. **D11** — no per-room content override. |
| **Blocks** | Nothing. It is the end of the chain. |
