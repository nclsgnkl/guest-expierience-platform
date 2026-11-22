# Design: Sidebar & Catalogue Overflow
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Approved — no unresolved clarification marker, no open gate.

> Requirements live in [01-prd.md](01-prd.md) and are referenced, never
> restated. Owner decisions D1–D6 are settled there and are **not re-decided**
> here; this document says only *how*.
>
> **Screen spec:** the popover is designed in-repo per D6 —
> [`docs/design/screens-inhouse/menu-overflow.md`](../../design/screens-inhouse/menu-overflow.md).
> That file owns pixels, tokens and copy; this file owns structure.

---

## 1. Shape of the change

Six seams, in dependency order. Nothing else in the repository is touched.

| # | Seam | Files | REQ |
|---|---|---|---|
| 1 | Contract bound | `api/openapi.yaml` | 901, 902 |
| 2 | Catalogue data | `V10__catalogue_expansion.sql`, `DevDataSeeder`, `MenuFixtures` (test) | 903, 905–910 |
| 3 | Icons | `core/icons/registered-icons.ts` | 904 |
| 4 | Routing | `app.routes.ts`, `section-placeholder.component.*` | 912–914 |
| 5 | Content layer | `guest-content.mapper.ts`, `property.model.ts`, `property-content.service.ts` | 901, 925, 926, 933 |
| 6 | Sidebar + popover | `menu-split.ts` (new), `sidebar.component.*`, `shell.component.*`, i18n catalogues | 915–932, 934 |

**Explicitly not touched:** `guest-data.service.ts` (Part B owns it),
`MenuControl`/`MenuRepository`/`GuestControl` (no resolution change — REQ-909),
the three state routes and their guards (REQ-914), the action-card path, the
asset path, `PreferencesService`, and the mapper's `sortIndex` re-sort (Part C's
REQ-962). **No new Angular service, no new injectable, no new Java class.**

---

## 2. Contract — `GuestMenu.items.maxItems`

```yaml
GuestMenu:
  properties:
    items:
      type: array
      description: Resolved-enabled items only, ordered by resolved sort index.
      maxItems: 50            # <- added
```

**Why 50.** It is a payload-sanity and boundary-validation bound, not a design
limit (REQ-901): 2.5× the owner's "plan for 20+" planning figure, far above any
plausible release catalogue (13 after this change), and small enough that a
hostile or corrupt payload is rejected rather than mapped. The sidebar splits at
5 regardless of this number, so raising or lowering it changes no behaviour a
guest can see — which is exactly the property the requirement asks for.

**Generator effect.** `GuestHomeScreen.actionCards` already carries
`maxItems: 4`, so both generators have handled this construct in this repo since
`device-pairing-and-guest-api`. `jaxrs-spec` emits a bean-validation annotation
on the field (generated into `target/`, never committed); `typescript-angular`
emits nothing for `maxItems`, so `packages/api-client/` is expected to show
**zero diff**. REQ-902's criterion is unchanged either way: two `./mvnw verify`
runs, clean `git status`.

Response DTOs are not validated at runtime, so this adds no server-side
enforcement and cannot break a live payload. The enforcement that matters is the
kiosk's, at the boundary — `guest-content.mapper.ts` rejects an envelope whose
`menu.items` exceeds 50 (§5). **Corrected 2026-08-27** (Part A review 🟠 1): §5
originally deleted that check outright, which left this sentence describing an
enforcement that did not exist. The lead decided under the autonomy grant to keep
the guard at the contract's real bound; `docs/handoff.md` records it.

---

## 3. Catalogue data

### 3.1 `V10__catalogue_expansion.sql`

Three statements, no DDL (REQ-903, REQ-910):

1. eight `menu_item_definition` rows — keys, icons, routes, `default_sort_index`
   continuing **5…12** after the existing 0–4, `introduced_release = '1.1.0'`;
2. sixteen `menu_item_definition_translation` rows — `de` + `en` for each
   (REQ-905; the column is `NOT NULL CHECK (language IN ('de','en'))`);
3. **eight property-scope `menu_item_config` rows with `enabled = false`** —
   D5 (REQ-908). `room_id IS NULL`, every other column left `NULL` so per-field
   inheritance is untouched. `uq_menu_item_config_scope UNIQUE NULLS NOT
   DISTINCT (menu_item_key, room_id)` accepts them.

The migration carries a header comment stating (a) that the rows are disposable
fixtures, (b) that removing them is this migration reversed plus a
`registered-icons.ts` edit and nothing else, and (c) why statement 3 exists —
*absence resolves to enabled, and updates arrive automatically since constitution
v1.8.1*.

`introduced_release = '1.1.0'` is written for traceability only. **It stays
inert**: nothing reads the column, and this change deliberately does not make it
load-bearing (that was D5's rejected alternative). No code, test or seeder may
join on it.

**Version number.** `V10` is the next free slot. `instance-identity-and-certs`
(deferred 2026-08-26, never started) planned `V10__instance_enrollment.sql`; that
plan renumbers when it is picked up, which its own index entry already
anticipates.

### 3.2 What the migration does to a running property

| Property state before | After V10 |
|---|---|
| No `menu_item_config` rows (the shipped default) | Guests see **exactly the same five sections**. The eight new rows resolve `enabled = false` from their explicit property row. |
| Some property-scope rows, written by the manager | Same — the migration's `INSERT`s do not collide (different keys) and the manager's rows are untouched. |
| A room override enabling a new key | Impossible: the key did not exist before the migration. |

The ceiling rule (`MenuControl.enabled`) is unchanged and does the work:
property `false` wins over any room row (REQ-909).

### 3.3 The whole-set-replace consequence — recorded, pinned, not gated

`PUT /api/manager/menu-config` **replaces** a scope's entire override set
(`MenuControl.putConfig` → `repository.deleteConfigsOf(scope)` then re-insert).
Two consequences follow, and both are handled here rather than discovered later:

- **Product.** A manager client that submits a partial set deletes the opt-out
  rows, and absence resolves back to *enabled*. D5's opt-out is durable against
  the update channel — its purpose — but not against a careless API consumer.
  `backend/manager-ui/` does not exist; the obligation ("GET the scope, edit,
  PUT the whole set back") is recorded here and pinned by
  `CatalogueExpansionTest.partialConfigReplaceReEnablesTheFixtures`, so the
  future manager UI meets a documented contract instead of a surprise.
  Making this structurally impossible means making `introduced_release`
  load-bearing — D5's rejected alternative — and is deliberately not done.
- **Test isolation — and it is wider than one test.** The backend suite shares
  one database, and **five** classes replace the property-scope config, several
  with a deliberately partial body and a `finally` that clears it to `List.of()`:
  `MenuCatalogueAndConfigTest`, `MenuDisableGuardTest` (five sites),
  `ActionCardTest`, `GuestHomeScreenTest` and `AuditCoverageTest`. Each of them
  deletes the eight opt-out rows and leaves the fixtures **enabled** for whatever
  runs next — which would make `GuestSessionTest`'s `menu.items hasSize(5)` and
  `hasSize(4)` depend on class order.

  Fix, in two halves:

  1. a test-scope fixture
     `de.gxp.infrastructure.testsupport.MenuFixtures.restoreCatalogueOptOut()`
     — native `INSERT … ON CONFLICT DO NOTHING` for the eight property-scope
     rows (the `DeviceFixtures` precedent, already named in `backend/CLAUDE.md`
     → Tests, so **no new convention and no `CLAUDE.md` entry**) — called from an
     **`@AfterEach`** in each of those five classes. `@AfterEach` rather than
     each existing `finally`: one method per class instead of eleven edits, and a
     case added to those classes later is covered without anyone remembering;
  2. `CatalogueExpansionTest`, which *asserts* the D5 state rather than
     disturbing it, calls the same fixture from **`@BeforeEach`** — so it is
     deterministic even if a writer class is ever added without half 1.

### 3.4 `%dev` override (REQ-906)

`DevDataSeeder.seedExampleContent()` gains one statement:

```
UPDATE menu_item_config SET enabled = true
 WHERE room_id IS NULL AND menu_item_key IN (<the eight keys, as a Java constant>)
```

An explicit key list, not a join on `introduced_release` (§3.1). It sits
**inside** the existing `devDeviceExists()` early return, so the seeder keeps its
stated contract — *"never touch what a developer changed since"*. Consequence,
recorded: a developer with a pre-existing dev database that already has the dev
device sees the fixtures disabled until the Dev Services volume is dropped or the
rows are flipped through the manager API. Acceptable: `quarkus:dev` normally
starts a fresh Dev Services database.

`@IfBuildProfile("dev")` keeps all of this out of `%prod` and `%test` at
augmentation, so the backend suite sees the migration-only state — 13
definitions, 8 property-disabled — which is what makes §3.2's assertions real.

### 3.5 Icons (REQ-904)

Seven names are added to `REGISTERED_ICONS` (`phosphorFileText` is already
there): `phosphorWifiHigh`, `phosphorClock`, `phosphorRecycle`,
`phosphorFirstAid`, `phosphorCar`, `phosphorForkKnife`,
`phosphorPersonSimpleHike` — plus `phosphorDotsThree` for the overflow trigger
(§7.1). All verified present in the installed
`@ng-icons/phosphor-icons/regular` set on 2026-08-27.

The map is both the registration and the wire-validation set, so registering
them is what makes the migration's icon names valid wire content — the file's own
comment states the rule. **This is the entire second half of REQ-903's "removing
them later is one migration plus a `registered-icons.ts` edit".**

---

## 4. Routing (D4)

### 4.1 The table

```
'' (ShellComponent, shellGuard)
  ├ ''              HomeContentComponent
  ├ 'einstellungen' SettingsComponent
  ├ ':sectionKey'   SectionPlaceholderComponent
  └ '**'            SectionPlaceholderComponent
```

The four hard-coded section children and their `data: { sectionId }` go. Order is
load-bearing and is the owner's: `einstellungen` **before** `:sectionKey`, so the
real Settings screen keeps its route (REQ-914).

**Where `**` goes, and why not at the top level.** It is the shell's **last
child**, not a top-level route. The `''` route matches every URL as a prefix, so
the router descends into its children for anything that is not `/pairing`,
`/standby` or `/connection` (those are declared first and match first — REQ-914
holds by ordering, exactly as today). `:sectionKey` catches one segment; `**`
catches everything deeper. A top-level `**` would be unreachable **and** would
render outside the shell if it ever were reached — the worse of two placements.

**Why this is not a new pattern, re-checked at design time (D4).** A
parameterised child and a wildcard are ordinary `Routes` configuration, already
used across every Angular app; no route is built at runtime, no guard changes, no
service learns about routing, and `frontend/CLAUDE.md` → Structure already fixes
the convention this obeys (*"a section without a real design yet gets
`SectionPlaceholderComponent`"*). **No constitution gate and no nested
`CLAUDE.md` entry is required.**

### 4.2 `SectionPlaceholderComponent`

It stops reading `route.snapshot.data['sectionId']` — there is no `data` any
more, and a snapshot would go stale when only the parameter changes and Angular
reuses the component instance. Instead:

- the component derives the **current path** reactively from `ActivatedRoute`'s
  url segments (`'/' + segments.map(s => s.path).join('/')`), as a signal, so it
  re-renders when the router swaps `:sectionKey` under it;
- it looks the section up **by `route`, not by key**, against
  `PropertyContentService.property().menuItems` (+ `settings`), using the shared
  `routeMatches()` helper from `menu-split.ts` (§6.1). `key` and `route` are
  independent columns in `menu_item_definition`; matching on `route` is what
  makes "every route the catalogue can name resolves" literally true.

Two rendered states:

| State | Renders |
|---|---|
| The delivered menu claims this route | the section's `label` as `h1` + `t('section.comingSoon')` — **unchanged from today** |
| It does not (REQ-912's fallback) | `t('section.unavailable')` in the same title slot, no note |

No redirect. A silent bounce to Home would hide a real configuration error and
would fight the router; the guest is inside the shell with the sidebar present,
so the screen is not a dead end.

**Cold/offline boot (REQ-913) is satisfied structurally**: the route table is a
static constant, so it exists before the first navigation. Content comes from the
last-known-good cache, and `shellGuard` already diverts to `/connection` when
nothing is loaded — so the placeholder never runs against a throwing
`property()`. Nothing here is derived from a payload.

---

## 5. Content layer

- **`guest-content.mapper.ts`** — `MAX_MENU_ITEMS` is **corrected from 5 to 50**,
  the contract's declared `GuestMenu.items.maxItems` (REQ-901). The length check
  and the *"exceeds the contract's 0–N"* message stay, and the message becomes
  true for the first time: the defect was a bound this file **invented** while
  citing a contract that declared none, not the existence of a bound. It is
  written as the exact sibling of `MAX_CARDS = 4` a few lines below — named
  constant, length check, `GuestContentMappingError` — so the two read
  identically. Every element is still validated field-by-field exactly as before
  (`requireText`, `localized`, `safeIcon`, `requireRoute`). The `sortIndex`
  re-sort stays (Part C's REQ-962).

  > **Corrected 2026-08-27** (Part A review 🟠 1). This bullet previously said
  > the constant and its check were *deleted*, justified by *"the wire is trusted
  > for count the same way `actionCards` is"* — factually wrong, `MAX_CARDS = 4`
  > still rejects in the same function. That left §2's "the enforcement that
  > matters is the kiosk's" describing something no code did, and REQ-901's
  > acceptance criterion unmet. Lead decision under the autonomy grant, recorded
  > in `docs/handoff.md`; the owner may reverse it.
- **`property.model.ts`** — the `0–5` comments on `menuItems` are corrected to
  name the contract's `maxItems` and to say the count is a wire bound, not a
  layout one.
- **`property-content.service.ts`** — gains one signal:

  ```ts
  /** The active language's Intl locale, from the property's own option list. */
  readonly locale: Signal<string>   // languages.find(o => o.code === language())?.locale ?? language()
  ```

  It is the same lookup `StalenessPillComponent` and `StatusBarComponent`
  already perform inline. It lives here because it *is* language resolution,
  which this service already owns, and because putting it here is what keeps
  `SidebarComponent` a pure input-driven view (§6.2). Reading it re-runs on a
  language switch and fetches nothing — REQ-926. **The two existing inline
  lookups are deliberately not refactored onto it**: that is unrelated churn in
  files this feature otherwise does not touch. Recorded as a follow-up in §11.

---

## 6. The split, promotion and collation

### 6.1 `layout/sidebar/menu-split.ts` — exported pure functions

No service, no injectable, no state. The `resolveProperty` precedent: pure
functions exported for their own unit test, tested directly rather than through a
component (`frontend/CLAUDE.md` → Tests).

```ts
export const SIDEBAR_SLOTS = 5;                       // D3. The only place n lives.

export interface MenuSplit {
  readonly sidebar: ResolvedMenuItem[];   // in delivered order; length ≤ SIDEBAR_SLOTS − 1 when hasOverflow
  readonly overflow: ResolvedMenuItem[];  // alphabetical, empty unless hasOverflow
  readonly hasOverflow: boolean;
}

export function routeMatches(item: ResolvedMenuItem, url: string): boolean;
export function itemForRoute(items: readonly ResolvedMenuItem[], url: string): ResolvedMenuItem | null;
export function splitMenu(
  delivered: readonly ResolvedMenuItem[],
  activeUrl: string,
  chosenKey: string | null,
  locale: string,
): MenuSplit;
```

`splitMenu`, in full:

```
if (delivered.length <= SIDEBAR_SLOTS)                 // REQ-931
    → { sidebar: delivered, overflow: [], hasOverflow: false }

fixed    = delivered.slice(0, SIDEBAR_SLOTS - 2)       // ranks 1–3, never move (REQ-923)
rest     = delivered.slice(SIDEBAR_SLOTS - 2)          // rank 4 and below
active   = itemForRoute(delivered, activeUrl)

rotating = (active && active ∉ fixed) ? active         // REQ-932
         : rest.find(i => i.id === chosenKey)          // REQ-923 / REQ-930
         ?? rest[0]                                    // the delivered order

sidebar  = [...fixed, rotating]
overflow = sortByLabel(rest \ {rotating}, locale)      // REQ-919, 924, 925, 927
```

Every one of six requirements falls out of that one expression, which is why it
is a pure function and not scattered logic:

| REQ | How it is met |
|---|---|
| 920 | The only input is the delivered array. Delivered order **is** resolved `sortIndex` order (the server sorts, the mapper re-sorts identically). No `pinned` concept exists anywhere. |
| 923 | `fixed` is `slice(0, 3)` and is never rewritten; only the 4th element varies. With `n = 5` that is literally slot 4. |
| 928 | Active and promotion are separate terms. When `active` is one of `fixed`, the expression falls through to `chosenKey` — so the promoted module stays in slot 4, inactive. |
| 930 | `rest.find(…)` returns `undefined` when the poll no longer delivers the chosen key, and the `?? rest[0]` arm restores the delivered order. Nothing else references it. |
| 931 | The early return. With ≤ 5 delivered there is no overflow entry, no popover and no promotion — the sidebar is byte-for-byte today's. |
| 932 | The first arm. Reaching an overflow module by an action card, a deep link or browser back promotes it, so **the active module is always in the sidebar**. "Nothing looks active" is unreachable. |

**REQ-932 picks promotion, not an active overflow entry.** One rule instead of
two, one active treatment instead of two, and the promoted-and-active state is
identical however the guest got there. It has a corollary worth stating: **a
popover tile can never be the active route**, so the popover needs no active
variant at all. (If REQ-932 were ever resolved the other way, that corollary is
the thing that changes.)

`sortByLabel(items, locale)`:

```
[...items].sort((a, b) =>
  collatorFor(locale).compare(a.label, b.label)
  || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0))        // REQ-927, locale-independent tie-break
```

`collatorFor` is a module-level `Map<string, Intl.Collator>` holding
`new Intl.Collator(locale, { numeric: true })` — one per locale, for the module's
lifetime, the memoisation shape `StatusBarComponent`'s `timeFormatter` already
uses (REQ-925). The map never grows past the property's offered languages.

Labels are **already language-resolved** when they arrive here
(`ResolvedMenuItem.label` is a `string`), which is REQ-926 by construction: the
sort cannot run before resolution because unresolved labels are not in scope in
this file.

### 6.2 `SidebarComponent` — one owner, one computed

```ts
menuItems  = input.required<ResolvedMenuItem[]>();   // unchanged
settings   = input.required<ResolvedMenuItem>();     // unchanged
logoAsset  = input.required<string>();               // unchanged
activeRoute= input.required<string>();               // unchanged
locale     = input.required<string>();               // new — from PropertyContentService.locale

protected readonly chosen = signal<string | null>(null);
protected readonly split  = computed(() =>
  splitMenu(this.menuItems(), this.activeRoute(), this.chosen(), this.locale()));
```

The component stays **input-driven and injects no content service**, which its
own class comment promises and which is what lets a spec render it from plain
objects.

**REQ-929 is satisfied by construction, not by discipline.** `chosen` is
component state. It has no writer other than `choose()`, and it dies with the
component:

| Event | Why the promotion is gone |
|---|---|
| Reload / re-pair | New application, new component. |
| Vacancy episode | `GuestStateRoutingService` navigates to `/standby`, a **top-level** route, which destroys the `''` route's `ShellComponent` and with it the sidebar. Returning re-creates it with `chosen = null`. |
| A poll that drops the module | §6.1's `rest.find(…)` arm — REQ-930. |

Nothing writes it to `localStorage` or to a request body, and there is no code
path that could: the signal is `protected` and the component injects no gateway.
A spec asserts the negative anyway (§9).

**REQ-921** — this component renders both surfaces (§7) and computes both lists,
so exactly one place decides what looks active. `isActive()` is rewritten to use
`routeMatches()` so the sidebar and `SectionPlaceholderComponent` normalise a
route identically (Home's `''` ↔ `/`).

`ShellComponent` changes by one line: `[locale]="locale()"`, reading the new
`PropertyContentService.locale`.

---

## 7. The popover

Pixels, tokens, copy and both themes:
[`docs/design/screens-inhouse/menu-overflow.md`](../../design/screens-inhouse/menu-overflow.md).
Structure only, here.

### 7.1 Where it lives — inside `SidebarComponent`

The trigger tile and the panel `<ng-template>` are both in
`sidebar.component.html`; the tile grid's CSS is in `sidebar.component.css`.
**No new component.** Reasons, in order of weight:

1. REQ-921 says one renderer owns the visible active state; keeping both
   surfaces in one component makes that structural rather than a convention.
2. The trigger **is** a `.sidebar-item` — same box, same focus ring, same press
   treatment. A second component would have to duplicate that CSS or export it.
3. The `StatusBarMenuComponent` precedent does not apply: that was extracted
   because **two** triggers share it. This panel has one consumer. The
   extraction trigger is a second consumer, and it does not exist.

Consequence: `SidebarComponent` grows from ~45 to ~110 lines. Accepted, and
noted here so a reviewer sees a decision rather than drift.

The trigger renders `t('nav.more')` with `phosphorDotsThree` — the universal
"more" idiom. (`phosphorSquaresFour` was considered: it reads as "dashboard",
which is a *section*, not an affordance.)

### 7.2 CDK usage (REQ-922) — and exactly what the CDK does and does not give

Same three directives as `StatusBarMenuComponent`: `CdkMenuTrigger` on the
trigger button, `CdkMenu` on the panel div, `CdkMenuItem` on each tile, with
`(cdkMenuItemTriggered)` for activation. Verified against the installed
`@angular/cdk` 22.1.2 source:

| Behaviour | Supplied by | Note |
|---|---|---|
| open / close / Escape / Tab-closes / outside-click close | `CdkMenu._handleKeyEvent`, `_subscribeToOutsideClicks` | No backdrop, exactly as the status-bar dropdown |
| focus into the panel, focus **return** to the trigger | `MenuStack` + `focusParentTrigger` | |
| `role="menu"` / `role="menuitem"`, `aria-haspopup`, `aria-expanded` | the directives | The trigger needs no hand-written aria |
| arrow traversal, wrap, Home/End, typeahead | `FocusKeyManager(...).withWrap().withTypeAhead().withHomeAndEnd()` | `cdkMenuitemTypeaheadLabel` feeds typeahead with the resolved label |
| position | `menuPosition: ConnectedPosition[]` | The **only** overlay knob a caller has — see below |

**What it does not give, and the honest consequence.** `CdkMenuTrigger`'s
`_getOverlayConfig()` is private and fixed (`withLockedPosition()`,
`withFlexibleDimensions(false)`); a caller cannot set `panelClass`, a scroll
strategy or a `maxHeight`. So:

- **sizing is ours, in CSS** — the panel's own `max-height` + internal
  `overflow-y` (REQ-917). That is a better fit anyway: the constraint is
  expressed in tokens rather than in an overlay config.
- **the CDK's default push** keeps a centred panel inside the viewport;
  declared fallback positions make the outcome deterministic rather than relying
  on it.

Positions (REQ-916 — right of the trigger, vertically centred on it):

```ts
[ { originX: 'end', originY: 'center', overlayX: 'start', overlayY: 'center' },   // preferred
  { originX: 'end', originY: 'bottom', overlayX: 'start', overlayY: 'bottom' },   // tall panel, low trigger
  { originX: 'end', originY: 'top',    overlayX: 'start', overlayY: 'top'    } ]  // tall panel, high trigger
```

The gap to the trigger is the panel's own `margin-left: var(--space-4)`, not a
pixel offset in the position config — the `margin-top` convention
`status-bar-menu.component.css` already established, so the gap stays a token.

### 7.3 The 2-D keyboard gap — resolved by the ARIA contract, not by code

Verified, not assumed: `CdkMenu.orientation` is `readonly 'vertical'`, so its
`FocusKeyManager` is configured `withVerticalOrientation()`. Up/Down move ±1
through DOM order; Left/Right reach a vertical manager and do **nothing** (the
CDK calls `preventDefault()` first). A wrapping tile grid is 2-D. That is the gap
REQ-922 names.

**Resolution: the linear model is kept, and it is the correct one.** Not for
economy — because the ARIA role decides it:

- the panel ships `role="menu"` with `role="menuitem"` children, and that role's
  keyboard contract **is** linear: arrows walk the items in DOM order. A
  screen-reader user is told "menu, 9 items"; moving spatially would contradict
  what was announced;
- spatial traversal is `role="grid"`'s contract, and the CDK supplies no grid
  keyboard model. Implementing one under `role="menu"` would make the announced
  semantics and the behaviour disagree — a worse defect than the one it fixes;
- **REQ-919's row-major fill makes linear traversal identical to reading
  order.** Down = the next tile you would read. That is the property row-major
  was chosen for, and it makes the two requirements reinforce rather than fight;
- the device is a touchscreen with no keyboard. The keyboard model exists for
  accessibility and for development, and both are served by the standard menu
  contract.

So: Up/Down/Home/End/typeahead work and are documented as reading-order
traversal in the screen spec; Left/Right are inert and are deliberately **not**
faked. **Nothing beyond CSS and existing public APIs is required — no gate.**

*(Recorded, not chosen: true spatial traversal is implementable without a new
dependency — a `(keydown)` handler on a wrapper **inside** the `cdkMenu` element
sees the event before the CDK's host listener, can `stopPropagation()`, and can
call the public `CdkMenu.setActiveMenuItem(index ± columns)`. It is written down
so it is not re-derived, and it should only ever be built together with a move to
`role="grid"`.)*

### 7.4 Geometry (REQ-917, 918) — the arithmetic behind "5 columns"

Tile = the sidebar item's own derived box: `--size-sidebar (6.25rem) − 2 ×
--space-3 (0.5rem)` = **5.25rem square**, expressed in CSS as
`grid-template-columns: repeat(5, calc(var(--size-sidebar) - 2 * var(--space-3)))`
so the two stay one number (the `sidebar-item` "derived, not tokenised"
precedent). Panel padding `--space-4`, grid gap `--space-2`.

Panel width = `2 × 0.75 + C × 5.25 + (C − 1) × 0.375` rem:

| Columns | Panel content+padding | Border box (what a browser reports) | at 1024×768 | at 1280×832 |
|---|---|---|---|---|
| 5 | 29.25rem | 29.25rem + 2px | **376 px** | **470 px** |
| 6 | 34.875rem | 34.875rem + 2px | **448 px** | **560 px** |

The `+ 2px` is `--border-glass`'s 1 px hairline on each side. It is deliberately
not expressible in `rem` — `docs/constitution.md` → Styling exempts hairline
borders and blur radii from the rem rule — which is why the rem column and a
measured `getBoundingClientRect().width` differ by exactly 2 px and always will.
Measured 2026-08-27: 376 px / 470 px.

Tile at 1024×768 = 5.25rem × 12.8 = **67.2 px**, against `--size-touch-min`
2.75rem = 35.2 px. ✓ (`--size-touch-min` — `--size-touch-target` does not exist.)

**Measurement admits both**, so REQ-918's criterion is satisfied either way and
the choice is a stated tie-break, not a hidden preference: **5 columns**, because
(a) at 29.25rem the panel stays visually subordinate to the content area instead
of covering most of it, (b) five echoes the sidebar's five slots, so the panel
reads as "the rest of the same menu", and (c) the `%dev` fixture set puts 9
modules in the popover, which fills 5 + 4 — two substantially full rows. Moving
to 6 is a one-token change with no layout risk; the numbers above are the
evidence, and `tools/measure.mjs` confirms them in a real browser (§9).

**Unbounded by construction (D3, REQ-917).** No `maxItems`, no `slice`, no
"and N more" affordance exists anywhere in the panel. Height is the only limit
and it is expressed as scroll, not truncation:

```
max-height: calc(100dvh - 2 * var(--space-6));   /* stays inside the viewport */
overflow-y: auto;                                /* the grid scrolls, the document never does */
```

The panel is rendered into the CDK overlay container on `document.body`
(`position: fixed`), so it **cannot** grow the document — the no-scroll rule
(v1.8.0) is met structurally, and the route behind it still measures clean. At 5
columns the panel needs internal scroll only past ~50 modules at 1280×832, i.e.
past the contract's own `maxItems`; the rule is nonetheless implemented, because
"no capacity ceiling" is the requirement.

**No entry animation.** The status-bar dropdown, the pattern being reused, has
none, and the token layer has no motion tokens at all (`sidebar.component.css`
carries raw `180ms cubic-bezier(...)` values today). `docs/design/design-sheet.md`
→ Motion is an unspent spec; spending it means introducing motion tokens, which
is a separate, deliberate change and not this feature's.

---

## 8. i18n (REQ-934)

Three keys, added to **both** catalogues (`de.ts` is the key source of truth, so
`en.ts` fails the build until it follows):

| Key | de | en |
|---|---|---|
| `nav.more` | Mehr | More |
| `nav.more.aria` | Weitere Bereiche | More sections |
| `section.unavailable` | Dieser Bereich ist nicht verfügbar | This section is not available |

`nav.more` is the trigger tile's visible label — so the trigger gets **no**
`aria-label` (one would override the visible name). `nav.more.aria` is the
panel's `aria-label` on the `cdkMenu` element. Module labels inside the panel
arrive through `PropertyContentService`, never the catalogue (REQ-933/934).

Tone follows `docs/design/design-sheet.md` → Sprache: short, plain, no
"Feature"/"Dashboard" register.

---

## 9. Tests

**Frontend (Vitest + jsdom).** jsdom computes no layout, so nothing here asserts
geometry (`frontend/CLAUDE.md`).

| File | Covers |
|---|---|
| `menu-split.spec.ts` *(new)* | The whole of §6.1 as pure functions: ≤ 5 → no overflow (931); > 5 → 3 fixed + rotating + alphabetical rest (915, 919, 920, 923, 924); chosen key honoured, then dropped when undelivered (930); active-route promotion and the fall-through when the active item is fixed (928, 932); `Intl.Collator` de vs. en ordering incl. the umlaut and `numeric: true` (925); identical labels tie-break by key, twice, stable (927); `routeMatches` normalises `''` ↔ `/`. |
| `sidebar.component.spec.ts` *(extended)* | Overflow tile **absent from the DOM** at ≤ 5 (931); present above; the panel lists the remainder; choosing a tile promotes it into slot 4 and navigates (923); slots 1–3 unchanged; the trigger label and the panel `aria-label` come from `TranslationService` (934); after a promotion, `localStorage` is untouched and no request is made (929). |
| `section-placeholder.component.spec.ts` *(extended)* | Resolves by URL against the delivered menu, for a known route and for Home's `''` ↔ `/`; an unclaimed route renders `t('section.unavailable')` and does not throw (912); a param change re-resolves without re-creating the component. |
| `guest-content.mapper.spec.ts` *(extended)* | 12 items map without error and none is dropped (901); **50 items map and 51 are rejected** with *"exceeds the contract's 0–50"* — the bound `api/openapi.yaml` actually declares (901's acceptance criterion); the old *"0–5"* message no longer exists anywhere. |
| `property-content.service.spec.ts` *(extended)* | `locale` resolves from the offered options and falls back to the language code; it changes on a language switch with no fetch (926). |

**Backend (`quarkus-junit` + `rest-assured`).**

| File | Covers |
|---|---|
| `menu/CatalogueExpansionTest.java` *(new)* | `@BeforeEach restoreCatalogueOptOut()`. After V10, the manager catalogue lists 13 definitions and the five originals keep positions 0–4 (903, 910); a guest session for a room with no config rows delivers **exactly the original five** (908); enabling one new key at property scope makes it the sixth (908); a room-scope enable of a property-disabled key stays absent — the ceiling (909); `partialConfigReplaceReEnablesTheFixtures` pins §3.3's semantics as a known, documented behaviour. |
| `menu/MenuCatalogueAndConfigTest.java` *(amended)* | `hasSize(5)` → `hasSize(13)` in `catalogueEqualsTheFiveFrontendTuples`, renamed accordingly (positions 0–4 unchanged — `MenuRepository.catalogue()` orders by `defaultSortIndex`). Plus the `@AfterEach` of §3.3. |
| `system/FirstRunSeedTest.java` *(amended)* | The **second** existing test the migration invalidates: its step 3 asserts `hasSize(5)` **and the exact five-key list**. The size becomes 13; the key list is narrowed to `key[0..4]` and keeps naming only the five real sections — REQ-903 forbids a test depending on a fixture key. *(Corrected 2026-08-27 after the Part A review; this row previously read "Both become the 13-row catalogue", which read literally means enumerating thirteen keys.)* It deliberately keeps asserting **definitions only, never the config rows** — it is a shared-database test with no `@AfterEach`, so asserting enablement there would reintroduce the very order-dependence §3.3 removes. The D5 enablement assertions live in `CatalogueExpansionTest`. |
| `menu/MenuDisableGuardTest.java`, `menu/ActionCardTest.java`, `guest/GuestHomeScreenTest.java`, `audit/AuditCoverageTest.java` *(amended)* | One `@AfterEach restoreCatalogueOptOut()` each (§3.3). No assertion in them changes: they submit partial property-scope sets and assert conflicts and audit shapes, neither of which the eight extra enabled rows affect — no action card targets a fixture key. |
| `audit/AuditCoverageTest.java` *(run, not changed)* | REQ-911: this feature adds no `@Transactional public` Control method, so the test must stay green untouched. If it does not, that is the signal. |
| `infrastructure/testsupport/MenuFixtures.java` *(new, test scope)* | §3.3's `restoreCatalogueOptOut()`. |

**Browser measurement (`node tools/measure.mjs`) — REQ-935.** `ROUTES` grows
from 6 to 14 to cover the `%dev` catalogue (`/wlan`, `/anreise`, `/hausordnung`,
`/muell`, `/aerztliches`, `/parken`, `/gastronomie`, `/aktivitaeten`), giving
14 × 2 themes × 2 languages × 3 viewports. A new step opens the overflow panel
and reports its box, its tile boxes and the document's scroll dimensions **while
open** — modelled on the existing REQ-115 dropdown-overlay routine, which already
knows the panel renders into the CDK overlay container on `document.body` and not
inside `.shell`. The instrument still only reports; it asserts nothing
(constitution v1.3.0).

---

## 10. Risks & trade-offs

| Risk | Judgement |
|---|---|
| **D5's opt-out is deletable through the manager API** (§3.3) | Accepted and pinned by a test. The structural fix is D5's rejected alternative. |
| **`SidebarComponent` gains weight** (§7.1) | Accepted. The logic that would justify extraction is already out, in `menu-split.ts`; what remains is markup. |
| **Left/Right arrows are inert in the panel** (§7.3) | Accepted deliberately: correct for `role="menu"`, and the device has no keyboard. |
| **`measure.mjs` hard-codes 14 routes** | Accepted; the list must track the `%dev` catalogue. Deriving it from the DOM is a bigger change to a review instrument than the problem warrants. |
| **A developer's existing dev database keeps the fixtures disabled** (§3.4) | Accepted; the alternative violates the seeder's "never overwrite" contract. |
| **`V10` collides with a deferred plan** | Recorded; that plan is explicitly not treated as a spec until re-validated. |

---

## 11. `docs/system-context.md` updates (this phase)

Applied as part of `02_design`, per the planner's standing rule — and applied in
the way that file's own convention demands. Its component tables *"still list
only real code"*, so the four affected rows are **not** rewritten yet. Instead a
marked **planned / implementation-pending** block was added before
"Cross-Cutting Concerns", on the precedent the `instance-identity-and-certs`
block already set. It covers:

- `SidebarComponent` — no longer "5 `menuItems`"; five fixed slots, rotating
  slot 4, overflow trigger, popover, promotion, locale-aware ordering.
- `layout/sidebar/menu-split.ts` — a new **file, not a service**, and the single
  home of `n`.
- `SectionPlaceholderComponent` — every catalogue route via `:sectionKey` plus
  the `**` fallback and the unclaimed-route state.
- `PropertyContentService` — the new `locale` signal.
- The binding facts: the contract bound, the disposable fixtures, D5's
  property-disabled arrival, decision 5 standing, and §3.3's whole-set-replace
  limitation.

T-21 folds these into the real-code tables once `dev` has landed them.

**Recorded follow-up, not done here:** `StalenessPillComponent` and
`StatusBarComponent` still resolve the locale inline; they can move onto
`PropertyContentService.locale` at their next touch.

---

## 12. Open decisions

**None.** D1–D6 are settled in the PRD; D4's "no new pattern" claim was
re-checked at design time (§4.1) and holds, so **no `frontend/CLAUDE.md` entry
and no constitution amendment is proposed by this design**.
