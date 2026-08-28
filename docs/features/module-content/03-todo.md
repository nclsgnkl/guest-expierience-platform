# TODO: Module Content

**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** Not Started — 22 tasks in 8 phases

> **Start condition:** Parts A (`sidebar-catalogue-overflow`) and B
> (`guest-payload-consolidation`) are **committed** — both are, as of
> `8b3856e`. This list is written against that tree.
>
> **Branch:** `refactor/bce-subpackages-and-boundary-security`. **The owner
> commits.**
>
> **T-01 is an approval pause and it comes first for a reason.** With a `oneOf`
> in the contract and the generator flag off, `./mvnw verify` goes **green** and
> ships `{"type":"GuestModule"}` on the wire (`02-design.md` §1.5, and the spike
> that measured it). Do not edit `api/openapi.yaml` before the owner has
> answered.
>
> Every design decision is already taken in `02-design.md`. **Do not re-decide**
> the union's spelling, the discriminator carve-out, where the registry lives,
> where the renderer map lives, or whether a vacant room gets its modules —
> implement them.

---

## Phase 1 — The one approval

- [x] **T-01** ⏸ **Ask the owner for the `pom.xml` generator option — then stop**
  — *REQ-961*
  - Propose **exactly** this line, in the `jaxrs-spec` execution's
    `<configOptions>` in `backend/app/pom.xml`:
    `<useOneOfInterfaces>true</useOneOfInterfaces>`.
    Say what it is (**a generator option, not a dependency** — the spike
    confirmed nothing is pulled in), what it costs (nothing on today's
    contract: there is no other `oneOf` for it to re-shape), and **why it
    cannot wait until after the contract edit** — without it `jaxrs-spec`
    emits a flattened class with `@JsonTypeName("GuestModule")`, the build is
    green, and the wrong discriminant goes on the wire silently.
  - Files: none — **this task writes nothing.**
  - Validate: **end the turn.** Do not start T-02 until the owner has
    answered. If he declines, **stop and report**: Variant C is strictly worse
    (no TypeScript union, a concrete instantiable base) and a free-form bag
    cannot be boundary-validated at all (A3) — neither is a fallback this
    plan may take alone.

## Phase 2 — Contract first (§1, §10)

- [x] **T-02** The `GuestModule` union, and the approved `pom.xml` line
  — *REQ-961, REQ-963*
  - `api/openapi.yaml`: add `GuestModule` (`oneOf` over `GuestInfoModule`,
    `discriminator: {propertyName: type, mapping: {info: …}}`),
    `GuestInfoModule` (`type` a **plain `string` with no `enum` and no
    `maxLength`** — the carve-out, with §1.3's reason in its `description`;
    `rows` `maxItems: 10`), `GuestInfoRow` (`required: [label, value]`) and
    `ModuleText` (`additionalProperties` `maxLength: 240`). Add the optional
    `module` property to `GuestMenuItem` and **leave its `required` list
    alone**. Copy the schemas from `02-design.md` §1.1 — the descriptions are
    part of the deliverable, not commentary.
    In `backend/app/pom.xml`, add the one approved line and nothing else.
  - Files: `api/openapi.yaml`, `backend/app/pom.xml`
  - Validate: `grep -A3 "type:" ` on the two module members shows **no
    `enum`**; `GuestMenuItem.required` is byte-identical to before;
    `GuestMenu.items.maxItems` is still `50`; **no top-level `modules` array
    exists**.

- [x] **T-03** Regenerate the client — one reviewable step
  — *REQ-966*
  - `./mvnw -o generate-sources` (or `verify`), then **read the diff under
    `packages/api-client/`** before touching anything else. Expected: new
    `guestModule.ts`, `guestInfoModule.ts`, `guestInfoRow.ts`, their `models.ts`
    entries, and one optional property on `guestMenuItem.ts`. Nothing else may
    change.
  - **Verify-at-implementation, §10.1, both of them:**
    (a) a **single-member** `oneOf` must still emit `export type GuestModule =
    GuestInfoModule;` and, on the Java side, an interface with one implementor
    (`String getType()`, `@JsonTypeName("info")` on the member). **If it
    degrades — no alias, or an inlined member — stop and report.** Do **not**
    add a second contract member: D17 put the second type in a test.
    (b) note whether `maxLength` under `additionalProperties` survives to the
    Java side as `@Size`. If it does not, **record it and continue** — the
    kiosk enforces the bound either way (T-13).
  - **Orphan check:** this Part removes and renames no schema, so nothing
    should disappear from `packages/api-client/model/`. Confirm none did, and
    that no `spike*` residue exists.
  - Files: `packages/api-client/**` *(generated — never hand-edited)*
  - Validate: backend compiles against the generated interfaces; a second
    `./mvnw verify` leaves `git status --porcelain` empty for
    `packages/api-client/`.

## Phase 3 — Backend (§2)

- [x] **T-04** `V12__info_module_content.sql`
  — *REQ-968, REQ-975*
  - The two tables of `02-design.md` §2.2, with its header comment: per-type
    tables (A3/D14), **no room scope** (A2/D11), **no seed rows** (Part A's D5
    reasoning — a release must not put content on a running property's guest
    screens). Re-pin the Flyway head in `MigrationTest` (`V11` → `V12`, and
    rename the test method with it).
  - Files: `…/db/migration/V12__info_module_content.sql` *(new)*,
    `…/de/gxp/system/MigrationTest.java`
  - Validate: `./mvnw -Dtest=MigrationTest test` green; a fresh instance and an
    upgraded one both start; on an upgraded instance `/info` still renders the
    placeholder, unchanged.

- [x] **T-05** Entities and repository, in `de.gxp.menu`
  — *REQ-968*
  - `InfoModuleRow` + `InfoModuleRowTranslation` in `de.gxp.menu.entity`
    (`jakarta.persistence` only, no `PanacheEntity`), and the read methods on
    the existing `de.gxp.menu.control` repository. **No new feature package** —
    §2.1 says why (a `de.gxp.module` with no HTTP surface would force an edit
    to `FeaturePackages.FEATURES_WITHOUT_BOUNDARIES`, i.e. to an architecture
    guard's exemption list).
  - Files: `…/de/gxp/menu/entity/InfoModuleRow.java`,
    `…/de/gxp/menu/entity/InfoModuleRowTranslation.java` *(both new)*,
    `…/de/gxp/menu/control/MenuRepository.java`, `…/de/gxp/menu/entity/MenuMapper.java`
  - Validate: the **five** architecture guards green — `PersistenceAccessTest`,
    `EntityExposureTest`, `ControlPackageVisibilityTest`,
    `FeatureInternalsImportTest`, `ScheduledJobInventoryTest`. **No sixth
    guard is added and no exemption list is edited.**

- [x] **T-06** `MenuControl.guestModuleContent()` — the raw-template read
  — *REQ-968, REQ-969*
  - `@Transactional public Map<String, GuestModule> guestModuleContent()`,
    returning generated DTOs carrying **raw** templates, ordered by
    `sort_index`, with the javadoc of §2.3 (the `guestResolvedCards`
    precedent, in those words). Add `"guestModuleContent"` to
    `AuditCoverageTest`'s `NOT_A_MANAGER_MUTATION` under "reads and lookups"
    — **and change nothing else in that file.** This Part introduces **no
    manager mutation**, which is the whole of REQ-969's work here.
  - Files: `…/de/gxp/menu/control/MenuControl.java`,
    `…/de/gxp/audit/AuditCoverageTest.java`
  - Validate: `./mvnw -Dtest=AuditCoverageTest test` green; the audit row count
    is unchanged by a guest content read.

- [x] **T-07** `GuestControl` — resolve the templates, attach the modules
  — *REQ-964, REQ-962*
  - `content()` calls `menuControl.guestModuleContent()` and attaches the
    resolved module to the matching delivered item. Resolution reuses
    `resolveLocalized` **unchanged** (same timezone, same per-language locale,
    same main-language fallback), through the one-arm pattern switch of §2.4
    with its defensive `default` (the stranded-card precedent).
    **Modules are attached in both occupancy states** (§2.5) — write that
    reasoning into the javadoc, together with the fact that the existing
    *"no braces ever leave the server"* sentence is scoped to `home` and must
    not be generalised.
  - Files: `…/de/gxp/guest/control/GuestControl.java`
  - Validate: backend compiles; `GuestResource` still holds **one delegating
    line per method** and gains none; no `if` moves into the Boundary.

- [x] **T-08** `%dev` — one authored module, and REQ-967's live duplication
  — *REQ-976, REQ-967*
  - Seed two `info` rows against key `info` with `info.md`'s copy table
    **verbatim** (`Check-out` / `{stay.checkOut|date}, {stay.checkOut|time} Uhr`;
    `WLAN-Passwort` / `Meeresrauschen-77`; the English column as written),
    only when `info_module_row` is empty — the `seedActionCards` shape.
    Then fix `CARDS[0]`: the description becomes
    `"Bis {stay.checkOut|time} Uhr am Abreisetag"` /
    `"By {stay.checkOut|time} on departure day"`. **That one edit is REQ-967** —
    the check-out time is stored once, in the stay, and rendered twice.
  - Files: `…/de/gxp/infrastructure/devseed/DevDataSeeder.java`
  - Validate: `grep -rn "Bis 11:00" backend/app/src/main` is empty; a fresh
    `quarkus:dev` database serves `menu.items[key=info].module` with two rows,
    and Home's check-out card shows the same time. Note in the commit that a
    developer with an existing dev database sees neither change until the Dev
    Services volume is dropped — the seeder's stated contract.

## Phase 4 — Backend tests (§8, §12.2)

- [x] **T-09** `GuestModuleContractTest` — the pinned discriminants
  — *REQ-961, REQ-971*
  - New test in **`de.gxp.guest`** (**not** in `de.gxp.architecture` — a sixth
    architecture guard would need a constitution amendment, and Part B refused
    one for the same reason). Pin `Map.of("info", GuestInfoModule.class)` by
    hand, read `@JsonSubTypes` off the generated `GuestModule` interface and
    compare; assert `GuestInfoModule` carries `@JsonTypeName("info")` and that
    `getType()` returns `String`. Header comment: this is the **only** guard on
    the third drift axis (§8) — the literals live only in the contract, the
    generated TypeScript carries none, and the fabricated-type test in T-16
    **cannot** cover this.
  - Files: `…/de/gxp/guest/GuestModuleContractTest.java` *(new)*
  - Validate: green; and once, by hand — rename the mapping key in
    `api/openapi.yaml`, watch this test go red, revert.

- [x] **T-10** `GuestModuleContentTest` — the wire, per language and per state
  — *REQ-964, REQ-962, REQ-963*
  - Cases: (a) occupied, `de`+`en` offered → row 1's value carries **no `{`**
    and equals the check-out formatted in the property timezone per language,
    row 2 is identical in both; (b) **vacant** → the item still carries its
    module and the stay placeholder is present as raw text (§2.5 — this is the
    documented state, not a defect); (c) a section disabled at property scope
    appears **neither** as a menu item **nor** as module content anywhere in
    the payload (REQ-962, structurally); (d) the delivered row order is the
    seeded `sort_index` order.
  - Files: `…/de/gxp/guest/GuestModuleContentTest.java` *(new)*
  - Validate: `./mvnw test` — whole backend suite green in one run.

## Phase 5 — The kiosk boundary (§3, §4, §5)

- [ ] **T-11** The domain model
  — *REQ-970, REQ-977*
  - `core/content/property.model.ts`: `InfoModuleShape<TText>` with a
    **literal** `type: 'info'`, the `ModuleShape<TText>` union, and
    `MenuItemShape.module: ModuleShape<TText> | null`. Comment the point that
    matters: the **domain** union is properly discriminated even though the
    wire union is not, so `switch (module.type)` narrows everywhere above the
    boundary — D9's loss is confined to one file.
  - Files: `…/core/content/property.model.ts`
  - Validate: `ng build` green; both the authored and the resolved aliases pick
    the new field up from the one generic shape.

- [ ] **T-12** The registry, the `never` check and `resolveModule`
  — *REQ-970, REQ-971, REQ-980*
  - New `core/guest/module-registry.ts`, exactly §4.2–§4.3 and §5:
    `ModuleWireMap` (one line per type), `MODULE_REGISTRY` typed
    `{ [K in ModuleKind]: ModuleEntry<K> }`, the exported
    `mapWireModule(registry, wire, defaultLanguage)` **taking the registry as a
    parameter** (that is what lets T-16's fabricated type exist only in a
    test), `Object.hasOwn` as the validation set (REQ-971 — **no second
    list**), the `AssertNever<Exclude<GuestModule, RegisteredWire>>` guard with
    its javadoc, and the `resolveModule` switch. Wire `resolveProperty` to it
    with one line inside its existing `menuItems.map`.
  - Files: `…/core/guest/module-registry.ts` *(new)*,
    `…/core/content/property-content.service.ts`
  - Validate: `ng build` green. **Then verify the guard by hand, once:** add a
    throwaway member to `ModuleWireMap`'s source union, confirm the build fails
    with `TS2344: … does not satisfy the constraint 'never'`, revert. Record
    the error text in the commit message.

- [ ] **T-13** The mapper — one branch, three drops, and the re-sort deleted
  — *REQ-962, REQ-963, REQ-972, REQ-973, REQ-977*
  - `guest-content.mapper.ts`: `mapMenuItem` gains
    `module: mapWireModule(MODULE_REGISTRY, item.module, defaultLanguage)`.
    `mapInfoModule` hand-validates every field (§3.2): array, `rows.length <=
    10`, both texts present with a non-blank default-language entry, **every
    string `<= 240` characters** — returning `null`, never throwing.
    Add `localizedOrNull` beside the existing throwing `localized()`; **no
    `try`/`catch`** around the module branch (it would swallow the envelope
    failures that must stay fatal). **Delete the `sortIndex` re-sort** from
    `mapGuestContent` with a comment naming the contract line that makes the
    delivered order authoritative (REQ-962).
    **Three prohibitions, each decided elsewhere:** no placeholder-shaped logic
    (D18 rejected it), no logging of any kind, and nothing that quotes a label
    or a value (principle 3 — `info.md` names the WiFi row as where somebody
    will be tempted).
  - Files: `…/core/guest/guest-content.mapper.ts`
  - Validate: `ng build` green; `grep -n "sort(" guest-content.mapper.ts` shows
    no sort over `menu.items`; `grep -rn "@gxp/api-client" frontend/src/app`
    still lists only `core/guest/` and `core/api/`.

## Phase 6 — The screen (§6)

- [ ] **T-14** `SectionHostComponent`, and the two route entries
  — *REQ-974, REQ-970, REQ-973*
  - New `features/section-host/`: resolves the section with
    `itemForRoute(items, url)` (**the shared function — no new lookup and no
    second normalisation**), reads `.module`, and renders either the `<h1>` +
    `<ng-container *ngComponentOutlet>` arm or `<app-section-placeholder />`.
    The renderer map is `{ info: InfoModuleComponent } satisfies
    Record<ModuleKind, Type<unknown>>` — §4.4's compiler-checked projection of
    the registry.
    `app.routes.ts`: `:sectionKey` and `**` point at the host.
    **Do not add a `{ path: 'info' }` route** and **do not modify
    `SectionPlaceholderComponent`** — it survives demoted, untouched, and its
    existing spec must pass unchanged.
  - Files: `…/features/section-host/section-host.component.{ts,html,css}`
    *(new)*, `…/app.routes.ts`
  - Validate: `ng build` green; then by hand, once — delete the `info` entry
    from the renderer map and confirm the build fails, revert.
    `SectionPlaceholderComponent`'s spec green without edits.

- [ ] **T-15** `InfoModuleComponent`
  — *REQ-976, REQ-977, REQ-978*
  - New `features/info-module/`, exactly `docs/design/screens-inhouse/info.md`:
    one glass panel at `calc(3 * var(--size-panel) + 2 * var(--space-4))`,
    `<dl>`/`<dt>`/`<dd>` rows, label `--text-body`/secondary over value
    `--text-title`/primary, `--space-1` apart, `overflow-wrap: anywhere` on the
    value, `--space-6` between rows, **no divider, no icon, no interactive
    element, no `text-transform`**. `:host { display: contents }`.
    One signal input (`module: ResolvedInfoModule`), no injected service.
    `@for (row of module().rows; track $index)`.
    **Zero new tokens, zero new icons, zero new translation keys** — nothing on
    this screen is app chrome.
  - Files: `…/features/info-module/info-module.component.{ts,html,css}` *(new)*
  - Validate: `ng build` green; no raw hex and no `px` in the CSS (hairlines
    excepted); no user-visible string in the template; `/info` in
    `quarkus:dev` shows the two seeded rows in both languages and both themes.

## Phase 7 — Kiosk tests (§12.1, §7)

- [ ] **T-16** The fabricated second type
  — *REQ-976, REQ-972, REQ-973*
  - `module-registry.spec.ts`: define a module type **that exists only in this
    file** — no contract member, no seed row, no component (D17) — and drive
    `mapWireModule` with a registry holding it beside the real `info` entry.
    Four cases: dispatch to the fabricated entry; `info` still maps with both
    registered; a discriminant in neither → `null` (REQ-973); a structurally
    invalid known type → `null` (REQ-972).
    **Header comment: this spec does not prove the registry's literals match
    the contract** — that is T-09's, and §8 explains why it cannot be here.
  - Files: `…/core/guest/module-registry.spec.ts` *(new)*
  - Validate: `ng test` green; `grep` confirms the fabricated discriminant
    appears in this file and nowhere else in the repository.

- [ ] **T-17** Mapper cases — bounds, isolation, silence
  — *REQ-963, REQ-972*
  - `guest-content.mapper.spec.ts`: 11 rows → dropped; a 241-character value →
    dropped; 10 rows and a 240-character value → mapped; a malformed module on
    one item leaves **every other item and the whole envelope intact** and
    `mapGuestContent` does **not** throw; a `console` spy proves nothing is
    logged on any drop path. Plus one case pinning the delivered menu order
    after the re-sort's removal (REQ-962's observable half).
  - Files: `…/core/guest/guest-content.mapper.spec.ts`
  - Validate: `ng test` green.

- [ ] **T-18** The cache classification guard, nested
  — *REQ-979*
  - `guest-data.service.spec.ts`: add the two compile-time tables of §7
    (`Record<keyof Required<GuestMenuItem>, true>` and
    `Record<keyof Required<GuestInfoModule>, true>`) beside Part B's top-level
    one, and extend the runtime case to reach **explicitly** into
    `menu.items[].module`. Keep Part B's credential assertions exactly as they
    are — **no name-based filter on `password`/`code`/`secret`**, which would
    assert close to the opposite rule. Add one case proving `/info` renders
    from the cache with the module intact.
  - Files: `…/core/guest/guest-data.service.spec.ts`
  - Validate: `ng test` green; then by hand, once — add a property to
    `GuestMenuItem` in the contract, regenerate, confirm the spec **fails to
    compile**, revert.

- [ ] **T-19** Host and component specs
  — *REQ-973, REQ-974*
  - `section-host.component.spec.ts`: a module renders its component with the
    resolved input; **no** module renders `SectionPlaceholderComponent`
    (`section.comingSoon`) with the sidebar entry intact; a route the menu does
    not claim renders `section.unavailable`.
    `info-module.component.spec.ts`: two rows in DOM order, `<dt>`/`<dd>` pairs,
    a language switch re-renders from held data and issues **no** request.
  - Files: `…/features/section-host/section-host.component.spec.ts` *(new)*,
    `…/features/info-module/info-module.component.spec.ts` *(new)*
  - Validate: `ng test` green, zoneless `TestBed` with a `PropertyContentService`
    double (never the real data file).

## Phase 8 — Measure, document, sweep

- [ ] **T-20** Measure `/info`, and the banner near-miss
  — *REQ-978*
  - `node tools/measure.mjs` — **`/info` is already in the route grid**, so no
    change to that instrument: every cell must report `scrollWidth <=
    clientWidth` and `scrollHeight <= clientHeight` at 1024×768 and 1280×832,
    both themes, both languages.
    Then add a `/info` variant of Part B's BANNER cell to
    `tools/measure-states.mjs` (navigate to `/info`, age the cached record's
    `storedAt` past the threshold, block `/api`, reload) and **report both
    boxes**: `info.md` computes that the bar clears the panel by 1.2 px at 1280
    and overlaps by 0.2 px at 1024.
    **Do not "fix" the overlap by moving anything.** The banner is an overlay by
    construction, the 0.2 px is against its padding rather than its copy, and
    the values are left-aligned so the bar lands on the panel's empty
    right-hand side. If a *real* collision with a value appears, change the CSS
    and re-measure — never `overflow: hidden`.
  - Files: `frontend/tools/measure-states.mjs`
  - Validate: every cell green; the measured banner/panel figures recorded in
    the commit message and, if they differ materially from the arithmetic,
    written back into `info.md` in the same commit.

- [ ] **T-21** Documentation sweep
  — *REQ-970, REQ-974, REQ-977*
  - `docs/system-context.md`: update the `de.gxp.menu`, `de.gxp.guest`,
    `guest-content.mapper.ts` and `SectionPlaceholderComponent` rows, and add
    rows for `SectionHostComponent`, `InfoModuleComponent` and
    `core/guest/module-registry.ts` (§13). Flip this feature's row in
    `docs/features/index.md`. Update
    `docs/features/module-content/handoff.md`.
  - Files: `docs/system-context.md`, `docs/features/index.md`,
    `docs/features/module-content/handoff.md`
  - Validate: no document still says `SectionPlaceholderComponent` is the
    component behind every section route; nothing claims a manager can author
    module content.

- [ ] **T-22** Whole suite, byte-identical client, final checks
  — *REQ-966, REQ-978, REQ-979*
  - `./mvnw verify` twice in a row — `packages/api-client/` **byte-identical**
    on the second build, `git status` clean after it, and **no orphan** under
    `model/`. `ng build` + `ng test`.
    Confirm: `package.json` untouched; `pom.xml` carries **only** the one
    approved line; the **five** architecture guards unchanged and no sixth
    added; `AuditCoverageTest` carries exactly the one added exemption entry;
    `SectionPlaceholderComponent` and its spec unmodified; `registered-icons.ts`
    unmodified (no new icon); the translation catalogues unmodified (no new
    chrome key).
  - Files: none
  - Validate: all green; **one commit** carries contract + `pom.xml` + backend
    + `packages/api-client/` + frontend + docs. **The owner commits.**

---

## Not in this feature — stop and report if a task seems to need it

- **A manager write path for module content** — any `manager*` endpoint, any
  `module_content` write API, any amendment to `docs/backend/specification.md`
  §3 decision 5. **D16 descoped it**; it is the successor feature
  `module-content-authoring`, and **no directory is created for it here**.
- **A second authored module type, or a second designed screen** — D17. The
  second type is fabricated and lives in T-16's spec file.
- **A generic `GuestRowsModule` member** — offered to the owner and **declined**
  (D14). Not a discovery.
- **A single-value `enum` on a member's `type`** — the one spelling that
  provably breaks (D9). Not "for safety".
- **`maxLength` on the discriminator property** — carved out deliberately
  (§1.3), and the same class of trap as the `enum`.
- **A per-field `sensitive` flag, a per-type cache classification, or "module
  content is never cached"** — all three withdrawn by D12. The WiFi row is
  exactly where they will be re-proposed.
- **Placeholder-shaped logic in the mapper** — a `{…}` in a value is not a drop
  trigger (D18, option (a) rejected).
- **A shared generic empty state** — D19. And do not collapse "no module"
  (→ placeholder) with "zero entries" (→ that type's own empty state).
- **A `{ path: 'info' }` route**, an SSID row, a WPA QR code (a new
  dependency), a per-row icon, a per-row sort key, a row id, or a `title` on
  the module — all closed by `info.md`.
- **A sixth architecture guard**, a new feature package with an exemption-list
  edit, or a `core → features` import — §2.1, §4.4, §11.
- **Any new dependency, `package.json` edit, further `pom.xml` edit, nested
  `CLAUDE.md` entry or constitution amendment** — propose the exact wording and
  **end the turn**.
- **A design-tool request** — `/info` is in-repo design
  (`docs/design/screens-inhouse/info.md`), and the request is metered.
