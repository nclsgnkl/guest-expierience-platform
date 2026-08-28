# Design: Module Content

**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **TODO:** [03-todo.md](03-todo.md) | **Spike:** [d9-spike.md](d9-spike.md) | **Screen:** [screens-inhouse/info.md](../../design/screens-inhouse/info.md)
**Status:** Draft
**Governing:** `docs/constitution.md` **v1.10.0**, `docs/backend/specification.md` §3 decision 5 / §4 / §5, `backend/CLAUDE.md`, `frontend/CLAUDE.md`

> **Part C of three** (owner decision D1, 2026-08-27). This document is HOW;
> REQ-961…REQ-980 live in the PRD and are not restated. Every owner decision
> (D8…D19) is settled there and **none of it is re-opened here**.
>
> **One approval is open and it is not this document's to take:** the single
> `<useOneOfInterfaces>true</useOneOfInterfaces>` line in
> `backend/app/pom.xml`. It is planned as **T-01, a stop-and-ask task**, and
> §1.5 states exactly why it must be answered *before* the contract is built
> rather than after.
>
> **Nothing else here opens a gate:** no dependency (`package.json` untouched;
> `pom.xml` gains one generator option and nothing else), no datastore or
> infrastructure choice (Postgres + Flyway, as already approved), no new
> architectural pattern — every mechanism below is named at its existing
> precedent — **no nested `CLAUDE.md` entry and no constitution amendment**.
> §11 records the two places that came closest and why neither is one.

---

## 0. What this phase was asked to decide

The PRD left exactly three items to `02_design`, all ordinary design work.
They are answered here, and the PRD's Acceptance criteria (written in the same
fold as this document) assert the answers:

| PRD "Open items" | Answer | Where |
|---|---|---|
| **1.** REQ-963 vs. the discriminator's unbounded `string` | **Explicit carve-out.** The discriminator property carries neither `enum` nor `maxLength`; every *other* string in the union is bounded by the new `ModuleText` schema | §1.3 |
| **2.** Nothing checks the registry's discriminant *literals* against the contract (the third drift axis) | **Covered, by a pinned contract test on the Java side** — `de.gxp.guest.GuestModuleContractTest`, the `ScheduledJobInventoryTest` precedent. **The fabricated-type test does not and cannot cover it**, and §8 says so in as many words | §8 |
| **5.** Part B's compile-time cache guard does not see nested fields | **The guard gains two nested tables** (`GuestMenuItem`, `GuestInfoModule`) in the same spec file and the same shape, **and** REQ-979's runtime case reaches into `menu.items[].module` explicitly. Both, not either | §7 |

Two further things this phase had to settle that no open item named, because
they only become visible once the code is laid out: **where the registry's
render half lives** (§4.4 — it is not in the registry file, and REQ-970 gains
an inline note saying so) and **whether a module is delivered to a vacant
room** (§2.5 — yes).

---

## 1. The contract

### 1.1 The union, spelled as D9 settled it

```yaml
    GuestModule:
      description: >-
        A section's content, one member per module type (A3: fixed shape per
        type, D14). The member's `type` is a plain string whose allowed values
        are pinned by `discriminator.mapping` below and by @JsonTypeName on the
        generated Java class — NEVER a single-value enum on the member. See
        docs/features/module-content/d9-spike.md: the enum spelling compiles
        and ships `"type":"GuestModule"` on the wire, or fails to compile once
        useOneOfInterfaces is on.
      oneOf:
        - $ref: "#/components/schemas/GuestInfoModule"
      discriminator:
        propertyName: type
        mapping:
          info: "#/components/schemas/GuestInfoModule"

    GuestInfoModule:
      type: object
      description: >-
        The `info` section's content — a list of label/value rows in delivered
        order (docs/design/screens-inhouse/info.md). No title (the screen's h1
        is GuestMenuItem.label), no per-row icon, no per-row sort key, no row
        id, no sensitive flag.
      required: [type, rows]
      properties:
        type:
          type: string
          description: >-
            Always "info". Deliberately NOT an enum (D9) and deliberately NOT
            maxLength-bounded (REQ-963 carve-out, 02-design.md §1.3): the value
            set is pinned by discriminator.mapping here, by @JsonTypeName in
            Java, and by the kiosk's registry-derived validation (REQ-971).
        rows:
          type: array
          maxItems: 10
          items:
            $ref: "#/components/schemas/GuestInfoRow"

    GuestInfoRow:
      type: object
      required: [label, value]
      properties:
        label:
          description: RESOLVED per offered language — never a raw template.
          $ref: "#/components/schemas/ModuleText"
        value:
          description: RESOLVED per offered language — never a raw template.
          $ref: "#/components/schemas/ModuleText"

    ModuleText:
      type: object
      description: >-
        Language-keyed map (keys are LanguageCode values), resolved per offered
        language — the LocalizedText convention (REQ-964) with the wire bound
        REQ-963 requires. LocalizedText itself stays unbounded: bounding it
        would put @Size on every existing text field in the contract, which is
        a contract-wide change this Part has no mandate for.
      additionalProperties:
        type: string
        maxLength: 240
```

and, on the existing schema, **one optional property and nothing else**:

```yaml
    GuestMenuItem:
      required: [key, label, icon, route, sortIndex]   # UNCHANGED
      properties:
        …
        module:
          description: >-
            Absent means no content is authored for this section yet (D15) —
            the kiosk renders SectionPlaceholderComponent.
          $ref: "#/components/schemas/GuestModule"
```

**No top-level `modules[]`, no join, no second array** (D15). `GuestMenu.items`
already declares `maxItems: 50`, so at most 50 modules can reach the wire by
construction and the module *count* needs no bound of its own (REQ-963's own
note).

### 1.2 The two numbers, and what they are not

| Bound | Value | Why this number |
|---|---|---|
| `GuestInfoModule.rows.maxItems` | **10** | `info.md` measures ≈490 px of vertical headroom below a two-row panel at **both** viewports; a further eight single-line rows fit inside it. So the payload-sanity bound and the layout budget agree for this type instead of being unrelated numbers |
| `ModuleText` value `maxLength` | **240** | ≈4 lines at the panel's ≈55-character line capacity. Long enough for a house-rule sentence or a long password, short enough that one value cannot consume the panel on its own |

**Neither is a no-scroll guarantee and neither may be treated as one.** A value
wraps (`overflow-wrap: anywhere`, `info.md`), so ten maximum-length rows would
overflow. No-scroll is REQ-978's *measurement*, not the contract's arithmetic —
`info.md` says the same thing about the renderer wrapping rather than trusting
the bound. What the bounds buy is that an untrusted wire cannot hand the kiosk
an unbounded array or an unbounded string (REQ-963), which is a different
property from "the screen fits".

**These are the contract's first `maxLength` declarations** — `grep -c maxLength
api/openapi.yaml` is `0` today. Two consequences: the generator's handling of
`maxLength` under `additionalProperties` is **unverified** (§10.1), and the
kiosk-side enforcement is the mapper's, not the type system's (§3.2).

### 1.3 The discriminator carve-out — PRD open item 1, answered

**Decided: carve the discriminator property out of REQ-963. Do not put
`maxLength` on it.** Three reasons, in the order that decided it:

1. **A length bound would not do REQ-963's job here.** REQ-963 exists so that
   nothing unbounded reaches the wire. `maxLength: 32` on `type` still admits
   `"nfo"`, `"Info"` and `"wlan"` — it bounds the wrong dimension. What
   actually pins the value is a **set**, and the set is pinned three times
   already: `discriminator.mapping` in the contract, `@JsonTypeName` on the
   generated Java class, and the kiosk's registry-derived validation
   (REQ-971), which drops anything not registered.
2. **It is the same class of trap the spike measured.** The generated interface
   declares `String getType()`; the one thing proven to break the build is
   changing what a member's `getType()` *is*. A `@Size`-annotated `getType()`
   is untested in this toolchain, and the PRD explicitly warns against adding
   it on instinct. Buying nothing (reason 1) for an unmeasured risk is a bad
   trade.
3. **The carve-out is visible where it matters.** The exemption is written into
   the contract next to the property (see §1.1) rather than living only in this
   document, so the next module member is written with the reason in front of
   the author.

**What the carve-out costs, stated:** `GuestModule`'s discriminant is the one
string on the guest wire with no schema-level bound. The kiosk never renders it
and never stores it unbounded — the mapper looks it up in the registry and
drops the module on a miss (§3.2), so an over-long or arbitrary discriminant
buys an attacker one failed map lookup.

### 1.4 What is deliberately absent from the wire

Each of these was closed by `info.md` or by a settled decision and **will be
re-proposed**; the contract's own descriptions say no:

no module `title` (the `<h1>` is `GuestMenuItem.label`) · no per-row `icon` ·
no per-row sort key (delivered array order is the order) · no row `id`
(client-derived, `@for track` only) · no `sensitive` flag (**D12 withdrew it
explicitly, and the WiFi row is exactly where somebody will want one**) · no
SSID row · no WPA QR code (a new dependency) · no `modules[]` array (D15) · no
route on the module (D15, rejected).

### 1.5 The `pom.xml` line, and why it is T-01 rather than a late step

D9's Variant B needs one line in the `jaxrs-spec` execution's `<configOptions>`:

```xml
<useOneOfInterfaces>true</useOneOfInterfaces>
```

The PRD says it "lands in the same commit as this Part's first union". **It
must also land in the same *build*, and that is stronger than the PRD's
wording.** With the flag off and a `oneOf` present, `jaxrs-spec` does not fail
— it emits a flattened concrete class, no interface, and `@JsonTypeName
("GuestModule")`, so the build goes **green** while the wire carries
`{"type":"GuestModule"}`. Anyone who runs `./mvnw verify` after editing
`api/openapi.yaml` but before the flag is approved gets a green build they can
believe. That is the spike's headline finding and it is the reason the approval
is **T-01, before the contract edit**, with the contract and the `pom.xml` line
landing together in **T-02**.

It is a **generator option, not a dependency** — the spike checked; nothing is
pulled in, and the blast radius on today's contract is provably zero because no
other `oneOf` exists. It is gated regardless: `docs/constitution.md` →
Constraints (*"No dependency-manifest edits … without explicit approval"*) and
`backend/CLAUDE.md` → *"any `pom.xml` addition needs prior user approval"*.

---

## 2. Backend

### 2.1 Where module content lives — `de.gxp.menu`, not a new feature package

**Decided: the module tables, entities and repository join `de.gxp.menu`.**

- The catalogue, its per-scope config and the action cards already live there,
  and `docs/system-context.md` calls that package *"ONE consistency unit on
  purpose"*. A module is the content of a catalogue section; under D15 it is
  literally delivered inside the menu item that `MenuControl` builds.
- **A new `de.gxp.module` feature would cost an exemption edit in a shared
  test.** It owns no HTTP surface (D16: no write path, and the read rides on
  `GuestMenuItem`), so `FeatureInternalsImportTest` would fail on *"has no
  `.boundary` package"* until `FeaturePackages.FEATURES_WITHOUT_BOUNDARIES`
  learned about it. Editing an architecture guard's exemption list to admit a
  package that exists for tidiness is a bad trade against a package that is
  already the right owner.
- REQ-968's *"no package exists without a Control"* is satisfied by
  `MenuControl`, which already exists and already owns these tables' siblings.

### 2.2 `V12__info_module_content.sql`

Flyway head moves `V11` → `V12` (`MigrationTest.flywayHeadIsV11HomeIsMandatory`
is renamed and re-pinned).

```sql
-- Module content for the `info` module type (module-content REQ-968).
-- Per-type tables, because a module type has a fixed shape per type (A3/D14):
-- the next module type brings its own table, not a column here.
-- NO room scope: module content is property-scoped (A2) and D11 settled that
-- there is no per-room content override.
-- NO seed rows: a release must not put content on a running property's guest
-- screens behind the manager's back (Part A's D5, same reasoning). An empty
-- table means `info` carries no module, which renders the placeholder exactly
-- as it does today (REQ-975). %dev seeds two rows; nothing else writes here in
-- this Part (D16 — the manager write path is the successor feature's).
CREATE TABLE info_module_row (
    id             bigserial PRIMARY KEY,
    menu_item_key  text    NOT NULL REFERENCES menu_item_definition (key) ON DELETE CASCADE,
    sort_index     integer NOT NULL,
    CONSTRAINT uq_info_module_row_order UNIQUE (menu_item_key, sort_index)
);

CREATE TABLE info_module_row_translation (
    row_id    bigint NOT NULL REFERENCES info_module_row (id) ON DELETE CASCADE,
    language  text   NOT NULL CHECK (language IN ('de', 'en')),
    label     text   NOT NULL,  -- raw template
    value     text   NOT NULL,  -- raw template
    PRIMARY KEY (row_id, language)
);
```

Shape notes, each deliberate:

- **`menu_item_key`, not a hardcoded `'info'`.** The type is "which table the
  row lives in"; the *binding* to a section is the key, exactly as
  `action_card_config.target_menu_item_key` binds a card. A property could bind
  an `info`-typed module to a second section without a schema change.
- **`label` and `value` are `NOT NULL`.** This is what discharges `info.md`'s
  *"an absent value means an absent row"* without a line of omission code: a
  row that exists has both, in every language it was authored in, and the
  existing main-language fallback in `GuestControl.resolveLocalized` fills the
  rest (REQ-426's rule, unchanged).
- **`text` columns hold raw templates**, like
  `action_card_config_translation.title_template`. Resolution is
  `GuestControl`'s (§2.4).
- **The cost, named:** per-type tables mean two tables and one migration per
  module type, and a read that consults one table per type. That is D14's price
  paid a second time, in the database. **A `module_binding` table (menu item →
  module type), which would let the read consult only the tables it needs, is
  deliberately *not* built now** — it buys nothing at one type and would be a
  third table nobody reads. The type that makes the per-type scan uncomfortable
  is the one that should build it.

### 2.3 `MenuControl` — the read, with raw templates

```java
/**
 * Guest-read: the module content each catalogue key RESOLVES to, with RAW
 * templates — {@code GuestControl} resolves them per offered language against
 * the active stay, exactly as it does for {@link #guestResolvedCards}. Property
 * scope only: module content is property-scoped (A2) and D11 settled that
 * there is no per-room override. Not HTTP-mapped.
 */
@Transactional
public Map<String, GuestModule> guestModuleContent() { … }
```

- Returns generated API DTOs (`GuestInfoModule` carrying raw template strings
  in its `ModuleText` maps), which is the `guestResolvedCards` precedent
  verbatim — *"RAW templates: `GuestControl` resolves them"*. **No new
  contract schema is invented as an internal transport DTO.**
- `@Transactional public` on a Control, so `AuditCoverageTest` sees it: it goes
  into `NOT_A_MANAGER_MUTATION` under "reads and lookups", beside
  `guestResolvedMenu` and `guestResolvedCards`. **That is the whole of REQ-969's
  work in this Part** — no manager mutation is introduced, so no audit call is.
- Rows come back ordered by `sort_index`; the ordering is not re-derived
  anywhere downstream (REQ-962 one level down, `info.md`'s rule).

### 2.4 `GuestControl` — resolve, then attach

`content()` gains four lines between the menu build and the payload assembly:

```java
List<GuestMenuItem> menu = menuControl.guestResolvedMenu(roomId, languages);
attachModules(menu, menuControl.guestModuleContent(), property, stay, languages);
```

`attachModules` walks the delivered items, looks the key up in the map and sets
`item.module(resolved)`. The per-type resolution is one arm of a Java 21
pattern switch:

```java
private GuestModule resolveModule(GuestModule raw, PropertyDto property, StayDto stay,
        List<String> languages) {
    return switch (raw) {
        case GuestInfoModule info -> new GuestInfoModule().type("info").rows(info.getRows().stream()
                .map(row -> new GuestInfoRow()
                        .label(resolveLocalized(row.getLabel(), property, stay, languages))
                        .value(resolveLocalized(row.getValue(), property, stay, languages)))
                .toList());
        default -> null;   // defensive skip — unreachable while one member exists
    };
}
```

- **`resolveLocalized` is reused unchanged.** REQ-964 is therefore satisfied by
  the mechanism that already satisfies it for `GuestHome` and `GuestActionCard`
  — same timezone, same per-language locale, same main-language fallback.
- **The `default` arm is the stranded-card precedent** (`home()` already skips a
  card whose target is not in the resolved menu): a defensive skip, never a 500.
  It is unreachable today; `GuestModule` is an interface and not sealed, so the
  compiler requires it.
- **Java's exhaustiveness is not free here either** (the `default` swallows a
  new member silently). The kiosk's guard is REQ-980; the server's is
  §8's pinned contract test, which fails the moment the contract's member set
  changes.

### 2.5 A vacant room still gets its modules — and one consequence

**Decided: modules are delivered in both occupancy states.** They are
property-scoped content (A2), and `menu.items` is delivered vacant already;
omitting modules when vacant would be a stay-shaped special case on
property-scoped content, i.e. exactly the kind of rule that gets rediscovered
as a bug.

**The consequence, stated rather than buried:** with `stay == null`,
`{stay.checkOut|time}` resolves to itself, so a vacant payload carries the raw
placeholder text in `info` row 1. Nobody sees it — a vacant kiosk is on
`/standby`, and this is precisely the state D18's correction describes: the
raw-braces outcome on `/info` is **unreachable, not unlikely**. Two things
follow and both are written into the code:

- `GuestControl`'s existing javadoc sentence *"no braces ever leave the
  server"* is scoped to `home` (occupied only) and **must not be generalised**
  to the payload. The `attachModules` javadoc says so.
- This is *not* a reason to add placeholder-shaped logic anywhere. D18 rejected
  option (a) and the mapper carries none (§3.2).

### 2.6 The ETag needs no change, and that is a decision

`GuestContentVersion`'s stamp is `sha256(bootId | max(audit_entry.id) | roomId
| stay fingerprint | 300 s bucket)`. Module content is written by **a migration
or the `%dev` seeder**, both of which happen at startup — and a restart mints a
new `bootId`, so every write this Part can produce already invalidates the
stamp. Nothing is added to the fingerprint.

**The hole, named:** a developer who edits `info_module_row` with native SQL
against a *running* instance gets the change up to **five minutes late**, via
the refresh bucket Part B built for exactly this class of under-invalidation.
That is acceptable here and would not be once a manager can author content —
**the successor feature `module-content-authoring` must fold module writes into
the stamp**, and it gets that for free by writing an audit row, since
`max(audit_entry.id)` is already in the hash.

### 2.7 `%dev` — one authored type, and the REQ-967 repair

Two seeder changes, both inside `seedIfMissing()`'s existing "never touch what
a developer changed since" guard:

1. **The `info` module** — two rows against key `info`, with `info.md`'s copy
   table verbatim (row 1 `Check-out` / `{stay.checkOut|date}, {stay.checkOut|time} Uhr`
   in German; row 2 `WLAN-Passwort` / `Meeresrauschen-77`). Written only when
   `info_module_row` is empty, like `seedActionCards`.
2. **REQ-967's live duplication** — `DevDataSeeder.CARDS[0]`'s description stops
   being the literal *"Bis 11:00 Uhr am Abreisetag"* and becomes
   `"Bis {stay.checkOut|time} Uhr am Abreisetag"` (en:
   `"By {stay.checkOut|time} on departure day"`). The value is now stored once,
   in the stay, and rendered twice — which is REQ-967 in one edit, and the
   smallest possible demonstration of it. Safe under D18's own rule:
   `{stay.checkOut|time}` is in `TemplateControl.ALL_PLACEHOLDERS` and would
   pass the write-time validation that already ships.

**Recorded caveat, inherited from `enableCatalogueFixtures`:** a developer whose
dev database already carries the dev device sees neither change until the Dev
Services volume is dropped. That is the seeder's stated contract, not a defect.

`info` needs no catalogue or config work: it is one of `V2`'s five original
rows, enabled by inheritance, and already carries `/info`. **REQ-975 is
therefore satisfied by construction** — an upgraded instance runs `V12`, gets
two empty tables, delivers no `module`, and renders exactly what it rendered
before.

---

## 3. The kiosk boundary — `guest-content.mapper.ts`

### 3.1 One field, one branch, one file

`mapMenuItem` gains one line:

```ts
module: mapWireModule(MODULE_REGISTRY, item.module, defaultLanguage),
```

That is the whole of the per-type branching outside the registry, and it is in
the one file `frontend/CLAUDE.md` and REQ-977 already confine generated models
to. `MenuItemShape<TText>` gains `module: ModuleShape<TText> | null`.

### 3.2 What "structurally invalid" means, and what happens to it

`mapWireModule` returns `AuthoredModule | null`. **`null` covers three
different situations that the guest is deliberately not asked to tell apart:**

| Situation | Result | Requirement |
|---|---|---|
| the item carries no `module` | `null` | D15 — nothing authored yet |
| the discriminant is in no registry entry | `null` | REQ-973 — forward compatibility |
| a known type fails validation | `null` | REQ-972 — the drop |

All three render `SectionPlaceholderComponent`'s `section.comingSoon` inside
the section host (§6.2). One consistent "nothing here yet", never a blank
screen and never a missing sidebar entry.

**Validation is by hand, field by field, and does not lean on the types.** Under
Variant B a `m is GuestInfoModule` predicate is an assertion TypeScript trusts,
not a proof (REQ-977's note). **The per-type checks live in the registry, not
in this file** *(corrected 2026-08-28, review finding F-11 — this paragraph read
"the mapper checks" until then)*: `mapInfoModule` ships in
`core/guest/module-registry.ts` beside `MODULE_REGISTRY`, because the mapper
imports the registry and a `mapInfoModule` in the mapper would have made that
import circular. `guest-content.mapper.ts`'s share stays the one `mapWireModule`
line of §3.1. For `info` the registry checks: `rows` is an array;
`rows.length <= 10`; every row has a `label` and a `value`; each is an object
carrying a non-blank entry for the property's default language; and **every
string in every language is `<= 240` characters** — the first string-length
check the kiosk's content mapping has ever carried, because the contract's first
`maxLength` arrives with it (§1.2).

**Reject, never truncate** — the mapper's existing posture for `MAX_MENU_ITEMS`
and `MAX_CARDS`, stated in its own comment. A 241-character value costs the
section its content and shows "coming soon"; it does not silently render a
truncated password. *(The alternative — drop the offending row and keep the
module — was considered and rejected: REQ-972's granularity is the module, and
a partial screen hides a server bug better than an empty one does.)*

**Three things the module path must not do**, each because it was decided
elsewhere:

- **No `try`/`catch` around the module branch.** It would also swallow the
  envelope failures that must stay fatal. The module path uses null-returning
  helpers: `localizedOrNull`, in `module-registry.ts`, is the **sibling in
  another file** of `guest-content.mapper.ts`'s throwing `localized()`
  *(corrected 2026-08-28, review finding F-11 — this read "beside the existing
  `localized()`", which was the argument for why the pair stays consistent)*.
  Being in two files, the pair **re-states** the default-language contract
  rather than sharing it, so each one's javadoc names the other.
- **No placeholder-shaped logic.** A value containing `{…}` is not a drop
  trigger — D18 rejected that option explicitly, and it false-positives on
  legitimately braced authored text.
- **No logging, and no message quoting the content.** The file logs nothing
  today and must not start: `info.md` names the WiFi row as exactly where
  somebody will be tempted, and notes that a console warning is not exempt
  from principle 3.

### 3.3 REQ-962 — the redundant re-sort goes

`mapGuestContent` currently does `[...items].sort((a, b) => a.sortIndex - b.sortIndex)`
even though the contract says the server ordered them, and `MenuItemShape`
carries no `sortIndex` at all. It is deleted here (Part A deliberately left it
alone), leaving `items.map(...)`, with a comment naming the contract line that
makes the delivered order authoritative. **This is what makes REQ-962
structural rather than careful** — combined with D15, a disabled section has no
item and therefore no module anywhere in the payload.

---

## 4. The module registry — `frontend/src/app/core/guest/module-registry.ts`

### 4.1 Why `core/guest/` and not `core/modules/`

REQ-970 offered both. It must import the generated `GuestModule` union — for
the predicates and for REQ-980's `never` check — and `frontend/CLAUDE.md`
permits `@gxp/api-client` imports in `core/guest/` and `core/api/` only.
`core/modules/` would need a `CLAUDE.md` amendment, which is owner-gated and
which this Part will not propose. **`core/guest/` it is**, no gate, no
exception.

### 4.2 The shape

```ts
/** One line per module type: the wire member each domain kind maps from. */
interface ModuleWireMap {
  readonly info: GuestInfoModule;
}

type ModuleKind = AuthoredModule['type'];          // from property.model.ts

interface ModuleEntry<K extends ModuleKind> {
  /**
   * Wire → authored, or null when the module is structurally invalid
   * (REQ-972). Takes the whole union: narrowing is the entry's own one-line
   * predicate (D9's cast-free b1), which keeps the call site below cast-free
   * too.
   */
  readonly map: (wire: GuestModule, defaultLanguage: string) => AuthoredOf<K> | null;
}

export const MODULE_REGISTRY: { [K in ModuleKind]: ModuleEntry<K> } = {
  info: { map: mapInfoModule },
};

/** REQ-971: the wire-validation set IS the registration map. No second list. */
export function mapWireModule(
  registry: typeof MODULE_REGISTRY,
  wire: GuestModule | null | undefined,     // null is what actually arrives — see below
  defaultLanguage: string,
): AuthoredModule | null {
  if (wire == null || !Object.hasOwn(registry, wire.type)) {
    return null;                       // REQ-973 — a type this build does not know
  }
  return registry[wire.type as ModuleKind].map(wire, defaultLanguage);
}
```

> **`== null`, not `=== undefined`** *(corrected 2026-08-28, review finding
> F-3 — this block read `wire: GuestModule | undefined` and `wire === undefined`
> until then, and that was wrong in a way a green build would not have shown).*
> The generated model types `GuestMenuItem.module` as **optional**, which is
> what the earlier guard was written against. The server does not omit the key:
> nothing configures Jackson's inclusion, so the default `ALWAYS` puts
> **`"module": null`** on the wire — measured, and exactly how
> `GuestContent.home` already behaves (`mapGuestContent` meets it with
> `content.home ?? null`). A `=== undefined` guard passes `null` through and
> then reads `.type` off it, throwing **out of a branch §3.2 forbids a
> `try`/`catch` around** — so one malformed-or-absent module would take the
> whole envelope down and REQ-972's per-module isolation would be lost on the
> commonest path there is. Use `== null` (or `?? null`); the loose comparison
> is the deliberate one, because both `null` and `undefined` mean "nothing
> authored".

Four properties fall out, and each is one of this Part's requirements:

1. **The mapped type `{ [K in ModuleKind]: … }` forces the registry's key set to
   equal the domain union's discriminants.** A kind added to
   `property.model.ts` with no registry entry fails `ng build`.
2. **`Object.hasOwn(registry, …)` is REQ-971 verbatim** — the `registered-icons.ts`
   precedent, whose comment states the reason: deriving the validation set from
   the registration map is what keeps the two from drifting.
3. **The registry is passed in, not imported, at the one call site.** That is
   what lets D17's fabricated type exist purely inside a test (§10.2) — no
   default parameter, no test-only seam in production code, the `menu-split.ts`
   precedent of exporting the pure rule.
4. **`map` takes the whole union**, so the lookup call is cast-free; the
   1-line predicate lives inside `mapInfoModule` where D9 put it.

### 4.3 The exhaustiveness guard — REQ-980, written out

```ts
/**
 * REQ-980: the compile-time guard Variant B costs. TypeScript checks a switch
 * over a properly discriminated union for exhaustiveness; with the member's
 * `type` a plain `string` (D9) it cannot, so an unregistered member would fall
 * through to REQ-973's "unknown -> not shown" path silently, in production.
 * This is the hand-written replacement, and it fails the BUILD:
 *
 *   error TS2344: Type 'GuestWlanModule' does not satisfy the constraint 'never'.
 */
type AssertNever<T extends never> = T;
type RegisteredWire = ModuleWireMap[ModuleKind];
type _EveryModuleMemberIsRegistered = AssertNever<Exclude<GuestModule, RegisteredWire>>;
```

**Its one limitation, recorded rather than discovered later:** `Exclude` is
assignability-based, so a hypothetical future member whose properties are a
subset of a registered member's would be excluded structurally and slip
through. Every real member adds its own fields (that is what A3/D14 means), so
this is a theoretical hole, not a live one — but it is the reason the guard is
`Exclude` over member *types* and not something stronger: with Variant B there
is no literal union to check against.

**How it is verified:** T-16's validate line requires the implementer to add a
throwaway second member to `ModuleWireMap`'s source union once, observe the
build fail with the error above, and revert. A guard nobody has seen fail is a
guard nobody knows works.

### 4.4 Where the render half lives — and the note REQ-970 gains

REQ-970 asks the registry to carry *"wire type discriminant, validator/mapper,
**and the component that renders it**"*. **The component is registered in the
section host instead, and this is a deliberate deviation:**

```ts
// features/section-host/section-host.component.ts
private static readonly RENDERERS = {
  info: InfoModuleComponent,
} satisfies Record<ModuleKind, Type<unknown>>;
```

**Why not in the registry file.** `core/guest/module-registry.ts` importing
`features/info-module/…` would be the **first `core → features` import in the
app** (checked: none exists today). That is a layering inversion, and inverting
the app's layering to satisfy the letter of a requirement is a worse trade than
splitting one table.

**Why the split does not re-open the drift REQ-970/971 exist to close.**
`satisfies Record<ModuleKind, Type<unknown>>` makes the renderer map **total
over the same `ModuleKind`** the registry is keyed on: a kind with no renderer
fails the build, and a renderer for a kind that does not exist fails the build
(excess property check). The registry stays *the single source of the type
set*; the host holds a compiler-checked projection of it.

**Cost per new module type, honestly:** the D10 approval was framed as "two
places" (a seed row and a frontend file). This design makes it **four
touchpoints in three files** on the kiosk side — a `ModuleWireMap` line, a
registry entry, a `resolveModule` switch arm (§5) and a `RENDERERS` line — of
which **three are compiler-enforced and the fourth is REQ-980's `never` check**.
Nothing here can be forgotten silently, which is the property D10 was actually
buying. `01-prd.md`'s REQ-970 carries an inline note recording this.

---

## 5. The domain model, and where language resolution goes

`core/content/property.model.ts` gains a module union in the same
`Shape<TText>` style the file already uses, so authored and resolved shapes
cannot drift:

```ts
interface InfoModuleShape<TText> {
  readonly type: 'info';                    // a LITERAL, not a string
  readonly rows: readonly { label: TText; value: TText }[];
}
type ModuleShape<TText> = InfoModuleShape<TText>;      // the union grows per type
```

**The domain union is properly discriminated even though the wire union is
not.** The mapper is where the literal is minted, so `switch (module.type)`
narrows and is exhaustiveness-checked everywhere downstream. That is worth
stating plainly: D9's loss is confined to the wire boundary, exactly as
REQ-977 predicted, and the app above it behaves as if Variant A had been chosen.

Language resolution therefore stays in `resolveProperty`, dispatched through
one function that lives beside the registry:

```ts
// module-registry.ts — one arm per type, and the compiler counts them
export function resolveModule(module: AuthoredModule, text: TextResolver): ResolvedModule {
  switch (module.type) {
    case 'info':
      return { ...module, rows: module.rows.map((row) => ({
        label: text(row.label, 'module.info.label'),
        value: text(row.value, 'module.info.value'),
      })) };
  }
}
```

Under `strict` + `noImplicitReturns` a new kind turns this into a compile
error, which is the third of §4.4's compiler-enforced touchpoints. `resolveProperty`
itself gains one generic line inside its existing `menuItems.map`:

```ts
module: item.module === null ? null : resolveModule(item.module, text),
```

*(A switch rather than a `resolve` function on each registry entry, deliberately:
indexing a record of per-kind functions with a union key is the classic
correlated-union problem and needs an `as never` at the call site. A switch over
a real discriminated union is cast-free and the compiler counts the arms.)*

---

## 6. The screen

### 6.1 The route table does not change

`app.routes.ts` keeps `{ path: ':sectionKey' }` and the `**` last child, and
**gains no `{ path: 'info' }`** — that would re-enumerate the catalogue the
route table deliberately stopped enumerating (`info.md`; the `einstellungen`
precedent is app chrome, not a catalogue row). Both entries point at
`SectionHostComponent` instead of `SectionPlaceholderComponent`.

### 6.2 `SectionHostComponent` — `features/section-host/`

Resolves the section from the URL exactly as the placeholder does today —
`itemForRoute(items, url)` from `layout/sidebar/menu-split.ts`, the convention
`frontend/CLAUDE.md` documents and `SidebarComponent` already shares, **so a
route normalises identically everywhere and no new lookup exists** (D15's
reason 2). Then two arms:

| `itemForRoute(...)?.module` | Renders |
|---|---|
| a module | `<h1>` (the item's resolved label) + `<ng-container *ngComponentOutlet="RENDERERS[module.type]; inputs: { module }">` |
| `null`, or no item at all | `<app-section-placeholder />`, untouched |

**`SectionPlaceholderComponent` is behaviourally unmodified** *(reworded
2026-08-28, owner decision — it previously read "not modified at all", which
T-11's required `module` field made false for its spec's fixture)*: no
assertion in its spec changes, no case is added or removed, and the only
permitted edit is type conformance forced by a shared model change. The
component source itself is untouched. It keeps resolving the
section from the URL itself and keeps both of its arms (`section.comingSoon`
for a claimed route, `section.unavailable` for one the menu does not claim), so
REQ-974's answer costs no change to a shipped component and no behavioural spec
churn. It re-does one `itemForRoute` call; that is a shared pure function over data
already held, and the alternative (an input) would rewrite a component and its
spec to save nothing.

**The host renders the `<h1>`, the module renders the panel.** `info.md`
sketches both inside one component, and this is a refinement of that sketch, not
a departure from it: the screen frame (`<h1>` = catalogue label, `--space-9` top
inset, `--space-6` to the content) is the first row of `info.md`'s *"What
generalises"* table, so it belongs to the host that every module type shares.
The measured geometry is unchanged — same DOM order, same tokens, same
`--space-*` values. The host is the flex column; the module component declares
`:host { display: contents }` so its panel is the real flex item
(`frontend/CLAUDE.md` → Layout).

The `<h1>` is only rendered in the module arm, so the placeholder arm keeps its
own title and centred layout byte-for-byte.

### 6.3 `InfoModuleComponent` — `features/info-module/`

Exactly `info.md`, and nothing more: one glass panel (`--color-surface-glass`,
`--border-glass`, `--radius-2xl`, `--shadow-panel`, `--blur-glass`), width
`calc(3 * var(--size-panel) + 2 * var(--space-4))`, `--space-6` padding and
row gap; `<dl>` with one `<div>` per pair wrapping `<dt>`/`<dd>`; label
`--text-body` / `--color-text-secondary` over value `--text-title` /
`--color-text-primary`, `--space-1` apart, `overflow-wrap: anywhere` on the
value; no divider, no icon, no interactive element, no `text-transform`.

- **Zero new tokens, zero new icons, zero new translation keys.** The `<dl>`
  semantics mean the screen needs no accessible-name string, and both rows are
  content — `info.md` is explicit that nothing on this screen is app chrome.
- `@for (row of module().rows; track $index)` — there is no wire id and
  delivered order *is* the identity, so the index is the honest track key
  (`ActionCardShape.id` exists only because two cards may share a `targetKey`).
- The component takes one signal input (`module: ResolvedInfoModule`) and reads
  no service. It never sees a generated model (REQ-977).
- **No empty state, and that is a finding, not an omission** (D19): row 1 is a
  stay fact always present under `OCCUPIED`, and `/info` never renders
  otherwise, so `info` cannot be empty. None is invented.

---

## 7. The cache classification guard — PRD open item 5, answered

**Both halves, not either.** In `core/guest/guest-data.service.spec.ts`, beside
Part B's existing top-level table:

```ts
type MenuItemKey = keyof Required<GuestMenuItem>;
const _menuItemIsClassified: Record<MenuItemKey, true> = {
  key: true, label: true, icon: true, route: true, sortIndex: true, module: true,
};
type InfoModuleKey = keyof Required<GuestInfoModule>;
const _infoModuleIsClassified: Record<InfoModuleKey, true> = { type: true, rows: true };
```

and REQ-979's runtime case reaches in explicitly rather than trusting the
top-level sweep:

```ts
expect(Object.keys(record.payload.menu.items[0]).sort()).toEqual([...ALLOWED_MENU_ITEM_KEYS].sort());
expect(Object.keys(record.payload.menu.items[0].module).sort()).toEqual(['rows', 'type']);
```

**Why both.** The compile-time table is what fails when someone adds a wire
field and forgets to classify it — the property Part B built and that D15
quietly cost (`menu: true` was already there, so `GuestMenuItem.module` would
have arrived without breaking anything). The runtime assertion is what proves
the *cache record actually written* carries only those keys. Neither implies
the other.

**The residual, stated:** this is two hand-written tables, not recursion. A
future nested wire type still needs its own table and nothing forces one. A
genuinely recursive type-level walk is possible and was considered; it is
unreadable, it would be the only construct of its kind in the codebase, and it
would still need a per-type value table to be useful. **Recorded as a known
gap** rather than solved by machinery, and the fix when it bites is one more
five-line table.

**What may be cached is unchanged** (constitution v1.9.0, D12, REQ-979): the
WiFi row is guest-facing content the manager deliberately displays, so it is
cached and `/info` **renders identically from cache** — no degradation, no
hidden row, no "unavailable" substitute. The test asserts the *credentials*
(the device token's literal, `bearer`, `authorization`) are absent and
**must not filter on names like `password` or `code`** — that would assert
close to the opposite rule (Part B's REQ-951 warning, inherited verbatim).

---

## 8. Drift — three axes, and the one that had nothing on it

| Axis | What it is | Guard |
|---|---|---|
| registry ↔ validator | the wire-validation set disagrees with the registration map | **REQ-971** — there is no second list; `Object.hasOwn(MODULE_REGISTRY, …)` *is* the set (§4.2) |
| contract ↔ registry *membership* | the contract gains a member nothing registers | **REQ-980** — the `never` check, §4.3 |
| contract ↔ registry *literal* | the registry handles a member under a wrong or stale discriminant string | **PRD open item 2 — answered below** |

### The third axis, answered

With `type` typed as `string`, `m.type === 'nfo'` typechecks, and so does a
registry keyed on a literal the contract has since renamed. Nothing in the
TypeScript world can see it: under Variant B the literals exist **only** in
`api/openapi.yaml`'s `discriminator.mapping` — the generated client contains no
literal at all.

**The fabricated-type test cannot cover this, and this design says so
explicitly** (the PRD asked for exactly that statement). The fabricated type's
discriminant is *defined by the test file*, so test and registry agree by
construction; there is no contract for them to disagree with. After D17 the
`%dev` exercise cannot cover it either — one seeded type, so a rename shows up
as "`/info` renders the placeholder", which is quiet unless somebody looks.

**Decided: cover it on the Java side, with a pinned contract test.**

```java
// backend/app/src/test/java/de/gxp/guest/GuestModuleContractTest.java
/**
 * The discriminant literals of the GuestModule union, PINNED BY HAND on
 * purpose — the ScheduledJobInventoryTest precedent. Under D9's Variant B the
 * literals live only in api/openapi.yaml's discriminator.mapping; nothing in
 * the generated TypeScript carries them, so a contract rename would leave the
 * kiosk's registry silently stale. This test turns that rename into a red
 * build with the old and new literal both on screen.
 */
private static final Map<String, Class<?>> EXPECTED = Map.of("info", GuestInfoModule.class);
// read @JsonSubTypes off the generated GuestModule interface and compare
```

It also asserts the generated `GuestInfoModule` carries `@JsonTypeName("info")`
and that `getType()` returns `String` — the two facts the spike proved break
silently under the `enum` spelling.

**Where it does *not* live: `de.gxp.architecture`.** The constitution's
sentence counts **five** architecture guards; a sixth needs an amendment, and
Part B refused one for the same reason. This is an ordinary feature test in
`de.gxp.guest`, next to `GuestEtagTest`.

**The residual, precisely.** After a contract rename the Java build goes red
and the developer must edit the pinned map — at which point the literal is in
front of them and the kiosk's registry is one grep away. It converts a silent
drift into a loud one with a pointer; it does not *prove* the kiosk agrees.
Proving that needs the literal to exist in one machine-readable place both
sides read, which Variant B forecloses. **That is the honest state of this
axis, and it is better than the "nothing currently requires anything of it"
the PRD recorded.**

---

## 9. Data model summary

| Change | Detail |
|---|---|
| **added** | `info_module_row`, `info_module_row_translation` (`V12`), property-scoped, no seed rows |
| **unchanged** | every existing table. No column is added to `menu_item_definition`, `menu_item_config` or `stay` |
| **Flyway head** | `V11` → `V12`, re-pinned in `MigrationTest` |

## 10. API contract diff

| Change | Detail |
|---|---|
| **added** | `GuestModule` (`oneOf` + `discriminator`, **the contract's first union**), `GuestInfoModule`, `GuestInfoRow`, `ModuleText` (**the contract's first `maxLength`**) |
| **added** | `GuestMenuItem.module` — optional; `required` unchanged |
| **removed / renamed** | **nothing** — see REQ-966 below |
| **build** | `backend/app/pom.xml` gains **two** generator options (T-01's approval, T-02's edit; the second added and approved at implementation, recorded here at review — F-2): `<useOneOfInterfaces>true</useOneOfInterfaces>` in the `generate-jaxrs` execution's `<configOptions>`, and `<openapiNormalizer>SIMPLIFY_ONEOF_ANYOF=false</openapiNormalizer>` at **plugin level** — the normalizer collapses a one-member `oneOf` before `useOneOfInterfaces` can act, and both executions need it |

`packages/api-client/` is regenerated, never hand-edited; contract, build,
backend, client, frontend and docs land in one commit.

**REQ-966 and the orphan sweep.** The spike found that the generator does not
delete a `.ts` file it previously wrote, so a removed or renamed schema leaves
an orphan and the committed tree stops being what a clean checkout produces.
**This Part removes and renames no schema — it only adds** — so the sweep has
nothing to delete. That is not a reason to drop the check: T-03 and T-21 assert
`git status --porcelain` is empty after a second `./mvnw verify`, which is what
would catch an orphan if one ever appeared. *(Checked while planning:
`packages/api-client/model/` holds 57 files and no `spike*` residue.)*

### 10.1 Two things to verify at implementation

Neither changes a requirement and neither can open a gate. Both are recorded
here so that discovering them does not look like a decision:

1. **A single-member `oneOf`.** The spike ran a **two**-member union. This Part
   ships **one** (D14/D17: a second type costs a second designed screen).
   `typescript-angular` is expected to emit `export type GuestModule =
   GuestInfoModule;` and `jaxrs-spec` an interface with one implementor.
   **If the generator degrades a single-member union** — no union alias, or an
   inlined member — **stop and report.** Do not invent a second contract member
   to work around it: D17 put the second type in a test, never in
   `api/openapi.yaml`. T-03's validate line is where this is seen.
2. **`maxLength` under `additionalProperties`.** The spike proved
   `List<@Size(max = 120) String>` for array items; the map-value case is
   analogous but untested. **If the Java side drops the bound**, the contract
   bound still stands and the kiosk still enforces it (§3.2) — which is where
   REQ-963's protection actually matters, since the untrusted direction is
   server → kiosk and the server is the producer. Record it and continue.

---

## 11. Two things that came close to a gate, and why neither is one

- **A `core → features` import** would have been a real layering change. It is
  avoided (§4.4), not argued around.
- **A sixth architecture guard** would need a constitution amendment. §8's
  pinned test is an ordinary feature test in `de.gxp.guest`, and the five
  guards in `de.gxp.architecture` are untouched — including their exemption
  lists, which is why §2.1 put the tables in `de.gxp.menu`.

Neither a dependency nor a datastore choice nor a `CLAUDE.md` entry appears
anywhere in this plan. The **one** open approval is §1.5's `pom.xml` line.

---

## 12. Test strategy — the seams that matter

### 12.1 The fabricated second type (REQ-976, D17)

`module-registry.spec.ts` defines a type that exists **only in that file** — no
contract member, no seed row, no component — and drives `mapWireModule` with a
registry containing it beside the real `info` entry. That is what §4.2's
registry-as-a-parameter is for. Four cases, and the last two are the ones a
well-formed second seed could never reach:

1. **dispatch** — a fabricated-type payload maps through the fabricated entry,
   and the `info` entry does not swallow it;
2. **the first does not shadow the second** — an `info` payload still maps
   through `info` with both registered;
3. **the miss (REQ-973)** — a discriminant in neither entry returns `null`;
4. **the drop (REQ-972)** — a structurally invalid known type returns `null`.

**What this deliberately does not prove:** that the registry's literals match
the contract's (§8). Stated in the spec's own header so the next reader does
not assume it.

### 12.2 The rest

| Seam | Instrument |
|---|---|
| REQ-964 server-side resolution | backend: `%dev`-shaped fixture, `GET /api/guest/content` with `de`+`en` offered → row 1's value carries no `{`, and equals the check-out formatted in the property timezone per language |
| REQ-962 no disabled section's content | backend: disable a module-carrying section at property scope → the item **and** its module are absent from the payload |
| REQ-963 bounds | mapper spec: 11 rows → dropped; a 241-character value → dropped; 10 rows and a 240-character value → mapped |
| REQ-972 isolation | mapper spec: a malformed module on one item leaves every other item and the whole envelope intact, `mapGuestContent` does not throw, and `console` is spied on to prove nothing is logged |
| REQ-973 forward compatibility | host spec: an unknown type renders `section.comingSoon`, the sidebar entry survives |
| REQ-979 cache | §7 — two compile-time tables and one runtime reach, no name-based filter |
| REQ-980 the `never` check | verified once by hand (§4.3) — an unregistered member must make `ng build` fail |
| REQ-978 no-scroll | `node tools/measure.mjs` — **`/info` is already in its route grid**, so no instrument change is needed for the route half |
| REQ-978 the banner near-miss | `tools/measure-states.mjs` gains a `/info` variant of Part B's BANNER cell (§12.3) |

### 12.3 The banner figure `info.md` flagged

`info.md` computes that the escalated staleness banner clears `/info`'s panel by
**1.2 px at 1280** and **overlaps by 0.2 px at 1024**, and says plainly that the
figures are arithmetic and must be measured. The route grid cannot see the
banner (it is a kiosk *state*), so Part B's BANNER cell in
`tools/measure-states.mjs` gains one variant that navigates to `/info` before
ageing the cached record. It reports both boxes and asserts no-scroll.

**The overlap is not expected to be a defect and the task must not "fix" it by
moving anything:** the banner is an overlay by construction, the 0.2 px is
against its padding rather than its copy, and the rows are left-aligned so the
bar lands on the panel's empty right-hand side (`info.md`, three stated
reasons). What the measurement buys is knowing the figure is a fifth of a pixel
and not a centimetre.

### 12.4 Must stay green, untouched

The five architecture guards (**no sixth**), `AuditCoverageTest`'s assertions
(one added entry in `NOT_A_MANAGER_MUTATION` and nothing else),
`CatalogueExpansionTest`, `MenuDisableGuardTest`, `MandatoryMenuItemTest`, and
`SectionPlaceholderComponent`'s spec — the component is behaviourally
unmodified, so **no assertion in that spec changes, no case is added or
removed, and the only permitted edit is type conformance forced by a shared
model change** (T-11's `module: null`, 4 sites). Written as a property rather
than as an exception list, so it still bites if an assertion is quietly
rewritten.

---

## 13. Documentation

`docs/system-context.md` needs four rows touched, and **this design does not
write them** — the components do not exist yet, and both shipped Parts made the
sweep a TODO task (A's T-21, B's T-18). T-20 does it:

- `de.gxp.menu` — the two module tables and the raw-template guest read;
- `de.gxp.guest` — module resolution and attachment inside `content()`;
- `guest-content.mapper.ts` — the module branch, the drop, and the removed
  `sortIndex` re-sort;
- `SectionPlaceholderComponent` — demoted to the empty state inside the section
  host; plus **new rows** for `SectionHostComponent`, `InfoModuleComponent` and
  `core/guest/module-registry.ts`.

`docs/features/index.md`'s Part C row moves to `05_implement`.

**Not written here and not by this Part:** `docs/backend/specification.md` §3
decision 5 stays **unamended** (D16), and D12's operator-documentation
disclosure stays an owner task with a trigger — no real property carries real
module content in this Part.

---

## 14. Risks & trade-offs

| Risk | Position |
|---|---|
| The `pom.xml` flag is forgotten or refused | T-01 is a stop-and-ask before any build runs, because the failure mode is a **green** build shipping `{"type":"GuestModule"}` (§1.5). If the owner declines, the Part stops — Variant C is strictly worse (no TS union, a concrete instantiable base) and a free-form bag cannot be boundary-validated |
| A single-member `oneOf` behaves differently from the spike's two | §10.1 (1) — verify at T-03, and **stop and report** rather than adding a contract member D17 put in a test |
| The registry's literals drift from the contract | §8 — a pinned Java test converts it into a red build. The residual is stated, not hidden |
| Per-type tables mean ~20 tables | D14's price, paid in the database. A `module_binding` table would let the read consult only the needed tables; deliberately not built at one type (§2.2) |
| Four kiosk touchpoints per module type instead of D10's "two places" | Three are compiler-enforced and the fourth is REQ-980's guard, so none can be forgotten silently (§4.4) |
| A too-long value costs the whole section its content | The mapper's stated posture is reject-not-truncate; the alternative renders a truncated password, which is worse (§3.2) |
| Module content is cached and rendered offline | Exactly what v1.9.0 permits and what D12 decided; `/info` **must** render identically from cache — a guest whose backend is unreachable is the guest who needs the WiFi password |
| Vacant payloads carry raw placeholder text | Unreachable on screen (`/standby`), and the alternative is a stay-shaped special case on property content (§2.5) |
| The nested cache guard is two tables, not recursion | A future nested type needs its own table and nothing forces one — recorded as a known gap with a five-line fix (§7) |
| Part C cannot serve a real property | D16, and the PRD says so in as many words. What ships is the transport, the per-type shape, the registry and one rendered module |

---

## 15. Open decisions

**None.** No `[NEEDS CLARIFICATION]` marker is left in this design: the PRD's
three open items are answered in §1.3, §8 and §7, and every other choice above
belongs to this phase and has been taken.

**One approval is open and is not a design decision:** §1.5's single `pom.xml`
line, planned as **T-01, which stops and asks**. Two items are
**verify-at-implementation** (§10.1) — a single-member `oneOf` and `maxLength`
under `additionalProperties` — and neither changes a requirement.

## 16. Decisions confirmed by the user

| Decision | Chosen | Date |
|---|---|---|
| D8 — may the manager author module content at all? | No; `docs/backend/specification.md` §3 decision 5 stands | 2026-08-27 |
| D9 — `oneOf` + `discriminator` on the wire? | **Variant B** — member `type` a plain `string`, plus `useOneOfInterfaces` | 2026-08-28 |
| D10 — the module registry as a frontend convention | Approved | 2026-08-27 |
| D12 — guest-sensitive module content in the kiosk cache | May be cached (constitution v1.9.0) | 2026-08-27 |
| D14 — generic row list, or bespoke per type? | **Bespoke per type** | 2026-08-28 |
| D16 — may Part C author module content? | No — REQ-965 descoped | 2026-08-28 |
| D17 — two authored types in `%dev`? | One, plus a fabricated type in a test | 2026-08-28 |
| D18 — an unresolvable placeholder | Refused at the write (successor feature); the frontend does nothing | 2026-08-28 |
| D19 — a shared empty state for modules? | No — per module type | 2026-08-28 |
| D15 — how does a module bind to its route? | `menu.items[].module`, optional *(the lead's, on the owner's explicit delegation — reversible; reasoning in the PRD)* | 2026-08-28 |
| **`backend/app/pom.xml` — `<useOneOfInterfaces>true</useOneOfInterfaces>`** | **⏸ NOT CONFIRMED — T-01 asks** | — |
