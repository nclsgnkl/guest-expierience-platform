# Design: Guest Payload Consolidation

**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Draft
**Governing:** `docs/constitution.md` **v1.9.0**, `backend/CLAUDE.md`, `frontend/CLAUDE.md`

> **Part B of three** (owner decision D1, 2026-08-27). This document is HOW;
> REQ-941…REQ-952 are in the PRD and are not restated here.
>
> **Nothing in this design opens an approval gate**: no dependency
> (`pom.xml`/`package.json` untouched), no datastore or infrastructure choice,
> no new architectural pattern — every mechanism below already exists in this
> repo and is named at its precedent — no nested `CLAUDE.md` entry, no
> constitution amendment.

---

## 0. Ordering against Part A — read this first

**Part A (`sidebar-catalogue-overflow`) lands first.** It is in
implementation now (T-01…T-09 done, T-10…T-21 open) and this design is written
against the **post-Part-A** tree, not against `HEAD~`. Concretely, that means
`GuestMenu.items` already carries `maxItems: 50` and
`guest-content.mapper.ts` has already lost `MAX_MENU_ITEMS`.

The PRD's blocking map originally said the two parts share *"`api/openapi.yaml`
only … no code file is touched by both"*. **That is no longer true** — Part A
has since touched frontend and backend-test files this Part must also change.
The corrected list (the PRD's Blocking map has been updated to match):

| Shared file | Part A does | Part B does | Resolution |
|---|---|---|---|
| `api/openapi.yaml` | `maxItems: 50` on `GuestMenu.items` (T-01, **done**) | restructures the guest operations (§8) | disjoint hunks; B rebases |
| `packages/api-client/**` | regenerated | regenerated | regenerate, never merge by hand |
| `core/guest/guest-content.mapper.ts` (+ spec) | dropped `MAX_MENU_ITEMS` (T-08, **done**) | changes `mapGuestContent`'s signature (§5) | disjoint; B rebases |
| `layout/shell/shell.component.html` | wires sidebar/overflow (T-14, **open**) | adds one `<app-staleness-banner />` element (§6) | disjoint lines |
| `frontend/tools/measure*.mjs` | extends **`measure.mjs`** (its route grid) — T-18, **open** | extends **`measure-states.mjs`** (a kiosk-state cell) | **different files**; no conflict, listed so nobody assumes one instrument |
| `de/gxp/guest/GuestHomeScreenTest.java` | added the `MenuFixtures` hygiene call (T-03, **done**) | **deletes the file**, folding its cases into `GuestContentTest` | B must carry the hygiene call across — see T-07 |
| `de/gxp/audit/AuditCoverageTest.java` | added the hygiene call (T-03, **done**) | swaps `"session", "homeScreen"` → `"content"` in `NOT_A_MANAGER_MUTATION` | disjoint lines |
| `de/gxp/system/MigrationTest.java` | pinned `V10` | pins `V11` (§7) | disjoint lines |

**Rule for the implementer:** do not start Part B until Part A's commit exists.
Every "current line number" in this document is pre-Part-A and is indicative
only.

---

## 1. One content resource, and its name

### 1.1 The endpoint

```
GET /api/guest/content            operationId: getGuestContent
```

`GET /api/guest/session` and `GET /api/guest/screens/home` both disappear into
it (D7 — and **only** those two: `assets/{assetId}` and `preferences` are
untouched).

**Why `content`, and why not the alternatives** (this call is the design's, per
the owner's instruction):

- It is what the resource *is*: everything this device's room renders from, in
  one document. It is also D7's own vocabulary — "one content resource".
- **It retires the `screens/` namespace, which is the point.** `screens/home`
  advertised a per-screen family (`screens/info`, `screens/umgebung`, …) that
  the project has since decided against: Part C hangs module content off the
  one payload, not off one endpoint per screen. Leaving `screens/` in the
  contract would keep an architectural promise we have chosen not to keep.
  After the change the guest tag reads `content` · `assets/{assetId}` ·
  `preferences` — three nouns, one level deep, no orphaned sub-namespace.
- **`session` is rejected**, even though keeping it would be the smallest
  diff. It names the *authentication* relationship, not the payload; a reader
  meeting `GuestSession` expects a token, an expiry, an identity — and gets
  property branding, a menu and a greeting. Keeping it would also make the
  merge invisible in the contract diff, leaving "where did `home` go?" as a
  question the contract cannot answer.
- `view` (vague), `kiosk` (names the client, not the resource) and `screen`
  (re-introduces the framing being removed) are rejected.

### 1.2 The payload shape

`GuestSession` → **`GuestContent`**, `GuestHomeScreen` → **`GuestHome`**,
nested under a new optional `home` property. Every other schema
(`GuestProperty`, `GuestRoom`, `GuestSettings`, `GuestPreference`,
`GuestMenu`, `GuestMenuItem`, `GuestSettingsEntry`, `GuestStandby`,
`GuestActionCard`) is **unchanged**, including Part A's `maxItems: 50`.

```
GuestContent
  property, room, occupancy, settings, menu, standby     required — property-scoped
  guestDisplayName?, preference?, home?                  occupied only — stay-derived
```

**Nested, not flattened**, for three reasons: `headline` would collide with
`standby.headline`; the frontend mapper keeps `mapHome()` verbatim; and Part C
gets an obvious place to add sibling content groups.

**`home` is absent exactly when the room is vacant.** This is today's `409
room-vacant` expressed as absence rather than as a second status code, and it
is what makes REQ-944/REQ-945 hold: the vacant answer is one authoritative 200
carrying the property-scoped half and nothing stay-derived. Home's texts are
resolved against the active stay (`{stay.displayName}`, `{stay.checkOut|time}`);
resolving them with `stay == null` would leave literal braces on a kiosk, which
the server has never allowed.

**Mapper invariant — occupied implies `home`.** `mapGuestContent` rejects a
payload whose `occupancy` is `OCCUPIED` and whose `home` is absent
(`GuestContentMappingError`, which `GuestDataService` already treats as a
failed fetch — never rendered, never cached). It is a server bug, and the
mapper's existing posture is to name it rather than render half a screen.
`HomeContentComponent`'s `@if (home())` branch stays as defence, but its
comment — *"the brief occupied-but-home-unfetched window of a cache-boot"* —
describes a window this Part removes and is corrected with it.

**Consequence — `asVacant()` dies.** It exists only because two calls could
disagree about occupancy. One call cannot.

---

## 2. The version stamp — REQ-946 and the sharp edge, REQ-947

### 2.1 What the ETag is made of

The ETag stops being `sha256(serialise(payload))`. It becomes the SHA-256 of a
short **stamp string** assembled from five terms, all cheap:

```
stamp = bootId | auditSequence | roomId | stayFingerprint | refreshBucket
ETag  = "<sha256hex(stamp)>"          (unchanged on the wire: 64 hex, strong)
```

| Term | Value | What it covers | Cost |
|---|---|---|---|
| `bootId` | a `UUID` fixed at bean construction | migrations, seeders, the server-side settings-chrome constants, **any release delivered by the signed update channel** — none of which writes a row | free |
| `auditSequence` | `max(audit_entry.id)`, `0` when empty | every manager mutation: property, room, menu config, action cards, stays, assets, devices | one indexed `max(id)` |
| `roomId` | the device identity's room | REQ-947's "two rooms must not share one stamp" | free |
| `stayFingerprint` | `vacant`, or `id:displayName:checkIn:checkOut:language:appearance` of the active stay | **clock-driven occupancy transitions** and the **un-audited guest preference write** | free — the row is loaded anyway |
| `refreshBucket` | `floor(epochSeconds / 300)` | the safety net, §2.3 | free |

### 2.2 Why the audit table is the write counter

`AuditControl.record()` is called by **every** content-affecting manager write
(verified 2026-08-27: `PropertyControl:73`, `RoomControl:54/74/102`,
`StayControl:54/72/80`, `MenuControl:99/166`, `AssetControl:97/162`,
`DeviceControl:239/258/269`), and that is not a coincidence to be re-checked by
hand: `AuditCoverageTest.everyMutatingControlMethodCallsTheAuditControl`
derives every `@Transactional public` Control method **from the sources** and
fails the build unless it either audits or is pinned in
`NOT_A_MANAGER_MUTATION`. Constitution v1.8.1 makes that obligation permanent.

So the stamp inherits an existing, enforced coverage guarantee instead of
creating a second one. **No new table, no new migration, no new bump call
site, no new bean state, nothing to forget.** A new mutation cannot be added
without passing through the test that already exists.

Rejected alternatives, and why:

- **A single-row `content_version` table** bumped with `UPDATE … SET v = v+1`.
  Correct (the row lock serialises commit order) but costs a migration, an
  entity, a repository, a bump at every write site and a *second* coverage
  obligation duplicating the audit's — to buy the 5-minute self-heal §2.3
  already buys for three lines.
- **An in-memory `AtomicLong`.** Worse than the audit table, not better: an
  in-JVM increment is not transactional, so a poll landing between "increment"
  and "commit" caches an ETag for content it has not seen — a *permanent*
  stale `304`, reachable with a single writer. Fixing that needs an interposed
  transaction synchronisation, i.e. more machinery than the option it was
  meant to simplify.
- **`count(*)` instead of `max(id)`** would be commit-ordered, but the audit
  table is pruned daily by `AuditScheduler` → `pruneOlderThan`, so its count
  is not monotonic. `max(id)` is: the sequence never goes backwards, and
  pruning removes the oldest rows. The one state where `max(id)` falls (an
  instance idle for the whole retention window, emptying the table) moves the
  stamp to a value **no kiosk has ever held during this boot**, so it can only
  cause an extra `200`, never a stale `304`.

### 2.3 The one hazard, and the three lines that close it

`max(id)` is allocated at INSERT, not at COMMIT. With two overlapping
audit-writing transactions the newer id can commit first:

> T1 (manager edit) inserts id 100 and stays open · T2 (the scheduled update
> check) inserts id 101 and commits · a poll reads `max = 101`, composes the
> **pre-T1** payload, answers `200` + ETag(101) · T1 commits. `max` is still
> 101 → every later poll is a `304` and **the edit never reaches the kiosk**
> until some other audit row is written.

Narrow — it needs a poll inside a millisecond window — but it does not
self-heal, and REQ-947 is exactly the requirement about not shipping that class
of bug.

**`refreshBucket` closes it:** the stamp changes at least every **300 s**, so
any invalidation the stamp fails to notice costs at most one bucket of
staleness instead of an unbounded one. At a 30 s poll that is nine `304`s and
one `200` — REQ-946's win is ~90 % of polls, not 100 %, and the failure mode
becomes *bounded latency* instead of *silent, unbounded staleness*.

It also protects the one coverage hole that is structural rather than
accidental: `NOT_A_MANAGER_MUTATION` **legitimately** contains guest-side
writes (`writePreference` is on that list today), so "everything is audited" is
true of manager writes only. The bucket makes REQ-947 robust rather than
exhaustive-by-inspection.

`STAMP_REFRESH_SECONDS = 300` is a named constant with this reasoning above
it, so nobody deletes it as noise.

*(Recorded, not implemented: at a bucket boundary every kiosk in the property
re-fetches at once. On one property with tens of rooms and a ~10 kB payload on
a LAN, that is nothing. A per-room phase offset would spread it and is
deliberately not added.)*

### 2.4 Where the code sits

```
de.gxp.guest.control.GuestContentVersion   (package-private, new)
    String stampFor(long roomId, StayDto stay)     → 64 hex chars
de.gxp.audit.control.AuditControl          (public method added)
    public long latestSequence()                   → max(id), 0 when empty
de.gxp.audit.control.AuditEntryRepository  (package-private method added)
    long maxId()
```

`GuestContentVersion` is a package-private collaborator in `.control` —
`backend/CLAUDE.md`'s explicitly allowed category ("every package-private
collaborator that is not a JPA type"), and `ControlPackageVisibilityTest`
requires it to be package-private. It injects `AuditControl`, which is the one
crossing the architecture allows (another feature's public `*Control`).
`latestSequence()` carries **no** `@Transactional` — like `AuditControl.page()`
— so it does not appear in `AuditCoverageTest`'s derived set at all.

---

## 3. How a `304` gets answered without resolving anything

### 3.1 The contract declares `If-None-Match`

`getGuestContent` takes `If-None-Match` as a **declared header parameter**,
exactly like `getGuestAsset` does today. Two things fall out:

- The generated TS client emits a real parameter
  (`getGuestContent(ifNoneMatch?, 'response')`, verified against the shipped
  `guest.service.ts:54–64`), so **`GuestDataService.withIfNoneMatch()` is
  deleted** — with it the documented `defaultHeaders` race-hack and the class
  comment defending it.
- The backend gets the header as a method argument instead of scraping it off
  a filter's request context.

### 3.2 The Control decides; the Boundary stays one line

```java
// GuestResource — one delegating line, unchanged in kind
public GuestContent getGuestContent(String ifNoneMatch) { return guestControl.content(ifNoneMatch); }
```

`GuestControl.content(String ifNoneMatch)`:

1. `roomId()` from the identity (REQ-411, unchanged),
2. `stayControl.activeStayOf(roomId)` — one indexed query, needed either way,
3. `stamp = contentVersion.stampFor(roomId, stay)`,
4. **match → `throw new WebApplicationException(Response.notModified(tag).build())`** — nothing else has been read, nothing is composed, nothing is serialised,
5. otherwise compose the payload (the current `session()` body plus, when
   `stay != null`, the current `homeScreen()` body) and
   `throw … Response.ok(content, APPLICATION_JSON).tag(tag).cacheControl(no-cache).build()`.

**Throwing the success Response is this repo's existing pattern**, not a new
one: `AssetControl.guestBinary():130` does `throw deliver(asset, ifNoneMatch,
…)` for both its `200` and its `304`, and `ApiErrorMappers` documents that
entity-carrying `WebApplicationException`s never reach a mapper — the runtime
uses their response directly. `ResponseStatusFilter` explicitly leaves thrown
responses alone.

*Verify at implementation (the repo's own convention for this kind of claim):
that a thrown `Response.ok(dto, APPLICATION_JSON)` is written by the Jackson
body writer with the ETag intact. If it is not, the fallback is the filter pair
of §3.4 — the stamp scheme and every requirement below are unaffected either
way.*

### 3.3 `GuestEtagFilter` is deleted

Its job was `sha256(objectMapper.writeValueAsBytes(entity))` on **every**
`/api/guest/**` JSON `200` (`GuestEtagFilter:60`) — precisely what REQ-946
forbids. After the merge there is exactly one guest JSON GET, and it now owns
its own ETag and `Cache-Control: no-cache`. Deleting the class makes REQ-946
**structurally** true: no code path exists that could serialise a payload in
order to tag it.

### 3.4 Recorded fallback (not the plan)

If §3.2's verification fails, `GuestEtagFilter` becomes a
`ContainerRequestFilter` + `ContainerResponseFilter` pair: the request half
computes the stamp and `abortWith(304)`, the response half writes the header
from `requestContext.getProperty(...)`. Same stamp, same tests, one more class
and the `defaultHeaders` hack survives. It is the second choice, and the reason
is contract honesty, not mechanics.

---

## 4. `home` becomes mandatory — REQ-948

The owner's fix (recorded in `docs/handoff.md` after the PRD was written):
**`home` is not removable by a manager and its sort index is fixed at 0** —
like `einstellungen`, except that `home` still comes from the backend. An
unreachable error case beats a handled one.

**Part A has not taken any of this.** `V10__catalogue_expansion.sql` only
inserts eight disposable fixture rows and their property-scope `enabled =
false` opt-outs; it touches neither `home` nor `MenuControl`. REQ-948 is
therefore Part B's in full.

Three changes, all in `de.gxp.menu.control.MenuControl`:

1. `public static final Set<String> MANDATORY_KEYS = Set.of("home")` — one
   place, keyed by catalogue key, extensible without a schema change.
2. **Resolution ignores config for a mandatory key**: `enabled(roomId, key)`
   returns `true`, and the resolved `sortIndex` is forced to `0`, whatever any
   property- or room-scope row says. A legacy row is therefore *inert*, not a
   trap.
3. **The write side refuses it**: `validateConfigEntries` rejects an entry for
   a mandatory key that carries `enabled = false` or a non-null `sortIndex`,
   at either scope, with `ApiErrors.badRequest("menu-item-mandatory", …)` —
   the neighbourhood's existing shape (`unknown-menu-item`,
   `duplicate-menu-item` are 400s). The contract's two `400` descriptions
   gain the case; no schema changes.

`GuestControl` then loses the `404 screen-not-found` branch entirely
(`GuestControl:164`), the contract loses the response, and `GuestDataService`
loses a fall-through it never had. **The state REQ-948 asks to be "defined and
tested" is: `home` is always in the resolved menu, and the merged payload
always carries `home` for an occupied room.**

A `V11` migration neutralises rows that already exist in the field (§7) so the
manager UI and the guest view cannot disagree about a section that is now
unremovable.

---

## 5. `GuestDataService` — one of everything

Removed outright: `HOME_CACHE_KEY`, `homeEtag`, `heldHome`, `fetchHome()`
(both overloads), `asVacant()` (and its 19-line comment), `withIfNoneMatch()`,
the `409` branch in `bootstrap()` and in `refresh()`, the home branch in
`adopt()` and in `adoptCache()`, and the `If-None-Match`-mechanism paragraph of
the class comment. Renamed for honesty:

| Before | After | Reason |
|---|---|---|
| `SESSION_CACHE_KEY = 'gxp.cache.session'` | `CONTENT_CACHE_KEY = 'gxp.cache.content'` | new shape ⇒ new key (see below) |
| `sessionEtag` | `etag` | there is one |
| `session()` signal | `payload()` | it is the raw wire record; `config()` is the mapped one. One production consumer (`GuestBootstrapService:41`) |
| `sessionPreference()` | `preferenceOverride()` | one production consumer (`HttpPreferenceGateway:37`) |
| `fetchSession()` | `fetchContent()` | — |

`mapGuestContent(session, home)` becomes `mapGuestContent(content)` and reads
`content.home` internally; `mapHome()` is unchanged. The overload pair on
`fetchContent()` stays — it is what gives the `bootstrap()` caller a payload
without a `notModified` arm.

**Legacy cache eviction.** The old keys are a different shape, so reading
`gxp.cache.session` as a `GuestContent` would yield an occupied payload with no
`home` — a state the merged server can never produce. The new key sidesteps
that; a one-line `removeItem` for both legacy keys runs once where the cache is
first touched, so an upgraded kiosk does not carry dead records forever. (Not
speculative: every kiosk in the field holds them today. Deletable in a later
release.)

**Cache record and REQ-951.** Unchanged shape — `{ payload, etag, storedAt }`
— now written once instead of twice. The allow-list the spec pins is
`payload` · `etag` · `storedAt` at the record level and
`property` · `room` · `occupancy` · `guestDisplayName` · `settings` ·
`preference` · `menu` · `standby` · `home` inside the payload. A new wire
field fails that test until someone classifies it. The test asserts the
serialised record contains no occurrence of `DeviceTokenStore`'s token and no
manager bearer, and — per the PRD's explicit warning — **filters on nothing
resembling `password`/`code`/`secret`**: under v1.9.0 a WiFi password would be
permitted in the cache and the device token would not, so a name-based rule
would assert close to the opposite of the constraint.

**Untouched, on purpose (REQ-943):** `ConnectionState`, the transitions, the
30 s cadence, the 4 s bound, the 5→30 s backoff, `schedule`/`scheduleRetry`/
`stopTimer`, the 401 wipe, `adopt()`'s "map before you cache" ordering, and the
mapper-rejects-the-cache path.

Expected size: ~136 lines removed against ~20 added ⇒ **~340 lines**, against
REQ-949's ceiling of 452.

---

## 6. The escalating banner — REQ-952

D13 decided the state shape and this design does not reopen it: **a
presentation property of `STALE`, not a fourth `ConnectionState`.**

### 6.1 One derived level, three files

```
core/guest/staleness.service.ts        (new, ~30 lines)
    STALE_ESCALATION_MS = 60 * 60 * 1000
    level: Signal<'NONE' | 'PILL' | 'BANNER'>
    since: Signal<number | null>        // passthrough of staleSince()
```

- `NONE` unless `connection() === 'STALE' && staleSince() !== null`;
- `BANNER` when `clock.now().getTime() - staleSince() >= STALE_ESCALATION_MS`,
  else `PILL`.

A **tri-state, not two booleans**: "the banner is shown and the pill is not"
(REQ-952's acceptance) becomes true by construction instead of by two
components agreeing.

**The clock is `ClockService`, which already exists** (`core/clock/`,
minute-aligned signal, `DestroyRef`-torn). No new timer, no `setInterval`, no
change to `GuestDataService` — which is also why REQ-943 stays honest: the file
that owns the machine gains nothing but the rename table of §5.

`staleSince()` is already the moment of the last **successful contact** on both
paths (`adopt()` sets `lastGoodAt = Date.now()` including on a `304`;
`adoptCache()` seeds it from the cache's `storedAt`), so REQ-952's
"contact, not change" and "cold boot starts already escalated" criteria are
satisfied by the existing field — verified 2026-08-27, do not re-derive.

Home: `core/guest/` — beside `guest-state-routing.service.ts`, which is the
existing precedent for "a decision derived from guest state that is not wire
I/O".

### 6.2 The component

`layout/staleness-banner/staleness-banner.component.{ts,html,css}` (new),
rendered as one more overlay sibling in the two hosts that already render the
pill: `layout/shell/shell.component.html` and
`features/standby/standby.component.html`. `StalenessPillComponent` changes by
exactly one line — its `visible` computed reads
`staleness.level() === 'PILL'`.

Two components rather than one branching component: the pill and the banner
are different visual objects with different CSS, and this way the pill's file
is effectively untouched.

`role="status"`, `aria-live="polite"`, set once — the pill's discipline
verbatim. The banner appears once per stale episode and its text is static, so
the live region cannot chatter on the minute tick.

### 6.3 Placement — the hint escalates *in place*

*(Revised 2026-08-27, after the review measured the first build. The
authoritative spec is `docs/design/screens-inhouse/connection.md` → "The
escalated staleness banner"; this section states what the feature builds and
why.)*

Same anchor as the pill (top-right, directly below the status-bar cluster,
right-aligned to it) — but it grows **sideways into a bar**, not into a panel.
The first build clamped it at `min(22rem, 40vw)` and the approved copy then ran
to **three** body lines; the owner struck the clamp rather than the line
clause, so the width follows the copy: `width: max-content`, `max-width` at the
content area's left edge, **one body line**. Measured, the object is
~540 × 60 at 1024×768 and ~679 × 74 at 1280×832 — about **9 : 1**. "The pill
grown into a panel" no longer describes it.

Surface unchanged (`--color-surface-glass`, `--border-glass`, `--radius-2xl`,
`--blur-glass`, `--shadow-panel`), padding `--space-4` all round. The glyph is
still the connection screen's own **already-registered** `WifiSlash` — no new
icon, so `registered-icons.ts` stays a Part A file — one step smaller at
`--size-icon-md`, and now in **`--color-status-warning`** (§6.7). It stays
decorative: the glyph names *what* is wrong, the colour says *that* it matters,
and the severity order stays honest because the full-screen panel is a
takeover. Two rows: the title (`--text-title` weight) with the pill's own
`Stand HH:MM` right-aligned beside it, then the one body line. The timestamp
rides the title's row because it costs no width there and keeps the bar flat.

- **It is an absolute overlay** (`:host { display: contents }`, the status
  bar's and pill's pattern), so it takes no flow space and **cannot grow the
  document on any route** — the constitution's "nothing scrolls" is satisfied
  by construction, not by a measurement that might drift.
- Bottom-centre was rejected: Home's action-card row lives there (measured
  `y 744` at 832), and the escalation would sit on the screen's primary
  controls. Top-centre was rejected: it collides with the reserved status-bar
  corner and with the headline that identifies the screen.
- **The anchor, restated against the measurement** *(corrected 2026-08-27 —
  the figures below replace x475…998 / x594…1248, which named the gap after
  the **heading block** as though that were Home's flex item; it is only the
  widget column's left edge on a Home with no cards)*. Home's left flex item
  `.home-content__screen` runs x138…**794** / x172…**992** — sized by the card
  row at its bottom, not by the heading — and lays heading and cards out with
  `space-between`, so the band beside the heading is empty by design. The
  empty widget column `.home-content__widgets` is x819…998 / x1024…1248, only
  **179 / 224 px** wide. A one-line bar (540…553 px at 1024, 675…694 at 1280)
  therefore lies mostly over that empty band — 335 px of 540 at 1024, 423 px
  of 679 at 1280 — plus the gutter and the whole widget column. It reaches
  neither `.heading-block` (which ends at x450 / x562) nor the card row
  (424 / 402 px of vertical clearance) nor the sidebar. On standby there is
  neither, and the measured clearance to the centred block is 230 / 224 px.
- **Clearance to Home's hero headline is structural now.** That headline is
  manager-authored with `{stay.displayName}` interpolated and the first build
  cleared it by 4–5 px — a margin that depended on one guest's name.
  `.heading-block` is a fixed `--size-heading` wide, and `.heading-block__headline`
  now carries **`overflow-wrap: anywhere`** so that an unbroken token cannot
  escape it either: no name can push a line past x450 / x562, while the bar
  starts at x459 / x569 — **≥ 9 px / ≥ 7 px for any name**. Measured with a
  37-character unbroken display name: five lines, reaching x450 / x560, 9 px
  clear at both viewports. Without that CSS line the same name renders one
  line to x935 / x1173, straight under the bar — which is a **pre-existing**
  overflow this round closes, and the instrument's `OVERFLOW` check is its
  ratchet.
- Sizes are `rem` and the root is viewport-proportional, but the *copy* is not,
  so a fit at 1280×832 is no longer automatically a fit at 1024×768: both
  viewports are **measured** (§9).
- **Motion** (added 2026-08-27), both behind `prefers-reduced-motion: reduce`:
  a 350 ms `cubic-bezier(0.16, 1, 0.3, 1)` entrance — the design sheet's *Page
  Transition* token — so the pill → bar swap is an event, and a slow 4 s pulse
  **on the glyph only**, never on text that must stay re-readable for hours. A
  marquee was proposed and rejected (WCAG 2.2.2 needs a pause mechanism a kiosk
  cannot offer, and it vanishes under reduced-motion exactly where a fallback
  is needed). The entrance must not be what changes the live region's text:
  the `role="status"` element is created and destroyed by an `@if`, and an
  animation that also re-wrote the copy would announce mid-motion.

### 6.4 Two string variants, both app chrome

The occupied wording directs the guest to their host; a vacant room has no
guest, so the standby variant addresses whoever else sees the screen — the
owner's answer of 2026-08-27, and the reason the occupied string cannot be
reused. The variant is chosen from **occupancy**
(`GuestDataService.occupancy()`), not from which host renders the component:
occupancy is the fact that decides which screen is up in the first place.

New keys in both catalogues (`core/i18n/catalog/de.ts` is the key set's source
of truth, so `en.ts` fails to compile until it is translated — REQ-111):

| Key | de | en |
|---|---|---|
| `stale.banner.title` | Diese Anzeige ist nicht aktuell | This display is out of date |
| `stale.banner.body` | Seit über einer Stunde besteht keine Verbindung zum Server. Bitte den Gastgeber informieren. | There has been no connection to the server for over an hour. Please let your host know. |
| `stale.banner.bodyStandby` | Seit über einer Stunde besteht keine Verbindung zum Server. Bitte die Verwaltung informieren. | There has been no connection to the server for over an hour. Please inform the property manager. |

`stale.asOf` is reused verbatim for the timestamp line. German copy is
imperative-neutral (no *ihr/euch*), matching the existing connection strings.

### 6.5 The design spec

**Written in this phase, not left to implementation** — the Part A precedent
(`menu-overflow.md` was written by `planner` at `02_design`).
`docs/design/screens-inhouse/connection.md` now carries **"The escalated
staleness banner"** beside "The staleness pill": trigger, layout, tokens,
`aria-live` discipline, both string variants and the reason the occupied
wording cannot be reused on standby. The pill's "Appears in `STALE` only" line
is amended to say the escalation replaces it, and the open-questions section
records what D13 deliberately left open (whether the full-screen
`DISCONNECTED` panel should also point at the host after an hour). In-repo
design under the convention approved 2026-08-20
(`guest-frontend-integration` D1) — **no design-tool request**, and the file is
not Part A's (`menu-overflow.md` is). T-14/T-15 implement that file; T-16 only
re-opens it if measurement forces a geometry change.

### 6.6 Constitution compliance note

v1.9.0's clause says the banner names the situation "and directing the guest to
their host". The standby variant addresses staff because a vacant room has no
guest; the clause's stated rationale — operational alerting on a box with no
remote access — is exactly what the vacant case serves. Nothing is contradicted
and **no amendment is proposed**. Recorded here so the analyze gate does not
have to rediscover it.

### 6.7 The warning colour — a global role, not a banner colour

*(Added 2026-08-27 with the bar redesign; owner-decided, with its derivation.)*

`docs/design/design-sheet.md` → *Status* already carries a v1 palette marked
*"noch nicht verwendet"*, and `Offline #A79DBA` was realised from it in 2026-08-20
as `--status-offline-raw`. The warning colour **continues that decision**
instead of placing a second one beside it: it is derived from the same
palette's `Warning #F8B84E` (hue 37°).

| Theme | Value | Measured contrast on glass |
|---|---|---|
| light | `#895806` | 4.64 over `--mesh-6`, 5.17 over `--mesh-8` |
| dark | `#f9b84e` | 5.63 over `--night-6`, 4.86 over `--night-8` |

Three guardrails, all of which live in the token comment as well as here:

- **Named after the status family, never after the banner**:
  `--status-warning-raw` / `--status-warning-dark-raw` (primitives) +
  `--color-status-warning` (semantic role) + a `[data-theme='dark']` override
  of the role only — the `--status-offline` shape, and the theme axis still
  never redefines a primitive.
- **It is a FOREGROUND role** — text, glyph, border. **Never a surface fill**:
  `#895806` as a background would fail any dark text placed on it. A tinted
  warning surface, if one is ever needed, is a second token.
- **Warning is not error.** `Danger #F26666` sits beside it in the v1 palette
  for destructive actions; nobody may repurpose warning for a delete dialog.

Both values clear 4.5 : 1 **deliberately**, even though a decorative glyph does
not need it — that shortcut is exactly what left `--color-status-offline` with
its permanent *"never a text colour"* caveat.

---

## 7. Data model

One migration, **`V11__home_is_mandatory.sql`** (Part A holds `V10`; the
deferred `instance-identity-and-certs` plan renumbers when it is picked up —
`docs/handoff.md` deferred question 4):

```sql
UPDATE menu_item_config SET enabled = NULL, sort_index = NULL
 WHERE menu_item_key = 'home';
```

No DDL. `enabled` and `sort_index` are already nullable (`V2`, "each field
nullable: absent = inherit per-field"), and `home`'s catalogue
`default_sort_index` is already `0`, so this restores inheritance rather than
inventing a value. Any icon or label override on `home` survives untouched —
only enablement and ordering are taken away, which is exactly what §4 makes
unmanageable.

No other schema changes: **the version stamp adds no table** (§2.2).

---

## 8. API contract diff

| Change | Detail |
|---|---|
| **removed** | `GET /api/guest/session`, `GET /api/guest/screens/home` (with their `404 screen-not-found` and `409 room-vacant`) |
| **added** | `GET /api/guest/content` — `getGuestContent`, `bearerAuth`, header param `If-None-Match`, responses `200` (+`ETag`), `304`, `401`. No room/stay/device/property parameter — REQ-411 stays checkable on the contract itself |
| **renamed** | `GuestSession` → `GuestContent`; `GuestHomeScreen` → `GuestHome` |
| **added** | `GuestContent.home` — optional, `$ref: GuestHome`, described as "occupied only" |
| **edited** | the two `manager-menu` `400` descriptions gain the mandatory-item case |
| **unchanged** | `GuestProperty`, `GuestRoom`, `GuestSettings`, `GuestPreference`, `GuestMenu` (**including Part A's `maxItems: 50`**), `GuestMenuItem`, `GuestSettingsEntry`, `GuestStandby`, `GuestActionCard`, `GuestPreferencesUpdate`; `assets/{assetId}`; `preferences` |

`packages/api-client/` is regenerated, never hand-edited; contract, backend and
client land in one commit (REQ-941).

---

## 9. Test strategy — the seams that matter

**REQ-946 — "a `304` without serialising, asserted at the seam, not by
timing."** The seam is proven by making the payload *unresolvable* while the
stamp is unchanged:

1. poll → `200` + ETag;
2. corrupt the property row's `timezone` with native SQL (a test may reach past
   the layering; native SQL writes **no audit row**, so the stamp is
   deliberately unchanged);
3. poll with `If-None-Match` → **`304`**. It cannot have resolved the payload:
   `resolveLocalized` calls `ZoneId.of(property.getTimezone())` on every path,
   including standby texts;
4. poll **without** `If-None-Match` → `5xx`, proving step 3's payload was
   genuinely unresolvable;
5. restore the timezone.

No new dependency, no config flag, no timing. *(Optional extra, not required:
`%test.quarkus.hibernate-orm.statistics=true` and a prepared-statement count.)*

**REQ-947 — the two failure modes, each as its own case.**

- *Check-in between two polls, with no write anywhere:* insert a stay for the
  device's room with native SQL and an already-elapsed `check_in` — no audit
  row, no restart, nothing but the clock — then poll with the vacant room's
  ETag and require a **`200`** carrying the new stay's content. A stamp
  tracking only "content writes" fails here, which is the whole point.
- *Two rooms:* two devices in two rooms, identical content, ETags **differ**.

**REQ-948:** `home` disabled at property scope, then at room scope → both
rejected `400 menu-item-mandatory`; a legacy row written by native SQL is
inert and `home` still resolves at `sortIndex 0`; the merged content answers
`200` with `home` present throughout.

**REQ-945/944:** occupied → vacant transition; the payload has no
`guestDisplayName`, no `preference`, no `home`, and the property half is intact
— asserted on the wire (backend) *and* on the `localStorage` record (frontend).

**REQ-950:** the 13 cases of `guest-data.service.spec.ts` rewritten around one
resource. The two `409`-specific cases become "the server answered vacant"
cases — the transition they assert (previous guest's content off the screen
immediately, standby now) is unchanged and must stay asserted. Every other case
keeps its observable expectations verbatim.

**REQ-952:** four unit cases against `StalenessService` with a DI-doubled
`ClockService` and `GuestDataService` (writable signals — `frontend/CLAUDE.md`):
59 min → `PILL`, 60 min → `BANNER`, a two-hour run of `304`s → `NONE`
throughout, cold boot from a `storedAt` older than an hour → `BANNER`
immediately. Plus one component case per string variant, and
`ConnectionState`'s three members asserted unchanged.

**Measurement (REQ-952 no-scroll).** The banner is a kiosk-*state* screen, not
a route, so `tools/measure.mjs`'s route grid cannot reach it: the instrument is
**`tools/measure-states.mjs`**, which already drives the STALE pill by loading
online once and reloading with `/api` blocked. Part B adds a BANNER cell —
same path, with the cached record's `storedAt` rewritten to 61 minutes ago
before the reload — over both viewports, both themes, both languages, and both
the shell and the standby host, plus (added in the 2026-08-27 fix round) a
third variant that renames the active stay to a **37-character unbroken
display name** and back, because Home's headline is the one thing on this
screen whose width a guest supplies. *(The PRD's acceptance line named
`tools/measure.mjs` when it was written and was corrected to this instrument at
`04_analyze` — finding F-2. The assertion is unchanged: `scrollWidth <=
clientWidth` and `scrollHeight <= clientHeight`. Note also that Part A's T-18
extends the **other** file, `measure.mjs`, so the two do not collide.)*

**Untouched and must stay green:** the five architecture guards in
`de.gxp.architecture` (this design deliberately adds **no** sixth — the
constitution's sentence counts them, and a sixth would need an amendment) and
`AuditCoverageTest`'s assertions, whose only edit is swapping two entries in
`NOT_A_MANAGER_MUTATION` for one.

---

## 10. Documentation

`docs/system-context.md` needs three rows updated (`de.gxp.guest`,
`GuestDataService`, `StalenessPillComponent` + one new banner row) and the
`screens/home` mentions at lines 132 and 606 corrected. **This design does not
write them**: Part A's `T-21` edits the same file to fold in its own planned
block, and Part B is planned while that is in flight. The edit is a TODO task
(T-18), to be done after Part A's commit.

---

## 11. Risks & trade-offs

| Risk | Position |
|---|---|
| The stamp is the sharp edge | §2.3's bucket turns every under-invalidation into ≤ 5 minutes of latency instead of an unbounded stale `304`. The two REQ-947 tests cover the two named failure modes exactly |
| One ETag for all content | Editing one restaurant tip retransmits ~10 kB on a LAN. Accepted in the PRD, unchanged here |
| Thrown success `Response` for JSON | Established for binary (`guestBinary`), new for JSON in this repo. §3.2 marks it verify-at-implementation and §3.4 records the fallback that changes no requirement |
| The banner overlays whatever is under it | Deliberate: an overlay cannot grow the document, and the top-right region is free on both hosts. Measured, not assumed |
| Renames ripple through specs | `payload()`, `preferenceOverride()`, `GuestContent`, `getGuestContent`, `gxp.cache.content` touch ~8 spec files. Mechanical, and the alternative is a contract whose names describe the shape it had before this Part |
| A bucket boundary re-adopts identical content | Every ~5 minutes a `200` carries a payload equal to the held one, so `adopt()` re-maps and re-sets `_config` with a fresh object identity: computeds recompute, bindings re-evaluate to equal values, `@for` tracks are stable, and `GuestBootstrapService`'s existing effect re-runs its idempotent `applyPropertyDefaults` + `hydrate()`. Same code path as any real content change, at a fixed low frequency instead of a rare one. Checked against REQ-943: no state, transition, timer or observable behaviour changes — only how often an already-exercised path runs. Deliberately **not** answered with a deep-equality guard in `adopt()`, which would add code to the file REQ-949 shrinks |
| The upgrade window | A kiosk booting in the seconds between the new bundle and a ready backend finds no cache under the new key and shows the connection screen until the first answer. It heals unaided; the alternative (reading the old record into the new shape) risks rendering an occupied payload with no `home` |

---

## 12. Open decisions

**None.** No `[NEEDS CLARIFICATION]` is left in this design: D7 and D13 are
settled, REQ-952's standby question was answered by the owner on 2026-08-27,
and the endpoint name, the stamp scheme, the banner's state shape and its
placement are decisions this phase owns and has taken above.

One item is **verify-at-implementation**, and it changes no requirement and
opens no gate: §3.2 (thrown JSON `Response` writes its entity and ETag) with
the §3.4 fallback. *(The second one — the exact `min(22rem, 40vw)` panel
geometry — was resolved: measurement showed the clamp and the line-count clause
could not both hold, and the owner struck the clamp on 2026-08-27. §6.3 now
states the bar's measured geometry.)*
