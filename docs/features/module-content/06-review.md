# Review: Module Content — Part C, Phases 1–4 (T-01 … T-10)

**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md) | **Spike:** [d9-spike.md](d9-spike.md)
**Reviewed:** 2026-08-28 · branch `refactor/bce-subpackages-and-boundary-security` · baseline `HEAD = 2ae2577`
**Governing:** `docs/constitution.md` **v1.10.0**, `backend/CLAUDE.md`, `CLAUDE.md`, `docs/system-context.md`

# 🔍 Review: PASS ✅

**Phase 5 (T-11) may start.** Nothing found blocks it. Three 🟡 items are
recorded below; one of them (#3) is a defect *in the plan for T-12* and should
be read before that task is written.

**Counts:** 🔴 0 · 🟠 0 · 🟡 3 · 🟢 7

**Step 0 (visual check) — not applicable, stated rather than skipped.** This cut
touches no rendered surface: `git status` carries no path under `frontend/`, and
the whole of the kiosk boundary, the screen and the measurement work is Phases
5–8. No screenshot was captured and none was required.

---

## 1. What was verified independently, and how

A green build has shipped a wrong discriminant on this feature twice. Nothing
below is taken from the build's exit code; every claim was re-measured.

### 1.1 `backend/app/pom.xml` — the diff, and both options proved load-bearing

`git diff backend/app/pom.xml` is **10 lines, all additions.** No version bump,
no plugin, no dependency, no other edit. `md5 871b274ad0ec247b0a5feb837e17e77d`.

**The structure is not quite what the handoff summary reads like, and the
difference is correct.** The two options went in at *different* levels:

| Option | Where | Scope |
|---|---|---|
| `<openapiNormalizer>SIMPLIFY_ONEOF_ANYOF=false</openapiNormalizer>` | **plugin-level `<configuration>`** | inherited by **both** executions |
| `<useOneOfInterfaces>true</useOneOfInterfaces>` | the **`generate-jaxrs` execution's** `<configOptions>` | Java only |

That is the right placement — `useOneOfInterfaces` is a Java-generator option and
the TypeScript execution has no use for it, while the normalizer is needed by
both. Four comment lines, in the file's existing style, owner-approved
(`docs/handoff.md`, 2026-08-28).

**Each option was removed in isolation and the tree regenerated, to establish
that neither is redundant:**

| Removed | Java `GuestModule` | TypeScript |
|---|---|---|
| *(nothing — as committed)* | `public interface GuestModule { String getType(); }` | `export type GuestModule = GuestInfoModule;` |
| `useOneOfInterfaces` only | `public class GuestModule` + **`@JsonTypeName("GuestModule")`** | `export type GuestModule = GuestInfoModule;` — **still correct** |
| `openapiNormalizer` only | `public class GuestModule` + **`@JsonTypeName("GuestModule")`** | `export interface GuestModule { … }` — **alias gone** |

Both are required. The middle row is the most deceptive state the toolchain can
produce: the TypeScript side looks perfect while Java is minting the wrong
discriminant. `pom.xml` was restored and re-verified by md5 after each probe.

**Blast radius confirmed zero:** `grep -n "oneOf\|anyOf\|allOf" api/openapi.yaml`
returns exactly one hit, line 2631 — `GuestModule` is the contract's only union.

### 1.2 The generated union, as observed here

```java
// target/generated-sources/openapi/src/gen/java/de/gxp/api/model/GuestModule.java
@JsonIgnoreProperties(value = "type", allowSetters = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = GuestInfoModule.class, name = "info"),
})
public interface GuestModule {
    String getType();
}
```
```java
// GuestInfoModule.java
@JsonTypeName("info")
public class GuestInfoModule  implements GuestModule {
  private String type;
  private @Valid List<@Valid GuestInfoRow> rows = new ArrayList<>();
  …
  @NotNull public String getType() { return type; }
  @NotNull @Valid  @Size(max=10)public List<@Valid GuestInfoRow> getRows() { return rows; }
```
```ts
// packages/api-client/model/guestModule.ts
export type GuestModule = GuestInfoModule;
```
```ts
// packages/api-client/model/guestInfoModule.ts
export interface GuestInfoModule {
    type: string;
    rows: Array<GuestInfoRow>;
}
```

All five facts the task named are present. `GuestInfoRow` is
`Map<String, String> label` / `value` with **no `@Size`** — the recorded
asymmetry, confirmed (see 🟢 F-6).

### 1.3 The serialised discriminant — my own Jackson probe

Compiled against `target/classes` + the project runtime classpath, verbatim
output:

```
union is interface        : true
member implements union   : true
getType() return type     : java.lang.String
member @JsonTypeName      : info
subtype mapping           : info -> de.gxp.api.model.GuestInfoModule
SERIALIZED concrete       : {"type":"info","rows":[{"label":{"de":"WLAN-Passwort"},"value":{"de":"Meeresrauschen-77"}}]}
SERIALIZED via interface  : {"type":"info","rows":[{"label":{"de":"WLAN-Passwort"},"value":{"de":"Meeresrauschen-77"}}]}
SERIALIZED menu item      : {"key":"info",…,"module":{"type":"info","rows":[…]}}
SERIALIZED menu           : {"items":[{"key":"info",…,"module":{"type":"info",…}}],"settingsEntry":{…}}
ROUNDTRIP class           : de.gxp.api.model.GuestInfoModule
ROUNDTRIP getType()       : info
ALL GuestModule impls     : [de.gxp.api.model.GuestInfoModule]
```

`SERIALIZED via interface` is the line that matters — `GuestModule asUnion = m;`
is the static type the payload actually takes, and it writes `"info"`, never
`"GuestModule"`. The two permanent HTTP tests
(`GuestModuleContentTest.anOccupiedRoomGetsEveryRowResolvedPerOfferedLanguage`,
`DevDataSeederTest.seedsTheInfoModuleAndRendersOneStoredCheckOutTimeTwice`) assert
the same off a served payload; both ran green here.

### 1.4 A third guard nobody claimed — the union is load-bearing in `javac`

While proving 1.1 I found something the plan did not anticipate and that is worth
recording: **with either generator option removed, this tree no longer compiles.**

```
GuestControl.java:[238,18] incompatible types: de.gxp.api.model.GuestModule cannot be converted to de.gxp.api.model.GuestInfoModule
GuestControl.java:[238,81] incompatible types: bad type in switch expression
MenuControl.java:[308,64] incompatible types: de.gxp.api.model.GuestInfoModule cannot be converted to de.gxp.api.model.GuestModule
```

`MenuControl.guestModuleContent()` returning `Map<String, GuestModule>` populated
with `GuestInfoModule`, and `GuestControl.resolveModule`'s pattern switch, make
the interface relationship a compile-time requirement. **The failure mode that
shipped green twice can no longer ship green on this tree**, and it is now
guarded at three independent levels: `javac`, `GuestModuleContractTest`, and two
HTTP assertions on `module.type`.

### 1.5 Byte-identical regeneration and the orphan sweep — REQ-966

- **Three clean regenerations of my own** (`./mvnw -o clean generate-sources`,
  `clean compile`, and again after the T-09 probe) all produced the same
  91-file tree. Sorted per-file md5 digest, identical every time:
  **`90090e21b79278c06e4a789c623371dc`**.
- `FILES` diff: **three additions, zero deletions**
  (`guestInfoModule.ts`, `guestInfoRow.ts`, `guestModule.ts`).
- **`FILES` ↔ disk agree in both directions:** every path listed in `FILES`
  exists, and every `model/*.ts` on disk is listed in `FILES`. Neither
  direction had a stray.
- **Every `model/*.ts` maps to a live schema in `api/openapi.yaml`** (checked
  mechanically, not assumed — REQ-966's criterion). 60 files vs. 61 schemas,
  the two map types `LocalizedText` and `ModuleText` correctly producing no
  file of their own.

### 1.6 The orphan `git status` could not show — gone, and checked for siblings

- `find . -iname "*zzprobe*"` and `-iname "*spike*"`: **nothing** but
  `docs/features/module-content/d9-spike.md`.
- After `mvn clean compile`: **`Compiling 174 source files`** =
  **102** under `src/main/java` + **72** generated. The count reconciles exactly
  with zero orphans. *(dev's "174 → 172" reconciles too, at T-03 time: `src/main`
  gained `InfoModuleRow.java` and `InfoModuleRowTranslation.java` later, at T-05.)*
- **Reflective scan of the whole of `target/classes`** for types assignable to
  `GuestModule`: **exactly one** — `de.gxp.api.model.GuestInfoModule`. A second
  implementor could not be hiding.
- `target/classes/de/gxp/api/model/` holds 62 class files, none matching
  `probe|spike`.
- **Swept for anything else of that shape:** the only gitignored file inside a
  committed generated tree is
  `packages/api-client/.openapi-generator/openapi.yaml-null.sha256` — pre-existing,
  explained and deliberately ignored at `.gitignore:447–452`, untouched by this
  cut (see 🟢 F-7).

### 1.7 T-09 proved red by hand — reproduced

Renamed the mapping key to `infoRENAMED` in `api/openapi.yaml`, regenerated, ran
the test:

```
Tests run: 3, Failures: 2, Errors: 0
theUnionsSubTypesAreExactlyThePinnedMapping
  → expected: <{info=class …GuestInfoModule}> but was: <{infoRENAMED=class …GuestInfoModule}>
everyMemberCarriesItsMappingKeyAsJsonTypeName
  → expected: <info> but was: <infoRENAMED>
```

**2 of 3 red, both literals on screen, exactly as claimed.** Reverted and
regenerated; `md5 api/openapi.yaml = 40bd69e30868825aabbc7630926656fe`, and the
client tree hash returned to `90090e21b79278c06e4a789c623371dc`. The guard is
real; it is not a test that cannot fail.

### 1.8 The suite — my own numbers, and where 449 comes from

`./mvnw -o test`, this box: **`Tests run: 438, Failures: 0, Errors: 4, Skipped: 3`,
25:24 min.**

Four classes errored, all with a custom `@TestProfile` that forces a second Dev
Services container, all failing **before any application code** with
`ContainerLaunchException: Container startup failed for image postgres:17.11`,
`Acquisition timeout while waiting for new connection` or `Failed to start
quarkus`. Re-run individually, **all four are green**:

| Class | In suite | In isolation |
|---|---|---|
| `TrustedProxyLoginTest` | 1 run, 1 error | **6 / 6 ✅** |
| `DevDataSeederTest` | 3 run, 1 error, 2 skipped | **3 / 3 ✅** |
| `UntrustedPeerAttributionTest` | 2 run, 1 error, 1 skipped | **2 / 2 ✅** |
| `OpenApiDisabledTest` | 2 run, 1 error | **2 / 2 ✅** |

**And a fifth class the summary hides.** `de.gxp.user.control.SecondFactorLoginTest`
reported **`Tests run: 0, Failures: 0, Errors: 0, Skipped: 0`** — it shares
`TrustedProxyProfile` with `TrustedProxyLoginTest`, so when that profile's Quarkus
instance failed to boot, all **six** of its tests vanished from the total as
neither run, failed, nor skipped. In isolation: **6 / 6 ✅**.

**Reconstruction of the full-green total.** Parsing the surefire XML after the
isolation re-runs gives `tests=443, errors=0, failures=0, skipped=0`, with
`SecondFactorLoginTest` still counted as 0. Add its six:

> **443 + 6 = 449.**

**That independently reproduces dev's 449** — and therefore `449 − 8 new tests
(3 contract + 4 content + 1 seeder) = 441`, the previously reported baseline.
I am satisfied 441 was real. See 🟢 F-5 for the reporting hazard this exposed.

*(No container-startup error was chased. `docker ps -a` shows no gxp container
before or after; no stray `mvn`/Quarkus process survives.)*

### 1.9 The four scope guarantees

| Guarantee | Result |
|---|---|
| **No sixth architecture guard** | `de.gxp.architecture/` is **unmodified** (`git status` clean for that path). Five guard classes present, no sixth. `FeaturePackages.FEATURES_WITHOUT_BOUNDARIES` is still `List.of("text")` — **no exemption list edited**. `GuestModuleContractTest` lives in `de.gxp.guest`, as the design required |
| **No manager write path (D16)** | No boundary file changed (`menu/boundary`, `guest/boundary` clean). The only `InfoModule` write in `src/main` is `DevDataSeeder`'s — the one named `PersistenceAccessTest` exemption. `docs/backend/specification.md` unamended |
| **No second real module type (D17)** | One `oneOf` member, one seeded type, one table pair. No fabricated type anywhere in `src/main` |
| **`menu.items[].module?` (D15)** | `GuestMenuItem.required` byte-identical to `HEAD`; `GuestMenu.items.maxItems` still `50`; **no top-level `modules[]`**; member `type` is a plain `string` with **no `enum`** and **no `maxLength`** (D9 / Variant B, and §1.3's carve-out written next to the property) |

### 1.10 Conventions — `backend/CLAUDE.md` and constitution v1.10.0

Checked and clean: entities in `de.gxp.menu.entity` are `jakarta.persistence`-only
and extend nothing (no `PanacheEntity`); the `@IdClass(… Id_.class)` record shape
and the per-field-widening javadoc are copied from `ActionCardConfigTranslation`
verbatim; DB access is confined to the package-private `MenuRepository` and the
entities; `@Transactional` sits on `MenuControl`, never a Boundary; `GuestResource`
is **unmodified** and still one delegating line per method, class-level
`@RolesAllowed("device")` (the one annotation for which class level is permitted).
No logging was added anywhere on the module path — which matters, because the
seeded WiFi row is exactly the value principle 3 names. No string-concatenated
SQL: every native query in `DevDataSeeder` and `GuestModuleContentTest` is
parameterised. `package.json` untouched. No new pattern was introduced, so no
nested `CLAUDE.md` entry was owed for anything in this cut except the one dev has
queued (§4).

---

## 2. Findings

| # | Sev | Category | File | Finding | Fix |
|---|-----|----------|------|---------|-----|
| F-1 | 🟡 | Correctness (docs) | `backend/app/src/main/java/de/gxp/guest/control/GuestControl.java:378` | `resolveLocalized`'s javadoc still says *"`stay == null` only for standby texts, whose allowed placeholder set has no stay fields."* **That is now false.** §2.5 attaches modules in both occupancy states, so this method is called with `stay == null` on content whose placeholder set *does* include stay fields — the raw-brace outcome `GuestModuleContentTest` case (b) asserts. T-07 required the scoping note; it was written into `attachModules` but the contradicted sentence on the *reused* method was left standing | Amend the sentence to name the second caller, e.g. that `stay == null` now also reaches it via `attachModules`, where a raw stay placeholder is the documented outcome (§2.5). Comment-only |
| F-2 | 🟡 | Conventions (plan) | `docs/features/module-content/03-todo.md` T-02, T-22 | T-02 still reads *"add the one approved line and nothing else"* and T-22's final check still reads *"`pom.xml` carries **only** the one approved line"*. **Two** lines are now approved and present. The boxes were ticked without amending the text, so T-22 will either fail against its own wording or be waved through | Amend T-02 and T-22 to say *two* approved options, naming both and their placement (plugin-level vs. `generate-jaxrs` `<configOptions>`) |
| F-3 | 🟡 | Correctness (forward, T-12/T-13) | `02-design.md` §4.2 vs. the served wire | An item with no module serialises as **`"module": null`**, not as an absent key — measured: no `quarkus.jackson.serialization-inclusion` is set and the generated DTO carries no `@JsonInclude`, so `{"key":"wlan",…,"module":null}` is what goes out. `GuestContent.home` already behaves this way and the shipped mapper handles it with `content.home ?? null`. **The design's `mapWireModule` snippet tests only `wire === undefined`**, so `null` would fall through to `wire.type` and throw a `TypeError` — and §3.2 forbids a `try`/`catch` around the module branch, so the throw would escape as an envelope failure and blank the whole screen instead of dropping one module | At T-12 write the guard as `wire == null` (or `wire ?? null` first), matching the file's existing `?? null` convention. Add a mapper case for `module: null` at T-17 |
| F-4 | 🟢 | Readability | `MenuMapper.java` `toInfoModule`, `GuestControl.java:238` | The literal `"info"` is minted **twice** on the Java side. `GuestControl.resolveModule` rebuilds the DTO with `.type("info")` and never reads `raw.getType()`, so `MenuMapper`'s `.type("info")` is write-only — its value never reaches the wire | Optional. The load-bearing one (`GuestControl`) *is* covered by two HTTP tests asserting `module.type == "info"`, so there is no silent-drift hole. Passing `raw.getType()` through would remove the duplicate |
| F-5 | 🟢 | Testing (environment) | suite reporting | A Quarkus boot failure on a **shared** `@TestProfile` makes sibling classes report **`Tests run: 0`** with zero errors and zero skips — six `SecondFactorLoginTest` tests disappeared from my total that way. Pre-existing, not caused by this cut, but it is precisely the mechanism by which a "baseline test count" drifts unnoticed | Record the number, not the exit code — as this review and dev's handoff both did. Worth remembering the next time a baseline is quoted |
| F-6 | 🟢 | Correctness | contract vs. server | dev's asymmetry note is right but understates it: `maxLength: 240` never reaches Java, **and `@Size(max=10)` is inert too** — the generated `GuestApi.getGuestContent` return carries no `@Valid`, so response DTOs are never bean-validated. The DB columns are unbounded `text` (consistent with every existing translation table). **So no layer enforces either bound server-side.** Nothing in this cut depends on either — only the `%dev` seeder writes, with short values | None now. Do **not** let T-13 be planned on "the backend already caps rows at 10": §3.2's hand-validation of both bounds is the only enforcement. The successor `module-content-authoring` should enforce 240/10 at the write |
| F-7 | 🟢 | Scope | `packages/api-client/.openapi-generator/openapi.yaml-null.sha256` | A gitignored file lives inside the committed generated tree. **Pre-existing and deliberate** — `.gitignore:447–452` explains it (a `quarkus:dev` run writes an execution-id-less twin) and it leaked into a commit once before (`5557318`). Untouched by this cut; its mtime predates the session | None |
| F-8 | 🟢 | Correctness | `V12__info_module_content.sql` | Nothing requires an `info_module_row` to have at least one translation row, so a row with none would serialise as `{"label":{},"value":{}}`. Unreachable today (only `%dev` writes, always both languages) and the kiosk's T-13 validation drops it | Name it in the successor feature's write path; no change here |
| F-9 | 🟢 | Accuracy | `docs/features/module-content/handoff.md` | Two verification notes are slightly off: *"`GuestResource` still 2 methods, 2 delegating lines"* — it has **3** (verified unmodified, one delegating line each); and *"174 → 172 sources"* only reconciles against the T-03-time tree (`src/main` gained two entity files at T-05). Neither affects the substance, both of which I re-verified directly | None; noted so the numbers are not re-quoted |
| F-10 | 🟢 | Scope | `docs/features/index.md`, `docs/system-context.md` | Both still describe the pre-Part-C world | Expected — T-21 owns the sweep. Not a finding against this cut |

**Nothing rose to 🔴 or 🟠.** No sensitive data is logged, no query is
string-concatenated, no endpoint or handler was added (so no authorization gap),
no secret is hardcoded in shipped code (`Meeresrauschen-77` is a fictional value
in the `%dev`-only seeder, mandated verbatim by `info.md`'s copy table and
alongside the existing `DEV_DEVICE_TOKEN` precedent), the dependency manifest
edit is the owner-approved one and nothing else, work is on a feature branch, and
no approval-gated decision was taken without a recorded confirmation.

---

## 3. The two declared deviations — judged

### 3.1 `DevDataSeederTest` gained assertions, though T-08 listed only `DevDataSeeder.java` — **accept**

T-08's validate line was written as a hand-check (*"a fresh `quarkus:dev`
database serves …, and Home's check-out card shows the same time"*). Making it
mechanical is strictly better, and this is the **only** place REQ-967 is actually
*proved*: the test pulls the resolved time out of the module row with
`Pattern.compile("\\d{1,2}:\\d{2}")` and asserts
`home.actionCards[0].description.de` equals `"Bis <that time> Uhr am Abreisetag"`.
A hand-check would have left REQ-967 asserted nowhere.

Three things I checked before accepting it:

- **Not flaky.** Both values are read from **one** `jsonPath` of **one** HTTP
  response, so no clock can move between them. The German `MEDIUM` date renders
  as `28.08.2026`, which contains no `\d{1,2}:\d{2}`, so the regex cannot latch
  onto the date instead of the time.
- **Not order-dependent.** The class runs under its own `@TestProfile` pointing
  Dev Services at database `gxp_devseed`, so it cannot collide with
  `GuestModuleContentTest`'s rows in the shared `%test` database — which also
  disposes of the `uq_info_module_row_order` collision I went looking for.
- **The file's own contract is respected.** `reseedingIsAFullNoOp` gained the
  matching `info_module_row` idempotence assertion; nothing else in the file
  moved. No later task touches it.

**Accepted, and it is the strongest single test in the cut.**

### 3.2 `MigrationTest` gained one `information_schema` assertion — **accept, the reasoning holds**

The file's own comment says the class shares the suite's database and has no
`@AfterEach`, so state assertions belong in the feature tests. A row-count
assertion would have been exactly that hazard: `GuestModuleContentTest` seeds
`info_module_row` in the same database, so *"the tables are empty"* would have
been order-dependent, and the class that ships the assertion would have been the
one broken by an unrelated test's `@BeforeEach`.

A `information_schema.tables` existence check is **DDL-level** — it cannot be
perturbed by any test's data — and it sits directly beside the file's existing
`information_schema.columns` assertion for `update_channel`, so it matches the
established style. The head literal moved `V11` → `V12` and the method was
renamed with it, as T-04 required. **Accepted.**

---

## 4. The queued `backend/CLAUDE.md` entry — my opinion only

**Not written, not duplicated, and no competing wording is offered here.**

**Is the fact worth recording? Yes, without reservation.** It has cost two green
builds shipping a wrong discriminant, the second by a route the spike did not
cover. It is not derivable from the contract, it is invisible in the generated
TypeScript in one of the two broken states (§1.1's middle row), and the next
`oneOf` will be written by somebody who was not here.

**Is dev's text correct? Substantively yes, with one imprecision.** It says the
two settings are needed *"on the openapi plugin"*, which reads as though both
apply to both sides. Measured (§1.1): `useOneOfInterfaces` is a **`generate-jaxrs`
`<configOptions>`** entry and affects **Java only**, while
`SIMPLIFY_ONEOF_ANYOF=false` sits at **plugin level precisely because both
executions need it** — Java for the interface, TypeScript for the alias. The
sentence about the build staying green is accurate; the pointer to
`GuestModuleContractTest` as an ordinary feature test rather than a sixth
architecture guard is accurate. Everything else is a matter for the owner.

*(One thing the entry could not have known, and which I would not add without the
owner either: on this tree the regression is now also a **compile error** — §1.4.
That is a property of `GuestControl`'s pattern switch, not of the generator, so a
future `oneOf` reached only through `Object` would not inherit it.)*

---

## 5. Requirement status at this cut

Phases 1–4 are the backend and the contract; Phases 5–8 are the kiosk. Only the
requirements those phases carry are assessed.

| REQ-ID | Status at T-10 | Evidence |
|---|---|---|
| **REQ-961** | ✅ **Complete** | Contract read (`oneOf`, one member, `discriminator.mapping`, plain-`string` `type`, no `enum`, `required` unchanged, no `modules[]`); generated interface + `@JsonTypeName("info")` read; discriminant `"info"` proved by serialisation including through the interface static type; `GuestModuleContractTest` proved red by hand |
| **REQ-962** | ✅ **Backend half** | `attachModules` walks only *delivered* items, so a disabled section structurally carries no module; `GuestModuleContentTest` cases (c) and (d). Frontend half (the deleted re-sort) is T-13/T-17 |
| **REQ-963** | ✅ **Contract half** | `rows: maxItems 10`; `ModuleText.maxLength: 240`; discriminator carved out **with its reason written next to it in the contract**. Kiosk enforcement is T-13/T-17. See 🟢 F-6: no server-side enforcement, correctly recorded |
| **REQ-964** | ✅ **Complete** | `resolveLocalized` reused unchanged; `GuestModuleContentTest` (a) asserts no `{`, and the check-out formatted per language in the property timezone |
| **REQ-965** | ⛔ **Declared exclusion (D16)** | Re-verified, not assumed: no boundary changed, no manager endpoint, no write outside the `%dev` seeder, `docs/backend/specification.md` unamended |
| **REQ-966** | ✅ **Complete for this cut** | Three clean regenerations byte-identical; `FILES` ↔ disk bidirectional; every model file maps to a live schema. T-22 repeats it |
| **REQ-967** | ✅ **Complete** | `CARDS[0]` now `"Bis {stay.checkOut\|time} Uhr am Abreisetag"`; `grep -rn "Bis 11:00" backend/app/src/main` empty; proved by `DevDataSeederTest` (green in isolation) |
| **REQ-968** | ✅ **Complete** | `V12` DDL only; entities in `de.gxp.menu.entity`, reads in `de.gxp.menu.control`; **five** guards untouched, exemption list unedited, no sixth guard |
| **REQ-969** | ✅ **Vacuous — knowingly** | Confirmed *knowingly* vacuous, not missed: `02-design.md` §2.3 and `04-analyze.md` **O-1** both declare it in advance. Footprint is exactly one word added to `NOT_A_MANAGER_MUTATION`; no other change to `AuditCoverageTest`; no manager mutation exists to audit |
| **REQ-971** | ◐ **Java half** | `GuestModuleContractTest` pins `Map.of("info", GuestInfoModule.class)` and was proved red. The `Object.hasOwn` half is T-12 |
| **REQ-975** | ✅ **Backend half** | `V12` ships **no seed rows**; `MigrationTest` pins the head and both tables; a payload without authored content carries no module (asserted for `umgebung`). The "`/info` renders as before" half is Phase 5/6 |
| **REQ-976** | ◐ **`%dev` half** | Two rows seeded verbatim from `info.md`'s copy table (checked word for word). The fabricated-type half is T-16 |
| REQ-970, 972, 973, 974, 977, 978, 979, 980 | ⏳ **Not in this cut** | Phases 5–8 |

Also re-verified as *knowingly* vacuous rather than quietly missed: **REQ-966's
orphan sweep** — `02-design.md` §10 and `04-analyze.md` **O-2** both state that
this Part removes and renames no schema, and the check was still run in both
directions (§1.5) precisely because a green build cannot tell "nothing found"
from "nothing looked".

---

## 6. Top 3 recommendations

1. **Fix F-1 before Phase 5** — one sentence in `resolveLocalized`'s javadoc that
   now contradicts §2.5, on the very function whose unchanged reuse is REQ-964's
   whole argument. It is the mirror image of the mistake `attachModules` was
   written to prevent.
2. **Read F-3 before writing T-12.** The wire carries `"module": null`, not an
   absent key. The design's `wire === undefined` guard would throw a `TypeError`
   through a branch that is explicitly forbidden a `try`/`catch`, turning a
   one-module drop into a blank screen. Use `== null`, matching the shipped
   mapper's `content.home ?? null`.
3. **Amend T-02 and T-22 for the second approved `pom.xml` line (F-2)**, so the
   final sweep checks what is actually there rather than a one-line rule that was
   superseded on the day it was written.

---

## 7. Environment note

Everything above was measured on the owner's Pi (3.8 GB, also running Immich and
Home Assistant), with `JAVA_HOME=/tmp/gxp-jdk/jdk-21.0.12+8` and Docker via
`sg docker`. Every probe was reverted and re-verified by digest: `api/openapi.yaml`
`40bd69e30868825aabbc7630926656fe`, `backend/app/pom.xml`
`871b274ad0ec247b0a5feb837e17e77d`, `packages/api-client/`
`90090e21b79278c06e4a789c623371dc`, and `git diff --stat` back to
**17 files changed, 559 insertions(+), 18 deletions(-)** — the tree is exactly as
the review found it. No container, Maven launcher or Quarkus process was left
running. **Nothing was committed.**

---
---

# Review — Round 2: Part C, Phases 5–8 (T-11 … T-22)

**Reviewed:** 2026-08-28 · branch `refactor/bce-subpackages-and-boundary-security` ·
baseline `HEAD = 7d56977` (Phases 1–4, reviewed PASS in round 1 above)
**Governing:** `docs/constitution.md` **v1.10.0**, `CLAUDE.md`, `frontend/CLAUDE.md`,
`backend/CLAUDE.md`, `docs/system-context.md`

# 🔍 Review: PASS ✅ — Part C is ready to commit

**Counts:** 🔴 0 · 🟠 0 · 🟡 6 · 🟢 6

Nothing found blocks the commit. The six 🟡 are documentation-accuracy and
convention-hygiene items; **two of them (F-11, F-12) are sentences in the PRD
and the design that the shipped code has made false**, which is the same class
of defect round 1 raised as F-1 and is the one worth fixing before the commit
rather than after.

**Step 0 (visual check) — performed, not skipped.** `/info` was rendered in a
real browser at 1280×832 light/de and 1024×768 dark/en, and `/umgebung` (the
placeholder arm) at 1280×832; screenshots captured and compared against
`docs/design/screens-inhouse/info.md`. See §2.6.

---

## 1. Numbers I measured myself

| Claim | dev | reviewer | Verdict |
|---|---|---|---|
| `ng test` | 333 / 333 | **333 passed, 37 files, 0 failed** | ✅ reproduced |
| new frontend cases | 22 (5+8+1+4+4) | 22 counted from the diff (8 mapper + 1 cache + 5 registry + 4 host + 4 info) → 311 + 22 = 333 | ✅ |
| sweep | 11 files, 42 sites, **41 physical insertions** | **41 added lines, 27 removed, across exactly those 11 files**, matching the per-file table row for row | ✅ |
| `measure.mjs --route /info` | 12 cells, 0 findings | **12 cells, 0 overflowing, 0 truncated, 0 findings** | ✅ reproduced |
| `measure-states.mjs` `/info` BANNER | 8 cells, 0 findings, both rows from cache | **8 cells, all `NO-SCROLL OK`, both rows rendered from cache in every one, no `!!` finding** | ✅ reproduced |
| clearance @1024 | **+0.219 px CLEARS** | **+0.219 px CLEARS** | ✅ exact |
| clearance @1280 | **+0.797 px CLEARS** | **+0.797 px CLEARS** | ✅ exact |
| panel box | 612×171 @1280, 490×137 @1024 | `{"w":612,"h":171}` / `{"w":490,"h":137}` | ✅ |
| `"module": null` on the wire | 12 of 13 items | **12 of 13 `null`, 0 with the key absent, `info` carrying `{"type":"info",…}`** — live off `quarkus:dev` | ✅ reproduced |
| `packages/api-client/` byte-identical | yes | a fresh `generate-sources` (inside `quarkus:dev`) left **`git status` clean** under it; `guestModule.ts` `md5 d74b5f987bfe5eae186c8f3c6264a386` | ✅ |
| backend 438 run / 0 failures, errors differing between runs | — | **not re-run** (instructed). See §5 — it cannot have moved | ✅ by construction |

**The backend suite could not have changed this session and that is checkable,
not assumed:** `git status --porcelain backend/app/src/test/` is **empty**. The
only backend file this cut touches is one javadoc block in `GuestControl`
(round 1's F-1). Round 1 independently reconstructed the full-green total as
**449** from surefire XML plus `SecondFactorLoginTest`'s six hidden cases; that
reconstruction still stands and nothing in Phases 5–8 can perturb it. The
differing error set across dev's two `verify` runs is the signature of
environmental flakiness, not of a test: **an error that is deterministic does
not change which class it lands on.** Not chased, per instruction.

---

## 2. The six declared deviations — judged

### 2.1 `mapInfoModule` / `localizedOrNull` in `module-registry.ts` — **accept**

**The circularity is real, and I checked it structurally rather than taking it
on report.** `02-design.md` §4.2 places `MODULE_REGISTRY = { info: { map:
mapInfoModule } }` in `module-registry.ts`; `03-todo.md` T-13 places
`mapInfoModule` in `guest-content.mapper.ts`; and T-13 also puts
`mapWireModule(MODULE_REGISTRY, …)` into the mapper's `mapMenuItem`. Those three
instructions cannot all be satisfied without `module-registry.ts` ↔
`guest-content.mapper.ts` importing each other. It would be the app's first
cycle (checked: none exists), and a cycle whose consumer is a **top-level
`const` initialiser** is a live TDZ hazard, not a stylistic one.

**Nothing else moved with it, and I verified that by reading the mapper's whole
diff rather than its summary.** `guest-content.mapper.ts` has exactly three
hunks: the `module-registry` import, the re-sort deletion, and two `module:`
lines. `localized()`, `requireText`, `requireRoute`, `safeIcon`, `mapBranding`,
the envelope throws and `MAX_MENU_ITEMS`/`MAX_CARDS` all stayed. §3.1's promise
— *"the mapper's per-type footprint is exactly one line"* — is met literally.

**Has the registry become the mapper? No — it has become the module mapper,
which is what a per-type registry is.** The split is clean on domain lines: the
envelope stays in `guest-content.mapper.ts`, modules live in
`module-registry.ts`. §3.2's rules are all present and each was read against the
code, not against the claim:

| §3.2 rule | Implementation |
|---|---|
| `rows` is an array | `Array.isArray(rows)` |
| `rows.length <= 10` | `MAX_INFO_ROWS`, drop at 11 |
| every row has `label` and `value` | `row?.['label']` → `localizedOrNull` → `null` on a non-object row, a `null` row, a string row |
| non-blank entry for the default language | `fallback === undefined || fallback.trim() === ''` |
| every string in every language `<= 240` | loops **all** entries, not just the default |
| returns, never throws | no `throw` anywhere on the module path |
| no `try`/`catch` | none |
| no placeholder-shaped logic | no `'{'`, no `RegExp`, no `match(` |
| no logging | no `console`, no logger, on any path |

**The one real cost, and it is a finding (F-13):** `localizedOrNull` now
re-states `localized()`'s default-language contract in a *second file*, with
nothing tying the two together.

### 2.2 The retargeted assertion — **accept; the requirement genuinely removed the behaviour**

**REQ-962 is unambiguous, and it is the requirement, not the code, that killed
the old assertion.** `01-prd.md` REQ-962: *"the kiosk … **never re-derives the
delivered order**"*, and its acceptance criterion: *"the delivered order is
preserved verbatim and `guest-content.mapper.ts` contains **no sort over
`menu.items`**."* `02-design.md` §3.3 deletes the re-sort by name. The old case
— *"orders menu items by sortIndex regardless of wire order"* — asserted
**exactly** the deleted behaviour. It could not survive, and retargeting it is
correct.

**And the new case would fail if the client re-sorted.** The fixture is
`home` `sortIndex: 0`, `info` `sortIndex: 1`; the case reverses the wire array to
`[info, home]` and expects **`['info','home']`**. A re-sort would produce
`['home','info']` and the assertion would fail. The test is not vacuous, and its
direction is the one the contract dictates: the comment quotes
`api/openapi.yaml:2616` *"Resolved-enabled items only, ordered by resolved sort
index."* — verified verbatim in the contract.

Two further things I checked before accepting a retarget: the fixture is
**unchanged** (same object, same `sortIndex` values — so the case is not passing
because the data was made agreeable), and `MenuItemShape` carries **no
`sortIndex`**, so the client structurally *cannot* re-derive the order. The
behaviour is removed, not merely untested.

### 2.3 The `@gxp/api-client` grep — **ruling below (F-10)**

The grep was already failing at `HEAD`, and this cut did not cause it: all four
files are present at `8b3856e`, three of them since `4459859` (Part B). **This
cut adds exactly one new importer, `core/guest/module-registry.ts`, which is
inside a permitted directory.** No violation is introduced here.

**But dev's characterisation is wrong for two of the four, and the rule as
written is violated.** `frontend/CLAUDE.md` says *"only `core/guest/` and
`core/api/` may import `@gxp/api-client` **models**"*. Mechanically:

| File | Imports | Under `model/`? |
|---|---|---|
| `core/preferences/http-preference-gateway.ts` | `GuestService` | no (`api/guest.service.ts`) |
| `core/preferences/http-preference-gateway.spec.ts` | `Configuration` | no (`configuration.ts`) |
| `features/pairing/pairing.service.ts` | `PairingService`, **`PairingCompleted`, `PairingRequestCreated`** | **yes — two DTO models** |
| `layout/staleness-banner/staleness-banner.component.ts` | **`OccupancyState`** | **yes — `model/occupancyState.ts`** |

So **three of the four import generated models**, and one of those is a
**component**, which also touches REQ-977's own wording. dev's summary —
*"import the generated service class or the `OccupancyState` enum, not a content
model"* — is accurate only for the two `http-preference-gateway` files.

**My ruling:** the rule's *letter* is breached by `pairing.service.ts` and
`staleness-banner.component.ts`; its *intent* (property content never reaches a
component as a wire model — the sentence ends *"components keep reading
`PropertyContentService`"*) is intact, because neither reads content: one is the
pre-content pairing lifecycle, the other compares an occupancy constant. **Not
this cut's defect and not a blocker.** It is 🟡 F-10, and closing it is
owner-gated either way (a `frontend/CLAUDE.md` amendment naming the carve-out,
or three refactors) — correctly not attempted here.

### 2.4 Nine over-length lines — **accept the decision, correct the number (F-14)**

The nine lines are real: **102, 104, 104, 106, 106, 108, 108, 108, 113 chars**,
against `printWidth: 100`. Prettier is genuinely unenforced — **no `format`
script, no lint script, no `.husky`, no active git hook, no `.github/workflows`.**
Exploding each fixture would indeed bury a one-token diff.

**But "9 files already differ at `HEAD`" is wrong: it is 13.** Measured with the
repo's own prettier against a clean `git archive` of `HEAD`. After the cut it is
**19** — six files newly non-conforming, and one of them,
`features/info-module/info-module.component.spec.ts`, is a **brand-new file**,
not a swept fixture, so the "don't explode a fixture" rationale does not cover
it (one 113-char `rows:` literal).

### 2.5 T-18's probe reverted with no residue — **confirmed**

`api/openapi.yaml` `md5 40bd69e30868825aabbc7630926656fe`; `git status` clean
under `api/` and `packages/api-client/`; `grep -rn zzProbe` finds nothing in
either generated tree; and a **fresh `generate-sources` I ran myself** (as part
of `quarkus:dev`) left `packages/api-client/` byte-identical — which is REQ-966
re-verified as a side effect. `Compiling 174 source files` reconciles with round
1's count (102 `src/main` + 72 generated), so no orphan survives in `target/`.

### 2.6 The vitest worker OOM — **accept, and it reproduces as a hazard, not a defect**

`ng test` ran green here in 19.3 s with 2.3 GB free. With `quarkus:dev`, a
Postgres container and Chromium up, available memory fell to **980 MB**. An OOM
worker exit under those conditions is the box, not the suite. 🟢 F-18.

---

## 3. The two compile-time guards — **re-run by me, both fail as claimed**

I did **not** perturb the repo tree. Instead I copied the *real*
`module-registry.ts`, the *real* generated `guestModule.ts` /
`guestInfoModule.ts` / `guestInfoRow.ts` and the *real* `property.model.ts` into
a scratch tree, stubbed only the two unrelated imports, and reproduced
`frontend/tsconfig.json`'s flags (`strict`, `noImplicitReturns`,
`noFallthroughCasesInSwitch`, `noImplicitOverride`,
`noPropertyAccessFromIndexSignature`). Baseline: **0 errors.**

**Probe A — REQ-980's `never` check, a second member added to the contract union:**

```
src/module-registry.ts(83,51): error TS2344: Type 'GuestWlanModule' does not
    satisfy the constraint 'never'.
src/module-registry.ts(185,30): error TS2339: Property 'rows' does not exist on
    type 'GuestModule'. Property 'rows' does not exist on type 'GuestWlanModule'.
```

Dev's recorded text, reproduced verbatim (line 83 vs. the real file's 82 is my
one-line import split), **including** the second error §4.2 predicted.

**Probe B — the renderer map's totality, both directions:**

```
# RENDERERS emptied
src/renderers.ts(6,3): error TS1360: Type '{}' does not satisfy the expected type
    'Record<"info", Type<unknown>>'.
    Property 'info' is missing in type '{}' …

# a renderer for a kind that does not exist
src/renderers.ts(7,3): error TS2353: Object literal may only specify known
    properties, and 'wlan' does not exist in type 'Record<"info", Type<unknown>>'.
```

`TS1360` is dev's exact error. The **reverse** direction (§4.4 claims it and
nobody proved it) also bites.

**Probe C — a fourth guard nobody claimed, and it is the strongest one.** Adding
a second kind to the *domain* union in `property.model.ts` and touching nothing
else fails the build **four times at once**, one per §4.4 touchpoint:

```
TS2741  MODULE_REGISTRY … Property 'wlan' is missing
TS2339  ModuleWireMap['wlan'] does not exist
TS2366  resolveModule: Function lacks ending return statement   ← the switch arm
TS1360  RENDERERS … Property 'wlan' is missing
```

**§4.4's "four touchpoints, three compiler-enforced and the fourth is REQ-980's
`never` check" understates it: all four are compiler-enforced, and I have seen
each of them fail.** These are not guards that cannot fail.

The generated tree was restored to `md5 d74b5f987bfe5eae186c8f3c6264a386` with
`git status` clean under `packages/api-client/`, and the scratch tree deleted.

---

## 4. The `info.md` corrections — dev corrected the right file, and both hold

### 4.1 The panel width: **612 / 489.6, not 614 / 491.6 — the design's arithmetic was wrong**

Derived independently: `--size-panel: 12.25rem`, `--space-4: 0.75rem` →
`3 × 12.25 + 2 × 0.75 = 38.25rem`. Root is `min(1.25vw, 1.9231vh)` = **16px** at
1280×832 and **12.8px** at 1024×768 → **612 px** and **489.6 px**.
`src/styles/global.css:34–37` sets `box-sizing: border-box` on `*`, `*::before`,
`*::after`, so `--border-glass`'s hairline is **inside** the declared width.
Measured: `panel {"w":612,"h":171}` and `{"w":490,"h":137}`.

**The right file was corrected.** The code implements the design's *intent*
(three action cards plus their two gaps — the `.settings__panel` derivation,
verbatim); it was the spec's `+2 px` that was wrong about this codebase. Fixing
the CSS to reach 614 would have been the wrong repair.

### 4.2 The 0.2 px overlap: **it does not occur, and nothing was moved to achieve that**

Reproduced exactly: **+0.219 px (CLEARS)** at 1024 and **+0.797 px (CLEARS)** at
1280, unchanged across all four theme × language cells at each size.

**Why the prediction was on the other side of zero, and it is not a moved
element.** The arithmetic took the banner's height from `connection.md` as
**h 60**; the real box is `h 59` (raw bottom **y117.53**, not y118) against a
panel top of **y117.75**. The predicted overlap was an artefact of a *rounded
input*, not of geometry. `layout/staleness-banner/` is **untouched by this cut**
(`git status` carries no path under it), and `info-module.component.css` sets no
offset — so nothing was moved, confirmed structurally rather than on report.

### 4.3 The instrument change is **sound**, not a way of making the number come out right

- It is **additive**: a new `reportInfoBanner()` and a new cell. `box()` and the
  existing `reportBanner()` are unchanged, so no previously reported figure moves.
- `b()` already returned the **raw** `DOMRect` (`getBoundingClientRect().toJSON()`);
  `box()` only rounds *for display*. Computing `panel.y - banner.bottom` from the
  raw values is the only way a 0.2 px figure can be seen at all — rounding was
  hiding it, which is precisely how the wrong prediction survived.
- It **adds failure conditions rather than removing them**: a `COLLISION` note
  when a value's line rect actually reaches the bar's band, a `CACHE` note when
  fewer than two rows render from cache, and it keeps the tri-state pill check.
  An instrument tuned to produce a pass does not grow new ways to fail.
- It measures the *rendered extent* of the values via `Range.getClientRects()`
  rather than the `<dd>` box — which is the honest number for the argument
  `info.md` reason 3 rests on, since the `<dd>` is always the panel's full inner
  width.

**Accepted.** The horizontal slack reproduces at **143.05 / 199.05 px** (1024)
and **179 / 252 px** (1280).

---

## 5. Everything the brief asked me to also confirm

| Item | Result |
|---|---|
| REQ-972 per-module isolation | ✅ code + spec. One malformed module → `null`; the other item, `home` and `settings` intact; `mapGuestContent` does not throw. Only the envelope throws (`MAX_MENU_ITEMS`, missing `settingsEntry`, `localized()`) |
| No user-visible string in a component/template | ✅ both new templates carry **no** text node, `aria-*`, `alt`, `title` or `placeholder`. No new translation key; `core/i18n/` untouched |
| Components never import `@gxp/api-client` | ✅ **for this cut** — the one new importer is `core/guest/module-registry.ts`. Pre-existing outliers: see F-10 |
| `info` has no empty state, deliberately (D19) | ✅ stated in `InfoModuleComponent`'s javadoc with its reason (row 1 is a stay fact always present under `OCCUPIED`; `/info` renders under nothing else) |
| Unresolved-placeholder fallback does nothing (D18) | ✅ no `'{'`, no `RegExp`, no `match(`, no `includes(` on the module path in either file |
| Fabricated type only in a test (D17) | ✅ `grep -rn zeitkapsel` → **5 hits, all in `module-registry.spec.ts`**, plus one prose mention in `03-todo.md` |
| No manager write path (D16) | ✅ `git diff 8b3856e -- backend/app/src/main/java/de/gxp/*/boundary/` is **empty** — no Boundary touched by Part C at all |
| Five architecture guards, no sixth | ✅ `git diff 8b3856e -- .../de/gxp/architecture/` **empty**; five guards + the two long-standing helpers |
| No `pom.xml` change in this cut | ✅ `git status` carries no `pom.xml`; vs. `8b3856e` it is the 10 approved lines from `7d56977` and nothing else. `package.json` / lockfile untouched |
| Queued `backend/CLAUDE.md` entry still unwritten | ✅ `backend/CLAUDE.md`, `frontend/CLAUDE.md`, `CLAUDE.md` and `docs/constitution.md` are all **unmodified** |
| `MenuItemShape.module` required, with the reason on the field | ✅ `property.model.ts:113–120`, and the reasoning is true — an optional field would put `undefined \| null` at every read site |
| The refinement applied as a property in four places + REQ-974 | ✅ `03-todo.md` T-14 (l.327) and T-22 (l.540), `02-design.md` §6.2 (l.714) and §12.4 (l.991), `01-prd.md` REQ-974 (l.587). **No "except for `module: null`" wording survives anywhere** |
| `SectionPlaceholderComponent` behaviourally unmodified | ✅ source **byte-identical** to `8b3856e`; spec diff is exactly 4 `module: null` tokens; `it(` 8 → 8, `expect(` 12 → 12 |
| F-3 closed in code, design and reality | ✅ all three — `wire == null` guard + a spec case pinning both; §4.2 corrected with the measurement quoted; and **measured live here**: 12 of 13 items carry `"module": null`, none omits the key |
| T-13's bound comment accurate | ✅ all four claims verified on the generated tree: `@Size(max=10)` present on `getRows()`; `GuestApi.getGuestContent`'s return carries **no `@Valid`**; `GuestInfoRow.label/value` are bare `Map<String,String>`; `V12`'s `label`/`value` are unbounded `text`. `MAX_MENU_ITEMS = 50` precedent is real |

### 5.1 The sweep, checked literally

Every one of the 41 added lines is the removed line **plus `module: null`**, or a
new `module: null,` line inside a multi-line literal. **No added or removed line
in any of the 11 files contains `expect`, `it(`, `describe(`, `toBe`, `toEqual`
or `beforeEach`** — checked mechanically, which is the only way to see a moved
assertion hiding in a 41-token diff. Per-file counts match dev's table exactly
(12/6/6/4/3/3/2/2/1/1/1 = 41).

### 5.2 Security

No sensitive data logged (nothing logs at all on the module path). No
string-concatenated query. No new endpoint or handler, so no authorization gap.
No secret in shipped code. No external runtime request. `overflow-wrap: anywhere`
and `{{ }}` interpolation only — no `innerHTML`. The WiFi password reaching
`localStorage` is **explicitly permitted** by constitution v1.9.0 and is asserted
to survive caching intact rather than be stripped; the cache-credential
assertions (device token literal, `bearer`, `authorization`) are unchanged and
still carry **no** name-based `password`/`code` filter, per Part B's warning.
Work is on a feature branch and nothing is committed.

### 5.3 Step 0 — the visual check, performed

Production bundle (verified newer than every file under `frontend/src`) served on
`:4300` with `/api` proxied to `quarkus:dev`.

- **`/info` 1280×832 light de** — `<h1>` "Info" at the `.settings` baseline, one
  glass panel `612×171` at x172/y147, two stacked rows, label secondary/14 px
  over value primary/18 px, `--space-6` between rows, **no divider, no icon, no
  focusable element**, status-bar corner clear. Matches `info.md` → Layout / The
  row / Content, row for row and token for token.
- **`/info` 1024×768 dark en** — the same layout at root 12.8 px, English copy
  (`Wi-Fi password`, `27 Sept 2026, 16:37`), dark glass recipe, `NO-SCROLL OK`.
- **`/umgebung` 1280×832** — the placeholder arm renders **unchanged**: centred
  `Umgebung` / `Bald verfügbar.`, sidebar entry intact. REQ-972/973/974's landing
  place confirmed visually, not only in jsdom.

---

## 6. Findings

| # | Sev | Category | File | Finding | Fix |
|---|-----|----------|------|---------|-----|
| F-10 | 🟡 | Conventions (pre-existing) | `features/pairing/pairing.service.ts`, `layout/staleness-banner/staleness-banner.component.ts` | **The `@gxp/api-client` rule *is* breached by the letter, and dev's reason is wrong for two of the four files.** `PairingCompleted` / `PairingRequestCreated` / `OccupancyState` all live under `packages/api-client/model/` — they are generated **models**, not service classes, and one importer is a **component**. Intent is intact (neither reads property content) and **all four predate this cut**; this Part's only new importer is inside `core/guest/`. But T-13's and T-22's validate lines still read *"still lists only `core/guest/` and `core/api/`"* and were ticked against a check that has been failing since Part B — the same stale-check pattern round 1 raised as F-2 | Owner call, not dev's: either amend `frontend/CLAUDE.md` to name the carve-out (a generated **enum/lifecycle DTO** outside the content path) or move the three imports. Until then, amend the two validate lines so they check what the rule actually says |
| F-11 | 🟡 | Correctness (docs) | `02-design.md` §3.2 | The relocation was recorded in `03-todo.md` T-12/T-13 and the handoff but **not in the design**, which still says *"For `info` **the mapper** checks: `rows` is an array; …"* and *"The module path uses null-returning helpers (`localizedOrNull`) **beside the existing throwing `localized()`**"*. The second sentence is now false in the way that matters: they are in different files, and "beside" was the whole argument for why the pair stays consistent | Two sentences. Say the registry hand-validates and that `localizedOrNull` is `localized()`'s **sibling in another file** — which is exactly what makes F-13 a risk worth naming |
| F-12 | 🟡 | Correctness (docs) | `01-prd.md` REQ-977, D15 note | Two claims the cut made false. (a) *"`MenuItemShape<T>` … gains one **optional** field"* — the owner's 2026-08-28 decision made it **required**, and REQ-974's criterion was amended for exactly that while this note was not. (b) *"the mapper gains one branch; **nothing else in the app sees a generated model**"* — `core/guest/module-registry.ts` now does (permitted, but the sentence is false). Also *"the per-type branch lands in the menu mapping of that same one file"* — it landed in the registry | Amend the note inline, the way REQ-974's was. This is round 1's F-1 repeating: a sentence left standing that the change contradicts |
| F-13 | 🟡 | Maintainability | `module-registry.ts:210` vs. `guest-content.mapper.ts:238` | `localizedOrNull` re-states `localized()`'s **default-language contract** (present, non-blank, `trim()`) in a second file, and **nothing ties them together** — no shared helper, no test asserting they agree, no comment on `localized()` pointing back. A change to one is invisible from the other. This is the price of 2.1's relocation and it is worth writing down rather than discovering | Cheapest honest fix: one sentence on `localized()` naming its null-returning sibling and the file it is in. A shared core would have to thread throw-vs-return through, which is worse |
| F-14 | 🟡 | Style | 9 swept fixture lines + `info-module.component.spec.ts:86` | The over-length decision is accepted — prettier is genuinely unenforced (no script, no hook, no CI). But **"9 files already differ at `HEAD`" is 13**, measured against a clean archive of `HEAD`; the cut takes it to **19**, and one of the six newly non-conforming files is a **new** file, not a swept fixture, so the "don't explode a fixture" rationale does not cover its single 113-char line | Reformat that one line in `info-module.component.spec.ts` (prettier's own output is 5 lines, no assertion touched). Leave the nine fixtures. Correct the count in the handoff |
| F-15 | 🟡 | Accuracy (design record) | `docs/design/screens-inhouse/info.md`, panel-size row | The corrected row mixes a **rounded measurement** with **exact arithmetic** in one paragraph: the table says **490.0** at 1024 while the prose three lines below says **489.6 px at 1024** (which is the correct derivation — `38.25rem × 12.8`). Same shape in the row below: `y254.8 → measured y255`, `y318.0 → measured y318`. The next person re-deriving from this table will get a different number than the one printed in it | Write `489.6` in the table (the instrument's `490` is `box()` rounding, and the file's own convention is exact values to one decimal) |
| F-16 | 🟢 | Accuracy (design record) | `info.md`, the new banner table | Three figures are **`%dev`-timestamp-dependent**: the check-out value is `now`-derived, so `rightmost value x316.78` / `gap 142.05 px` came from a run seeded at 13:55. My run (seeded 16:37) measures **x315.78 / 143.05**. The *conclusion* — clears at both sizes, 143–252 px of horizontal slack — is robust; the digits are not reproducible | None required. Worth one clause noting the row's width follows the seeded check-out string |
| F-17 | 🟢 | Correctness (theoretical) | `module-registry.ts:216` | `entry.length` counts **UTF-16 code units**; OpenAPI's `maxLength` counts **code points**. A 240-code-point value containing astral characters (emoji) would be rejected by the kiosk though the contract permits it. Nothing enforces the bound server-side anyway (the comment says so), so there is no disagreement in practice | None. Named so it is not rediscovered as a bug |
| F-18 | 🟢 | Testing (environment) | `guest-data.service.spec.ts` nested tables | `GuestInfoRow` is the **third** level and has no table — a field added to it (a row id, a `sensitive` flag) passes the guard silently. Correctly disclosed in both the code comment and §7 as a known gap, not discovered here | None now. One more five-line table when it bites |
| F-19 | 🟢 | Testing (environment) | the box | The vitest OOM reproduces as a condition, not a defect: `ng test` is 19.3 s and green with 2.3 GB free; with `quarkus:dev` + Postgres + Chromium up, available memory fell to **980 MB**. Run the suites serially on this hardware | None |
| F-20 | 🟢 | Design (forward) | `property-content.service.ts:151` | `resolveProperty` writes `module: null` on the settings entry unconditionally. Correct and commented (D15 — `GuestSettingsEntry` carries no module), but it would **silently swallow** one if the contract ever grew it | None. The comment already names the reason |
| F-21 | 🟢 | Accuracy (docs) | `handoff.md:525` | The archived planner section still says `SectionPlaceholderComponent`'s *"existing spec must pass unchanged"*, superseded by the property wording above it | None — it is below the fold and marked superseded |

---

## 7. Requirement status — Part C complete

| REQ-ID | Status | Evidence (this round unless noted) |
|---|---|---|
| REQ-961, 964, 966, 967, 968, 969, 975 | ✅ | Round 1. REQ-966 re-confirmed here by a fresh `generate-sources` leaving the client byte-identical |
| **REQ-962** | ✅ **Complete** | Re-sort deleted with the contract line quoted; `grep "sort("` → no match; `MenuItemShape` carries no `sortIndex`; the retargeted case fails if a re-sort returns (§2.2) |
| **REQ-963** | ✅ **Complete** | 11 rows dropped, 241 chars dropped, 10 × 240 mapped — all three asserted; bounds live where they are the only enforcement, with the inertness of both server-side bounds verified on the generated tree |
| REQ-965 | ⛔ **Declared exclusion (D16)** | Re-verified structurally: **no Boundary file changed at all** vs. `8b3856e` |
| **REQ-970** | ✅ **Complete** | Registry in `core/guest/`; renderer map in the host, total by `satisfies`, **proved red in both directions** (§3) |
| **REQ-971** | ✅ **Complete** | `Object.hasOwn(registry, …)` is the validation set — no second list. Registry passed as a parameter, exercised by the fabricated type |
| **REQ-972** | ✅ **Complete** | Isolation asserted at the mapper and the registry; the drop quotes nothing and logs nothing (a 6-level `console` spy over 4 drop paths); visually confirmed as `section.comingSoon` |
| **REQ-973** | ✅ **Complete** | Unknown discriminant → `null` → placeholder, at both levels; the `/umgebung` render is the same arm |
| **REQ-974** | ✅ **Complete** | `SectionHostComponent` at `:sectionKey` and `**`, **no `{ path: 'info' }`**, `einstellungen` still first; placeholder demoted and behaviourally unmodified (source byte-identical) |
| **REQ-976** | ✅ **Complete** | `%dev` serves one authored `info` module (measured live); the fabricated second type exists **only** in `module-registry.spec.ts` |
| **REQ-977** | ✅ **Complete** | The domain union is a real discriminated union; components read resolved shapes only; validation does not lean on the types. *(Its PRD note needs the amendment in F-12 — the requirement is met, the note is stale)* |
| **REQ-978** | ✅ **Complete** | 12 route cells + 8 banner cells, **0 findings**, re-measured here; both `info.md` figures corrected in the design record |
| **REQ-979** | ✅ **Complete** | Two nested compile-time tables + a runtime assertion reaching explicitly into `menu.items[].module`; a cache-render case in jsdom **and** both rows rendered from cache in all 8 browser cells |
| **REQ-980** | ✅ **Complete** | `AssertNever<Exclude<GuestModule, RegisteredWire>>`, **driven red by me**, with its `Exclude`-assignability limitation recorded on the type |

**19/19 in-scope requirements complete. 1 declared exclusion (REQ-965, D16).**

---

## 8. Top 3 recommendations

1. **Fix F-11 and F-12 before the commit.** Four sentences across `02-design.md`
   §3.2 and `01-prd.md` REQ-977 now describe a layout the code does not have —
   *"the mapper checks"*, *"beside the existing `localized()`"*, *"one **optional**
   field"*, *"nothing else in the app sees a generated model"*. This is round 1's
   F-1 recurring: the change was recorded in the TODO and the handoff and not in
   the two documents that get quoted forward.
2. **Put F-10 to the owner as a question, not a fix.** The import rule is
   breached by its letter in two pre-existing files, one of them a component —
   and the two validate lines that were supposed to catch it have been ticked
   against a failing grep since Part B. Either the rule gains a carve-out or
   three imports move; both are owner-gated, and neither belongs in this commit.
3. **Add one sentence to `localized()` naming `localizedOrNull` and its file
   (F-13), and reflow the one 113-char line in the new spec (F-14).** Both are
   a minute's work and both close a drift the relocation created.

---

## 9. Environment note

Measured on the owner's Pi (3.8 GB, alongside Immich and Home Assistant),
`JAVA_HOME=/tmp/gxp-jdk/jdk-21.0.12+8`, Docker via `sg docker`. Every probe was
run outside the repo tree or reverted and re-verified by digest:
`packages/api-client/model/guestModule.ts` `md5 d74b5f987bfe5eae186c8f3c6264a386`,
`git status` clean under `packages/api-client/` and `api/`, and `git diff --stat`
back to **26 files changed, 926 insertions(+), 98 deletions(-)** plus the four
untracked paths — the tree is exactly as this review found it. Quarkus, the
Maven launcher, the static server, the Postgres container and the
Testcontainers reaper were all stopped and verified gone. **Nothing was
committed and nothing was fixed.**
