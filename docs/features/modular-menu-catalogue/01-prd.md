# PRD: Modular Menu Catalogue
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Draft — **decision-incomplete**: D1-D7 below are open and four of
them are `docs/constitution.md` → "Decisions Requiring Your Approval" gates
(new architectural pattern / cross-cutting convention). No implementation may
start until the owner has answered them.

> Origin: owner conversation 2026-08-26, while reading `GuestDataService` in
> detail. The guest frontend today renders exactly two content resources
> (`session` + `screens/home`) and four hard-coded section routes that render
> a placeholder. The owner's product intent is a **catalogue**: a property
> manager picks from 20+ menu modules which ones this property shows, in
> which order — so the shape of the guest app is data, not code.
>
> Settled ground — referenced, never re-opened: constitution **v1.7.1**;
> the guest contract as built by `device-pairing-and-guest-api`
> (REQ-401…435); the **ceiling rule** from `backend-domain-config`
> (property is the global ceiling for `enabled`, rooms may only narrow);
> the vacancy/data-sparing contract; the ETag/304 polling contract;
> `frontend/CLAUDE.md`; `backend/CLAUDE.md` and the BCE package rules.
>
> **REQ-ID block: `REQ-6xx`** (0xx home screen, 1xx appearance/language,
> 2xx foundation, 3xx domain-config, 4xx pairing/guest API,
> 5xx guest-frontend-integration).

## Problem statement

Three problems, and only the first one is visible from the outside.

**1. The catalogue exists; its content and its consumer do not.** Verified
2026-08-26 — `backend-domain-config` already shipped the full catalogue data
model in `V2__domain_config.sql`: `menu_item_definition` (key, icon, route,
default sort index, **`introduced_release`** — a column that exists precisely
so the catalogue can grow across releases), its per-language
`menu_item_definition_translation`, and `menu_item_config` carrying the
per-property (`room_id NULL`) and per-room selection with **per-field
nullable inheritance** for `enabled`, `sort_index` and `icon_override`. The
ceiling rule already governs `enabled`. This feature therefore does **not**
invent a catalogue — it grows one that is seeded with exactly five rows, and
supplies the two things that are genuinely missing:

  - **content per module.** A catalogue entry today carries a label, an icon
    and a route. There is no model for what a section actually *shows*, and
    every section route renders `SectionPlaceholderComponent`.
  - **a frontend that can cope with more than five.** The router hard-codes
    four section paths in `frontend/src/app/app.routes.ts`, so the wire can
    name a `route` the router does not have and nothing detects it; and the
    mapper rejects the payload above five items (see 2).

**2. The current shape imposes bounds nobody decided.**
`guest-content.mapper.ts:51` enforces `MAX_MENU_ITEMS = 5` and rejects the
whole payload above it, with the message *"menu.items exceeds the contract's
0–5"*. **The contract declares no such limit** — `GuestMenu.items` in
`api/openapi.yaml` has no `maxItems`. The bound is a frontend invention that
cites a source that does not say it, and it directly contradicts the intended
catalogue of 20+ modules. (`GuestActionCard` genuinely carries `maxItems: 4`
in the contract; that one is real and is a separate question.)

**3. Content is needed across screen boundaries, and per-screen endpoints
make that unresolvable.** The owner's concrete example: the Home action card
for Info shows the **check-out time** as its subtitle, and that value belongs
to the Info module's data. With one endpoint per screen there are only two
outcomes — Home must additionally fetch Info (so the split bought nothing and
Home's correctness now depends on a second request), or the server duplicates
the value into the Home payload (two sources for one fact, which will drift).
An overview screen that summarises modules is *by definition* a cross-module
read; with a catalogue this is the normal case, not an exception.

Underneath all three: `GuestDataService` hard-codes "session + home" in eight
places (two ETags, two cache keys, two held copies, the two-call sequence in
both `bootstrap()` and `refresh()`, the `409 room-vacant` race window between
the two calls, and `asVacant()`). Adding a third resource in the same style
doubles that; a fifth makes it unmaintainable.

## Goals / Non-goals

**Goals**

1. **A catalogue that actually holds a catalogue.** The data model is in
   place; this feature grows it past the five seeded entries and makes every
   layer above it cope — mapper bound, router, sidebar, popover. Enablement
   keeps resolving through the existing ceiling rule; no new authorization
   concept, no schema change to `menu_item_definition`.
2. **One aggregated guest content payload.** The kiosk makes one content
   request and holds one resource, so cross-module values (the check-out
   time on a Home card) are resolved server-side from data the server already
   has in hand, with a single source of truth.
3. **Data-driven routing.** The set of in-app routes is derived from the
   modules the wire delivers; a module the server did not send has no route,
   and a route the server names always resolves.
4. **A module registry in the frontend**, in the shape
   `core/icons/registered-icons.ts` already establishes: one registration
   map, several consumers (wire validation, mapping, rendering, route
   dispatch), so registering a module type makes it valid wire content in the
   same line and the two cannot drift.
5. **Per-module failure isolation.** One malformed module is dropped; only a
   structurally invalid envelope is fatal. Twenty modules must not mean twenty
   ways to blank a kiosk.
6. **A net reduction in `GuestDataService`.** Folding the second resource in
   removes `HOME_CACHE_KEY`, `homeEtag`, `heldHome`, `fetchHome()`, the
   two-call sequence in both entry points, and — with it — the `409` race and
   `asVacant()` entirely. The class must be **smaller** after this feature
   than before it.
7. **Bounds that are declared, realistic and enforced at the boundary.** The
   invented `MAX_MENU_ITEMS = 5` goes; a catalogue-sized bound and per-field
   `maxLength` go into the contract, where the mapper's error message already
   claims they live.

**Non-goals**

- **Designing the individual module screens.** This feature delivers the
  content model, the transport and the rendering host. What each module
  *looks like* is per-module design work (`docs/design/screens/**` or
  in-repo), planned separately.
- **The manager UI for picking modules.** Manager-side CRUD is contract +
  backend here; no manager frontend exists yet (see the registry note in
  `docs/system-context.md`).
- **Lazy/per-route fetching.** Explicitly rejected: a guest tapping a section
  while the backend is unreachable must not get an empty screen when the rest
  of the app is still rendering from cache.
- **Changing pairing, occupancy, preferences, assets or the connection state
  machine.** Their contracts are settled and stay untouched.
- **A second cache tier or any storage other than the existing
  `localStorage` last-known-good record.**

## Assumptions

- A1. Module content is **text and asset references**. Binary content stays
  behind `GET /api/guest/assets/{id}` as it does today — nothing is inlined,
  base64 or otherwise. Under this assumption a full 20-module payload is
  ~40–60 kB JSON (~10 kB gzip), and the 30 s poll costs a `304` (~200 B).
- A2. Modules are **property-scoped content**, not stay-scoped. Hausordnung,
  WLAN and surroundings do not identify a guest and need not be erased at
  check-out; only the greeting, the guest display name and the stay
  preference are stay-derived. D3 asks the owner to confirm this split.
- A3. Every module type has a **fixed, known-at-build-time shape**. The
  catalogue grows by adding types, not by allowing free-form JSON — a
  free-form payload cannot be boundary-validated, and boundary validation is
  the frontend's contract with an untrusted wire.
- A4. The kiosk continues to hold and render whatever it last had when the
  backend is unreachable. Adding modules must not weaken that.

## User stories

- As a **property manager**, I want to choose which of the available modules
  my property shows and in which order, so that a small apartment is not
  forced to display the same sections as a hotel.
- As a **property manager**, I want to author each chosen module's content in
  the languages I offer, so that guests read it in their own language.
- As a **guest**, I want the overview screen to show current, real values
  (like the check-out time) rather than static labels, so I do not have to
  open a section to learn one fact.
- As a **guest**, I want the whole app to keep working from the last known
  content when the property's server is unreachable, so a power cut does not
  leave me with a blank screen.
- As the **operator**, I want one badly-authored module never to take the
  kiosk down, so a content mistake is a missing tile and not a site visit.

## Requirements

### Contract

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-601 | `api/openapi.yaml` defines a `GuestModule` schema as a **discriminated union** (`oneOf` + `discriminator: type`), one member per module type, each with a fixed shape. | Must |
| REQ-602 | The guest session payload carries the **resolved, enabled, ordered** module list; the kiosk performs no enablement or ordering logic. | Must |
| REQ-603 | Every module member declares explicit bounds: array `maxItems`, text `maxLength`. No unbounded array or string reaches the wire. | Must |
| REQ-604 | The contract declares a realistic bound on the module list (catalogue-sized, not 5). The frontend's invented `0–5` and its false "the contract's" attribution are removed. | Must |
| REQ-605 | Localised module text follows the existing `LocalizedText` convention and is **resolved per offered language** server-side, exactly as `GuestHomeScreen` fields already are — never a raw template on the wire. | Must |
| REQ-606 | Manager-side CRUD for module selection, ordering and content exists in the contract under the existing `manager*` tags and auth scheme. | Must |
| REQ-607 | The generated TypeScript client regenerates **byte-identically** from the amended contract, per the existing contract-first toolchain. | Must |

### Backend

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-608 | Module enablement resolves through the **existing ceiling rule** — the property is the global ceiling, a room may only narrow. No new authorization concept is introduced. | Must |
| REQ-609 | Cross-module values rendered on the overview (e.g. the Info module's check-out time as an action-card subtitle) are resolved **server-side from the owning module's data**; the value is never stored twice. | Must |
| REQ-610 | The session `ETag` is derived from a **monotonic content version stamp** bumped on any content write, not by serialising and hashing the payload on every poll. | Must |
| REQ-611 | A vacant room serves no stay-derived field, unchanged from today (`guestDisplayName`, `preference`, greeting). Whether module content is also withheld is D3. | Must |
| REQ-612 | Persistence for module content follows the existing BCE package rules and the migration conventions of `backend-domain-config`; no package exists without a Control. | Must |

### Frontend

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-613 | A **module registry** under `core/guest/` (or `core/modules/`) is the single registration point per module type, carrying at minimum: wire type discriminant, validator/mapper, and the component that renders it. | Must |
| REQ-614 | Wire validation of module types is **derived from the registry**, not from a hand-maintained parallel list — the `registered-icons.ts` precedent. | Must |
| REQ-615 | An unknown or structurally invalid **single** module is dropped from the mapped result; the rest of the payload renders. Only an invalid envelope throws `GuestContentMappingError`. | Must |
| REQ-616 | In-app section routes are **derived from the delivered modules**, replacing the four hard-coded paths in `app.routes.ts`. A wire `route` that no delivered module claims resolves to a defined fallback, never a blank screen. | Must |
| REQ-617 | `GuestDataService` holds **one** content resource: one ETag, one cache key, one held copy, one fetch. `HOME_CACHE_KEY`, `homeEtag`, `heldHome`, `fetchHome()` and `asVacant()` are removed, together with the `409 room-vacant` race they exist to handle. | Must |
| REQ-618 | The `ONLINE` / `STALE` / `DISCONNECTED` machine, the 30 s cadence, the 4 s fetch bound, the 5→30 s backoff, the last-known-good cache and the 401 wipe are **behaviourally unchanged**. | Must |
| REQ-619 | Components continue to read `PropertyContentService` and never `@gxp/api-client` models — the `frontend/CLAUDE.md` import rule holds for module code too. | Must |
| REQ-620 | No user-visible string introduced by this feature is written in a component or template; module chrome resolves through `TranslationService`, module content through `PropertyContentService`. | Must |
| REQ-621 | Every route the catalogue can produce satisfies the no-scroll constraint at 1024×768 and 1280×832, in both themes and every offered language, measured with `tools/measure.mjs`. | Must |
| REQ-622 | The frontend renders a module type it does not know **without crashing** — forward compatibility, so a backend that gains a module before the kiosk is updated degrades to "not shown". | Should |
| REQ-626 | When more modules are enabled than the sidebar holds, the sidebar shows the first *n−1* by resolved sort index plus a fixed **overflow entry** in the last slot; the overflow entry is app chrome, not a catalogue item, and no configuration removes it while it is needed. | Must |
| REQ-627 | The overflow entry opens a **popover anchored to its own trigger** — owner decision 2026-08-26, against the planning recommendation of a separate route. It opens to the **right of the button and vertically centred on it**, not from the viewport edge, so the panel stays visually attached to what was pressed. It is not a route and gets no URL. | Must |
| REQ-628 | The popover presents modules as a **wrapping tile grid, 5–6 tiles wide** (owner 2026-08-26), flowing into as many rows as the delivered module count needs. It **may scroll vertically inside the panel**: the no-scroll constraint bounds the *screen*, not a component inside it (constitution v1.8.0), so the panel is capped to the viewport and the route behind it still measures `scrollWidth <= clientWidth` / `scrollHeight <= clientHeight`. `overflow: hidden` at screen level stays forbidden. | Must |
| REQ-628a | Whether the grid is 5 or 6 columns is **decided by measurement, not preference**: the panel is anchored beside the sidebar and must fit the content area at the 1024x768 minimum viewport with each tile at or above `--size-touch-target`. Column count is fixed, not responsive — there are no breakpoints in this UI. | Must |
| REQ-628b | The alphabetical order of REQ-634 fills the grid **row-major** (left to right, then the next row), matching how the label is read. A column-major fill would put alphabetically adjacent entries on different rows and destroy the point of sorting. | Must |
| REQ-629 | The sidebar/overflow split is derived from the **existing resolved `sortIndex`** — no second `pinned` concept is introduced on the wire or in the data model. | Must |
| REQ-630 | **`SidebarComponent` alone owns the selected/active state** of every menu entry, whether it is rendered as a sidebar tile or as a popover row. The popover is a presentation surface, not a second state owner — a guest choosing a module from the popover leaves the sidebar as the single source of truth for what is active. | Must |
| REQ-631 | The popover is built on the **existing `@angular/cdk/menu` pattern** already proven by `StatusBarMenuComponent`: unstyled CDK for the keyboard model (Enter/Space, arrows, Escape), focus movement and return, and the ARIA roles; `ConnectedPosition` with declared fallbacks for the anchoring; every pixel ours, driven by semantic tokens. No new dependency. | Must |
| REQ-632 | **Promotion on choice** (owner 2026-08-26): choosing a module from the popover moves it into the sidebar's **slot 4**, where it renders as the active entry; whatever occupied slot 4 moves into the popover. Slots 1–3 never move — they are the manager's fixed top three, and slot 4 is the single rotating slot. Repeated popover choices therefore rotate through slot 4 only. | Must |
| REQ-633 | **Promotion is in-memory and per-session — it is never persisted**, not to `localStorage`, not to the wire. A promotion that outlived a stay would tell the next guest which sections the previous one opened; that is guest-behaviour data the platform deliberately does not keep (`docs/constitution.md` principle 3, and the vacancy data-sparing rule this feature inherits). A reload, a re-pair or a vacancy episode restores the manager's `sortIndex` order. | Must |
| REQ-634 | The popover list is sorted **alphabetically by the label in the active language** (owner 2026-08-26), not by `sortIndex`. This supersedes the earlier draft rule that a displaced module returns to its `sortIndex` position: alphabetical is the stronger form of the same intent, because a one-time guest can predict it without knowing the manager's ordering. The split is deliberate — **the sidebar is curated (manager priority), the popover is a directory (findability)**. | Must |
| REQ-634a | Sorting uses a **locale-aware, memoised `Intl.Collator`** built from the active language option's `locale` (the same field the status bar and staleness pill already use for `Intl.DateTimeFormat`), with `numeric: true` so "Raum 2" precedes "Raum 10". One collator per locale, kept for the module's lifetime — constructing one per render is the quiet performance mistake `02-design.md` §8.5 already names. | Must |
| REQ-634b | The sort runs **after language resolution**, so it lives with the resolved content (a `computed` reading `language()`), never in `guest-content.mapper.ts`, which holds unresolved `LocalizedText` maps. A language switch therefore re-sorts the popover from data already held, with no fetch — the REQ-108/514 property, unchanged. | Must |
| REQ-634c | Ties are broken deterministically by the module's stable `key`, so two modules sharing a label never render in a run-dependent order. | Must |
| REQ-635 | Promotion and active state are **two independent things that merely coincide at the moment of choice**. `SidebarComponent` owns both (REQ-630): active follows the route, promotion is view state. Navigating afterwards to a slot-1 module leaves the promoted module in slot 4, now inactive. | Must |
| REQ-636 | If a poll delivers a module list that no longer contains the promoted module (the manager disabled it), the promotion is **dropped** and slot 4 reverts to the delivered order. No stale entry survives a content change. | Must |
| REQ-637 | The rule engages **only above the sidebar's capacity**. With few enough modules that no overflow entry is needed, there is no trigger, no popover and no promotion — the sidebar is the delivered order, unchanged from today. | Must |

### Catalogue expansion (owner request 2026-08-26 — testable overflow)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-638 | A Flyway migration adds further `menu_item_definition` rows (plus their `_translation` rows) so the shipped catalogue **comfortably exceeds the sidebar's capacity** — target ≥ 12 entries total. It uses the existing tables and the existing `introduced_release` column; **no schema change**. | Must |
| REQ-639 | Every new entry's `default_icon` is **registered in `frontend/src/app/core/icons/registered-icons.ts` in the same change**. An unregistered name renders `CONTENT_FALLBACK_ICON` (a neutral circle) rather than failing — so an unregistered catalogue icon would silently produce a popover full of identical glyphs and make the overflow test misleading rather than broken. This is a two-sided change; a backend-only migration is incomplete. | Must |
| REQ-640 | New entries are **placeholders**: they carry key, icon, route and labels in **both `de` and `en`** (the `_translation` table's `language` column is `NOT NULL` and `CHECK (language IN ('de','en'))`), and their routes render the placeholder component until module content exists. They exist to exercise overflow, promotion (REQ-632) and alphabetical sorting (REQ-634) with real data. | Must |
| REQ-641 | The `%dev` seed leaves the expanded catalogue **enabled by default**, so overflow, popover and promotion are the default developer and review experience rather than something that must be configured before it can be seen. | Must |
| REQ-642 | Labels of the placeholder entries are chosen so that **alphabetical order differs visibly from `sortIndex` order**, and at least one label starts with an umlaut, so REQ-634a's collation is exercised by the seed and not only by a unit test. | Should |

### Migration

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-623 | The four existing placeholder sections become catalogue modules or are explicitly retired; `SectionPlaceholderComponent` survives only if a delivered-but-unauthored module is a real state. | Must |
| REQ-624 | Existing properties migrate without manual intervention: a Flyway migration seeds each existing property's current four sections as catalogue selections. | Must |
| REQ-625 | The `%dev` seeder produces a property with at least two authored module types, so the registry path is exercised in development rather than only in tests. | Should |

### Placeholder entries (REQ-638) — **accepted as test data by the owner, 2026-08-26**

Eight sections that a small rental or hotel plausibly needs, taking the
catalogue from 5 rows (4 sections + `home`) to 13. Every `default_icon` was
**verified against the installed `@ng-icons/phosphor-icons` `regular` set on
2026-08-26**; `phosphorFileText` and `phosphorKey` are already registered, the
rest are new registrations under REQ-639.

| key | `de` | `en` | icon | why it earns a slot |
|-----|------|------|------|---------------------|
| `wlan` | WLAN | Wi-Fi | `phosphorWifiHigh` | the single most-asked question in any rental |
| `anreise` | Anreise & Abreise | Arrival & departure | `phosphorClock` | check-in/out times, key handover — **the source of the check-out time the Home action card shows (REQ-609)** |
| `hausordnung` | Hausordnung | House rules | `phosphorFileText` ✔ | quiet hours, smoking, pets |
| `muell` | Müll & Recycling | Waste & recycling | `phosphorRecycle` | separation and collection days — reliably the second-biggest source of confusion in German rentals |
| `aerztliches` | Ärztliche Versorgung | Medical care | `phosphorFirstAid` | doctor, pharmacy, emergency numbers — **and the umlaut entry REQ-642 needs** |
| `parken` | Parken | Parking | `phosphorCar` | where, permit, EV charging |
| `gastronomie` | Essen & Trinken | Food & drink | `phosphorForkKnife` | recommendations nearby |
| `aktivitaeten` | Aktivitäten | Things to do | `phosphorPersonSimpleHike` | excursions, weather-dependent options |

`phosphorWifiSlash` is deliberately **not** reused for `wlan`: it is the
connection screen's offline glyph, and a Wi-Fi page marked with a struck-out
Wi-Fi symbol reads as "no connection".

With `default_sort_index` continued from the existing 0–4, the sidebar holds
`home`, `info`, `umgebung`, `smart-home` plus the overflow entry, and
**`services` — an existing item — drops into the popover**, which is exactly
the realistic case worth exercising.

Two product observations this list surfaces, for `02-design.md` rather than
for the seed:

- **The set is knowingly overlapping, and the owner sharpened why**
  (2026-08-26): `gastronomie` and `aktivitaeten` compete with `umgebung`, and
  — the more interesting half — *"die ersten paar gehoeren auch eigentlich
  alle zum Info"*. `wlan`, `anreise`, `hausordnung` and `muell` are all
  arguably **children of Info**, not siblings of it. Accepted as-is for the
  test data; raised as **D7** below, because it questions the shape of the
  catalogue rather than its contents.
- **Routes stay German** (`/wlan`, `/anreise`, …) because routes are not
  localised (`PropertyContentService`). That is already true for `/umgebung`
  and `/einstellungen`; eight more compounds it. Worth a deliberate decision
  rather than drifting into it.

## Acceptance criteria (testable)

- **REQ-601/607:** Given the amended contract, when the Maven build runs, then
  the JAX-RS interfaces and `packages/api-client/` regenerate and a second
  build produces a byte-identical client (`git status` clean).
- **REQ-602:** Given a property with modules B, A, C enabled at sort indices
  2, 1, 3 and module D disabled, when the kiosk fetches the session, then the
  payload lists exactly A, B, C in that order and the kiosk performs no sort.
- **REQ-604:** Given a property with 12 enabled modules, when the kiosk maps
  the payload, then all 12 are mapped and no `GuestContentMappingError` is
  raised. Given a payload above the declared bound, then the envelope is
  rejected.
- **REQ-608:** Given a module enabled on a room but disabled on the property,
  when the session resolves, then the module is absent — the ceiling holds,
  identically to the existing menu-item guards.
- **REQ-609:** Given the Info module's check-out time is edited, when the next
  poll lands, then the Home action card's subtitle shows the new value, and a
  search of the persistence layer finds the value stored exactly once.
- **REQ-610:** Given no content write between two polls, when the kiosk sends
  `If-None-Match`, then the server answers `304` **without** serialising the
  module payload.
- **REQ-615:** Given a payload of 8 modules where one carries an unregistered
  `type` and one fails its own validation, when the mapper runs, then 6
  modules are mapped, no error is thrown, and the kiosk stays `ONLINE`.
- **REQ-616:** Given the wire delivers modules A and B, when the guest deep-
  links to a route belonging to neither, then a defined fallback renders and
  the app does not blank.
- **REQ-617:** Given this feature is complete, when `guest-data.service.ts` is
  measured, then it has **fewer** lines than at `HEAD` of this PRD, and
  `grep` finds no `HOME_CACHE_KEY`, `homeEtag`, `heldHome`, `fetchHome` or
  `asVacant`.
- **REQ-618:** Given the existing 13 `guest-data.service.spec.ts` cases are
  rewritten for one resource, when they run, then every state transition
  (200/304/error/401/vacancy/cache-rejected) asserts the same observable
  behaviour as today.
- **REQ-632:** Given 9 enabled modules and a sidebar of 4 slots plus the
  overflow entry, when the guest opens the popover and chooses a module from
  it, then it renders in slot 4 as active, the module previously in slot 4
  appears in the popover, and slots 1–3 are untouched.
- **REQ-634/634a:** Given popover modules labelled `Umgebung`, `Ärztliches`
  and `Services` with the active language `de`, when the popover opens, then
  the order is `Ärztliches`, `Services`, `Umgebung` — German collation, not
  code-point order, which would place `Ärztliches` last.
- **REQ-634b:** Given an open popover, when the guest switches the language,
  then the list re-sorts by the new language's labels and no request is
  made.
- **REQ-633:** Given a promoted module, when the kiosk reloads, then the
  sidebar shows the delivered `sortIndex` order and a search of
  `localStorage` and of every request body finds no trace of the promotion.
- **REQ-635:** Given a module promoted to slot 4 and active, when the guest
  then navigates to the slot-1 module, then slot 4 still holds the promoted
  module and slot 1 is the only active entry.
- **REQ-636:** Given a promoted module, when the next poll delivers a list
  without it, then slot 4 shows the delivered module for that position and
  nothing references the removed one.
- **REQ-638/641:** Given a freshly migrated `%dev` database, when the kiosk
  boots, then the sidebar shows 4 modules plus the overflow entry and the
  popover lists the remainder — with no manual configuration step.
- **REQ-639:** Given the expanded catalogue, when the popover renders, then
  every row shows a distinct glyph and `CONTENT_FALLBACK_ICON` appears
  nowhere.
- **REQ-621:** Given a property with the maximum declared number of modules,
  when `node tools/measure.mjs` runs, then every route × theme × language ×
  viewport cell reports `scrollWidth <= clientWidth` and
  `scrollHeight <= clientHeight`.
- **REQ-622:** Given a payload containing a module type the build does not
  register, when the kiosk maps it, then that module is absent from the menu
  and every other module renders.

## Technical constraints

- `docs/constitution.md` **v1.7.1** governs. Principle 4 (no external runtime
  requests) is unaffected — this feature adds no fetch of any kind; it removes
  one.
- **No new dependency is anticipated.** A discriminated union needs nothing
  beyond the existing OpenAPI generator and TypeScript. If the design finds
  one necessary, that is a separate approval gate.
- Backend: Quarkus, BCE packages, Flyway, the existing manager auth and
  ceiling rules. Frontend: Angular 22, standalone, zoneless, signals, plain
  CSS with the two-layer token system; Vitest + jsdom for unit tests,
  `tools/measure.mjs` for geometry.
- The contract-first toolchain is binding: `api/openapi.yaml` is the source of
  truth, the generated client is committed and never hand-edited.
- **Resolved before this PRD was finalised (2026-08-26):** the frontend suite
  had started failing ~37 of 251 specs on `localStorage` with no code change.
  Cause: Node 26 defines `globalThis.localStorage` (undefined without
  `--localstorage-file`), and Vitest skips any jsdom window key that
  `globalThis` already carries — so jsdom's storage never landed. Fixed with
  a per-worker in-memory `Storage` in `frontend/src/test-setup.ts`, wired via
  `setupFiles`. Suite is 251/251. Any work on this feature starts from a
  green baseline.

## Risks & open questions

### Approval gates — `docs/constitution.md` → "Decisions Requiring Your Approval"

- **D1 `[NEEDS CLARIFICATION]` — the module registry as a cross-cutting
  frontend convention.** A registry that is simultaneously the wire-validation
  set, the mapping table, the render dispatch and the route source is a new
  architectural pattern. *Recommendation: approve.* It is the pattern
  `core/icons/registered-icons.ts` already uses and whose comment states the
  reason (deriving one consumer from the other is what stops them drifting);
  the alternative is four parallel lists to keep in sync by hand.
- **D2 `[NEEDS CLARIFICATION]` — the discriminated union on the wire.**
  `oneOf` + `discriminator` is a contract pattern this API has not used yet.
  *Recommendation: approve.* The generator produces a TypeScript union that
  narrows on `switch (module.type)` with no cast, and the alternative —
  a generic bag of key/value content — cannot be boundary-validated (A3).
- **D3 `[NEEDS CLARIFICATION]` — one content resource or two.** This is about
  the number of **HTTP resources**, not about mixing property and stay data:
  `GuestSession` already carries `guestDisplayName` and `preference`, so the
  current split is *session vs. home screen*, not *property vs. stay*. The
  data-lifetime separation is and stays enforced by the server **omitting
  fields** for a vacant room, not by two endpoints.
  *Recommendation: **one**.* It removes the second round trip and with it the
  window between two calls in which a stay can end — the `409 room-vacant`
  race, `asVacant()`, the second ETag, cache key and held copy all go.
  Separation is preserved and in one respect improved: on vacancy the server
  sends property-scoped module content **without** the stay fields, the kiosk
  caches exactly that, and property content survives a vacancy episode where
  today the whole home cache is deleted. Whether module content should
  *additionally* be withheld while vacant is a separate, independent choice —
  not a consequence of this decision.
- **D4 `[NEEDS CLARIFICATION]` — is Home a module?** Either the overview stays
  fixed app chrome (like the settings entry, which the contract already
  documents as *"NOT a catalogue item, no configuration removes it"*), or it
  becomes a module a manager could remove. *Recommendation: fixed chrome.* A
  kiosk with no landing screen has no defined entry route.

### Open questions without an approval gate

- **D5 - answered by the owner 2026-08-26: plan for 20+, and do not let the
  number be load-bearing.** *"Ich habe ehrlich gesagt keine Ahnung wie viele
  Items es geben wird. Aber ich moechte von mehr ausgehen als zu wenig. Die
  App wird ueber Monate bis Jahre entwickelt, da werden immer wieder neue Use
  Cases dazu kommen."* Consequences for `02-design.md`:
  - the wrapping, scrolling grid (REQ-628) has **no capacity ceiling** - 20
    modules are four rows at 5 wide and need no scrolling at all; 40 simply
    scroll. The design must not acquire a fixed capacity anywhere;
  - REQ-604's contract `maxItems` is chosen for **payload sanity and boundary
    validation**, generously above any plausible catalogue - not as a design
    limit, and never again as a number the frontend invents (the current
    `MAX_MENU_ITEMS = 5` is exactly that mistake);
  - the only genuinely *measured* number is REQ-628a's column count, because
    that one is bounded by the physical viewport rather than by product
    ambition.

- **D6** — per-room module content. The ceiling rule governs *enablement*
  today. May a room override a module's *content* (a different WLAN password
  per room), or only its visibility? This changes the persistence model
  materially and should be answered before `02-design.md`.
- **Risk — payload/ETag granularity.** One ETag for all content means editing
  one restaurant tip retransmits the whole payload. At ~10 kB gzip on a LAN
  this is irrelevant, but it should be a recorded trade-off rather than a
  discovery.
- **Risk — the overflow popover has no design (open).** The sidebar was drawn
  for five entries. The owner's popover (REQ-626…631) answers the *mechanism*,
  but the panel itself is not in `docs/design/screens/` and has no drawn
  reference. It is, however, built entirely from tokens and a component
  pattern that already exist in the repo (`StatusBarMenuComponent`'s glass
  panel, option rows, selected-row glyph and CDK anchoring), which is exactly
  the situation the three in-repo kiosk states under
  `docs/design/screens-inhouse/` were designed for. Recommendation: design it
  in-repo rather than spending a metered Figma request.
- **Risk — scope.** This feature touches the contract, the backend, the
  generated client and the frontend at once. `03-todo.md` must sequence it so
  the contract lands first and the client regeneration is one reviewable step.
