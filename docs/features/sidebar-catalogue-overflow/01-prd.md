# PRD: Sidebar & Catalogue Overflow
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Approved — every decision this Part depends on is settled (owner, **2026-08-27**). No open gate.

> **Part A of three.** The 2026-08-27 owner decision **D1** split
> `modular-menu-catalogue` into three sequenced features. This is the first and
> the only one the owner actually asked for — *"die Sidebar, die strukturell
> verbessert werden soll"*. The other two:
> [`guest-payload-consolidation`](../guest-payload-consolidation/01-prd.md)
> (Part B, REQ-941…951) and [`module-content`](../module-content/01-prd.md)
> (Part C, REQ-961…979, **not ready to plan**). The retired parent directory
> keeps a pointer: [`modular-menu-catalogue/README.md`](../modular-menu-catalogue/README.md).
>
> **Requirement IDs are stable.** REQ-901…REQ-935 mean here exactly what they
> meant in the parent PRD; the block moved unchanged. (The `REQ-6xx` block the
> owner's 2026-08-26 notes used is owned by `auth-hardening` — shipped, PASS,
> cited in constitution v1.8.1 and in `AuditCoverageTest` — which is why the
> block is `9xx`. No migration table is kept: the 2026-08-26 draft was the
> owner's personal notes, not an official artifact, and no provenance trail is
> wanted. Git history holds it.)
>
> **Governing:** `docs/constitution.md` **v1.8.3**, `frontend/CLAUDE.md`,
> `backend/CLAUDE.md`, `docs/backend/specification.md` §3 decision 5 / §5.

---

## Problem statement

### P1 — The catalogue exists; the frontend cannot cope with more than five

`backend-domain-config` shipped the whole catalogue data model in
`V2__domain_config.sql`: `menu_item_definition` (key, icon, route, default sort
index, `introduced_release`), `menu_item_definition_translation`,
`menu_item_config` with per-field nullable inheritance for `enabled`,
`sort_index` and `icon_override`, **and `menu_item_config_translation` for a
per-scope label override**. The ceiling rule governs `enabled`
(`MenuControl.enabled`, line 444). The catalogue is seeded with **exactly five
rows** — `home`, `info`, `umgebung`, `smart-home`, `services`
(`V2__domain_config.sql:184`).

Everything above it assumes five:

- `frontend/src/app/core/guest/guest-content.mapper.ts:51` sets
  `MAX_MENU_ITEMS = 5` and **rejects the whole payload** above it with the
  message *"menu.items exceeds the contract's 0–5"*. The contract declares no
  such bound — `GuestMenu.items` (`api/openapi.yaml:2630`) has no `maxItems`.
  The bound is a frontend invention citing a source that does not say it.
  (`GuestHomeScreen.actionCards` genuinely carries `maxItems: 4` at
  `api/openapi.yaml:2704`; that one is real and unaffected.)
- `frontend/src/app/core/content/property.model.ts` documents `menuItems` as
  `0–5` in the same false spirit.
- `frontend/src/app/app.routes.ts` hard-codes exactly four section paths —
  `info`, `umgebung`, `smart-home`, `services` — plus `''` and `einstellungen`.
  There is **no `**` fallback route anywhere in `frontend/src/`. The wire can
  name a `route` the router does not have, and nothing detects it: the guest
  gets a router error, not a screen.
- The sidebar was drawn for five entries (`docs/design/screens/home.md` →
  Sidebar: *"MenuItems — five `MenuIcons` instances"*) and there is no overflow
  affordance of any kind.

### P2 — A routine update would silently change every guest screen

`MenuControl.propertyEnabled` (line 458) resolves an absent config row to
`enabled = true`, and `V2__domain_config.sql:182` says so explicitly: *"No
`menu_item_config` rows (absence = catalogue defaults, enabled)"*.
`introduced_release` is stored and exposed to the manager API but **nothing
reads it** — it gates nothing.

Consequence: since constitution **v1.8.1** instances are patched through an
**automatic outbound update channel**. A Flyway migration adding catalogue rows
would therefore turn those sections on **on every room of every deployed
property**, the moment the box applies an update, with no manager action and no
preview. That is a behaviour change delivered by a security-update mechanism.
It needed a decision — **settled as D5 below**, not left as a default.

---

## Goals / Non-goals

**Goals**

1. **A sidebar that copes with a catalogue.** The invented five-item bound
   goes; every catalogue route resolves; more entries than the sidebar holds get
   a deliberate, designed overflow rather than a clipped column.
2. **Growth is a decision, not a side effect.** Adding catalogue rows in a
   release must not silently change what guests see on running properties.
3. **Bounds that are declared, realistic and enforced at the boundary** — in
   the contract, where the mapper's error message already claims they live.

**Non-goals**

- **Module content of any kind.** No `GuestModule` union, no `oneOf`, no module
  registry, no per-type schema, no manager content CRUD. That is Part C and it
  is not pulled forward: this Part needs none of it, which is what makes the
  split worth having.
- **Consolidating the two guest resources.** That is Part B (REQ-941…951).
  `GuestDataService` keeps its two fetches here, untouched.
- **Designing the individual module screens.** Every new catalogue route renders
  `SectionPlaceholderComponent`, the convention `frontend/CLAUDE.md` fixes.
- **A manager frontend.** `backend/manager-ui/` does not exist.
- **Lazy/per-route fetching.** Explicitly rejected: a guest tapping a section
  while the backend is unreachable must not get an empty screen while the rest
  of the app renders from cache.
- **Changing pairing, occupancy, preferences, assets or the connection state
  machine.** Their contracts are settled and stay untouched.
- **A `parent_key` on `menu_item_definition`** — closed by D2.
- **Persisting anything about what a guest opened.** See REQ-929.

---

## Assumptions

- **A1.** The eight rows this Part adds are **disposable test fixtures**
  (see "Placeholder catalogue entries" below). Nothing in the design, the
  contract or any test depends on a specific key or label.
- **A2.** The kiosk continues to hold and render whatever it last had when the
  backend is unreachable. Nothing in this Part changes the cache's contents, and
  today's two cached payloads carry no guest-sensitive value — so
  `docs/constitution.md` → Constraints (*"the last-known-good cache never holds
  guest-sensitive values"*) is unaffected here. That constraint is where Part C
  meets real pressure, not this Part.
- **A3.** No new dependency. The popover is built on the already-approved
  `@angular/cdk/menu`, the pattern `StatusBarMenuComponent` already proves.

---

## User stories

- As a **guest**, I want to reach every section the property offers, even when
  there are more of them than fit down the side of the screen.
- As a **guest**, I want the section I am looking at to be visible in the
  sidebar, however I got there — a tap, a Home card or a deep link.
- As a **property manager**, I want a release that adds new sections not to put
  them on my guests' screens behind my back.
- As a **property manager**, I want to choose which sections my property shows
  and, for the ones that occupy the sidebar, in which order.

---

## Requirements

### Bound and contract

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-901 | The invented `MAX_MENU_ITEMS = 5` in `guest-content.mapper.ts:51` and its false *"the contract's 0–5"* attribution are **removed**, together with the `0–5` comment in `property.model.ts`. `api/openapi.yaml`'s `GuestMenu.items` gains an explicitly declared `maxItems`, chosen for payload sanity and boundary validation — generously above any plausible catalogue, never as a design limit (owner, 2026-08-26: *plan for 20+, do not let the number be load-bearing*). | Must |
| REQ-902 | The generated TypeScript client regenerates **byte-identically** from the amended contract, per the existing contract-first toolchain; contract, backend and `packages/api-client/` land in one commit. | Must |

### Catalogue expansion

> **Reframed 2026-08-27 (owner).** The rows below are **placeholders whose only
> job is to exercise overflow, promotion and collation with real data**. They
> are not a product decision and not a roadmap: *"Es wird kein extra WLAN Menu
> geben (gehört zu info, genauso wie viele andere platzhalter)."* The
> requirements are therefore written in terms of **counts and properties**, never
> in terms of a particular key or label.

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-903 | A Flyway migration adds further `menu_item_definition` rows (plus their `_translation` rows) so the shipped catalogue **comfortably exceeds the sidebar's capacity** — target ≥ 12 entries total. Existing tables, existing `introduced_release` column, **no schema change**. *(Amended 2026-08-27: the rows are disposable fixtures. Removing them later must be **one migration plus a `registered-icons.ts` edit and nothing else** — no code path, no test and no design decision may come to depend on their existence, so the design must keep them out of everything but data.)* | Must |
| REQ-904 | Every new entry's `default_icon` is **registered in `frontend/src/app/core/icons/registered-icons.ts` in the same change**. An unregistered name renders `CONTENT_FALLBACK_ICON` (`phosphorCircle`) rather than failing, so an unregistered catalogue icon would silently produce a popover full of identical neutral glyphs and make the overflow test misleading rather than broken. This is a two-sided change; a backend-only migration is incomplete. | Must |
| REQ-905 | New entries are **placeholders**: key, icon, route and labels in **both `de` and `en`** (`menu_item_definition_translation.language` is `NOT NULL CHECK (language IN ('de','en'))`), and their routes render `SectionPlaceholderComponent` until module content exists — the convention `frontend/CLAUDE.md` already fixes. *(Amended 2026-08-27: they will **never** be implemented under the names they carry; several of them belong under Info if they are ever built at all. "Placeholder" is the whole of their specification.)* | Must |
| REQ-906 | The `%dev` seed leaves the expanded catalogue **enabled**, so overflow, popover and promotion are the default developer and review experience rather than something that must be configured before it can be seen. | Must |
| REQ-907 | Labels are chosen so that **alphabetical order differs visibly from `sortIndex` order**, and at least one label starts with an umlaut, so REQ-925's collation is exercised by the seed and not only by a unit test. *(2026-08-27: together with REQ-903's count, this is one of only three properties of the fixture set anything may rely on — count, umlaut, alphabetical-vs-`sortIndex` divergence. Any fixture row may be renamed or dropped freely as long as those three hold.)* | Should |
| REQ-908 | The **default enablement of a newly introduced catalogue row on an already-running property is explicit** — never an accident of `MenuControl.propertyEnabled`'s *absence = enabled*. **Settled (D5, owner 2026-08-27): the migration writes explicit property-scope `menu_item_config` rows with `enabled = false` for every row it introduces; the `%dev` seeder overrides them to `true`.** It is stated in the migration and covered by a test that asserts what an existing property sees after the migration runs. | Must |

### Backend conventions (unchanged surface)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-909 | Resolution is **unchanged**: the existing ceiling rule (`MenuControl.enabled`), per-field room → property → catalogue inheritance, no new authorization concept, and `menu_item_definition` stays **not writable through any API** (`docs/backend/specification.md` §3 decision 5 / §5). | Must |
| REQ-910 | The migration follows the existing Flyway conventions (`backend/CLAUDE.md` → Dependencies, database, build) and leaves a fresh instance usable with no manual database work. | Must |
| REQ-911 | If Part A adds any `@Transactional public` Control method, it **records an audit row** or is explicitly pinned in `AuditCoverageTest.NOT_A_MANAGER_MUTATION` with a reason (`docs/constitution.md` v1.8.1; `AuditCoverageTest` derives the list from source and fails the build otherwise). Part A is expected to add none — the requirement exists so that is a checked fact, not an assumption. | Must |

### Routing

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-912 | **Every route the catalogue can name resolves**, replacing the four hard-coded section paths in `app.routes.ts`. A route the delivered menu does not claim renders a **defined fallback**, never a router error and never a blank screen. **Settled (D4, owner 2026-08-27): one parameterised placeholder route** `{ path: ':sectionKey', component: SectionPlaceholderComponent }` after `einstellungen`, plus a `**` fallback. Runtime `Router.resetConfig()` is **rejected**. | Must |
| REQ-913 | Route resolution works **from the last-known-good cache and on a cold, offline boot** — a deep link must not depend on a live payload having arrived first. This is the trap in any runtime-derived-routes design and is a pass/fail criterion, not a nicety. | Must |
| REQ-914 | `/einstellungen` and the three full-screen state routes (`/pairing`, `/standby`, `/connection`) keep their precedence and their guards, unchanged (`frontend/CLAUDE.md` → Structure). | Must |

### Sidebar, overflow and popover *(owner decisions 2026-08-26 and 2026-08-27 — settled)*

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-915 | When more modules are enabled than the sidebar holds, the sidebar shows the first *n−1* by resolved sort index plus a fixed **overflow entry** in the last menu slot — above the structurally fixed `Einstellungen` group, which is unaffected. The overflow entry is app chrome, not a catalogue item, and no configuration removes it while it is needed. **Settled (D3, owner 2026-08-27): `n = 5`, fixed on every supported viewport** — 4 catalogue slots + the overflow entry. There are no breakpoints in this UI. | Must |
| REQ-916 | The overflow entry opens a **popover anchored to its own trigger** (owner 2026-08-26, against the earlier planning recommendation of a separate route). It opens to the **right of the button and vertically centred on it**, not from the viewport edge, so the panel stays visually attached to what was pressed. It is not a route and gets no URL. | Must |
| REQ-917 | The popover presents modules as a **wrapping tile grid, 5–6 tiles wide** (owner 2026-08-26), flowing into as many rows as the delivered module count needs, with **no capacity ceiling** anywhere in the design. **The popover holds however many modules are delivered — the only ceiling is the number of modules that exist** (owner 2026-08-27). It **may scroll vertically inside the panel**: the no-scroll constraint binds the *document*, not a bounded sub-component (`docs/constitution.md` v1.8.0), so the panel stays inside the viewport and the route behind it still measures `scrollWidth <= clientWidth` / `scrollHeight <= clientHeight`. `overflow: hidden` at screen level stays forbidden. | Must |
| REQ-918 | Whether the grid is 5 or 6 columns is **decided by measurement, not preference**: the panel is anchored beside the sidebar and must fit the content area at the **1024×768 minimum viewport** with each tile at or above **`--size-touch-min`** (2.75rem — the token is `--size-touch-min`; `--size-touch-target` does not exist in `tokens.css`). Column count is fixed, not responsive. | Must |
| REQ-919 | The alphabetical order of REQ-924 fills the grid **row-major** (left to right, then the next row), matching how the label is read. A column-major fill would put alphabetically adjacent entries on different rows and destroy the point of sorting. | Must |
| REQ-920 | The sidebar/overflow split is derived from the **existing resolved `sortIndex`** — no second `pinned` concept is introduced on the wire or in the data model. | Must |
| REQ-921 | **`SidebarComponent` is the single renderer and owner of the visible selected/active state** for every menu entry, whether it appears as a sidebar tile or as a popover tile. The popover is a presentation surface, not a second state owner. *(The route remains the source of truth for what is active — see REQ-928; "owns" here means "no second component decides what looks active".)* | Must |
| REQ-922 | The popover is built on the **existing `@angular/cdk/menu` pattern** already proven by `StatusBarMenuComponent`: unstyled CDK for the keyboard model, focus movement and return, and the ARIA roles; `ConnectedPosition` with declared fallbacks for the anchoring; every pixel ours, driven by semantic tokens. **No new dependency.** *(Design must resolve one gap the pattern does not cover: `CdkMenu`'s arrow-key model is one-dimensional and a wrapping 2-D tile grid needs a spatial or explicitly-documented linear traversal.)* | Must |
| REQ-923 | **Promotion on choice** (owner 2026-08-26): choosing a module from the popover moves it into the sidebar's **single rotating slot** — the last catalogue slot before the overflow entry, i.e. **slot 4** under D3's `n = 5` — where it renders as the active entry; whatever occupied that slot moves into the popover. The slots above it never move: they are the manager's fixed top *n−2*. Repeated popover choices therefore rotate through one slot only. | Must |
| REQ-924 | The popover list is sorted **alphabetically by the label in the active language** (owner 2026-08-26), not by `sortIndex`. The split is deliberate — **the sidebar is curated (manager priority), the popover is a directory (findability)** — because a one-time guest can predict alphabetical order without knowing the manager's ordering. | Must |
| REQ-925 | Sorting uses a **locale-aware, memoised `Intl.Collator`** built from the active language option's `locale` (the same field the status bar and staleness pill already use for `Intl.DateTimeFormat`), with `numeric: true` so "Raum 2" precedes "Raum 10". One collator per locale, kept for the component's lifetime — constructing one per render is a known quiet performance mistake. | Must |
| REQ-926 | The sort runs **after language resolution**, so it lives with the resolved content (a `computed` reading `language()`), never in `guest-content.mapper.ts`, which holds unresolved `LocalizedText` maps. A language switch therefore re-sorts the popover from data already held, with no fetch — the REQ-108/REQ-514 property, unchanged. | Must |
| REQ-927 | Ties are broken deterministically by the module's stable `key`, so two modules sharing a label never render in a run-dependent order. | Must |
| REQ-928 | **Promotion and active state are two independent things that merely coincide at the moment of choice.** Active follows the route; promotion is view state. Navigating afterwards to a top-slot module leaves the promoted module in the rotating slot, now inactive. | Must |
| REQ-929 | **Promotion is in-memory and per-session — never persisted**, not to `localStorage`, not to the wire. A promotion that outlived a stay would tell the next guest which sections the previous one opened; that is guest-behaviour data the platform deliberately does not keep (`docs/constitution.md` principle 3, and the vacancy data-sparing rule this feature inherits). A reload, a re-pair or a vacancy episode restores the manager's `sortIndex` order. | Must |
| REQ-930 | If a poll delivers a module list that no longer contains the promoted module (the manager disabled it), the promotion is **dropped** and the rotating slot reverts to the delivered order. No stale entry survives a content change. | Must |
| REQ-931 | The whole rule engages **only above the sidebar's capacity**. With few enough modules that no overflow entry is needed, there is no trigger, no popover and no promotion — the sidebar is the delivered order, unchanged from today. | Must |
| REQ-932 | **Reaching an overflow module by any means other than a popover tap has a defined sidebar state** — a Home action card, a deep link, or browser back. This is a real case, not a hypothetical: `docs/backend/specification.md` invariant 2 requires a card's target to be *enabled*, and with `n = 5` the enabled `services` module sits in the popover, so a card can target a module that is not in the sidebar. Either such a navigation promotes the module into the rotating slot, or the overflow entry itself renders as active — the design picks one and states it; "no entry looks active" is not acceptable. | Must |

### Cross-cutting frontend

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-933 | Components continue to read `PropertyContentService` and never `@gxp/api-client` models — the `frontend/CLAUDE.md` import rule holds for the popover and every new component. | Must |
| REQ-934 | No user-visible string introduced by this feature is written in a component or template — including the overflow entry's label and the popover's `aria-label`. App chrome resolves through `TranslationService`; module labels arrive through `PropertyContentService`. | Must |
| REQ-935 | Every route the catalogue can produce satisfies the no-scroll constraint at 1024×768 and 1280×832, in both themes and every offered language, measured with `node tools/measure.mjs`, with the popover both closed and open. The four existing sections and their routes are otherwise unchanged by Part A. | Must |

---

## Acceptance criteria (testable)

- **REQ-901:** Given a property with 12 enabled menu items, when the kiosk maps
  the payload, then all 12 are mapped and no `GuestContentMappingError` is
  raised. Given a payload above the declared `maxItems`, then the envelope is
  rejected — and the rejection message names a bound that actually exists in
  `api/openapi.yaml`.
- **REQ-902:** Given the amended contract, when `./mvnw verify` runs twice,
  then `git status` is clean under `packages/api-client/`.
- **REQ-903/906:** Given a freshly migrated `%dev` database, when the kiosk
  boots, then the sidebar shows 4 modules plus the overflow entry and the
  popover lists the remainder — with no manual configuration step.
- **REQ-904:** Given the expanded catalogue, when the popover renders, then
  every tile shows a distinct glyph and `CONTENT_FALLBACK_ICON`
  (`phosphorCircle`) appears nowhere.
- **REQ-908:** Given a property that existed before the migration (no
  `menu_item_config` rows), when the migration runs, then its guests see
  **exactly the five sections they saw before** — asserted directly against the
  guest session, not inferred. Given the manager then enables one of the new
  rows at property scope, then it appears.
- **REQ-909:** Given a module enabled on a room but disabled on the property,
  when the session resolves, then the module is absent — the ceiling holds,
  identically to the existing menu-item guards.
- **REQ-912/913:** Given the wire delivers modules A and B, when the guest
  deep-links to a route belonging to neither, then a defined fallback renders
  and the app does not blank. Given the backend is unreachable and only the
  cache is available, when the guest deep-links to a cached module's route,
  then that route still resolves.
- **REQ-914:** Given a deep link to `/pairing`, `/standby`, `/connection` or
  `/einstellungen`, when the router resolves it, then it reaches the same
  component behind the same guard as before the parameterised route existed.
- **REQ-918:** Given the popover open at 1024×768, when the panel is measured
  in a real browser, then it fits the content area and every tile's smaller
  dimension is ≥ `--size-touch-min`.
- **REQ-923:** Given 9 enabled modules and `n = 5`, when the guest opens the
  popover and chooses a module, then it renders in slot 4 as active, the module
  previously there appears in the popover, and slots 1–3 are untouched.
- **REQ-924/925:** Given popover modules labelled `Umgebung`, `Ärztliche
  Versorgung` and `Services` with the active language `de`, when the popover
  opens, then the order is `Ärztliche Versorgung`, `Services`, `Umgebung` —
  German collation, not code-point order, which would place `Ärztliche` last.
- **REQ-926:** Given an open popover, when the guest switches the language,
  then the list re-sorts by the new language's labels and **no request is made**.
- **REQ-927:** Given two modules with the identical label in the active
  language, when the popover renders twice, then their relative order is the
  same both times and follows their `key`.
- **REQ-928:** Given a module promoted into slot 4 and active, when the guest
  navigates to a top-slot module, then slot 4 still holds the promoted module
  and only the top slot is active.
- **REQ-929:** Given a promoted module, when the kiosk reloads, then the sidebar
  shows the delivered `sortIndex` order and a search of `localStorage` and of
  every request body finds no trace of the promotion.
- **REQ-930:** Given a promoted module, when the next poll delivers a list
  without it, then slot 4 shows the delivered module for that position and
  nothing references the removed one.
- **REQ-931:** Given a property with 5 or fewer enabled modules, when the
  shell renders, then no overflow entry exists in the DOM at all.
- **REQ-932:** Given `services` is in the popover and a Home action card targets
  it, when the guest taps that card, then the sidebar shows the state the design
  chose — and never "nothing active".
- **REQ-935:** Given the `%dev` catalogue, when `node tools/measure.mjs` runs,
  then every route × theme × language × viewport cell reports
  `scrollWidth <= clientWidth` and `scrollHeight <= clientHeight`, with the
  popover closed and open.

---

## Technical constraints

- **`docs/constitution.md` v1.8.3 governs.** What binds this Part:
  - **v1.8.0** — the "nothing scrolls" rule binds the **document**; a bounded
    sub-component may scroll its own content. This is what makes the scrolling
    popover (REQ-917) legal. `overflow: hidden` at screen level stays forbidden.
  - **v1.8.1** — (a) instances are patched through an **automatic outbound
    signed update channel**, which is what turns a catalogue-expanding migration
    into a silent live-property change (P2, REQ-908, D5); (b) an **append-only
    audit trail** binds every manager mutation, mechanically enforced by
    `AuditCoverageTest` (REQ-911); (c) the **survival invariant**, satisfied
    trivially — this Part adds no external request; (d) the application **never
    branches on where a request came from**.
  - **v1.8.2 / v1.8.3** — per-user token invalidation and the rotatable signing
    trust set. Neither touches this Part.
- **`docs/backend/specification.md` §3 decision 5 and §5 are in force and
  uncontested** (owner, 2026-08-27): the menu catalogue is **release data**,
  `MenuItemDefinition` is *not writable through any API*, and *"a new menu item
  becomes a seed row plus a frontend screen — not a feature"*. Part A complies
  by construction.
- **Invariants that constrain the catalogue** (`docs/backend/specification.md`
  §5): an action card's `target_menu_item_key` must exist **and be enabled in
  the resolved menu** (invariant 2), and disabling a menu item a card still
  targets is rejected with `409` (invariant 3). Pushing a module into the
  popover is presentation only and does not touch `enabled`, so neither
  invariant is affected — but REQ-932 exists because of invariant 2.
- **No new dependency.** The popover reuses the approved `@angular/cdk/menu`
  (`@angular/cdk` 22.1.2, installed).
- Backend: Quarkus, BCE sub-packages, Flyway, the existing manager auth and
  ceiling rules. Frontend: Angular 22, standalone, zoneless, signals, plain CSS
  with the two-layer token system; Vitest + jsdom for unit tests,
  `node tools/measure.mjs` for geometry.
- The contract-first toolchain is binding: `api/openapi.yaml` is the source of
  truth, the generated client is committed and never hand-edited.
- **Branch:** work stays on `refactor/bce-subpackages-and-boundary-security` at
  the owner's explicit instruction, despite the name.

---

## Verified facts this PRD rests on

Checked against the working tree on **2026-08-27**. Anything not in this table
is an assumption.

| Claim | Verdict | Evidence |
|---|---|---|
| Mapper invents `MAX_MENU_ITEMS = 5` and misattributes it to the contract | **true** | `guest-content.mapper.ts:51,68,69` |
| `GuestMenu.items` declares no `maxItems` | **true** | `api/openapi.yaml:2630-2640` |
| `GuestHomeScreen.actionCards` declares `maxItems: 4` | **true** *(on the array, not on `GuestActionCard` itself)* | `api/openapi.yaml:2704` |
| `app.routes.ts` hard-codes four section paths; no `**` fallback exists | **true** | `app.routes.ts:33-58`; no `'**'` anywhere in `frontend/src/` |
| `V2__domain_config.sql` ships the three catalogue tables with `introduced_release` and per-field nullable inheritance | **true**, **and one more table than claimed**: `menu_item_config_translation` gives a **per-scope label override** | `V2__domain_config.sql:80-113` |
| The catalogue is seeded with exactly five rows | **true** — `home`, `info`, `umgebung`, `smart-home`, `services` | `V2__domain_config.sql:184-189` |
| All eight placeholder icons exist in the installed `@ng-icons/phosphor-icons` `regular` set | **true** — all 8 present, plus the 5 already in use | `node_modules/@ng-icons/phosphor-icons/types/ng-icons-phosphor-icons-regular.d.ts` |
| `phosphorFileText` is already registered; unknown names fall back | **true** — `CONTENT_FALLBACK_ICON = 'phosphorCircle'` | `core/icons/registered-icons.ts` |
| `StatusBarMenuComponent` already proves the CDK-menu + `ConnectedPosition` pattern | **true** | `status-bar-menu.component.ts:2,36,57-63` |
| The `REQ-6xx` block is free | **FALSE** — `auth-hardening` owns REQ-601…628 | `docs/features/auth-hardening/01-prd.md`; constitution v1.8.1; `AuditCoverageTest` javadoc |
| `--size-touch-target` is a token | **FALSE** — the token is `--size-touch-min` (2.75rem) | `frontend/src/styles/tokens.css:263` |
| `introduced_release` makes the catalogue safe to grow across releases | **FALSE in effect** — stored and exposed, **read by nothing**; absence of a config row resolves to `enabled = true` | `MenuControl.java:444,458-465`; `V2__domain_config.sql:182` |
| `menu_item_config` writes are per-row | **FALSE — whole-set replace.** `PUT /api/manager/menu-config` does `deleteConfigsOf(scope)` then re-inserts the submitted list | `MenuControl.java:89-98`; `MenuCatalogueAndConfigTest.propertyScopeOverrideRoundtrip` |
| `repository.catalogue()` is ordered | **true** — `order by defaultSortIndex`, so new rows append after the five | `MenuRepository.java:24-25` |
| `CdkMenu`'s key manager is one-dimensional | **true** — `orientation = 'vertical'`, `FocusKeyManager … withWrap().withTypeAhead().withHomeAndEnd()`; Left/Right reach a vertical manager and do nothing | `@angular/cdk/fesm2022/menu.mjs:1259-1265,1397-1407` |
| `CdkMenuTrigger` lets a caller configure the overlay beyond positions | **FALSE** — `_getOverlayConfig()` is private and fixed (`withLockedPosition()`, `withFlexibleDimensions(false)`); only `menuPosition` is an input | `@angular/cdk/fesm2022/menu.mjs:704-717` |

### The sidebar's capacity is not a constant — measured, not assumed

Derived from the token layer and `global.css`:
`html { font-size: min(1.25vw, 1.9231vh) }`, sidebar height = viewport −
`2 × --space-6`, sidebar padding `--space-6` vertical, item = sidebar inner
width = `--size-sidebar (6.25rem) − 2 × --space-3 (0.5rem)` = **5.25rem
square**, logo tile **3.5rem**, settings item **5.25rem**.

| Viewport | root px | usable menu column | items that physically fit |
|---|---|---|---|
| 1280 × 832 (design, 16:10) | 16.0 | 37.25rem | **7** |
| 1024 × 768 (minimum, 4:3) | 12.8 | 45.25rem | **8** |
| 1920 × 1080 (16:9) | 20.77 | 37.25rem | **7** |

At aspect ratios ≥ 1.538 the root is height-bound, so the column is a constant
37.25rem; below that it is width-bound and grows. **The physical floor across
the supported range is 7** — but "fits" is not "looks right": the design was
drawn for five with generous `space-between` gaps, and seven sit flush. `n` is
therefore a **design choice that must be constant across viewports** (there are
no breakpoints), which is why D3 fixes it at 5 rather than measuring it.

---

## Placeholder catalogue entries (REQ-903) — **disposable test fixtures**

> **What these are** (owner, 2026-08-27, verbatim intent): *"Die neuen Menu
> Punkte sind rein PLATZHALTER um das neue Verhalten zu testen. Sie werden mit
> dieser Funktion die sie benennen niemals implementiert werden. … Da ich
> aktuell aber nicht weiß, welche Menu Punkte es in Zukunft geben wird, habe ich
> irgendwas gebraucht. Aber halt auch nur irgendwas."*
>
> So: **no product justification is recorded for any row**, deliberately. There
> is no "why it earns a slot" column, because none of them earns one. `wlan`,
> `anreise`, `hausordnung` and `muell` in particular will never be top-level
> modules — they belong under Info if they are ever built at all.

Eight rows, taking the catalogue from 5 to 13. Every `default_icon` verified
against the installed `@ng-icons/phosphor-icons` `regular` set on 2026-08-27.

| key | `de` | `en` | icon |
|-----|------|------|------|
| `wlan` | WLAN | Wi-Fi | `phosphorWifiHigh` |
| `anreise` | Anreise & Abreise | Arrival & departure | `phosphorClock` |
| `hausordnung` | Hausordnung | House rules | `phosphorFileText` *(already registered)* |
| `muell` | Müll & Recycling | Waste & recycling | `phosphorRecycle` |
| `aerztliches` | Ärztliche Versorgung | Medical care | `phosphorFirstAid` |
| `parken` | Parken | Parking | `phosphorCar` |
| `gastronomie` | Essen & Trinken | Food & drink | `phosphorForkKnife` |
| `aktivitaeten` | Aktivitäten | Things to do | `phosphorPersonSimpleHike` |

**The only three properties anything may depend on:**

1. the total catalogue count is ≥ 12, so overflow is exercised (REQ-903);
2. at least one label starts with an umlaut, so `Intl.Collator` is exercised
   against code-point order (REQ-907, REQ-925);
3. alphabetical order differs visibly from `sortIndex` order, in **both**
   offered languages (REQ-907, REQ-924, REQ-926).

Any row may be renamed, re-iconed or dropped without touching a requirement, a
design decision or a test, as long as those three still hold. **Removing the set
later is one migration plus a `registered-icons.ts` edit** — nothing else, by
construction (REQ-903).

`phosphorWifiSlash` is deliberately **not** reused for `wlan`: it is the
connection screen's offline glyph, and a Wi-Fi page marked with a struck-out
Wi-Fi symbol reads as "no connection".

**Consequences of the fixture set, with `default_sort_index` continued from the
existing 0–4 and `n = 5`:** in `%dev` the sidebar holds `home`, `info`,
`umgebung`, `smart-home` plus the overflow entry, and **`services` — an existing
item — drops into the popover**, which is what makes REQ-932 a live requirement
rather than a hypothetical. Routes stay German (`/wlan`, `/anreise`, …) because
routes are not localised; that is already true for `/umgebung` and
`/einstellungen`. Recorded, not escalated: no guest ever types a URL on a kiosk.

---

## Settled decisions (owner, 2026-08-27)

Nothing below is open. Each records the decision, the owner's reasoning where it
is load-bearing, and what it closed.

### D1 — Split into three features, sequenced A → B → C — **APPROVED**

Part A is the owner's stated ask, needs no contract change beyond a `maxItems`
that should always have been there, complies with spec §3 decision 5 as written,
and delivers the visible improvement on its own. Part B is small, mechanical,
independently testable and closes a live `404`-on-disabled-`home` defect. Part C
is blocked on a module screen that does not exist. A ↔ B may swap order; C
cannot move earlier.

### D2 — Flat catalogue + overflow popover — **APPROVED**

No hierarchy, no `parent_key`, no third enablement axis. Reasoning: flat needs
no schema change, no wire change and no change to `MenuControl.enabled` — the
code constitution principle 4 makes the most security-relevant path in the
backend; grouping is a content judgement and the content does not exist yet; and
`docs/design/design-sheet.md` → Touchscreen-Regeln allows **"maximal zwei
Navigationsebenen"**, of which flat + popover spends one. Hierarchy remains
addable later on top of flat, since flat changes nothing hierarchy would undo.

**Its consequence is the placeholder reframing above:** the owner's own argument
for hierarchy was that several fixtures belong under Info. He resolved it the
other way — the fixtures are throwaway, so there is nothing to nest.

### D3 — `n = 5`, fixed on every viewport; the popover is unbounded — **APPROVED**

5 sidebar slots total = **4 catalogue entries + the overflow entry**, so
REQ-923's rotating slot is literally slot 4. Owner, verbatim: *"Ja n ist fix auf
5. Aber nur für die Sidebar. Es sollen natürlich mehr menus möglich sein, die
dann in das popover kommen (Dafür ist dieses feature ja da). Für die Anzahl an
menu items im popover gibt es keine Begrenzung. Die Anzahl ist natürlich durch
die überhaupt verfügbaren Items gedeckelt."*

`n = 5` preserves today's drawn sidebar exactly, sits below the physical floor of
7 on every supported viewport so it never needs a breakpoint, and is the minimal
change. **No capacity ceiling may appear anywhere in the popover design** — this
confirms the 2026-08-26 *"plan for 20+, do not let the number be load-bearing"*.

### D4 — The parameterised placeholder route — **APPROVED**

`{ path: ':sectionKey', component: SectionPlaceholderComponent }` after
`einstellungen`, plus a `**` fallback. Runtime `Router.resetConfig()` is
**rejected**: it buys stricter validation at the price of an ordering problem —
the route table must exist before the first navigation, including on a cold
offline boot from cache, and be rebuilt on every poll that changes the module
set while the guest is standing on one of the routes being replaced.

Every catalogue route resolves **by construction**; nothing is derived at
runtime; REQ-913 is satisfied for free; the existing guards are untouched. This
is **not a new architectural pattern** — a parameterised route and a wildcard are
ordinary Angular Router configuration — so it needs no constitution gate and no
`frontend/CLAUDE.md` entry. Re-checked while designing it (02-design.md §4):
the conclusion holds.

### D5 — New catalogue rows arrive disabled on running properties — **APPROVED**

The migration writes explicit property-scope `menu_item_config` rows with
`enabled = false` for every row it introduces; the `%dev` seeder overrides them
to `true` so REQ-906 still holds. No schema change, no code change, no new
concept — just rows the migration already knows how to write. The manager opts a
new section in when they have content for it, which is also the only sane
behaviour given REQ-905 ships the sections as empty placeholders: shipping eight
blank pages to live guests is worse than shipping none.

Rejected: *accept auto-enable* (a security-update mechanism silently changes the
guest-facing product) and *make `introduced_release` load-bearing* (an "adopted
release" marker on `property` plus new resolution logic in `MenuControl` — more
correct in the long run, more than this feature needs now).

**Known limitation, recorded not gated** (found while designing, 2026-08-27):
`PUT /api/manager/menu-config` is a **whole-set replace** for a scope
(`MenuControl.putConfig` deletes the scope's rows and re-inserts the submitted
list). A manager client that submits a partial set therefore deletes the opt-out
rows, and absence resolves back to *enabled*. The opt-out is durable against the
update channel — which is what D5 is for — but not against a careless API
consumer. `backend/manager-ui/` does not exist yet; the round-trip obligation is
recorded in 02-design.md §3 and pinned by a test so the future manager UI meets
a documented contract rather than a surprise.

### D6 — Design the overflow popover in-repo — **APPROVED**

Under `docs/design/screens-inhouse/`, no design-tool request. The precedent
exists and worked (`/pairing`, `/standby`, `/connection` for
`guest-frontend-integration`), and `docs/design/design-sheet.md` already marks
the status bar and settings as *"Selbst entworfen, ohne Figma-Vorlage"*. The
panel is built entirely from components and tokens that already exist:
`StatusBarMenuComponent`'s glass panel and CDK anchoring, and the
`--gradient-menu-active` tile treatment the sidebar already uses. Deliverable:
[`docs/design/screens-inhouse/menu-overflow.md`](../../design/screens-inhouse/menu-overflow.md).

### Manager authority over ordering — stated plainly

The owner scoped it on 2026-08-27: *"Der manager darf nur auswählen welche Items
angezeigt werden sollen (und bei den ersten 4 in welcher Reihenfolge)."*
Reconciled with the shipped per-scope `sort_index` model, the rule is:

> Resolved `sortIndex` orders **everything**, exactly as it does today. What the
> manager's ordering *buys* is only the first four positions: ranks 1–4 are the
> sidebar, in that order, and everything below rank 4 lands in the popover,
> whose order is alphabetical by label and ignores `sortIndex` entirely. So a
> manager reordering ranks 5+ changes nothing a guest can see.

This contradicts nothing: REQ-920 derives the split from resolved `sortIndex`,
REQ-924 sorts the popover alphabetically, and the two meet exactly at rank 4.
The guest's promotion (REQ-923) temporarily replaces the occupant of slot 4 —
it is per-session view state that reverts (REQ-929) and never touches the
manager's stored order.

---

## Risks (recorded, not decisions)

- **`CdkMenu` has no 2-D grid model.** Verified in the installed source:
  `orientation = 'vertical'` and a `FocusKeyManager` with wrap, typeahead and
  Home/End; Left/Right reach a vertical manager and do nothing. 02-design.md §7
  resolves this — and does so with the ARIA contract rather than with code.
- **The whole-set-replace API is the soft edge of D5** — see D5's recorded
  limitation. It also makes test isolation matter: a test that clears the
  property-scope config re-enables all eight fixtures for every test that runs
  after it in the same database. Handled in 02-design.md §3.
- **Client-side sorting already exists and duplicates the contract.**
  `guest-content.mapper.ts` re-sorts `menu.items` by `sortIndex` although
  `api/openapi.yaml` documents them as *"ordered by resolved sort index"*.
  Harmless, and **deliberately left alone here** — removing it is Part C's
  REQ-962, and touching it in Part A would change the delivered order's source
  for no benefit.
- **Routes are German and unlocalised.** Eight more compounds it. Recorded; no
  guest types a URL on a kiosk.
- **`V10` is contested by a deferred plan.** `instance-identity-and-certs`
  (deferred 2026-08-26, never started) planned `V10__instance_enrollment.sql`.
  Part A takes `V10`; that plan renumbers when it is picked up, which its own
  index entry already anticipates (*"to be re-validated against the real
  conditions when picked up, not treated as the spec"*).
