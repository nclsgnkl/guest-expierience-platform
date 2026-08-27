# TODO: Sidebar & Catalogue Overflow
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** Phases 1–3 implemented (T-01…T-09). Phase 4 next, after review.

> Every design decision is in [02-design.md](02-design.md) and is referenced by
> section, never re-decided here. Screen spec:
> [`docs/design/screens-inhouse/menu-overflow.md`](../../design/screens-inhouse/menu-overflow.md).
>
> **Branch:** stay on `refactor/bce-subpackages-and-boundary-security` (owner
> instruction). **One commit at the end** — contract + backend +
> `packages/api-client/` + frontend + docs together, per the contract-first rule.
>
> **Two standing prohibitions for this feature.** (1) No `frontend/CLAUDE.md`
> entry and no constitution amendment may be written — propose the exact wording
> and stop. (2) If any task turns out to need a `GuestModule` union or a module
> registry, **stop and report**: that is Part C being pulled forward and the
> design says this feature does not need it.

**21 tasks, six phases.** Phases 1–3 are independently verifiable before any UI
exists; phase 5 is the only one that cannot be split further.

---

## Phase 1 — Contract

- [x] **T-01** Declare the menu bound in `api/openapi.yaml`
  — *REQ-901, REQ-902*
  - Add `maxItems: 50` to `GuestMenu.items` (design §2). Nothing else in the
    contract changes.
  - Files: `api/openapi.yaml`, `packages/api-client/**` (generated)
  - Validate: `./mvnw verify` twice from clean → `git status` clean under
    `packages/api-client/` (expected: **zero diff** — `typescript-angular` emits
    nothing for `maxItems`). Backend compiles; the generated
    `GuestMenu` under `target/` carries the bean-validation annotation, exactly
    as `GuestHomeScreen.actionCards`' existing `maxItems: 4` already does.

## Phase 2 — Catalogue data

- [x] **T-02** `V10__catalogue_expansion.sql`
  — *REQ-903, REQ-905, REQ-907, REQ-908, REQ-909, REQ-910*
  - Three statement groups, **no DDL** (design §3.1): 8 `menu_item_definition`
    rows (`default_sort_index` 5…12, `introduced_release = '1.1.0'`), 16
    `menu_item_definition_translation` rows (`de` + `en`), and **8 property-scope
    `menu_item_config` rows with `enabled = false`, `room_id IS NULL`** — D5.
  - Keys, labels and icons: PRD → "Placeholder catalogue entries". Copy them
    exactly; the umlaut label and the alphabetical-vs-`sortIndex` divergence are
    load-bearing (REQ-907), the individual keys are not.
  - Header comment must state: these are **disposable fixtures**; removing them
    is this migration reversed plus a `registered-icons.ts` edit and nothing
    else; and why the `enabled = false` rows exist (absence resolves to enabled,
    and updates arrive automatically since constitution v1.8.1).
  - `introduced_release` stays inert — do not read it, join on it or gate on it.
  - Files: `backend/app/src/main/resources/db/migration/V10__catalogue_expansion.sql`
  - Validate: `./mvnw verify` boots against a fresh Dev Services Postgres and
    migrates; `GET /api/manager/menu/catalogue` returns 13.

- [x] **T-03** Test fixture + repair the shared-database hazard
  — *REQ-903, REQ-908*
  - New `de.gxp.infrastructure.testsupport.MenuFixtures.restoreCatalogueOptOut()`
    — native `INSERT … ON CONFLICT DO NOTHING` for the eight property-scope
    opt-out rows (design §3.3; the `DeviceFixtures` precedent named in
    `backend/CLAUDE.md` → Tests, so **no new convention and no `CLAUDE.md`
    entry**).
  - **Five classes replace the property-scope config and therefore delete those
    rows** — `MenuCatalogueAndConfigTest`, `MenuDisableGuardTest` (five sites),
    `ActionCardTest`, `GuestHomeScreenTest`, `AuditCoverageTest`. Give each an
    **`@AfterEach`** calling the fixture. `@AfterEach`, not each existing
    `finally`: one method per class instead of eleven edits, and a case added
    later is covered without anyone remembering. No assertion in the four
    non-catalogue classes changes.
  - **Two existing tests are invalidated by the migration and must be amended:**
    `MenuCatalogueAndConfigTest.catalogueEqualsTheFiveFrontendTuples`
    (`hasSize(5)` → `hasSize(13)`, rename it; positions 0–4 unchanged because
    `MenuRepository.catalogue()` orders by `defaultSortIndex`) and
    `FirstRunSeedTest` step 3 (`hasSize(5)` → `hasSize(13)`; its exact five-key
    list is **narrowed to `key[0..4]`**, never widened to thirteen — naming a
    fixture key in a test file is precisely what REQ-903 forbids and what T-21
    greps for). `FirstRunSeedTest` keeps asserting **definitions
    only, never the config rows** — it has no `@AfterEach` and asserting
    enablement there would reintroduce the order-dependence this task removes.
    *(Corrected 2026-08-27 after the Part A review; this bullet previously read
    "its exact five-key list → the 13-row catalogue", which taken literally
    would have created the REQ-903 violation T-21 exists to detect.)*
  - Files: `backend/app/src/test/java/de/gxp/infrastructure/testsupport/MenuFixtures.java`,
    `…/de/gxp/menu/MenuCatalogueAndConfigTest.java`,
    `…/de/gxp/menu/MenuDisableGuardTest.java`, `…/de/gxp/menu/ActionCardTest.java`,
    `…/de/gxp/guest/GuestHomeScreenTest.java`, `…/de/gxp/audit/AuditCoverageTest.java`,
    `…/de/gxp/system/FirstRunSeedTest.java`
  - Validate: the whole backend suite green, **run twice**, and once with a
    genuinely different class order —
    `-Djunit.jupiter.testclass.order.default='org.junit.jupiter.api.ClassOrderer$Random'`
    plus a recorded `-Djunit.jupiter.execution.order.random.seed=…`.
    **`-Dsurefire.runOrder` does not work here**: Quarkus' profile-aware orderer
    overrides Surefire's, and the class order comes out byte-identical (measured
    2026-08-27). Record the order the seed produced, and check a property-scope
    writer really preceded `GuestSessionTest` — the point is that its
    `menu.items hasSize(5)`/`hasSize(4)` no longer depend on class order.

- [x] **T-04** `CatalogueExpansionTest`
  — *REQ-903, REQ-908, REQ-909, REQ-910*
  - `@BeforeEach restoreCatalogueOptOut()` — this class *asserts* the D5 state,
    so it arranges it, and is then deterministic even if a writer class is ever
    added without T-03's `@AfterEach`.
  - Cases (design §9): 13 definitions with the five originals at positions 0–4;
    a guest session for a room with no config rows delivers **exactly the
    original five keys**; enabling one new key at property scope makes it the
    sixth; a room-scope enable of a property-disabled key stays absent (the
    ceiling); and `partialConfigReplaceReEnablesTheFixtures`, which pins design
    §3.3's whole-set-replace behaviour as documented rather than surprising.
  - Files: `backend/app/src/test/java/de/gxp/menu/CatalogueExpansionTest.java`
  - Validate: green; the "exactly five" case is the direct assertion REQ-908's
    acceptance criterion asks for.

- [x] **T-05** `%dev` seed enables the fixtures
  — *REQ-906*
  - One `UPDATE menu_item_config SET enabled = true WHERE room_id IS NULL AND
    menu_item_key IN (…)` in `seedExampleContent()`, with the eight keys as a
    Java constant — **not** a join on `introduced_release` (design §3.4). It
    stays **inside** the `devDeviceExists()` early return so the seeder keeps its
    "never overwrite what a developer changed" contract.
  - Files: `backend/app/src/main/java/de/gxp/infrastructure/devseed/DevDataSeeder.java`,
    `backend/app/src/test/java/de/gxp/infrastructure/devseed/DevDataSeederTest.java`
  - Validate: `quarkus:dev` against a **fresh** Dev Services database → the guest
    session delivers 13 menu items. `%prod`/`%test` are unaffected
    (`@IfBuildProfile("dev")`), which T-04's five-key assertion proves.

- [x] **T-06** Confirm the audit and architecture surface is untouched
  — *REQ-911*
  - No new `@Transactional public` Control method is expected — this feature adds
    no Control at all. Verify rather than assume.
  - Files: none (verification only)
  - Validate: `AuditCoverageTest` green with **its assertions and its
    `NOT_A_MANAGER_MUTATION` exemption list untouched** (T-03 adds only an
    `@AfterEach` for database hygiene — that is not a change to what it
    asserts); the five `de.gxp.architecture` guards green and unedited. If
    either needs a real change, stop and report — it means the design was
    departed from.

## Phase 3 — Frontend content layer

- [x] **T-07** Register the new glyphs
  — *REQ-904*
  - Add `phosphorWifiHigh`, `phosphorClock`, `phosphorRecycle`,
    `phosphorFirstAid`, `phosphorCar`, `phosphorForkKnife`,
    `phosphorPersonSimpleHike` and `phosphorDotsThree` (the overflow trigger,
    design §7.1) to `REGISTERED_ICONS`. `phosphorFileText` is already there.
  - Files: `frontend/src/app/core/icons/registered-icons.ts`
  - Validate: `ng build`; and — the point of the requirement — no popover tile
    renders `phosphorCircle` in T-19's run.

- [x] **T-08** Correct the invented menu bound
  — *REQ-901, REQ-933*
  - **Corrected 2026-08-27** after the Part A review (🟠 1); this task originally
    read *"delete `MAX_MENU_ITEMS`, its check and the 0–5 message"*, which left
    REQ-901's rejection criterion enforced by nothing. Lead decision in
    `docs/handoff.md`.
  - Set `MAX_MENU_ITEMS` to **50** — the contract's declared
    `GuestMenu.items.maxItems` — and keep its length check and its
    *"exceeds the contract's 0–N"* message, now a true statement. Write it as the
    exact sibling of `MAX_CARDS = 4` below it. The defect was a bound this file
    invented while citing a contract that declared none; both bounds are real
    now. Per-element validation is untouched.
  - Correct the `0–5` comments in `property.model.ts` to name the contract's
    `maxItems` and to say the count is a wire bound, not a layout one.
  - Leave the `sortIndex` re-sort alone — that is Part C's REQ-962 (design §5).
  - Files: `frontend/src/app/core/guest/guest-content.mapper.ts`,
    `frontend/src/app/core/content/property.model.ts`,
    `frontend/src/app/core/guest/guest-content.mapper.spec.ts`
  - Validate: a new spec case maps 12 items with no error and drops none; a
    second maps 50 and rejects 51 against the *"0–50"* message;
    `grep -r "0–5" frontend/src` finds nothing outside the `0–50` bound itself.

- [x] **T-09** `PropertyContentService.locale`
  — *REQ-925, REQ-926*
  - One `computed`: the active language's `locale` from the property's own
    option list, falling back to the language code (design §5). Do **not**
    refactor `StalenessPillComponent` or `StatusBarComponent` onto it — recorded
    follow-up, not this feature.
  - Files: `frontend/src/app/core/content/property-content.service.ts`,
    `frontend/src/app/core/content/property-content.service.spec.ts`
  - Validate: spec — resolves from the offered options, falls back to the bare
    code, and changes on a language switch **with no fetch**.

## Phase 4 — Routing and app-chrome strings

- [ ] **T-10** Three catalogue keys
  — *REQ-934*
  - `nav.more`, `nav.more.aria`, `section.unavailable` in **both** catalogues,
    wording per design §8 / the screen spec's Content table. `de.ts` is the key
    source of truth, so `en.ts` fails the build until it follows.
  - Files: `frontend/src/app/core/i18n/catalog/de.ts`,
    `frontend/src/app/core/i18n/catalog/en.ts`
  - Validate: `ng build` (a missing English key is a compile error by design).

- [ ] **T-11** The parameterised route table
  — *REQ-912, REQ-914*
  - Replace the four hard-coded section children with
    `{ path: ':sectionKey', component: SectionPlaceholderComponent }` **after**
    `einstellungen`, and add `{ path: '**', component: SectionPlaceholderComponent }`
    as the shell's **last child** — not a top-level route (design §4.1; the
    reasoning about why is in the file's comment too).
  - The three state routes, their guards and `shellGuard` are untouched.
  - Files: `frontend/src/app/app.routes.ts`
  - Validate: existing router specs green; deep links to `/pairing`,
    `/standby`, `/connection` and `/einstellungen` still reach the same
    component behind the same guard (REQ-914's acceptance criterion).

- [ ] **T-12** `SectionPlaceholderComponent` resolves by URL
  — *REQ-912, REQ-913*
  - Derive the current path reactively from `ActivatedRoute`'s url segments (a
    signal, **not** `snapshot`, so a param change re-resolves without
    re-creating the component) and match the delivered menu **by `route`**, via
    `routeMatches()` from `menu-split.ts` — so land T-13 first or add the helper
    with this task and import it there.
  - Two states (design §4.2): claimed route → label + `section.comingSoon`,
    unchanged; unclaimed → `t('section.unavailable')` in the title slot, no
    note, **no redirect**.
  - Files: `frontend/src/app/features/section-placeholder/section-placeholder.component.ts`,
    `…/section-placeholder.component.html`,
    `…/section-placeholder.component.spec.ts`
  - Validate: spec — a known route, Home's `''` ↔ `/`, an unclaimed route
    (renders the fallback, does not throw), and a param change re-resolving.
    Offline/cold-boot (REQ-913) is structural — the route table is a static
    constant — and is re-checked live in T-19.

## Phase 5 — Sidebar split and popover

- [ ] **T-13** `menu-split.ts` — the whole rule as pure functions
  — *REQ-915, REQ-919, REQ-920, REQ-923, REQ-924, REQ-925, REQ-926, REQ-927, REQ-928, REQ-930, REQ-931, REQ-932*
  - `SIDEBAR_SLOTS = 5` (**the only place `n` lives**), `routeMatches`,
    `itemForRoute`, `splitMenu`, `sortByLabel`, `collatorFor`. Implement design
    §6.1 exactly — the five-line `splitMenu` expression is what makes six
    requirements fall out at once; do not restructure it into branches.
  - No service, no injectable, no state beyond the module-level collator map.
  - Files: `frontend/src/app/layout/sidebar/menu-split.ts`,
    `frontend/src/app/layout/sidebar/menu-split.spec.ts`
  - Validate: the spec covers every row of design §6.1's requirement table,
    plus: `Intl.Collator` de vs. en ordering including the umlaut label (the
    `Ärztliche Versorgung` / `Services` / `Umgebung` case from the PRD's
    acceptance criteria) and `numeric: true`; identical labels tie-breaking by
    key, asserted twice for stability; and a chosen key that the next call no
    longer delivers.

- [ ] **T-14** Wire `SidebarComponent` and `ShellComponent`
  — *REQ-915, REQ-920, REQ-921, REQ-923, REQ-928, REQ-929, REQ-930, REQ-931, REQ-932*
  - `locale` input; `chosen = signal<string | null>(null)`; one `split` computed
    (design §6.2). Rewrite `isActive()` onto `routeMatches()` so the sidebar and
    the placeholder normalise a route identically. `choose()` sets `chosen` and
    navigates.
  - The component stays **input-driven and injects no content service** — its own
    class comment promises that.
  - `ShellComponent`: one new binding, `[locale]="locale()"` from
    `PropertyContentService.locale`.
  - Files: `frontend/src/app/layout/sidebar/sidebar.component.ts`,
    `frontend/src/app/layout/sidebar/sidebar.component.html`,
    `frontend/src/app/layout/shell/shell.component.ts`,
    `frontend/src/app/layout/shell/shell.component.html`
  - Validate: `ng build`; existing sidebar and shell specs still green before
    T-17 adds the new ones.

- [ ] **T-15** The popover — markup and CDK
  — *REQ-916, REQ-917, REQ-922, REQ-933, REQ-934*
  - Inside `SidebarComponent` (design §7.1 — **no new component**): the trigger
    `.sidebar-item` with `phosphorDotsThree` + `t('nav.more')` and
    `[cdkMenuTriggerFor]`, behind `@if (split().hasOverflow)`; an `<ng-template>`
    holding the `cdkMenu` panel with `[attr.aria-label]="t('nav.more.aria')"`;
    one `cdkMenuItem` tile per overflow entry with
    `[cdkMenuitemTypeaheadLabel]="item.label"` and `(cdkMenuItemTriggered)`.
  - `menuPosition`: the three `ConnectedPosition`s of design §7.2, in that
    order. No pixel offsets — the gap is CSS (T-16).
  - No `aria-label` on the trigger (the visible label is its name); no active
    variant on a tile (design §6.1's corollary).
  - Files: `frontend/src/app/layout/sidebar/sidebar.component.html`,
    `frontend/src/app/layout/sidebar/sidebar.component.ts` (CDK imports)
  - Validate: `ng build`; open the panel by hand in `ng serve` and confirm
    Escape, outside-click and focus-return work with nothing hand-written.

- [ ] **T-16** The popover — CSS and geometry
  — *REQ-917, REQ-918, REQ-919*
  - Panel: the glass recipe, `padding: var(--space-4)`,
    `margin-left: var(--space-4)` (the gap, as a token),
    `max-height: calc(100dvh - 2 * var(--space-6))`, `overflow-y: auto` on the
    grid.
  - Grid: `repeat(5, calc(var(--size-sidebar) - 2 * var(--space-3)))`,
    `gap: var(--space-2)`, row-major. Tile: `aspect-ratio: 1`, the sidebar
    item's internals, label wrapping to **2 lines, clamped**.
  - Semantic tokens only — no raw hex, no raw px (constitution principle 5). No
    entry animation (design §7.4).
  - Files: `frontend/src/app/layout/sidebar/sidebar.component.css`
  - Validate: T-19's browser run. jsdom asserts no geometry, ever
    (`frontend/CLAUDE.md`).

- [ ] **T-17** Sidebar specs
  — *REQ-921, REQ-923, REQ-929, REQ-931, REQ-934*
  - Extend `sidebar.component.spec.ts` (design §9): the overflow tile is
    **absent from the DOM** at ≤ 5 items and present above; the panel lists the
    remainder; choosing a tile promotes it into slot 4 and navigates, with slots
    1–3 unchanged; the trigger label and the panel `aria-label` come from
    `TranslationService`; and — the negative REQ-929 asks for — after a
    promotion `localStorage` is untouched and no request is made.
  - Files: `frontend/src/app/layout/sidebar/sidebar.component.spec.ts`
  - Validate: `ng test` green, whole suite.

## Phase 6 — Verification

- [ ] **T-18** Extend the measurement instrument
  — *REQ-935*
  - `ROUTES` 6 → 14 (the `%dev` catalogue's routes; keep the list beside a
    comment saying it must track that catalogue). Add a step that opens the
    overflow panel and reports its box, its tile boxes and the **document's**
    scroll dimensions while open — model it on the existing REQ-115
    dropdown-overlay routine, which already knows the panel renders into the CDK
    overlay container on `document.body`.
  - It still only reports. No assertions, nothing wired into `npm test`
    (constitution v1.3.0).
  - Files: `frontend/tools/measure.mjs`
  - Validate: it runs and prints; Prettier-clean.

- [ ] **T-19** Measure
  — *REQ-918, REQ-935*
  - Backend on the `%dev` seed, frontend served. Full grid: 14 routes × 2 themes
    × 2 languages × 3 viewports, panel **closed**; then the panel-open step at
    1024×768 and 1280×832 in both themes and both languages.
  - Record: `scrollWidth <= clientWidth` and `scrollHeight <= clientHeight` on
    every cell; the panel fits the content area; every tile's smaller dimension
    ≥ `--size-touch-min` (2.75 rem — **the token is `--size-touch-min`**); no
    tile shows `phosphorCircle` (T-07); Home's signed-off baselines (heading
    x 172 / y 88, cards bottom 744 at 1280×832) are unchanged.
  - Files: none (measurement)
  - Validate: the numbers land in the review notes. Design §7.4 predicts a
    374 px panel at 1024×768 — a material deviation from that means the CSS
    departed from the design.

- [ ] **T-20** Whole-suite green and the byte-identical client
  — *REQ-902, REQ-911*
  - `./mvnw verify` (fresh Postgres), `ng test`, `ng build`, then a second
    `./mvnw verify`; the second run leaves the tree unchanged relative to the
    first, the only file the contract change touches under `packages/api-client/`
    is `.openapi-generator/*.sha256` (the generator's input hash, which must
    change when its input does) and no generated source moves;
    `pom.xml`/`package.json` untouched (no dependency was added — the popover is
    the already-approved `@angular/cdk/menu`).
  - **One backend run in a genuinely different class order**, same mechanism as
    T-03: `ClassOrderer$Random` with a recorded seed. Do **not** use
    `-Dsurefire.runOrder` — Quarkus' orderer overrides it and the order comes out
    byte-identical, so the check would always pass (measured 2026-08-27).
    Record the seed and the order it produced in the handoff.
  - Files: none
  - Validate: all green; `AuditCoverageTest`'s assertions and exemption list and
    the five architecture guards unchanged.

- [ ] **T-21** Documentation sweep and the single commit
  — *REQ-903*
  - Confirm `docs/system-context.md`'s four updated rows still describe what
    shipped (they were written in `02_design`); flip this feature's row in
    `docs/features/index.md`; confirm the migration's header comment says
    removing the fixtures is one migration plus a `registered-icons.ts` edit —
    then **prove it by inspection**: grep that no fixture **key or label**
    appears in any `.ts`, `.html`, `.css` or test file outside
    `V10__catalogue_expansion.sql`, `DevDataSeeder`, `MenuFixtures` and
    `measure.mjs`'s route list. A hit anywhere else means REQ-903's
    disposability was broken.
  - The grep is **key-based and will not catch the catalogue COUNT**: five
    assertions across four test classes pin it at 13 (`CatalogueExpansionTest`
    twice, `MenuCatalogueAndConfigTest`, `FirstRunSeedTest`, `DevDataSeederTest`).
    That is stricter than REQ-903's *"count ≥ 12"* and deliberate — a hard count
    catches a duplicated insert. Confirm the migration header names those five
    numbers as the one other thing an add/remove touches.
  - Files: `docs/system-context.md`, `docs/features/index.md`
  - Validate: the grep is clean; one commit carries contract + backend +
    `packages/api-client/` + frontend + docs. **The user commits.**

---

## Not in this feature — stop and report if a task seems to need it

- A `GuestModule` union, `oneOf`/`discriminator`, or any module registry — Part C.
- Touching `guest-data.service.ts`, the two guest fetches or the ETag — Part B.
- Making `introduced_release` load-bearing — D5's rejected alternative.
- Runtime `Router.resetConfig()` — rejected by D4.
- A `parent_key` / catalogue hierarchy — closed by D2.
- Any new dependency, `frontend/CLAUDE.md` entry or constitution amendment —
  propose the exact wording and **end the turn**.
