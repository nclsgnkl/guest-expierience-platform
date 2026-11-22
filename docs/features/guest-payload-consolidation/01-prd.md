# PRD: Guest Payload Consolidation
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Draft — **decision-complete, not yet designed.** D7 and D13 are
settled; **no approval gate is open** and **no clarification marker remains** —
REQ-952's standby-screen scope question was answered by the owner on
2026-08-27 (yes, the banner shows there too). Plan it after
[`sidebar-catalogue-overflow`](../sidebar-catalogue-overflow/01-prd.md)
(Part A) lands, or in parallel — the two share no file except the contract.

> **Part B of three.** The 2026-08-27 owner decision **D1** split
> `modular-menu-catalogue` into three sequenced features. This is the second:
> small, mechanical, independently testable, and it closes a **live defect**.
> Siblings: [`sidebar-catalogue-overflow`](../sidebar-catalogue-overflow/01-prd.md)
> (Part A, REQ-901…935) and [`module-content`](../module-content/01-prd.md)
> (Part C, REQ-961…979, **not ready to plan**). Retired parent:
> [`modular-menu-catalogue/README.md`](../modular-menu-catalogue/README.md).
>
> **Requirement IDs are stable.** REQ-941…REQ-950 mean here exactly what they
> meant in the parent PRD; that block moved unchanged. **Two changes since,
> both 2026-08-27:** **REQ-951 was rewritten** against constitution **v1.9.0**
> — the amendment removed the absolute prohibition its old text restated, so
> the row now pins the line that was always the load-bearing one (no
> credential, nothing that reaches past the device) — and **REQ-952 is new**
> (owner decision **D13**, escalating staleness). REQ-943 gained a scope note
> and no new meaning.
>
> **Governing:** `docs/constitution.md` **v1.9.0**, `frontend/CLAUDE.md`,
> `backend/CLAUDE.md`.

---

## Problem statement

### P4 — `GuestDataService` hard-codes two resources, and one of them can 404

`frontend/src/app/core/guest/guest-data.service.ts` (452 lines) carries
`SESSION_CACHE_KEY` + `HOME_CACHE_KEY` (52–53), `sessionEtag` + `homeEtag`
(88–89), `heldHome` (90), `fetchSession()` + `fetchHome()` (330), the two-call
sequence in both `bootstrap()` (122) and `refresh()` (196), the `409
room-vacant` race between the two calls (126, 200) and `asVacant()` (411). A
third resource in the same style doubles that.

**A live defect sits in the same place.** `GuestControl.homeScreen()` (line 164)
answers a contract-declared **`404 screen-not-found`** when `home` resolves
disabled — `home` is an ordinary catalogue row and a manager may disable it.
`fetchHome()` handles `304`, `bootstrap()`/`refresh()` handle `401` and `409`;
a `404` falls through to the generic catch and drops the kiosk to
`STALE`/`DISCONNECTED`. **A supported configuration takes the kiosk offline.**

### P5 — Corrected: the check-out-time example does not motivate aggregation

The 2026-08-26 draft argued consolidation from a cross-module read: the Home
action card's check-out subtitle *"belongs to the Info module's data"*, leaving
*"only two outcomes — Home must additionally fetch Info … or the server
duplicates the value"*. **Both halves are false**, and the argument is withdrawn.

- The check-out time is a **stay** fact (`stay.check_out`), and
  `{stay.checkOut|time}` / `{stay.checkOut|date}` are already shipped members of
  `TemplateControl.ALL_PLACEHOLDERS`
  (`backend/app/src/main/java/de/gxp/text/control/TemplateControl.java:40`),
  resolved server-side per language from one source. The `%dev` seed simply
  hardcodes the literal *"Bis 11:00 Uhr am Abreisetag"*
  (`DevDataSeeder.java:78`) instead of using the placeholder that exists.
- Even for a genuinely module-authored value, the dichotomy is false: **both
  endpoints are served by the same `GuestControl` on the same box**, so the
  server can read the owning module's row and render it into the Home payload —
  one source of truth, one round trip, no aggregation of the whole catalogue.

Consolidation is still worth doing. It is worth doing for **P4** — the race, the
dead code and the unhandled 404.

---

## Goals / Non-goals

**Goals**

1. **One content resource, and a `GuestDataService` that is smaller after this
   than before it.** Folding the second resource in removes `HOME_CACHE_KEY`,
   `homeEtag`, `heldHome`, `fetchHome()`, the two-call sequence in both entry
   points and — with them — the `409` race and `asVacant()`, and it turns the
   unhandled `404` into a defined state.
2. **A cheap `304`.** The ETag stops being a hash of a freshly serialised
   payload on every poll.

**Non-goals**

- **Merging anything but those two endpoints.** See D7 — this is explicit.
- **Module content.** Part C.
- **Changing the sidebar, the catalogue or the routes.** Part A.
- **Changing pairing, occupancy, preferences, assets or the connection state
  machine's observable behaviour.**
- **A second cache tier or any storage other than the existing `localStorage`
  last-known-good record.**

---

## Assumptions

- **A1.** Module content is **text and asset references**; binary content stays
  behind `GET /api/guest/assets/{id}`. *(Cadence verified:
  `POLL_INTERVAL_MS = 30_000`, `FETCH_TIMEOUT_MS = 4_000`,
  `RETRY_BACKOFF_MS = [5, 10, 20, 30] s`.)*
- **A2.** Modules are **property-scoped content**, not stay-scoped; only the
  greeting, the guest display name and the stay preference are stay-derived.
- **A4.** The kiosk continues to hold and render whatever it last had when the
  backend is unreachable — **including after REQ-952's escalation**. The v1.9.0
  cache amendment and D13's rejection of a hard cutoff point the same way: a
  guest whose backend is unreachable is precisely the guest who still needs the
  door code and the WiFi password.

---

## User stories

- As a **guest**, I want the whole app to keep working from the last known
  content when the property's server is unreachable, so a power cut does not
  leave me with a blank screen.
- As a **property manager**, I want disabling a section — any section — never to
  take a kiosk offline.
- As the **operator**, I want one resource to reason about, so the next feature
  does not double the bookkeeping again.

---

## Requirements

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-941 | The contract change collapsing `GET /api/guest/session` and `GET /api/guest/screens/home` into one resource regenerates the JAX-RS interfaces and `packages/api-client/` **byte-identically** on a second build; contract, backend and client land in one commit. | Must |
| REQ-942 | `GuestDataService` holds **one** content resource: one ETag, one cache key, one held copy, one fetch. `HOME_CACHE_KEY`, `homeEtag`, `heldHome`, `fetchHome()` and `asVacant()` are removed, together with the `409 room-vacant` race they exist to handle. | Must |
| REQ-943 | The `ONLINE` / `STALE` / `DISCONNECTED` machine, the 30 s cadence, the 4 s fetch bound, the 5→30 s backoff, the last-known-good cache and the 401 wipe are **behaviourally unchanged**. *(Scope note 2026-08-27, narrowing nothing: "the machine" is its states, its transitions, its timers and the cache/401 rules. REQ-952 adds no state and no transition — the banner is derived from `staleSince()` and the clock — so this row holds as written. Specified as a **fourth state** it would have contradicted this row head-on; that is why D13 decides the state shape rather than leaving it to `02_design`.)* | Must |
| REQ-944 | A vacant room serves no stay-derived field, unchanged from today (`guestDisplayName`, `preference`, the resolved greeting). Data-lifetime separation stays enforced by the server **omitting fields**, not by two endpoints. | Must |
| REQ-945 | The **property-scoped half survives a vacancy episode**: on vacancy the server sends property content without the stay fields, the kiosk caches exactly that. Today the whole home cache is deleted. This is a net improvement and must be asserted, not assumed. | Must |
| REQ-946 | The session `ETag` is derived from a **monotonic content version stamp** bumped on any content write, not by serialising and hashing the payload on every poll — `GuestEtagFilter:60` currently does `sha256Hex(objectMapper.writeValueAsBytes(entity))` on **every** request, so a `304` today costs a full resolve and serialise. | Must |
| REQ-947 | The version stamp is **correct across everything that changes what a given kiosk sees** — content writes, occupancy/stay transitions, preference writes, and the room scope itself (two rooms must not share one stamp). A stamp that only tracks "content writes" would serve a stale `304` across a check-in. This is the failure mode that makes REQ-946 dangerous if under-specified. | Must |
| REQ-948 | A **resolved-disabled `home` is a defined kiosk state**. `GuestControl.homeScreen():164` answers the contract-declared `404 screen-not-found`; today `bootstrap()`/`refresh()` handle only `401` and `409`, so that `404` drops a working kiosk to `STALE`/`DISCONNECTED`. Consolidation removes the second call and therefore the fall-through; the requirement is that the resulting state is deliberate and tested. | Must |
| REQ-949 | `guest-data.service.ts` is **structurally simpler** after this Part, and `grep` finds no `HOME_CACHE_KEY`, `homeEtag`, `heldHome`, `fetchHome` or `asVacant`. Simpler means, concretely and checkably: **one** fetch, **one** ETag field, **one** cache key, **one** held copy; the two-call sequence gone from both entry points; and no `409`-handling branch, because a vacant room is now expressed as an absent `home` field rather than an error. *(Amended 2026-08-27, owner. The original wording demanded **fewer than 452 lines**. Struck as a bad metric: a line count is always reachable — by joining statements, deleting an explanatory comment, or declining a well-named helper because its signature costs a line — and every one of those makes the number better and the code worse. The owner's words: "Ob das am Ende wirklich weniger Zeilen sind ist mir eigentlich egal. Hauptsache es ist sauberer Code." The line count was a proxy for the case reduction; the case reduction is now stated directly, so nothing is lost and the perverse incentive goes. Fewer lines is expected to fall out anyway — it is simply no longer a pass criterion.)* | Must |
| REQ-950 | The **13** existing cases in `guest-data.service.spec.ts` are rewritten for one resource and assert the **same observable behaviour** for every transition (200 / 304 / error / 401 / vacancy / cache-rejected). | Must |
| REQ-951 | The merged payload written to the last-known-good cache **carries nothing that extends reach beyond the device**: no device token, no manager bearer or session token, no key material, no credential that could address the backend, and nothing scoped to another room or another stay. Every guest-facing value it does carry stays inside the amended boundary — one the server resolved **for this room's current guest**, because the manager chose to display it. *(Rewritten 2026-08-27 against `docs/constitution.md` **v1.9.0**. The old text — "holds no guest-sensitive value" — restated an absolute prohibition the amendment removed; the credential line replaces it and is the half that was always load-bearing. Factually today's merged payload still carries no WiFi password or door code — the contract defines none — but that is now an observation, not the requirement.)* | Must |
| REQ-952 | **Staleness escalates on time, and content never stops rendering.** In `STALE`, once **60 minutes** have elapsed since the last **successful backend contact**, the discreet staleness pill is replaced by a **prominent banner** naming the situation and directing the guest to contact their host. Content keeps rendering before and after the escalation — a hard cutoff was explicitly rejected. The escalation is a **presentation property derived from the already-held `staleSince`, not a fourth `ConnectionState`**: the union stays `'ONLINE' \| 'STALE' \| 'DISCONNECTED'` and no transition, timer, cadence or backoff changes (REQ-943). The rationale is **operational visibility, not security**: on a box with no remote access a guest complaint is the only alerting channel that exists. *(Owner decision 2026-08-27 — **D13**, option (b). REQ-950's thirteen rewritten cases are unaffected; none advances the clock by an hour, and REQ-952 brings its own.)* **Answered by the owner 2026-08-27: yes — the banner shows on the standby screen of a vacant room too.** A vacant room has no guest, so the escalation there is aimed at whoever else sees the screen (cleaning staff, the manager), which serves the same operational purpose: a dead backend must not stay invisible just because the room happens to be empty. *Note for `02_design`: the occupied-room wording directs the guest to their host and cannot be reused verbatim on standby — the standby variant addresses staff, not a guest. Both strings resolve through `TranslationService` like all app chrome.* | Must |

---

## Acceptance criteria (testable)

- **REQ-942/949:** Given this Part is complete, when `guest-data.service.ts` is
  inspected, then `grep` finds no `HOME_CACHE_KEY`, `homeEtag`, `heldHome`,
  `fetchHome` or `asVacant`; there is exactly one fetch, one ETag field, one
  cache key and one held copy; neither entry point runs a two-call sequence;
  and no branch handles a `409`. **Line count is not a criterion** (amended
  2026-08-27, owner) — judge the structure, not the size.
- **REQ-945:** Given an occupied room with cached content, when the stay ends,
  then the kiosk still holds and can render the property-scoped content and
  holds nothing stay-derived in RAM or in `localStorage`.
- **REQ-946:** Given no content write between two polls, when the kiosk sends
  `If-None-Match`, then the server answers `304` **without** serialising the
  payload — asserted at the seam, not by timing.
- **REQ-947:** Given a `304`-eligible ETag and a check-in occurring between two
  polls, when the kiosk polls, then it receives `200` with the new stay's
  content — never a `304`. Given two rooms, then their stamps are independent.
- **REQ-948** *(restated 2026-08-27 against the owner's fix below — the
  original "given `home` resolves disabled" is **unconstructible** once `home`
  is mandatory, which is the point of the fix: the error case is removed, not
  handled):* Given a manager attempts to disable or re-sort `home` at either
  scope, then the write is refused with a named `400`; given a row from before
  this Part already carries `enabled = false` or a `sort_index` for `home`,
  then it is **inert** — `home` still resolves enabled at index 0, the kiosk
  boots normally and does **not** report `STALE`/`DISCONNECTED` while the
  backend is reachable.
- **REQ-950:** Given the rewritten spec file, when it runs, then every state
  transition asserts the same observable behaviour as the 13 cases do today.
- **REQ-951:** Given a bootstrapped occupied room, when the `localStorage`
  record is read back and serialised, then it contains no occurrence of the
  device token held by `DeviceTokenStore`, no manager bearer token and no key
  material, and its top-level keys match an **explicit allow-list** pinned in
  the spec — a new wire field fails the test until someone classifies it. The
  test must **not** filter on names like `password`, `code` or `secret`: under
  v1.9.0 a WiFi password is permitted in the cache and the device token is not,
  so a name-based rule would assert close to the opposite of the constraint.
- **REQ-952 (threshold):** Given `STALE` with the last successful contact 59
  minutes ago, when the kiosk renders, then the discreet pill is shown and no
  banner; at 60 minutes the banner is shown, the pill is not, and the current
  route still renders its content.
- **REQ-952 (contact, not change):** Given a backend answering `304` to every
  poll for two hours, when the kiosk renders, then it is `ONLINE` throughout and
  no banner has appeared — the clock measures time since a **successful
  response**, not since content last changed.
- **REQ-952 (cold boot):** Given a boot from a cache whose `storedAt` is already
  older than 60 minutes and a backend that does not answer, when the cache is
  adopted, then content renders **with** the banner immediately — an
  implementation that starts its hour at boot serves a second silent hour and
  fails this case.
- **REQ-952 (machine untouched):** Given the implementation, when
  `ConnectionState` is inspected, then it still has exactly three members, and
  REQ-943/REQ-950's transition expectations are unchanged.
- **REQ-952 (no-scroll):** Given the banner visible, when it is measured at
  1024×768 and 1280×832, in both themes and every offered language, then no
  route produces a page-level scrollbar (`docs/constitution.md` → "Nothing
  scrolls"). *(Instrument corrected 2026-08-27: the banner is a kiosk-**state**
  screen, not a route, so `tools/measure.mjs`'s route grid cannot reach it —
  `tools/measure-states.mjs` can, and already drives the STALE pill the same
  way. The assertion is unchanged: `scrollWidth <= clientWidth` and
  `scrollHeight <= clientHeight`.)*

---

## Technical constraints

- **`docs/constitution.md` v1.9.0 governs.** Relevant here: the append-only
  audit obligation (v1.8.1) — any manager-visible mutation this Part introduces
  writes a row or is pinned in `AuditCoverageTest`; the **amended**
  last-known-good cache constraint (2026-08-27, three bullets under Constraints
  — the cache may hold guest-facing content the manager deliberately chose to
  display, must never hold anything that extends reach beyond the device, and
  physical loss of a kiosk is disclosed to the operator rather than defended
  against), which REQ-951 pins; and **"nothing scrolls"** (v1.8.0 scope), which
  binds REQ-952's banner — it may not grow the document on any route.
- Contract-first: `api/openapi.yaml` is the source of truth; the generated
  client is committed and never hand-edited.
- Backend: Quarkus, BCE sub-packages, Panache repositories. Frontend: Angular
  22, standalone, zoneless, signals; Vitest + jsdom.
- **Branch:** `refactor/bce-subpackages-and-boundary-security`, owner instruction.

---

## Settled decisions (owner, 2026-08-27)

### D7 — One content resource — **APPROVED, with an explicit scope**

**Exactly two endpoints merge: `GET /api/guest/session` and
`GET /api/guest/screens/home`.** `GET /api/guest/assets/{assetId}` and the
preferences endpoints are **untouched and remain separate** — the owner asked
directly whether two requests remain, and the answer is: content is one request;
an asset is still its own authenticated fetch (it is binary, streamed, strongly
ETagged and fetched at most once per asset), and a preference write is still its
own `PUT`. "One content resource" never meant "one endpoint".

The case, on corrected grounds (the cross-module argument of P5 is withdrawn):

- it removes the window between two calls in which a stay can end — the `409
  room-vacant` race, `asVacant()`, the second ETag, the second cache key and the
  second held copy all go with it (REQ-942/949);
- it removes the fall-through that turns a supported configuration (`home`
  disabled) into an offline kiosk (REQ-948, a live defect);
- property content survives a vacancy episode, where today the whole home cache
  is deleted (REQ-945);
- data-lifetime separation is unaffected: it is enforced by the server
  **omitting** stay-derived fields for a vacant room, not by two endpoints —
  `GuestSession` already carries `guestDisplayName` and `preference`, so the
  current split is *session vs. home screen*, not *property vs. stay*.

*Independent of this: whether module content should additionally be withheld
while a room is vacant is a separate choice for Part C, not a consequence of D7.*

### REQ-948's fix — **`home` becomes mandatory** (owner, 2026-08-27)

Decided **after** this PRD was written and recorded in `docs/handoff.md`;
written down here so the PRD stays the source of truth. `home` is **not
removable by a manager** and its sort index is **fixed at 0** — like
`einstellungen`, except that `home` still comes from the backend.

**This removes the `404` branch rather than handling it**, which both the lead
and the owner preferred: an unreachable error case beats a handled one. The
requirement REQ-948 states is unchanged — the resulting state must be
deliberate and tested — and the deliberate state is now "`home` is always in
the resolved menu, so the merged payload always carries `home` for an occupied
room".

Checked at design time: **Part A has not taken any of this.**
`V10__catalogue_expansion.sql` only inserts eight disposable fixture rows and
their property-scope opt-outs; it touches neither `home` nor `MenuControl`.
REQ-948 is Part B's in full, and it needs a small `V11` to neutralise rows that
already exist in the field.

### D13 — Escalating staleness after one hour — **APPROVED (option b); the hard cutoff rejected**

The owner chose **(b) escalate the hint**, at a **one-hour** threshold, over
**(a) a hard cutoff** that stops rendering.

**The reason is operational visibility, not security** — recorded that way
deliberately, because the security framing is the one the owner rejected. In his
words: *"Es ist uns dann möglich einen Error Screen zu zeigen, was bewirkt, dass
sich ein Gast beim Manager meldet. Ansonsten läuft der Screen mit alten Infos
vielleicht tagelang ohne bemerkt zu werden."* On a box with no remote access,
**a guest complaint is the only alerting channel that exists**. Nobody watches
these kiosks; the failure being fixed is a screen that looks perfectly normal
while serving day-old content.

The cutoff was rejected on the same ground as the v1.9.0 cache amendment: a
guest whose backend is unreachable is exactly the guest who still needs the door
code and the WiFi password. Blanking the screen takes those away at the moment
they matter most, to buy an accuracy guarantee nobody asked for.

**The state shape is decided here, not at design time**, because getting it
wrong silently contradicts REQ-943: **the escalation is a presentation property
of `STALE`, not a fourth `ConnectionState`.** The union stays
`'ONLINE' | 'STALE' | 'DISCONNECTED'`; the banner is derived from `staleSince()`
and the current time, wherever `02_design` puts that derivation. A fourth state
would add transitions to a machine REQ-943 freezes and would force every
existing `connection() === 'STALE'` consumer to be re-audited for a state it has
never seen.

**Scope: `STALE` only.** `DISCONNECTED` already renders the full-screen
connection panel (`docs/design/screens-inhouse/connection.md`), which names the
situation as prominently as anything can — the invisibility the owner described
is specific to a screen that *looks normal*. Whether that panel's copy should
also point at the host after an hour is a design-time question, not a
requirement here.

**Two implementation facts, verified against the code on 2026-08-27 — and one
correction to the briefing that carried them:**

- **`storedAt` is the right basis, but it is not dead.** A note reaching this
  session said `guest-data.service.ts` writes `storedAt` and *"nothing ever
  reads it"*. That is wrong: `adoptCache()` reads it back at `:287–288` into
  `lastGoodAt` and `_staleSince`, and `StalenessPillComponent` renders it as
  *"Stand HH:MM"* (`connection.md` → "The staleness pill"). The **true and
  useful** half of the claim is that **nothing compares it against the current
  time** — it is a display timestamp today, and no elapsed-time check exists.
  No new field is needed either way: `_staleSince` is already the moment of the
  last successful contact on **both** paths (`adopt()` sets
  `lastGoodAt = Date.now()`, including on a `304`; `adoptCache()` sets it from
  `storedAt`).
- **Nothing gives up, sleeps or resets before an hour.** `STALE` re-arms
  `schedule(POLL_INTERVAL_MS)` at the end of `refresh()` after every failure and
  never touches `retryStep` — **exactly 120 attempts an hour, indefinitely**.
  `DISCONNECTED` uses `scheduleRetry()`, whose
  `Math.min(retryStep, RETRY_BACKOFF_MS.length - 1)` pins the ceiling at 30 s
  after 5 + 10 + 20 — **~122 attempts an hour, also indefinitely**. The only
  things that stop the timer are the 401 wipe (which routes to pairing, not a
  staleness case) and `DestroyRef` teardown; `retryStep`, `lastGoodAt` and
  `_staleSince` reset only on a successful `adopt()`. The one-hour threshold is
  therefore reached by elapsed time alone, and no retry policy interferes with
  it.

---

## Risks (recorded, not decisions)

- **The version stamp is the sharp edge of this Part.** REQ-946 is a performance
  win; REQ-947 is what makes it safe. A stamp that tracks only "content writes"
  serves a stale `304` across a check-in, and a stamp that is not room-scoped
  serves room 102's ETag to room 101. This is the one place where getting it
  wrong is a correctness bug rather than a tidiness regression.
- **Payload/ETag granularity.** One ETag for all content means editing one
  restaurant tip retransmits the whole payload. At ~10 kB gzip on a LAN this is
  irrelevant — recorded as a trade-off rather than left as a discovery.
- **A cheaper minimal position exists.** If the owner would rather leave the
  transport alone and fix only the `404` defect, this Part shrinks to REQ-948
  alone. That is coherent; it was not chosen.
- **The escalation banner has no design spec yet.**
  `docs/design/screens-inhouse/connection.md` specifies the discreet pill and
  states it appears *"in `STALE` only"*; it says nothing about a prominent
  variant. `02_design` extends that file or adds a sibling under the in-repo
  design convention approved 2026-08-20 (`guest-frontend-integration` D1) — **no
  design-tool request is needed or permitted.** Two constraints it inherits: the
  banner may not grow the document on any route, and the pill's
  `role="status"` / `aria-live="polite"` discipline (set once, must not chatter)
  applies to whatever replaces it.
- **The escalation clock must measure contact, not change.** A `304` runs
  through `adopt()` with `changed = false` and still sets `lastGoodAt` and
  `ONLINE` — correct, and it must stay so. An implementation that timed "since
  content last changed" would raise the banner on every property that simply
  does not edit its content, which is most of them.
- **A cold boot can start already escalated**, and that is the correct
  behaviour: `adoptCache()` seeds `_staleSince` from a `storedAt` that may be
  hours old.
- **REQ-952 does not re-complicate what REQ-949 simplifies.** REQ-942 removes
  `fetchHome` with both overloads, `asVacant` with its comment, the two-call
  sequence in both entry points and the second cache key / ETag / held copy;
  the escalation adds a threshold constant plus one derived value, and lives in
  its own `StalenessService` rather than in `guest-data.service.ts`. *(This note
  used to argue a line budget. That criterion was struck 2026-08-27 — see
  REQ-949 — so the point now is the structural one it was standing in for.)*

---

## Blocking map

| Blocks / blocked by | Detail |
|---|---|
| **Blocked by** | Nothing. |
| **Blocks** | Part C (`module-content`) wants one payload to hang modules off — but that is a convenience, not a hard dependency. |
| **Shares with Part A** | **Corrected 2026-08-27 at `02_design`:** more than the contract, and **Part A lands first**. Part A amends `GuestMenu.items`; this Part restructures the guest operations. Since this row was first written Part A has also touched `frontend/src/app/core/guest/guest-content.mapper.ts` (+ spec), `GuestHomeScreenTest.java`, `AuditCoverageTest.java` and `MigrationTest.java`, and still has `shell.component.html` open (its own measurement task extends `measure.mjs`, while this Part extends `measure-states.mjs` — different files) — all of which this Part must change too. The full table, with the resolution for each, is [`02-design.md` §0](02-design.md). No hunk genuinely conflicts; the ordering assumption is that Part B rebases onto Part A's commit. |
