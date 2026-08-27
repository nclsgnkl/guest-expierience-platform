# TODO: Guest Payload Consolidation

**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** All 19 tasks implemented (2026-08-27), uncommitted — review next

> **Start condition:** Part A (`sidebar-catalogue-overflow`) is **committed**.
> This list is written against the post-Part-A tree — see
> [`02-design.md` §0](02-design.md) for the seven shared files (plus one near-miss) and the
> resolution of each. Rebase, do not merge by hand.
>
> **Branch:** `refactor/bce-subpackages-and-boundary-security`. **The owner
> commits.** No `pom.xml` / `package.json` change exists in this list — if a
> task seems to need one, stop and report.
>
> Every design decision is already taken in `02-design.md`. **Do not re-decide
> the endpoint name, the stamp scheme, the banner's state shape or its
> placement here** — implement them.

---

## Phase 1 — Contract (§1, §8)

- [x] **T-01** Merge the two guest operations in `api/openapi.yaml`
  — *REQ-941, REQ-948*
  - Delete `GET /api/guest/session` and `GET /api/guest/screens/home`. Add
    `GET /api/guest/content` (`getGuestContent`, `bearerAuth`, header param
    `If-None-Match` shaped exactly like `getGuestAsset`'s, responses `200` with
    the `ETag` header, `304`, `401` — and **no** `404`/`409`). Rename
    `GuestSession` → `GuestContent` and `GuestHomeScreen` → `GuestHome`; add
    the optional `home: $ref GuestHome` to `GuestContent` with the
    "occupied only — a vacant room serves no stay-derived value" wording the
    sibling fields use. Leave every other guest schema alone, **including Part
    A's `maxItems: 50`**. Extend the two `manager-menu` `400` descriptions with
    the mandatory-item case.
  - Files: `api/openapi.yaml`
  - Validate: `./mvnw verify` regenerates and **fails to compile** the backend
    (expected — T-02/T-03 fix it); `packages/api-client/` shows the renamed
    models and `getGuestContent(ifNoneMatch?, …)`.

## Phase 2 — Backend: the stamp and the merged read (§2, §3)

- [x] **T-02** `AuditControl.latestSequence()`
  — *REQ-946, REQ-947*
  - Add `long maxId()` to `AuditEntryRepository` (`select coalesce(max(id), 0)
    from AuditEntry`) and a **public, non-`@Transactional`** `latestSequence()`
    on `AuditControl` — non-transactional deliberately, like `page()`, so it
    never enters `AuditCoverageTest`'s derived set. Javadoc states why the
    guest ETag may lean on it: v1.8.1 makes "every manager mutation writes a
    row" a constitutional obligation and `AuditCoverageTest` enforces it, and
    `max(id)` survives the retention prune because the sequence never goes
    backwards.
  - Files: `…/de/gxp/audit/control/AuditControl.java`,
    `…/de/gxp/audit/control/AuditEntryRepository.java`
  - Validate: `./mvnw -Dtest=AuditCoverageTest test` green; the five
    architecture guards green.

- [x] **T-03** `GuestContentVersion` — the stamp
  — *REQ-946, REQ-947*
  - New **package-private** class in `de.gxp.guest.control`:
    `String stampFor(long roomId, StayDto stay)` = `sha256hex(bootId | audit
    sequence | roomId | stay fingerprint | refresh bucket)`, per
    `02-design.md` §2.1. `bootId` is a `UUID` fixed at construction;
    the fingerprint is `vacant` or
    `id:displayName:checkIn:checkOut:language:appearance`;
    `STAMP_REFRESH_SECONDS = 300` is a named constant carrying §2.3's
    reasoning (the commit-interleaving hazard **and** the fact that
    `NOT_A_MANAGER_MUTATION` legitimately holds guest-side writes) so nobody
    deletes it as noise. Output stays 64 hex chars — the wire shape does not
    change.
  - Files: `…/de/gxp/guest/control/GuestContentVersion.java` *(new)*
  - Validate: compiles; `ControlPackageVisibilityTest` green (the class must
    **not** be public).

- [x] **T-04** `GuestControl.content(ifNoneMatch)` and the deletion of the filter
  — *REQ-941, REQ-942, REQ-944, REQ-946, REQ-948*
  - Replace `session()` and `homeScreen()` with one `@Transactional public
    GuestContent content(String ifNoneMatch)`: room from the identity, one
    `activeStayOf`, stamp, **match → throw `Response.notModified(tag)` before
    composing anything**, else compose (today's `session()` body, plus today's
    `homeScreen()` body when `stay != null`) and throw
    `Response.ok(content, APPLICATION_JSON).tag(tag).cacheControl(no-cache)`.
    The `404 screen-not-found` branch (`GuestControl:164`) and the
    `409 room-vacant` branch (`:158`) are **deleted**, not moved. Delete
    `GuestEtagFilter` entirely. `GuestResource` keeps one delegating line per
    method and loses `getGuestHomeScreen`.
  - **Verify-at-implementation** (§3.2): a thrown `Response.ok(dto,
    APPLICATION_JSON)` is written by the Jackson body writer with the ETag
    intact. If it is not, take §3.4's request/response filter pair — same
    stamp, same tests, one more class — and record it; **do not** change the
    stamp scheme or the contract.
  - Files: `…/de/gxp/guest/control/GuestControl.java`,
    `…/de/gxp/guest/boundary/GuestResource.java`,
    `…/de/gxp/guest/control/GuestEtagFilter.java` *(delete)*
  - Validate: backend compiles; `grep -r "screen-not-found\|GuestEtagFilter"
    backend/app/src/main` is empty.

- [x] **T-05** `home` becomes mandatory
  — *REQ-948*
  - In `MenuControl`: `public static final Set<String> MANDATORY_KEYS =
    Set.of("home")`; resolution treats a mandatory key as enabled whatever any
    config row says and forces its resolved `sortIndex` to `0`;
    `validateConfigEntries` rejects `enabled = false` or a non-null
    `sortIndex` for a mandatory key **at either scope** with
    `ApiErrors.badRequest("menu-item-mandatory", …)`. The ceiling rule,
    invariants 2/3 and the stranded-card logic are untouched.
  - Files: `…/de/gxp/menu/control/MenuControl.java`
  - Validate: `./mvnw -Dtest='Menu*Test,ActionCardTest' test` green.

- [x] **T-06** `V11__home_is_mandatory.sql`
  — *REQ-948*
  - `UPDATE menu_item_config SET enabled = NULL, sort_index = NULL WHERE
    menu_item_key = 'home';` — no DDL. Header comment: this repairs instances
    where a manager already disabled or re-sorted `home` (the live defect's
    residue), both columns are nullable by `V2` so this restores per-field
    inheritance, and icon/label overrides on `home` survive. Pin it in
    `MigrationTest` beside Part A's `V10`.
  - Files: `…/db/migration/V11__home_is_mandatory.sql` *(new)*,
    `…/de/gxp/system/MigrationTest.java`
  - Validate: `./mvnw -Dtest=MigrationTest test` green; a fresh instance and an
    upgraded one both start.

## Phase 3 — Backend tests (§9)

- [x] **T-07** `GuestContentTest` — the merged read
  — *REQ-941, REQ-944, REQ-945, REQ-948*
  - Rename `GuestSessionTest` → `GuestContentTest` and fold in
    `GuestHomeScreenTest`'s surviving cases (template resolution per offered
    language, artwork room → property fallback, 0–4 cards), then **delete
    `GuestHomeScreenTest.java`**. **Carry Part A's `MenuFixtures
    .restoreCatalogueOptOut(entityManager)` teardown across** — the deleted
    file's `@AfterEach` is the only reason the shared database survives its
    property-scope config replace. Occupied → `home` present, stay fields
    present; vacant → `200`, **no** `home`, no `guestDisplayName`, no
    `preference`, property half intact.
  - Files: `…/de/gxp/guest/GuestContentTest.java` *(renamed)*,
    `…/de/gxp/guest/GuestHomeScreenTest.java` *(delete)*
  - Validate: `./mvnw -Dtest=GuestContentTest test` green; the whole backend
    suite green in one run (the shared-database hygiene is only visible there).

- [x] **T-08** `GuestEtagTest` — the stamp's three properties
  — *REQ-946, REQ-947*
  - Rewrite for `/api/guest/content` and add, as separate cases:
    (a) **the seam** — poll, corrupt `property.timezone` with native SQL (no
    audit row ⇒ stamp unchanged), poll with `If-None-Match` → **`304`**, poll
    without it → `5xx` proving the payload was genuinely unresolvable, restore;
    (b) **check-in with no write** — insert a stay with an elapsed `check_in`
    by native SQL, poll with the vacant ETag → **`200`** with the new stay's
    content; (c) **two rooms** — two devices, identical content, **different**
    ETags. Keep today's round-trip/replay/manager-edit case.
  - Files: `…/de/gxp/guest/GuestEtagTest.java`
  - Validate: `./mvnw -Dtest=GuestEtagTest test` green; every case restores the
    state it changed.

- [x] **T-09** Mandatory-item tests and the audit list
  — *REQ-948*
  - New `MandatoryMenuItemTest` (a **new file**, to keep Part A's
    `MenuDisableGuardTest` edits out of the way): disabling `home` at property
    scope → `400 menu-item-mandatory`; at room scope → the same; a
    natively-written legacy `enabled = false` row is inert and `home` still
    resolves at `sortIndex 0`; `GET /api/guest/content` answers `200` with
    `home` throughout. In `AuditCoverageTest`, replace `"session",
    "homeScreen"` with `"content"` in `NOT_A_MANAGER_MUTATION` — **and change
    nothing else in that file**.
  - Files: `…/de/gxp/menu/MandatoryMenuItemTest.java` *(new)*,
    `…/de/gxp/audit/AuditCoverageTest.java`
  - Validate: `./mvnw test` — whole backend suite green, five architecture
    guards green.

## Phase 4 — Frontend: one resource (§5)

- [x] **T-10** Collapse `GuestDataService`
  — *REQ-942, REQ-943, REQ-946, REQ-949*
  - Remove `HOME_CACHE_KEY`, `homeEtag`, `heldHome`, `fetchHome()` (both
    overloads), `asVacant()` with its comment, `withIfNoneMatch()` and the
    class comment's `If-None-Match`-mechanism paragraph; remove the `409`
    branch from `bootstrap()` and `refresh()` and the home branches from
    `adopt()` and `adoptCache()`. Rename per §5's table
    (`CONTENT_CACHE_KEY = 'gxp.cache.content'`, `etag`, `payload()`,
    `preferenceOverride()`, `fetchContent()`), call
    `this.api.getGuestContent(etag ?? undefined, 'response')`, and evict the
    two legacy keys once where the cache is first touched. **Do not touch**
    `ConnectionState`, the transitions, the timers, the backoff, the 401 wipe
    or the map-before-cache ordering (REQ-943).
  - Files: `frontend/src/app/core/guest/guest-data.service.ts`,
    `…/core/guest/guest-bootstrap.service.ts`,
    `…/core/preferences/http-preference-gateway.ts`
  - Validate: `grep -n
    "HOME_CACHE_KEY\|homeEtag\|heldHome\|fetchHome\|asVacant"
    frontend/src/app/core/guest/guest-data.service.ts` is empty; the file
    holds **one** fetch, **one** ETag field, **one** cache key and **one**
    held copy; neither entry point runs a two-call sequence; no branch
    handles a `409`; `ng build` green. *(The `wc -l < 452` check was struck
    by the owner on 2026-08-27 — `01-prd.md` REQ-949. Judge the structure,
    not the size.)*

- [x] **T-11** `mapGuestContent(content)`
  — *REQ-942, REQ-948*
  - One parameter instead of two; `home` read from `content.home`;
    `mapHome()` and every validation rule unchanged, **including Part A's
    trust-the-count comment on `menu.items`**. `mapStoredPreferences` follows
    the rename. Add the invariant of `02-design.md` §1.2: `occupancy ===
    OCCUPIED` with `home` absent is a `GuestContentMappingError`. Correct
    `HomeContentComponent`'s class comment — the "occupied-but-home-unfetched
    window of a cache-boot" it describes no longer exists; the `@if` branch
    stays as defence.
  - Files: `…/core/guest/guest-content.mapper.ts`,
    `…/core/guest/guest-content.mapper.spec.ts`,
    `…/features/home/home-content.component.ts` (comment only)
  - Validate: `ng test` for the mapper spec green.

- [x] **T-12** Rewrite `guest-data.service.spec.ts`
  — *REQ-950, REQ-945, REQ-951*
  - The 13 cases, rebuilt around one request to `/api/guest/content`: every
    transition (200 / 304 / error / 401 / vacancy / cache-rejected) keeps its
    **observable** expectations verbatim. The two `409`-on-home cases become
    "the server answered vacant" cases and must still assert that the previous
    guest's content leaves the screen at once. Add REQ-945 (the property half
    survives a vacancy episode, in RAM **and** in `localStorage`) and REQ-951
    (top-level key allow-list on the record and on the payload; the device
    token's literal absent from the serialised record; **no name-based filter
    on `password`/`code`/`secret` — see the PRD's warning**).
  - Files: `…/core/guest/guest-data.service.spec.ts`,
    `…/core/api/provide-guest-api.spec.ts` (uses `getGuestSession` today)
  - Validate: `ng test` green; the spec references `/api/guest/content` only.

## Phase 5 — The escalating banner (§6)

- [x] **T-13** `StalenessService`
  — *REQ-952, REQ-943*
  - New `core/guest/staleness.service.ts`: `STALE_ESCALATION_MS = 60 * 60 *
    1000` and `level: 'NONE' | 'PILL' | 'BANNER'` derived from
    `connection()`, `staleSince()` and `ClockService.now()` — **tri-state**, so
    pill and banner cannot both be visible. No new timer, no change to
    `GuestDataService`, no fourth `ConnectionState`.
  - Files: `…/core/guest/staleness.service.ts` *(new)*,
    `…/core/guest/staleness.service.spec.ts` *(new)*
  - Validate: spec covers 59 min → `PILL`, 60 min → `BANNER`, two hours of
    `304`s → `NONE`, cold boot from an hour-old `storedAt` → `BANNER`
    immediately; and asserts `ConnectionState` still has exactly three members.

- [x] **T-14** The banner component and the pill's one-line change
  — *REQ-952*
  - *(Geometry superseded 2026-08-27 by the fix round: the clamp is dropped —
    `width: max-content` to a one-line body, glyph `--size-icon-md` in
    `--color-status-warning`, timestamp on the title's row, entrance + glyph
    pulse behind `prefers-reduced-motion`. Read §6.3 and `connection.md`, not
    this bullet, for what ships.)*
  - New `layout/staleness-banner/` (ts/html/css) per §6.3: absolute overlay in
    the pill's anchor, `min(22rem, 40vw)`, glass panel, the already-registered
    `WifiSlash` in `--color-status-offline` (decorative — **no new icon**, so
    `registered-icons.ts` stays a Part A file), title + body + the reused
    `stale.asOf` line, `role="status"` / `aria-live="polite"` set once, host
    `display: contents`. Body string chosen from `occupancy()`. Render it
    beside `<app-staleness-pill />` in **both** hosts.
    `StalenessPillComponent.visible` becomes `level() === 'PILL'` — one line.
  - Files: `…/layout/staleness-banner/*` *(new)*,
    `…/layout/staleness-pill/staleness-pill.component.ts`,
    `…/layout/shell/shell.component.{ts,html}`,
    `…/features/standby/standby.component.{ts,html}`, their specs
  - Validate: `ng test` green — one case per string variant (occupied vs.
    vacant), one asserting the pill is gone when the banner is up; no raw hex
    or px in the CSS, no user-visible string in the template.

- [x] **T-15** Catalogue keys
  — *REQ-952*
  - Add `stale.banner.title`, `stale.banner.body`, `stale.banner.bodyStandby`
    to `de.ts` (the key set's source of truth) and translate them in `en.ts`,
    copy verbatim from `02-design.md` §6.4. German stays imperative-neutral.
  - Files: `…/core/i18n/catalog/de.ts`, `…/core/i18n/catalog/en.ts`
  - Validate: `ng build` green (a missing key fails the build by design).

- [x] **T-16** Build against `connection.md`, do not rewrite it
  — *REQ-952*
  - **The spec is already written** (`planner`, `02_design`, the Part A
    precedent of `menu-overflow.md`):
    `docs/design/screens-inhouse/connection.md` → "The escalated staleness
    banner", with the trigger, the layout, the tokens, the `aria-live`
    discipline, both string variants and the reason the occupied wording
    cannot be reused on standby. T-14/T-15 implement **that file**; do not
    invent copy or geometry. Touch it only if measurement in T-17 forces a
    geometry change — then update the spec in the same commit and say so, and
    keep the in-repo provenance warning intact. **No design-tool request.**
  - Files: `docs/design/screens-inhouse/connection.md` (only if T-17 forces it)
  - Validate: the shipped copy matches the spec's table verbatim in both
    languages; the shipped geometry matches, or the spec was updated with it.

## Phase 6 — Measure, sweep, hand over

- [x] **T-17** Measure the banner
  — *REQ-952*
  - Add a BANNER cell to `frontend/tools/measure-states.mjs` beside the
    existing STALE-pill cell: load online once, rewrite the cached record's
    `storedAt` to 61 minutes ago, block `/api`, reload — the banner must be up
    immediately (which also measures the cold-boot case). Both viewports, both
    themes, both languages, **and both hosts** (shell + standby; the standby
    path already exists and needs `GXP_SMOKE_ADMIN_PW`).
  - Files: `frontend/tools/measure-states.mjs`
  - Validate: every cell reports `scrollWidth <= clientWidth` and
    `scrollHeight <= clientHeight`; the banner collides with neither the
    status-bar cluster nor Home's card row. If geometry needs adjusting,
    change the CSS and re-measure — never `overflow: hidden`.

- [x] **T-18** Documentation sweep
  — *REQ-941, REQ-942, REQ-952*
  - `docs/system-context.md`: update the `de.gxp.guest` row (one content
    resource, the stamp, `GuestEtagFilter` gone, `home` mandatory), the
    `GuestDataService` row (one ETag/cache key/held copy, `gxp.cache.content`,
    no `defaultHeaders` hack), the `StalenessPillComponent` row, and add a
    `StalenessBannerComponent` row; correct the `screens/home` mentions at
    lines 132 and 606. **Only after Part A's `T-21` has landed** — that task
    edits the same file. Flip this feature's row in
    `docs/features/index.md`. Update `docs/features/guest-payload-consolidation
    /handoff.md`.
  - Files: `docs/system-context.md`, `docs/features/index.md`,
    `docs/features/guest-payload-consolidation/handoff.md`
  - Validate: no document still claims two guest content endpoints.

- [x] **T-19** Whole-suite green and the byte-identical client
  — *REQ-941, REQ-949, REQ-950*
  - `./mvnw verify` twice in a row: `packages/api-client/` must be
    **byte-identical** on the second build (`git status` clean after it).
    `ng build` + `ng test`. Re-check REQ-949's grep and its four structural
    properties (one fetch, one ETag field, one cache key, one held copy; no
    two-call sequence; no `409` branch). **Not the line count** — struck by
    the owner 2026-08-27.
    Confirm `pom.xml` and `package.json` are untouched, `AuditCoverageTest`'s
    assertions and exemption list carry exactly the one intended swap, and the
    five architecture guards are unchanged — **no sixth was added**.
  - Files: none
  - Validate: all green; one commit carries contract + backend +
    `packages/api-client/` + frontend + docs. **The owner commits.**

---

## Not in this feature — stop and report if a task seems to need it

- Merging `assets/{assetId}` or the preference endpoints into the content
  resource — D7 scoped this explicitly, and the owner asked directly.
- A fourth `ConnectionState`, a new timer, or any change to the cadence,
  backoff, 401 wipe or transitions — REQ-943, and D13 decided the state shape.
- A hard cutoff that stops rendering after the escalation — explicitly
  rejected by D13.
- Module content, a `GuestModule` union, `oneOf`/`discriminator` — Part C.
- A `content_version` table, an in-memory bump counter, or a sixth
  architecture test — all considered and rejected in `02-design.md` §2.2/§9;
  the last one would need a constitution amendment.
- Any new dependency, `pom.xml`/`package.json` edit, nested `CLAUDE.md` entry
  or constitution amendment — propose the exact wording and **end the turn**.
- A design-tool request — the banner is in-repo design (§6.5).
