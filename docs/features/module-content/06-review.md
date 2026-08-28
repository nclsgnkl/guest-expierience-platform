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
