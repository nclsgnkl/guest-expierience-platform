# Handoff — module-content (Part C)

**Last session:** 2026-08-28, `dev` — **round 2's four closing items done
(F-11, F-12, F-13, F-14).** Branch `refactor/bce-subpackages-and-boundary-security`.
**Nothing committed.**

**Next action: commit Part C** (one commit: contract + `pom.xml` + backend +
`packages/api-client/` + frontend + docs — the owner commits).

## What changed, one item at a time

| Item | File | Change |
|---|---|---|
| **F-11** | `02-design.md` §3.2 | *"the mapper checks"* → the **registry** checks, naming `mapInfoModule` in `core/guest/module-registry.ts` and the circular import that put it there; *"`localizedOrNull` **beside** the existing `localized()`"* → the **sibling in another file**, with the consequence stated (the pair re-states the contract rather than sharing it, so each javadoc names the other). One further stale word: *"three things **the mapper** must not do"* → *"the module path"*, and *"the file's existing posture"* → *"the mapper's"* |
| **F-12** | `01-prd.md` REQ-977, D15 note | Amended inline the way REQ-974's was: the field is **required**, not optional (owner, 2026-08-28, reason now a comment on the field); the per-type branch landed in the **registry**, not the menu mapping, and the mapper's share is one line; *"nothing else in the app sees a generated model"* replaced — `module-registry.ts` does (permitted), and three pre-existing files outside `core/guest/`/`core/api/` do too (F-10, owner-gated). What the requirement actually holds — **components** read `PropertyContentService` — is restated |
| **F-13** | `guest-content.mapper.ts` `localized()` | Five javadoc lines naming `localizedOrNull` in `core/guest/module-registry.ts`, why it returns instead of throwing, and that the two are **not shared code**. `localizedOrNull` already named this one, so the link is now bidirectional |
| **F-14** | `features/info-module/info-module.component.spec.ts:86` | The one 113-char line reflowed by prettier itself (`rows:` literal → 5 lines). Verified first that prettier's whole-file output differs **nowhere else**, so no assertion moved. The file is now prettier-clean |

## The prettier figure, re-measured rather than taken on report

`npx prettier --list-different` against a clean `git archive` of `HEAD`
(`--config` pointed at `HEAD`'s own `.prettierrc`), then against the worktree:

| | files differing |
|---|---|
| `HEAD` | **13** — the review's number; dev's 9 was wrong |
| after the cut, before this session | 19 |
| **now** | **18** |

The five newly non-conforming files are **all swept fixtures**
(`app.routes.spec.ts`, `property-content.service.spec.ts`,
`section-placeholder.component.spec.ts`, `settings.component.spec.ts`,
`shell.component.spec.ts`) — the sixth was the new spec, and it is out of the
list. The nine fixture lines stay one-line, as the review accepted.

## Suite

`ng test` **333 passed / 37 files, 0 failed** — the baseline, unchanged (the
reflow is whitespace and the other three items are comments/docs). `ng build`
green, `main` 467.13 kB / 106.43 kB transfer.

## Not touched, deliberately

- **F-10** — owner-gated. Neither `frontend/CLAUDE.md` nor the three imports were
  changed, and **the two validate lines in `03-todo.md` T-13/T-22 were left as
  they are**: they tick against the grep F-10 is about, and they go with whatever
  the owner decides. F-12's amendment records the fact without pre-empting the call
- The queued **`backend/CLAUDE.md`** generator-flags entry — still unwritten, still
  the owner's. No nested `CLAUDE.md` and no constitution file was touched
- The 🟢 findings (F-15 … F-21) — recorded, not actioned, per the brief
- **`backend/` and `packages/` are untouched by this session:**
  `git status --porcelain packages/` and `api/` are **empty**; `backend/` carries
  only `GuestControl.java`, the round-1 F-1 javadoc from an earlier session
  (mtime 13:14, 11 insertions / 2 deletions), byte-unchanged here. No JVM, no
  Maven, no dev server was started

---

---

# Handoff — module-content (Part C)

**Last session:** 2026-08-28, `reviewer` — **Phases 5–8 (T-11 … T-22) reviewed:
PASS ✅.** Round 2 appended to [`06-review.md`](06-review.md) below the phases
1–4 round. Nothing was fixed, nothing was committed, the tree was restored
byte-for-byte after every probe.

**Result: Part C is ready to commit. 🔴 0 · 🟠 0 · 🟡 6 · 🟢 6 —
19/19 in-scope requirements complete, 1 declared exclusion (REQ-965, D16).**

## Next action

**Fix F-11 and F-12, then commit.** They are four sentences in the two documents
that get quoted forward, and they now describe a layout the code does not have:

- **F-11** — `02-design.md` §3.2 still says *"For `info` **the mapper** checks
  …"* and *"`localizedOrNull` **beside** the existing throwing `localized()`"*.
  Both moved to `module-registry.ts`; "beside" was the argument for why the pair
  stays consistent, and it is no longer true.
- **F-12** — `01-prd.md` REQ-977's D15 note still says `MenuItemShape` gains one
  ***optional*** field (the owner made it required) and that *"nothing else in
  the app sees a generated model"* (`module-registry.ts` does).

**This is round 1's F-1 recurring** — the relocation was recorded in `03-todo.md`
and this file, and not in the PRD or the design.

## The four other 🟡, none of them blocking

- **F-10 — put to the owner, do not fix.** The `@gxp/api-client` rule *is*
  breached by its letter: `PairingCompleted`/`PairingRequestCreated` (in
  `features/pairing/`) and `OccupancyState` (in a **component**) all live under
  `packages/api-client/model/` — they are generated **models**, not service
  classes, so dev's reason is wrong for two of the four files. Intent is intact
  (none reads property content) and **all four predate this cut**. Either
  `frontend/CLAUDE.md` gains a carve-out or three imports move — owner-gated
  both ways. T-13's and T-22's validate lines should stop claiming a grep that
  has failed since Part B.
- **F-13** — `localizedOrNull` re-states `localized()`'s default-language
  contract in a second file with nothing tying them together. One sentence on
  `localized()` naming its sibling closes it.
- **F-14** — the nine over-length fixture lines are accepted; the count is
  wrong (**13** files differ at `HEAD`, not 9, → **19** after the cut), and one
  newly non-conforming file is a **new** spec, not a swept fixture: reflow the
  single 113-char line in `info-module.component.spec.ts`.
- **F-15** — `info.md`'s corrected panel row prints **490.0** where its own prose
  three lines below derives **489.6**.

## What was re-measured rather than believed

| | dev | reviewer |
|---|---|---|
| `ng test` | 333/333 | **333 passed, 37 files** ✅ |
| the sweep | 11 files, 41 insertions, no assertion moved | **41 added / 27 removed**, per-file counts row for row, and **no added or removed line in any of the 11 contains `expect`/`it(`/`toEqual`** ✅ |
| `measure.mjs /info` | 12 cells, 0 findings | **12 cells, 0 findings** ✅ |
| `measure-states.mjs /info` | 8 BANNER cells, 0 findings, both rows from cache | **8 cells, all NO-SCROLL OK, both rows from cache, no finding** ✅ |
| clearance | +0.219 / +0.797 px | **+0.219 / +0.797 px** ✅ exact |
| `"module": null` | 12 of 13 | **12 of 13 null, 0 with the key absent**, live off `quarkus:dev` ✅ |
| client byte-identical | yes | a fresh `generate-sources` left `git status` clean under `packages/api-client/` ✅ |

**Both compile-time guards were driven red again, outside the repo tree** (a
scratch copy of the real `module-registry.ts` + the real generated models under
`frontend/tsconfig.json`'s flags): `TS2344 … does not satisfy the constraint
'never'` and `TS1360: Type '{}' does not satisfy … Record<"info",
Type<unknown>>`, plus the **reverse** direction nobody had proved
(`TS2353` on a renderer for a kind that does not exist). A third probe found
that adding a second **domain** kind fails the build **four times at once** —
so §4.4's "three compiler-enforced touchpoints and one `never` check"
understates it: **all four are compiler-enforced and each has now been seen to
fail.**

## The declared deviations, judged

1. **`mapInfoModule`/`localizedOrNull` in the registry — accepted.** The
   circularity is real (§4.2 puts `MODULE_REGISTRY = { info: { map:
   mapInfoModule } }` in the registry while T-13 puts `mapInfoModule` in the
   mapper, which imports `MODULE_REGISTRY`). Nothing else moved — the mapper's
   diff is three hunks — and all nine of §3.2's rules are implemented. The
   registry has become the *module* mapper, which is what a per-type registry
   is. Its one cost is F-13.
2. **The retargeted assertion — accepted.** REQ-962 and its acceptance criterion
   remove the behaviour by name, the fixture is unchanged, and the new case
   **would fail if the client re-sorted** (`home` sortIndex 0, `info` 1, wire
   reversed, expects `['info','home']`).
3. **The `info.md` corrections — the right file was corrected, both times.**
   `box-sizing: border-box` is set on `*` at `global.css:34`, so the declared
   width IS the border box (612 / 489.6). The predicted overlap was an artefact
   of a **rounded input** — `connection.md` recorded the banner as `h 60`; it is
   `h 59`, raw bottom y117.53 against a panel top of y117.75. `staleness-banner/`
   is untouched by this cut, so nothing was moved. The instrument change is
   sound: additive, computes from raw rects that `b()` already returned
   unrounded, and **adds** failure conditions (`COLLISION`, `CACHE`) rather than
   removing them.

## Visual check — performed, not skipped

`/info` at 1280×832 light/de and 1024×768 dark/en, and `/umgebung` (the
placeholder arm) at 1280×832, rendered from the production bundle with `/api`
proxied to `quarkus:dev` and compared against `info.md`. Panel 612×171 at
x172/y147, label secondary/14 px over value primary/18 px, no divider, no icon,
no focusable element, status-bar corner clear. The placeholder arm renders
unchanged with its sidebar entry intact.

## Confirmed clean

No Boundary file changed at all (D16); `de/gxp/architecture/` has no diff and
still holds five guards; `pom.xml`, `package.json` and the lockfile untouched by
this cut; `registered-icons.ts` and `core/i18n/` untouched; nothing logs on the
module path; no placeholder-shaped logic; `zeitkapsel` appears only in
`module-registry.spec.ts`; the property-wording refinement is in all four places
with **no "except for `module: null`" surviving anywhere**;
`SectionPlaceholderComponent`'s source is byte-identical and its spec's `it(`
and `expect(` counts are unchanged; and **`backend/CLAUDE.md`,
`frontend/CLAUDE.md`, `CLAUDE.md` and `docs/constitution.md` are all
unmodified** — the generator-flags entry is still queued for the owner.

**The backend suite cannot have moved this session and that is checkable:**
`git status --porcelain backend/app/src/test/` is empty. Round 1's independent
reconstruction of **449** stands. The container-startup errors were not chased.

---

---

# Handoff — module-content (Part C)

**Last session:** 2026-08-28, `dev` — **Phases 5–8 implemented and swept:
T-11 … T-22 done. All 22 tasks are complete.** Branch
`refactor/bce-subpackages-and-boundary-security`. **Nothing committed — the
owner commits, in one commit.**

**Next action: review** (`06-review.md` covers Phases 1–4 only; Phases 5–8 have
not been reviewed).

## The two owner decisions this session ran on

1. **`MenuItemShape.module` stays REQUIRED** (§3.1). `module?: … | null` would
   put `undefined | null` in front of every read site and re-import the wire's
   ambiguity into the **domain** model — which is what F-3 was a bug about. The
   reason is now a comment on the field itself.
2. **The `SectionPlaceholderComponent` check is a property, not an exception
   list.** T-14, T-22, `02-design.md` §6.2/§12.4 and `01-prd.md`'s REQ-974 all
   now read: *behaviourally unmodified — no assertion in its spec changes, no
   case is added or removed, and the only permitted edit is type conformance
   forced by a shared model change.* It still bites on a rewritten assertion.

## The other two review findings, closed earlier in the same working tree

- **F-1** — `GuestControl.resolveLocalized`'s javadoc now names **both** routes
  by which `stay == null` reaches it (standby texts, and `attachModules` on a
  vacant room, where raw braces are the documented §2.5 outcome). Comment only.
- **F-2** — `03-todo.md` T-02/T-22 and `02-design.md` §10 now say **two**
  approved `pom.xml` generator options and name each one's placement.

## The sweep, counted

**11 files, 42 error sites, 41 physical insertions.** The 42nd was a `TS2345`
in `heading-block.component.spec.ts` that fell out when the `settings` literal
three lines away was fixed. **No assertion moved, no case was added or
removed** — every changed line is the identical line plus `module: null`.

| file | sites |
|---|---|
| `layout/sidebar/sidebar.component.spec.ts` | 12 |
| `features/settings/settings.component.spec.ts` | 6 |
| `app.routes.spec.ts` | 6 |
| `features/section-placeholder/section-placeholder.component.spec.ts` | 4 |
| `layout/shell/shell.component.spec.ts` | 3 |
| `core/content/property-content.service.spec.ts` | 3 |
| `features/standby/standby.component.spec.ts` | 2 |
| `core/preferences/preferences.persistence.spec.ts` | 2 |
| `features/home/heading-block/heading-block.component.spec.ts` | 1 (+1 cascade) |
| `features/home/home-content.component.spec.ts` | 1 |
| `layout/sidebar/menu-split.spec.ts` | 1 |

Nine of the resulting lines run 102–113 characters, over `.prettierrc`'s
`printWidth: 100`. Left one-line on purpose: prettier is **not** enforced here,
and exploding each fixture to six lines would have buried a one-token diff.
*(Count corrected 2026-08-28, review finding F-14: **13** files already differ at
`HEAD`, not 9 — re-measured against a clean `git archive` of `HEAD` with the
repo's own prettier. The cut took it to 19; after reflowing the one new spec
line it is **18**, five newly non-conforming files, all of them swept fixtures.)*

## F-3, closed twice and then confirmed a third time

- **Code:** `mapWireModule(registry, wire: GuestModule | null | undefined, …)`
  guards `wire == null`. A spec case pins both `null` and `undefined`.
- **Design text:** `02-design.md` §4.2's block no longer says
  `wire === undefined`; it carries a quoted correction naming the measurement.
- **Measured live**, not inferred: `GET /api/guest/content` off `quarkus:dev`
  returns `"module": null` on twelve of thirteen menu items and the real object
  on `info`. Root cause named in the design: nothing configures Jackson's
  inclusion, so the default `ALWAYS` emits the key.

## T-13's row bound — where the number comes from, and where it does not

`MAX_INFO_ROWS = 10` and `MAX_MODULE_TEXT = 240` live in `module-registry.ts`
under a comment that says **the contract is their source and not their
enforcement**, with both halves measured:

- `rows: maxItems: 10` **does** become `@Size(max=10)` on
  `GuestInfoModule.getRows()` — and is **inert**, because
  `GuestApi.getGuestContent`'s return carries no `@Valid` and Bean Validation
  does not walk an unannotated response;
- `ModuleText`'s `maxLength: 240` does not reach Java at all (`GuestInfoRow.label`
  is a bare `Map<String, String>`), and `V12`'s `label`/`value` columns are
  unbounded `text`.

That is Part A's invented `MAX_MENU_ITEMS = 5` avoided rather than repeated: the
bound is real, and the code does not claim a rule nothing applies.

## One deviation from the TODO's file lists, and its reason

`mapInfoModule` and `localizedOrNull` shipped in **`module-registry.ts`**, not
in `guest-content.mapper.ts` where T-13 put them. As written the two tasks
require the registry to reference `mapInfoModule` while the mapper references
`MODULE_REGISTRY` — **a circular import, and the app's first**. Keeping every
per-type concern in the registry makes the dependency one-directional and leaves
the mapper's per-type footprint at exactly the one line §3.1 promises. §3.2's
rules are all implemented; only their address changed.

## Three guards driven red by hand, each reverted byte-identically

| guard | error it produced | revert |
|---|---|---|
| REQ-980's `never` check | `module-registry.ts(82,51): error TS2344: Type 'GuestWlanModule' does not satisfy the constraint 'never'.` | `guestModule.ts` `md5 d74b5f987bfe5eae186c8f3c6264a386` |
| the renderer map's totality | `section-host.component.ts(58,5): error TS1360: Type '{}' does not satisfy the expected type 'Record<"info", Type<unknown>>'.` | `section-host.component.ts` `md5 703fff749fa1591f866ffc707a79276b` |
| REQ-979's nested cache table | `guest-data.service.spec.ts(74,7): error TS2741: Property 'zzProbeUnclassified' is missing …` — driven through the **contract** and a real `./mvnw generate-sources`, not simulated | `api/openapi.yaml` `md5 40bd69e30868825aabbc7630926656fe`, no `zzProbe` residue in either generated tree |

## Measured in a real browser, not looked at

Production bundle served on :4300 with `/api` proxied to `quarkus:dev`.

- `measure.mjs --route /info`: **12 cells** (3 viewports × 2 themes ×
  2 languages) — **0 overflowing, 0 truncated, 0 findings.**
- `measure-states.mjs` gained a `/info` BANNER variant: **8 cells, 0 findings**,
  and **both rows rendered from the cache** in every one (REQ-979 proved live).
- Frame against `info.md`, at 1280×832: `<h1>` y88 (the `.settings` baseline
  exactly), title→panel 24 px, panel 612×171 at y147, padding 24, radius 32,
  row gap 24, label→value 4, label 14 px, value 18 px, `overflow-wrap: anywhere`,
  `:host` computed `display: contents`, **0 focusable elements**. Every figure
  ×0.8 at 1024.

**Two `info.md` figures were wrong and are corrected in that file:**

1. The panel is **612 px** (1280) / **490 px** (1024), not 614 / 491.6.
   `info.md` added `--border-glass`'s hairline **outside** the declared width;
   `global.css` sets `box-sizing: border-box`, so it is inside.
2. **The predicted 0.2 px banner overlap at 1024 does not happen.** Measured
   clearance **+0.219 px (clears)** at 1024 and **+0.797 px** at 1280, against
   a predicted −0.2 and +1.2. Nothing was moved to get there; the instrument
   now computes the clearance from raw rects, because 0.2 px cannot survive
   rounding. The rightmost value ends **142–252 px** short of the bar's left
   edge in every cell, which is the reason the near-miss was acceptable in the
   first place.

## Suite

`ng build` green. `ng test` **333/333** (311 baseline + 5 registry + 8 mapper +
1 cache + 4 host + 4 info-module). Backend untouched this session; its last full
run remains **449 / 0 failures**, 2 environmental container-startup errors.

## T-22 — the sweep, and what the backend numbers actually say

`./mvnw -o verify` ran **twice** (21:14 min, 23:38 min). Both reported
**438 run, 0 failures**; 3 errors / 3 skipped on the first, 4 / 4 on the second.
**Every error is a container or connection-pool failure before any application
code runs** — `ContainerLaunchException` for `postgres:17.11`, or Flyway
*"Acquisition timeout while waiting for new connection"* — and **the set is not
the same between the two runs**, which is the signature of the environmental
flakiness this box has. `DevDataSeederTest`, `OpenApiDisabledTest` and
`TrustedProxyLoginTest` re-run **in isolation: 11/11, BUILD SUCCESS.**

**The full-green total reconstructs to 449 — the established baseline, exactly:**
438 counted + 5 (`TrustedProxyLoginTest` holds 6 cases; surefire counted 1
before the abort) + 6 (`SecondFactorLoginTest` reported `Tests run: 0`, the
documented consequence of its shared `@TestProfile` sibling failing to boot).
**No backend test was added or lost this session.** Do not chase these.

`packages/api-client/` is **byte-identical across both builds** — `git status`
clean under it after each, 91 files, 60 models, no orphan. The one byte that
moves is `.openapi-generator/openapi.yaml-null.sha256`, the generator's own
input cache, **explicitly gitignored** at `.gitignore:452`.

Every final check against `8b3856e` (the pre-Part-C base): `package.json` and
its lockfile untouched; `pom.xml` carrying **exactly the two approved generator
options** and nothing else; `de/gxp/architecture/` with **no diff at all** and
still **five** guards — no sixth; `AuditCoverageTest`'s only change the single
added word `"guestModuleContent"`; `registered-icons.ts` and `core/i18n/`
untouched; `SectionPlaceholderComponent`'s source byte-identical.

## Still proposed, still NOT written

The `backend/CLAUDE.md` entry about the two generator flags, queued for the
owner from the previous session. **Do not write it and do not duplicate it.**
No constitution amendment is proposed and none is needed.

---

---

**Previous session:** 2026-08-28, `reviewer` — **Phases 1–4 (T-01 … T-10) reviewed:
PASS ✅.** Full report in [`06-review.md`](06-review.md). Nothing was fixed,
nothing was committed, the tree was restored byte-for-byte after every probe.

**Findings: 🔴 0 · 🟠 0 · 🟡 3 · 🟢 7.** The three 🟡:

1. **F-1** — `GuestControl.resolveLocalized`'s javadoc still says *"`stay == null`
   only for standby texts, whose allowed placeholder set has no stay fields"*,
   which §2.5 made false. Comment-only fix, on the one function REQ-964's
   argument rests on.
2. **F-2** — `03-todo.md` T-02 and T-22 still say `pom.xml` carries *"only the one
   approved line"*; there are two approved options now.
3. **F-3 — read this before writing T-12.** The wire carries **`"module": null`**,
   not an absent key (measured; `GuestContent.home` already behaves this way).
   `02-design.md` §4.2's `wire === undefined` guard would reach `wire.type` on
   `null` and throw — through a branch §3.2 forbids a `try`/`catch` — turning a
   one-module drop into a blank screen. Use `== null` / `?? null`, matching the
   shipped mapper's `content.home ?? null`.

**Independently re-measured, all confirming dev:** the union interface and
`@JsonTypeName("info")` read off the generated tree; `"info"` on the wire proved
by Jackson including through `GuestModule asUnion = m;`; three clean
regenerations byte-identical (`90090e21b79278c06e4a789c623371dc`, 91 files); no
`ZzProbe`/`spike` residue and exactly **one** `GuestModule` implementor in
`target/classes`; T-09's red probe reproduced (2 of 3 red, reverted to
`md5 40bd69e30868825aabbc7630926656fe`); suite 438/0F/4E/3S here, all four error
classes green in isolation, and the full-green total reconstructed as **449**
(443 from surefire XML + 6 from `SecondFactorLoginTest`, which reported
`Tests run: 0` when its shared `@TestProfile` sibling failed to boot) — so **441
was real**. Both `pom.xml` options were removed one at a time and each proved
load-bearing; with either missing the tree **no longer compiles**, which is a
third guard nobody claimed.

**Next action: Phase 5 (T-11) may start.** Fix F-1 and F-2 when convenient; carry
F-3 into T-12.

---

**Previous session:** 2026-08-28, `dev` — **T-03 unblocked and Phases 2, 3 and 4
finished.** Branch `refactor/bce-subpackages-and-boundary-security`.
**Nothing committed.**

**Status: T-01…T-10 done ✅. Phase 5 (the kiosk boundary, T-11) is next, after
the planned review round.**

## The blocker is closed

The owner approved the second `pom.xml` line (2026-08-28). It went in as ONE
plugin-level `<configuration>` — `<openapiNormalizer>SIMPLIFY_ONEOF_ANYOF=false
</openapiNormalizer>` — rather than twice, because plugin-level configuration is
inherited by both executions, and the Java side needs it for the interface while
the TypeScript side needs it for the alias. Four comment lines beside it, in the
file's existing style. **Nothing else in `pom.xml` changed:** no version bump, no
plugin, no dependency.

## The generated union, verified by reading it and by serialising it

All five checks the task named are present, verbatim:

```java
// target/generated-sources/openapi/.../de/gxp/api/model/GuestModule.java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = GuestInfoModule.class, name = "info"),
})
public interface GuestModule {
    String getType();
}
```

```java
@JsonTypeName("info")
public class GuestInfoModule  implements GuestModule {
```

```ts
// packages/api-client/model/guestModule.ts
export type GuestModule = GuestInfoModule;
```

**The discriminant on the wire is `"info"`, proved from serialisations and not
from the annotations** — three of them, plus a round-trip:

    MODULE_JSON={"type":"info","rows":[…]}
    ITEM_JSON={"key":"info",…,"module":{"type":"info","rows":[…]}}
    UNION_JSON={"type":"info",…}          // serialised through the interface type
    ROUNDTRIP_CLASS=de.gxp.api.model.GuestInfoModule
    ROUNDTRIP_TYPE=info

and again over real HTTP, in two committed tests
(`DevDataSeederTest.seedsTheInfoModuleAndRendersOneStoredCheckOutTimeTwice`,
`GuestModuleContentTest.anOccupiedRoomGetsEveryRowResolvedPerOfferedLanguage`),
both asserting `module.type == "info"` off the served payload.

**Regeneration is byte-identical.** Three runs; the 91-file `packages/api-client`
tree hashed identically each time, and again after the full test suite.

**One orphan was found and removed — on the Java side, not the TypeScript one.**
`ZzProbeModule.java` (timestamped 10:46 — last session's throwaway probe) had
survived in `target/generated-sources/openapi/.../de/gxp/api/model/` and,
compiled, in `target/classes`: the generator does not delete files for removed schemas,
exactly as warned, and `target/` is gitignored so `git status` could not show it.
Cleared with `./mvnw clean` (174 → 172 source files). `packages/api-client/`
itself lost nothing — the `FILES` diff is three additions and no deletions — and
no `zzProbe` residue exists anywhere. The only `spike` string in the client is
the deliberate `d9-spike.md` reference inside the schema description.

## Recorded, unchanged

- **`maxLength: 240` under `additionalProperties` does not reach Java.**
  `GuestInfoRow` is `Map<String, String>` with no `@Size`. Recorded, not a
  blocker — the kiosk enforces it at T-13.
- **`maxItems: 10` DOES survive**, as `@Size(max=10)` on
  `GuestInfoModule.getRows()`. Worth knowing: the two bounds behave differently.

## What each task did

| Task | Change | Verification |
|---|---|---|
| T-03 | regenerated `packages/api-client/` (3 new models, 2 index entries, 1 optional property) | union read by hand, wire proved by serialisation, 3× byte-identical, orphan sweep |
| T-04 | `V12__info_module_content.sql`; `MigrationTest` head re-pinned `V11` → `V12`, method renamed, two-table existence assertion added | `MigrationTest` 6/6, all 12 migrations applied to a fresh schema |
| T-05 | `InfoModuleRow`, `InfoModuleRowTranslation`, two repository reads, `MenuMapper.toInfoModule` | the **five** architecture guards 10/10 — no sixth guard, no exemption list edited |
| T-06 | `MenuControl.guestModuleContent()`; one word added to `NOT_A_MANAGER_MUTATION` | `AuditCoverageTest` 5/5 |
| T-07 | `GuestControl.attachModules` + `resolveModule` (one-arm pattern switch, defensive `default`) | compiles; `GuestResource` still 2 methods, 2 delegating lines, no `if`/`for`/`try` |
| T-08 | `%dev` seeds info.md's copy table verbatim; `CARDS[0]` becomes `Bis {stay.checkOut\|time} Uhr am Abreisetag` | `DevDataSeederTest` 3/3; `grep -rn "Bis 11:00" backend/app/src/main` empty |
| T-09 | `de.gxp.guest.GuestModuleContractTest` (plain JUnit, no container, 0.5 s) | 3/3; **and red by hand** — renaming the mapping key to `infoRENAMED` failed 2 of 3 with both literals on screen, then reverted byte-identically (`md5 40bd69e30868825aabbc7630926656fe`) |
| T-10 | `de.gxp.guest.GuestModuleContentTest` — occupied/vacant/disabled/order | 4/4 first run |

## The suite, measured rather than claimed

`./mvnw -o test`: **449 run, 0 failures, 2 errors, 2 skipped**, 19:43 min.

Both errors are the box, not the code, and both are **before application code**:
`UntrustedPeerAttributionTest` → `ContainerLaunchException` for `postgres:17.11`;
`OpenApiDisabledTest` → Flyway `Acquisition timeout while waiting for new
connection`. Both classes are custom-`@TestProfile` and spin their own database.
**Re-run in isolation they are 4/4 green.** The 2 skips are the sibling cases of
those two aborted classes.

449 = the previously reported 441 plus this session's 8 new tests (3 contract,
4 content, 1 seeder). That is the only reason to believe 441 was ever the real
number; **it is still not an established baseline** — do not chase container
startup errors on this box.

## Two things a reviewer should look at rather than skim

1. **`DevDataSeederTest` gained assertions** although T-08 listed only
   `DevDataSeeder.java`. T-08's validate was written as a hand-check ("a fresh
   `quarkus:dev` database serves …, and Home's check-out card shows the same
   time"); it is now mechanical instead, and it is the single place where
   REQ-967 is actually proved — the test extracts the time from the module row
   and asserts the action card renders that exact time. No later task touches
   that file.
2. **`MigrationTest` gained one `information_schema` assertion** (the two V12
   tables exist), in the file's established style. Deliberately **not** a
   row-count assertion: the suite shares one database and `GuestModuleContentTest`
   seeds rows, so "the tables are empty" would have been order-dependent — the
   exact hazard that file's own comment names.

## Proposed, NOT written — one nested `CLAUDE.md` entry

`backend/CLAUDE.md`, under **Contract-first**. Exact text:

> - A `oneOf` needs **both** generator settings on the openapi plugin:
>   `useOneOfInterfaces` (member classes implement an interface) and
>   `openapiNormalizer=SIMPLIFY_ONEOF_ANYOF=false` (a ONE-member union is not
>   collapsed first). Without either, the build stays green and the wrong
>   discriminant goes on the wire. The literals are pinned by hand in
>   `de.gxp.guest.GuestModuleContractTest` — an ordinary feature test, not a
>   sixth architecture guard.

**Do not write it without the owner's word on the wording.** No constitution
amendment is proposed and none is needed.

## Next

**Review round**, then **Phase 5 (T-11)** — the kiosk domain model. Phase 5
starts from: the contract and the regenerated client in the tree, the backend
serving `menu.items[].module` for both occupancy states, `V12` applied, and the
`%dev` instance authoring two `info` rows. `frontend/` is **untouched** so far
(baseline 311/311, unverified this session).

---

---

# Earlier — planning (superseded above, kept for its decisions)

**Last session:** 2026-08-28, `planner` — **the fourth fold of the day, and the
one that finished the plan.** Wrote [`02-design.md`](02-design.md),
[`03-todo.md`](03-todo.md) and [`04-analyze.md`](04-analyze.md), closed the
PRD's missing **Acceptance criteria** section, answered its three open items,
and made two corrections to [`docs/design/screens-inhouse/info.md`](../../design/screens-inhouse/info.md).
*(Earlier the same day: the D9 fold, the D14/D15 fold, then the D16…D19 fold.)*
**Branch:** `refactor/bce-subpackages-and-boundary-security`. **Nothing was
committed.**

**Status: PLANNED THROUGH `04_analyze` ✅ — result PASS.**
19/19 in-scope requirements covered by **22 tasks in 8 phases**, 0 orphans, 0
unresolved markers, 1 declared exclusion (REQ-965, D16), no constitution
conflict.

**⏸ ONE OWNER APPROVAL IS OPEN, AND IT IS THE FIRST TASK.**
`backend/app/pom.xml` needs `<useOneOfInterfaces>true</useOneOfInterfaces>` on
the `jaxrs-spec` execution. **T-01 asks and ends the turn. Nothing may be built
before the answer** — see the next section for why that is stronger than the
PRD's earlier wording.

**Constitution:** **v1.10.0**, and it still **binds nothing here** — its
threshold clause has no subject in this Part. The v1.9.0 cache boundary is what
governs.

---

## Why T-01 comes before the contract edit

The PRD said the `pom.xml` line "blocks the commit, not the plan". The design
sharpened that to **it blocks the build**: with a `oneOf` in
`api/openapi.yaml` and the flag **off**, `jaxrs-spec` does not fail. It emits a
flattened concrete class with `@JsonTypeName("GuestModule")`, `./mvnw verify`
goes **green**, and the wire carries `{"type":"GuestModule"}` — a discriminant
in no `mapping` that no kiosk `switch` can match. Anyone who regenerates between
the contract edit and the approval gets a green build they can believe.

It is a **generator option, not a dependency** (the spike checked: nothing is
pulled in), gated anyway by `docs/constitution.md` → Constraints and
`backend/CLAUDE.md`. **If the owner declines, stop and report** — Variant C is
strictly worse (no TypeScript union, a concrete instantiable base) and a
free-form bag cannot be boundary-validated at all.

## What the design decided — the calls a later session must not re-take

1. **The union.** `GuestModule` = `oneOf` over one member `GuestInfoModule`,
   `discriminator.mapping: {info: …}`, member `type` a **plain `string`**.
   Reached through the **optional `GuestMenuItem.module`** (D15) —
   `required` unchanged, no top-level `modules[]`.
2. **The discriminator is carved out of REQ-963** (open item 1). Not given a
   `maxLength`. The decisive reason is semantic, not the generator risk: a
   length bound still admits `"nfo"` and `"Info"`, so it bounds the wrong
   dimension. What pins the value is a **set**, pinned three times already.
3. **Bounds:** `rows: maxItems 10`, and a new **`ModuleText`** schema at
   `maxLength: 240` for module label/value. **These are the contract's first
   `maxLength` declarations** (`grep -c maxLength api/openapi.yaml` was 0).
   `LocalizedText` stays unbounded on purpose — bounding it would put `@Size`
   on every existing text field in the contract.
4. **Persistence joins `de.gxp.menu`** — two tables in `V12`, no room scope
   (A2/D11), **no seed rows** (a release must not put content on a running
   property's screens). A new `de.gxp.module` package was rejected: with no HTTP
   surface it would force an edit to `FeaturePackages.FEATURES_WITHOUT_BOUNDARIES`,
   i.e. to an architecture guard's exemption list.
5. **`MenuControl` returns raw templates; `GuestControl` resolves them** — the
   `guestResolvedCards` precedent verbatim, so REQ-964 is met by the mechanism
   that already meets it for `GuestHome`.
6. **Modules are delivered to a vacant room too** (property-scoped, A2). The
   consequence is written down: a vacant payload carries the raw
   `{stay.checkOut|time}` text, nobody sees it (`/standby`), and
   `GuestControl`'s *"no braces ever leave the server"* javadoc is scoped to
   `home` and **must not be generalised**.
7. **The registry is `core/guest/module-registry.ts`** — the only location
   permitted to import `@gxp/api-client`, so `core/modules/` would have needed a
   `frontend/CLAUDE.md` amendment. **The renderer map lives in the section
   host**, not in the registry, because a registry importing a feature component
   would be **the app's first `core → features` import**. The split is held
   total by `satisfies Record<ModuleKind, Type<unknown>>`; REQ-970 carries an
   inline amendment recording it (finding F-1).
8. **REQ-980's guard, written out:**
   `AssertNever<Exclude<GuestModule, ModuleWireMap[ModuleKind]>>`. Its one
   limitation (assignability, so a structurally-subset member would slip) is
   recorded in the design rather than left to be discovered.
9. **The third drift axis is covered** (open item 2) — by a **pinned contract
   test on the Java side**, `de.gxp.guest.GuestModuleContractTest`, the
   `ScheduledJobInventoryTest` precedent. **Not** in `de.gxp.architecture`: a
   sixth architecture guard would need a constitution amendment.
   **The fabricated-type test cannot cover this axis and says so in its own
   header** — its discriminant is defined by the test file, so there is no
   contract for it to disagree with.
10. **The cache guard gains two nested classification tables AND an explicit
    runtime reach** (open item 5) — both, not either. The residual (it is two
    hand-written tables, not recursion) is recorded as a known gap.
11. **The domain union is a real discriminated union** (`type: 'info'` as a
    literal), so `switch` narrows everywhere above the boundary. D9's loss is
    confined to `guest-content.mapper.ts`, exactly as REQ-977 predicted.

## Two things to verify at implementation — neither changes a requirement

1. **A single-member `oneOf`.** The spike ran **two** members; this Part ships
   **one**. If either generator degrades it (no TS union alias, an inlined
   member), **stop and report**. **Do not add a second contract member** —
   D17 put the second type in a *test*.
2. **`maxLength` under `additionalProperties`** on the Java side. If the bound
   does not survive to `@Size`, **record it and continue**: the kiosk enforces
   it, which is where REQ-963's protection actually matters.

## Two findings this gate produced, both already repaired in `01_prd`

- **F-1 — REQ-970 vs. the renderer map** (see 7 above). The PRD now carries the
  inline amendment; the criterion asserts both compile-time guards.
- **F-2 — REQ-978 named an instrument that cannot see the banner.**
  `tools/measure.mjs` measures **routes** (and `/info` is already in its grid);
  the escalated staleness banner is a kiosk **state**, so the 1.2 px / 0.2 px
  figure `info.md` flags needs `tools/measure-states.mjs`. REQ-978 amended to
  name both. **Part B's REQ-952 carried the identical defect** — this is the
  second occurrence of one mistake.

## Corrections made to `docs/design/screens-inhouse/info.md`

- **Decision 2's factual error is fixed.** It framed the unresolvable
  placeholder as *"an occupied stay with no check-out timestamp"*. **That state
  cannot exist** — `stay.check_out` is `NOT NULL`, and a null `checkOut` reaches
  `resolveLocalized` only when `stay == null`, i.e. a vacant room, which is on
  `/standby`. The outcome is **unreachable, not unlikely**. The same correction
  is carried inline in the PRD's "Open items" 3 and its "What stood in the way"
  row 3.
- **Open questions 2 and 3 are closed** in the file (D18, D19) rather than left
  reading as open.
- **"What generalises" gained the line D19 implies:** *every module type states
  its empty state — or a stated reason the type cannot be empty*, with `info`'s
  reason in the Info-specific column.

## Do not re-derive these

- **Manager-authored module content is a Non-goal (D16).** No manager write
  path, no `manager*` content endpoint, no `module_content` write API, and **no
  `module-content-authoring` directory** — it is named and tracked, not planned.
- **Do not mint a Part C requirement for D12's disclosure obligation.** Owner
  task with a trigger, in `docs/features/index.md` → "Tracked, not blocking".
- **No placeholder-shaped logic in the mapper** (D18, option (a) rejected). A
  `{…}` in a value is not a drop trigger.
- **Do not design a second module type** (D17). The second type is fabricated
  and lives in T-16's spec file.
- **No shared generic empty state** (D19), and do not collapse "no module"
  (→ `SectionPlaceholderComponent`) with "zero entries" (→ that type's own
  empty state).
- **The generic-rows option was offered to the owner and declined** (D14). Not
  a discovery.
- **The module carrying its own route was rejected** (D15) — two sources for
  one fact, and a renamed `route` would silently orphan content.
- **A single-value `enum` on a member's `type`** is the one spelling that
  provably breaks (D9). Not "for safety".
- **D12's three options are withdrawn, all of them** — no per-field `sensitive`
  flag, no per-type cache classification, no "module content is never cached".
  What survives is the credential line (REQ-979), and the test must **not**
  filter on names like `password` or `code`.
- **`/info` must render identically from cache.** No degradation, no hidden
  row. And **the REQ-972 drop must not quote the offending content** — value or
  label — a console warning is not exempt (principle 3).
- **Do not add a `{ path: 'info' }` route**, an SSID row, a WPA QR code (a new
  dependency), a per-row icon, a per-row sort key, a row id, or a `title` on the
  module — all closed by `info.md`.
- **`SectionPlaceholderComponent` is not modified.** It survives demoted, and
  its existing spec must pass unchanged.
- **REQ-969 is vacuously satisfied** — no manager mutation exists to audit. Its
  whole footprint is one entry in `AuditCoverageTest`'s exemption list. It will
  look like non-coverage at review; it is not (O-1).
- **REQ-966's orphan sweep has nothing to sweep** — this Part removes and
  renames no schema. The check still runs, because a green build cannot tell
  "nothing found" from "nothing looked" (O-2).
- **Part C still cannot serve a real property** (D16, O-4). What ships is the
  transport, the per-type wire shape, the registry and one rendered module
  against seeded content.

## Next

**`05_implement` (`dev`), starting at T-01 — which writes nothing and waits for
the owner.** After the answer: T-02 (contract + the approved `pom.xml` line
together), T-03 (client regeneration as one reviewable step), then the backend,
the kiosk boundary, the screen, the tests, and T-20…T-22 (measure, docs, sweep).
The owner commits; one commit carries contract + `pom.xml` + backend +
`packages/api-client/` + frontend + docs.
