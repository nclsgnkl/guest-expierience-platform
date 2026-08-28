# D9 generator spike — `oneOf` + `discriminator` through `jaxrs-spec` and `typescript-angular`

**Status:** evidence only. **This document does not answer D9** — it exists so
the owner can answer it, and so nobody re-runs the experiment.
**Run:** 2026-08-28, branch `refactor/bce-subpackages-and-boundary-security`,
tree at `8bfb6df`. **Everything was reverted; `git status --porcelain` is empty.**

---

## The one-sentence answer

**Yes, but only with a one-line `pom.xml` config flag
(`useOneOfInterfaces=true`, no new dependency), and only if the per-member
`type` is a plain `string` rather than a single-value `enum` — and that choice
costs the TypeScript side its automatic `switch (m.type)` narrowing, which the
mapper buys back with one 1-line type predicate per module type.**
Without that flag, `jaxrs-spec` emits something **actively broken**: not an
`Object`, not a compile failure — a class that compiles, serialises the wrong
discriminant, and throws at deserialisation.

---

## What was run

| | |
|---|---|
| Generator | `openapi-generator-maven-plugin` **7.24.0** (unchanged) |
| Java side | `jaxrs-spec`, `interfaceOnly=true`, `useJakartaEe=true`, `dateLibrary=java8`, `openApiNullable=false`, `returnResponse=false`, `useTags=true`, `useSwaggerAnnotations=false` — i.e. exactly `backend/app/pom.xml` |
| TS side | `typescript-angular`, `hideGenerationTimestamp=true` — exactly `backend/app/pom.xml` |
| Command | `JAVA_HOME=/tmp/gxp-jdk/jdk-21.0.12+8 ./mvnw -o generate-sources` from `backend/app/`. **`generate-sources`, not `verify`** — both generator executions are bound to that phase, so it regenerates both artefacts and is equivalent for this question. `./mvnw -o compile` was run additionally to prove the generated Java compiles; the full `./mvnw verify` was run only on the reverted tree, to confirm the baseline |
| `pom.xml` | **never modified.** The `useOneOfInterfaces=true` case was produced by a throwaway harness in `/tmp` (`CodegenConfigurator` + `DefaultGenerator`) configured with the identical option set. It was checked against the Maven output first: with the flag off, its `SpikeModule.java`, `SpikeTextModule.java`, `SpikeListModule.java` and `GuestContent.java` are **byte-identical to Maven's, modulo the `@Generated` timestamp line** |

Three contract shapes were tried. All wired into the existing `GuestContent`
as an optional `spikeModules` array (`maxItems: 20`) — no new endpoint.

---

## Variant A — `oneOf` + `discriminator`, member `type` as a single-value `enum`

This is REQ-961 read literally, and it is the shape you would write by instinct.

```yaml
    SpikeModule:
      oneOf:
        - $ref: "#/components/schemas/SpikeTextModule"
        - $ref: "#/components/schemas/SpikeListModule"
      discriminator:
        propertyName: type
        mapping:
          text: "#/components/schemas/SpikeTextModule"
          list: "#/components/schemas/SpikeListModule"
    SpikeTextModule:
      type: object
      required: [type, body]
      properties:
        type: { type: string, enum: [text] }
        body: { $ref: "#/components/schemas/LocalizedText" }
    SpikeListModule:
      type: object
      required: [type, entries, columns]
      properties:
        type: { type: string, enum: [list] }
        entries:
          type: array
          maxItems: 10
          items: { type: string, maxLength: 120 }
        columns: { type: integer }
```

### TypeScript — perfect

```ts
// packages/api-client/model/spikeModule.ts   (verbatim, trimmed to the body)
export type SpikeModule = SpikeListModule | SpikeTextModule;
```
```ts
// packages/api-client/model/spikeTextModule.ts   (verbatim, trimmed)
export interface SpikeTextModule {
    type: SpikeTextModule.TypeEnum;
    body: { [key: string]: string; };
}
export namespace SpikeTextModule {
    export const TypeEnum = { Text: 'text' } as const;
    export type TypeEnum = typeof TypeEnum[keyof typeof TypeEnum];
}
```

**Narrowing proof.** File typechecked with
`tsc --ignoreConfig --noEmit --strict --noImplicitReturns --target ES2022 --module preserve --moduleResolution bundler`:

```ts
import { SpikeModule } from './packages/api-client/model/spikeModule';

export function render(m: SpikeModule): string {
  switch (m.type) {
    case 'text': return Object.keys(m.body).join(',');
    case 'list': return `${m.entries.length}/${m.columns}`;
  }
}
```
→ **`exit=0`, no cast, no `default` branch needed.** `--noImplicitReturns`
passing proves TypeScript also considers the switch **exhaustive** — adding a
third member later turns every such switch into a compile error, which is the
forward-compatibility guard REQ-972/973 would otherwise have to hand-write.

Negative control (same flags, must fail):
```ts
export function wrong(m: SpikeModule): number { return m.entries.length; }
```
```
error TS2339: Property 'entries' does not exist on type 'SpikeModule'.
  Property 'entries' does not exist on type 'SpikeTextModule'.
```
→ the narrowing is real, not `any`.

### Java — silently broken

`jaxrs-spec` did **not** emit `Object` and did **not** fail the build. It
emitted a **concrete class that flattens every member's fields together**, and
two member classes **unrelated to it**:

```java
// target/.../de/gxp/api/model/SpikeModule.java   (verbatim, trimmed)
@JsonIgnoreProperties(value = "type", allowSetters = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = SpikeListModule.class, name = "list"),
  @JsonSubTypes.Type(value = SpikeTextModule.class, name = "text"),
})
@JsonTypeName("SpikeModule")
public class SpikeModule   {
  public enum TypeEnum { TEXT(String.valueOf("text")), LIST(String.valueOf("list")); ... }

  private TypeEnum type;
  private @Valid Map<String, String> body = new HashMap<>();
  private @Valid List<@Size(max = 120)String> entries = new ArrayList<>();
  private Integer columns;

  @JsonCreator
  public SpikeModule(
    @JsonProperty(required = true, value = "type") TypeEnum type,
    @JsonProperty(required = true, value = "body") Map<String, String> body,
    @JsonProperty(required = true, value = "entries") List< @Size(max=120)String> entries,
    @JsonProperty(required = true, value = "columns") Integer columns
  ) { ... }
```
```java
// SpikeTextModule.java   (verbatim, trimmed)  — note: implements nothing
public class SpikeTextModule   {
  public enum TypeEnum { TEXT(String.valueOf("text")); ... }
  private TypeEnum type;
  private @Valid Map<String, String> body = new HashMap<>();
```

`./mvnw -o compile` **succeeds** on this. It is wrong at runtime. Probe
(compiled against `target/classes` + the project runtime classpath), verbatim
output:

```
SpikeModule superclass  : class java.lang.Object
SpikeModule interfaces  : []
TextModule interfaces   : []
Text assignable to union: false
declared fields on union: [type, body, entries, columns]
SERIALIZED union        : {"type":"SpikeModule","body":{"de":"hallo"},"entries":["a"],"columns":2}
DESERIALIZE FAILED      : com.fasterxml.jackson.databind.exc.InvalidTypeIdException:
    Could not resolve type id 'text' as a subtype of `de.gxp.api.model.SpikeModule`:
    Class `de.gxp.api.model.SpikeTextModule` not subtype of `de.gxp.api.model.SpikeModule`
GuestContent spikeModules: "spikeModules":[{"type":"SpikeModule","body":{"de":"hallo"},"entries":["a"...
```

Three separate defects, and the first two are the dangerous kind:

1. **A Control cannot return a member as the union.** `SpikeModule` is not a
   supertype of `SpikeTextModule` (`isAssignableFrom = false`). The only thing
   a Control can construct is the flattened class, whose `@NotNull` fields are
   the union of *all* members — so "a text module" must also carry `entries`
   and `columns`. The union does not exist in Java; A3's "fixed shape per type"
   is not represented at all.
2. **The discriminant is destroyed on the way out.** `@JsonTypeName("SpikeModule")`
   makes Jackson write `"type":"SpikeModule"` instead of `"text"`. The kiosk
   receives a value that is not in the contract's `mapping` and that no
   `switch` can match — and nothing on the Java side fails while this happens.
3. **A legitimate wire value fails to parse.** `{"type":"text",...}` throws
   `InvalidTypeIdException`. Only matters if the union is ever accepted on a
   request; today it would only be produced.

**Verdict on Variant A: unusable on the Java side, and unusable in the worst
way — it compiles and ships wrong bytes.**

---

## The fix: `useOneOfInterfaces=true`

`jaxrs-spec` 7.24.0 **does** have the right knob. Dumped from the generator's
own `cliOptions()`:

```
useOneOfInterfaces = false  :: whether to use a java interface to describe a set of
                               oneOf options, where each option is a class that
                               implements the interface
```

**It is a `<configOptions>` line, not a dependency.** The spike explicitly
checked for the dependency risk the PRD flagged: nothing asks for
`jackson-databind-nullable` or any other artifact. The full diff of the
generated Java tree between flag-off and flag-on is **only the three `Spike*`
files** plus one line (`x-one-of-name: SpikeModule`) in a spec copy the pom
does not emit (`generateSupportingFiles=false`). So the blast radius on the
existing contract is zero — there is no other `oneOf` in `api/openapi.yaml`.

With the flag on, Variant A's contract still **fails to compile**:

```
SpikeListModule.java:24: error: SpikeListModule is not abstract and does not
    override abstract method getType() in SpikeModule
SpikeListModule.java:99: error: getType() in SpikeListModule cannot implement
    getType() in SpikeModule
  return type @jakarta.validation.constraints.NotNull TypeEnum is not compatible with String
(4 errors)
```

The generated interface declares `String getType()`, but a member whose `type`
is an `enum` generates `TypeEnum getType()`. **The single-value `enum` on the
member is what breaks it.** Drop it → Variant B.

---

## Variant B — `oneOf` + `discriminator`, member `type` as plain `string` ✅ Java

Identical to Variant A except `type: { type: string }` on both members (the
`discriminator.mapping` still pins the allowed values in the contract, and
`@JsonTypeName` still pins them in Java).

### Java — correct, with the flag on

```java
// SpikeModule.java   (verbatim, complete)
@JsonIgnoreProperties(value = "type", allowSetters = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = SpikeListModule.class, name = "list"),
  @JsonSubTypes.Type(value = SpikeTextModule.class, name = "text"),
})
public interface SpikeModule {
    String getType();
}
```
```java
// SpikeTextModule.java   (verbatim, trimmed)
@JsonTypeName("text")
public class SpikeTextModule  implements SpikeModule {
  private String type;
  private @Valid Map<String, String> body = new HashMap<>();
```

`javac` on the whole generated tree: **exit 0.** Runtime probe, verbatim:

```
union is an interface   : true
member implements union : true
SERIALIZED member       : {"type":"text","body":{"de":"hallo"}}
DESERIALIZED            : SpikeListModule -> class SpikeListModule { type: list  entries: [a, b]  columns: 2 }
JAVA SWITCH RESULT      : list:[a, b]/2
GuestContent spikeModules: "spikeModules":[{"type":"text","body":{"de":"hallo"}},{"type":"list","entries":["a","b"...
```

Everything the Part needs works: a Control writes
`SpikeModule m = new SpikeTextModule().type("text").body(...)` with no cast,
each member keeps **only its own** fields (so A3's fixed shape and REQ-963's
`maxItems`/`maxLength` land on the right class), the correct discriminant goes
on the wire, and Java 21 pattern-matching `switch` over the members works:

```java
String rendered = switch (module) {
  case SpikeTextModule t -> ...;
  case SpikeListModule l -> ...;
  default -> "unknown";
};
```

### TypeScript — the union survives, the automatic narrowing does not

```ts
export type SpikeModule = SpikeListModule | SpikeTextModule;   // still a real union
export interface SpikeTextModule { type: string; body: { [key: string]: string; }; }
```

`switch (m.type)` now **fails** to narrow (`type` is `string`, not `'text'`):

```
error TS2366: Function lacks ending return statement ...
error TS2339: Property 'body' does not exist on type 'SpikeModule'.
error TS2339: Property 'entries' does not exist on type 'SpikeModule'.
error TS2339: Property 'columns' does not exist on type 'SpikeModule'.
```

**Cast-free narrowing is still available**, two ways — both typechecked under
the same strict flags, **`exit=0`**:

```ts
// (b1) one 1-line predicate per module type, keyed on the discriminant
const isText = (m: SpikeModule): m is SpikeTextModule => m.type === 'text';
const isList = (m: SpikeModule): m is SpikeListModule => m.type === 'list';
if (isText(m)) { /* m.body is typed here */ }

// (b2) structural narrowing, no extra declarations at all
if ('body' in m) { /* m.body is typed here */ }
```

**How much this actually costs is small, and it is worth being precise about
it.** Per `frontend/CLAUDE.md` and REQ-977, nothing outside
`core/guest/` and `core/api/` may import `@gxp/api-client`; the only consumer
of these wire types would be `guest-content.mapper.ts`, which already
hand-validates every field and throws `GuestContentMappingError`. So the lost
narrowing is lost in exactly one file, which was going to write a per-type
branch anyway (REQ-970/971 put that branch in the module registry). Components
narrow over the hand-written *domain* model, which is unaffected.

**The two real losses:**

- **Exhaustiveness.** Variant A's `switch` becomes a compile error when a
  member is added; Variant B's predicate chain does not. That guard has to be
  written by hand (a `never` check in the registry) or it is not there.
- **Soundness.** `m is SpikeTextModule` is an *assertion* TypeScript trusts. It
  is only true because the backend put the right fields next to the right
  `type` — which the mapper validates anyway, but the type system is no longer
  the thing proving it.

---

## Variant C — `allOf` inheritance instead of `oneOf` (the classic fallback)

Base schema carries `type` + `discriminator`; each member is
`allOf: [$ref base, {own properties}]`. **Requires no `pom.xml` change at all.**

```java
// SpikeModule.java   (verbatim, trimmed) — a concrete, non-abstract base class
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = SpikeListModule.class, name = "list"),
                @JsonSubTypes.Type(value = SpikeTextModule.class, name = "text") })
@JsonTypeName("SpikeModule")
public class SpikeModule   { private String type; ... }

// SpikeTextModule.java   (verbatim, trimmed)
@JsonTypeName("text")
public class SpikeTextModule extends SpikeModule  { private @Valid Map<String, String> body = ...; }
```

`javac`: **exit 0.** Runtime probe, verbatim:

```
union is interface      : false  (abstract: false)
member extends union    : true
SERIALIZED member       : {"type":"text","body":{"de":"hallo"}}
DESERIALIZED            : SpikeListModule
JAVA SWITCH RESULT      : list:[a]
BASE instantiable?      : {"type":"SpikeModule"}
```

Java works. **TypeScript is strictly worse than Variant B** — there is no union
type at all:

```ts
export interface SpikeModule { type: string; }
export interface SpikeTextModule extends SpikeModule { body: { [key: string]: string; }; }
// GuestContent:  spikeModules?: Array<SpikeModule>;
```

so the mapper sees `{ type: string }` and **must cast**. Typechecked, verbatim:

```ts
if (m.type === 'text') { return Object.keys(m.body).join(','); }
//                                            ^ error TS2339: Property 'body' does not exist on type 'SpikeModule'
if (m.type === 'text') { return Object.keys((m as SpikeTextModule).body).join(','); }   // compiles
```

Second cost, visible in the probe's last line: the base class is **concrete and
instantiable**, and serialises as `{"type":"SpikeModule"}`. Nothing prevents a
Control from putting a typeless module on the wire.

---

## The three options, side by side

| | Contract | `pom.xml` | Java | TypeScript |
|---|---|---|---|---|
| **A** `oneOf`, member `type` as `enum` | REQ-961 read literally | unchanged | ❌ **broken and silent** — flattened class, wrong discriminant on the wire, `InvalidTypeIdException` on read. With the flag on: **compile error** | ✅ perfect: real union, `switch (m.type)` narrows, exhaustiveness enforced |
| **B** `oneOf`, member `type` as `string` | REQ-961, one word different | **+1 line** `<useOneOfInterfaces>true</useOneOfInterfaces>` — **no new dependency** | ✅ interface + `implements`, correct discriminant, per-member shapes, Java `switch` patterns | ⚠️ union exists, `switch` does not narrow; **cast-free** via one 1-line predicate per type in the mapper. No exhaustiveness guard |
| **C** `allOf` inheritance | not `oneOf`; REQ-961 would need rewording | unchanged | ✅ base class + `extends`, correct discriminant — but the base is concrete and instantiable | ❌ no union; the mapper **must cast**. Strictly worse than B |
| *(D)* free-form key/value bag | — | unchanged | no generator risk | ❌ cannot be boundary-validated (A3); every renderer is a cast |

---

## What this changes about planning Part C

- **REQ-961 needs one word added if B is chosen:** the member's `type` is a
  plain `string` pinned by `discriminator.mapping` and `@JsonTypeName`, *not* a
  single-value `enum`. Writing the enum "for safety" is the trap — it is the
  one thing that breaks the build.
- **The `pom.xml` line is its own approval item.** It is a generator option,
  not a dependency, and its blast radius on today's contract is provably zero
  (no other `oneOf` exists) — but `backend/CLAUDE.md` treats `pom.xml` as
  approval-gated regardless.
- **REQ-966 (byte-identical regeneration) has an unrelated wrinkle worth
  knowing:** removing a schema does **not** delete the file the generator
  previously wrote under `packages/api-client/model/`. Three orphan
  `spike*.ts` files were left behind by the revert and had to be deleted by
  hand. Any Part that removes or renames a schema must delete the orphans in
  the same commit.
- **Nothing here unblocks the other blocker.** A designed module screen is
  still required before REQ-961/963/964 can be written. This spike only removes
  the generator uncertainty.

---

## Reproducing / cleanliness

Everything above was produced against `8bfb6df` and reverted.
`git status --porcelain` after the revert, including a full regeneration from
the restored contract: **empty** — and `git diff HEAD` is empty, so the tree is
bit-for-bit `HEAD`. No `pom.xml`, `package.json` or dependency was touched at
any point.

- **Frontend: 311/311 passed** (34 files) — matches the baseline.
- **Backend: `./mvnw -o verify` on the reverted tree did *not* reach the
  441 · 0 baseline** — `Tests run: 430, Failures: 0, Errors: 4, Skipped: 3`.
  All four errors are `ContainerLaunchException: Container startup failed for
  image postgres:17.11` / `Failed to start quarkus`, raised in
  `interceptBeforeAllMethod` **before any application code runs**
  (`TrustedProxyLoginTest`, `DevDataSeederTest`, `UntrustedPeerAttributionTest`,
  `OpenApiDisabledTest` — the four that force their own `@TestProfile` and
  therefore a *second* Dev Services container). Re-running only those four
  reproduced it; a raw `docker run postgres:17.11` on the same host is ready in
  **7 s**, so the image is fine — it is the extra per-test Dev Services
  container the Quarkus boot brings up that times out. **The spike cannot have
  caused it: it was fully reverted first, and the backend tree was
  byte-identical to `HEAD` for both runs.** Two confounds worth naming rather
  than hiding: this box is a 3.8 GB Pi already running Immich and Home
  Assistant, and **another session was editing `frontend/` in the same working
  copy while these runs were in flight**, so "reproduced" here does not mean
  "reproduced on a quiet box". Flagged because it will bite the next session
  that trusts the 441 baseline; it is not a D9 finding.

Containers and Maven launchers started by this spike were removed
(`docker ps -a` shows no gxp container; no stray `mvn`/Quarkus process).
