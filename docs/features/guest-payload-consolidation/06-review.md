# Review: Guest Payload Consolidation — Part B, whole feature (T-01 … T-19)

**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)

**Result:** **NEEDS CHANGES ❌** — 🔴 0 · 🟠 1 · 🟡 6 · 🟢 7.

**Nothing in the implementation is defective.** Every requirement REQ-941…REQ-952
is met in code, and I re-ran or re-measured every number `dev` reported rather
than accepting it. The findings are documentation reconciliation, two test
hardenings, and one measured warning for the banner fix round the owner has
already approved.

> **The `connection.md` contradiction was ruled on by the owner while this
> review was running** — `docs/handoff.md` → "Part B fix round — the staleness
> banner". He took a **fourth** option nobody had offered: drop the
> `min(22rem, 40vw)` clamp and widen until the body is **one line**, plus a
> warning colour, `WifiSlash` kept, and motion behind `prefers-reduced-motion`.
> That round runs *after* this review closes and carries this review's findings
> with it. **I therefore do not propose competing spec text.** What I do instead
> is the thing his note asks for in as many words — *"measure it, do not assume
> it"* — and the measurement says the width bound in the note is too generous by
> ~1.7×. See "The queued banner fix round, measured" below; it is finding 1.

Run 2026-08-27 by `reviewer` against `docs/constitution.md` **v1.9.0**,
`CLAUDE.md`, `frontend/CLAUDE.md`, `backend/CLAUDE.md`, `docs/system-context.md`,
`docs/design/screens-inhouse/connection.md` and the working tree at
`refactor/bce-subpackages-and-boundary-security` — nothing committed, nothing
staged, no branch switched, no file fixed.

> **Excluded from review by the lead's instruction, and not flagged:** the
> `.gitignore` entry for `packages/api-client/.openapi-generator/openapi.yaml-null.sha256`
> and `docs/handoff.md`. Both are the owner's. *(I did confirm the ignore works:
> the file exists on disk and `git status --untracked-files=all packages/` does
> not list it.)*
>
> **Also not written here:** the owner-approved `frontend/CLAUDE.md` correction
> retiring `PropertyContentService.getSectionRoute()`, queued in
> `docs/handoff.md` for its own round after Part B.

---

## Measured numbers — my own runs

Host: aarch64 Raspberry Pi, 4 cores, ~3.8 GB RAM, **ten unrelated containers
running** (Immich ×4, Home Assistant, Matter, …), load 4–8.
`JAVA_HOME=/tmp/gxp-jdk/jdk-21.0.12+8`, `sg docker -c "…"`.

| # | Command | Result |
|---|---|---|
| B1 | `./mvnw verify` (full) | **429 run · 1 failure · 4 errors · 2 skipped** — `BUILD FAILURE` |
| B2 | `./mvnw test` over 19 classes, `ClassOrderer$Random` **secondary orderer**, no `@TestProfile` class | **167 run · 0 failures · 0 errors** — `BUILD SUCCESS` |
| B3 | `./mvnw test -Dtest='TrustedProxyLoginTest,UntrustedPeerAttributionTest,OpenApiDisabledTest,DevDataSeederTest'` | **12 run · 0 failures · 0 errors** — `BUILD SUCCESS` |
| F1 | `ng test --watch=false` | **313 passed / 313, 34 files** |
| F2 | `ng build` | clean — `main` 459.51 kB, `styles` 9.10 kB, initial total 468.62 kB |
| F3 | `tsc -p tsconfig.spec.json --noEmit` | clean |
| V1 | Own playwright grid — banner × 2 viewports × 2 hosts × 2 themes × 2 languages | **16/16 rendered, 16/16 no-scroll, 16/16 `pillUp=false`** |
| C1 | `./mvnw verify` regenerates `packages/api-client/` | `git status` on `packages/` **unchanged** after my own regeneration |

**F1 = 313/313 matches `dev` exactly.** B1's five problems are all environmental
and every one of them passed in B2 or B3 — see below.

### B1's five problems, judged independently

| Class | B1 symptom | Verdict |
|---|---|---|
| `TrustedProxyLoginTest` | `ContainerLaunchException: Container startup failed for image postgres:17.11` | Environmental — **passes in B3** |
| `UntrustedPeerAttributionTest` | same | Environmental — **passes in B3** |
| `OpenApiDisabledTest` | same | Environmental — **passes in B3** |
| `AuditCoverageTest` | `SocketTimeoutException: Read timed out` after 48.7 s on one of five cases | Environmental — **5/5 green in B2**, in 49.4 s total |
| `ManifestClientTest` | `expected <HttpTimeoutException> but was <TimeoutException>` | Environmental — an update-channel timeout race that resolves to the outer future's exception under load; **15/15 green in B2**, and the class is untouched by Part B |

All three container failures are `@TestProfile` classes, which restart Quarkus
and start a fresh Testcontainer each — the established failure mode on this box,
and the reason the lead's own instruction scopes adverse-order runs away from
them. **No assertion about Part B's behaviour failed in any run.** B1's 429 and
`dev`'s run-1 429 agree exactly; the delta to `dev`'s run-2 440 is entirely
accounted for by which `@TestProfile` classes aborted at startup (an aborted
class contributes one error and none of its tests).

**B2 is a genuine adverse-order run**, not a no-op: the executed order was
`AuditCoverageTest → FirstRunSeedTest → DeviceAuthTest → UpdateCheckTest →
GuestContentTest → ContractSecuredOperationsDeniedTest → RoomScopingTest →
GuestEtagTest → MigrationTest → MenuCatalogueAndConfigTest →
MandatoryMenuItemTest → ActionCardTest → GuestPreferenceTest →
SpaRoutingHandlerTest → CatalogueExpansionTest → GuestAssetTest →
ManagerSurfaceDeniedTest → MenuDisableGuardTest → ManifestClientTest` — neither
alphabetical nor reverse. The shared-database hygiene `dev` carried over from
the deleted `GuestHomeScreenTest` (`MenuFixtures.restoreCatalogueOptOut`) holds
under it.

---

## Visual check (`code-review` step 0) — performed, not skipped

I built the app, served `dist/frontend/browser` statically, seeded a device
token plus a `gxp.cache.content` record with `storedAt` 61 minutes old, blocked
`/api/**` at the browser, and screenshotted **16 cells**: 2 viewports × 2 hosts
(occupied shell / vacant standby) × 2 themes × 2 languages. This drives the
banner through exactly the path REQ-952's cold-boot criterion names, without
touching the manager API.

My numbers, independent of `dev`'s and identical to them:

| | 1024 × 768 | 1280 × 832 |
|---|---|---|
| banner box | `x717 y58 w282 h114` | `x896 y72 w352 h143` |
| body box / lines | `216 × 50`, **3 lines** | `270 × 63`, **3 lines** |
| title / timestamp lines | 1 / 1 | 1 / 1 |
| status-bar bottom, right | 54, 998 | 68, 1248 |
| Home card row | `y 541` | `y 548` |
| `scrollW ≤ clientW && scrollH ≤ clientH` | true ×8 | true ×8 |
| `.staleness-pill` present | **false ×8** | **false ×8** |

Right edge flush with the status bar's (998 / 1248); top offset 4 px = `--space-1`
at both roots; clearance to Home's card row 369 / 333 px; no collision with the
standby block. The wording follows **occupancy**, verified on the wire in all
16 cells: `Gastgeber` / `your host` on the shell, `Verwaltung` / `property
manager` on standby, never crossed.

**Read, not just measured.** The render is right: the panel is recognisably
"the pill grown into a panel", the `WifiSlash` glyph in `--color-status-offline`
reads as muted-but-visible on both meshes, the title carries the weight, and
content keeps rendering behind it on both hosts. The one thing the screenshots
settle that no number does is the typography question the lead asked — see the
ruling below.

---

## Requirement coverage

| REQ | Verdict | Evidence I checked myself |
|---|---|---|
| **941** | ✅ | Contract merged; `getGuestContent(ifNoneMatch?)`; my own `./mvnw verify` regenerated `packages/api-client/` and left `git status` on `packages/` **byte-identical to what `dev` had produced** — that is the byte-identity proof, done twice by two agents |
| **942** | ✅ | One fetch, one `etag`, one `CONTENT_CACHE_KEY`, one `_payload`; `HOME_CACHE_KEY`/`homeEtag`/`heldHome`/`fetchHome`/`asVacant` grep empty; both `409` branches gone |
| **943** | ✅ | `ConnectionState` still three members, pinned by a `Same<A,B>` mutual-extends assertion I verified out-of-tree: it errors `TS2322` for a **fourth** member *and* for a **removed** one. `ng test` type-checks specs (`@angular/build:unit-test` builds through ngtsc; `tsc -p tsconfig.spec.json` also clean), so the pin bites. Timers, cadence, backoff, 401 wipe, map-before-cache ordering all byte-identical in the diff |
| **944** | ✅ | `GuestContentTest.vacancyIsDataSparingAndOccupancyFlips` — vacant ⇒ no `guestDisplayName`, no `preference`, no `home` |
| **945** | ✅ | Asserted on both sides: same backend case (property half intact across the episode) and `guest-data.service.spec.ts` "the property half survives a vacancy episode, in RAM and in the cache" |
| **946** | ✅ | Seam test is sound — see the priority-2 note below |
| **947** | ⚠️ ✅ | Implementation correct on all five terms (I traced each); **the preference-write term has no test** — 🟡 1 |
| **948** | ✅ | `MANDATORY_KEYS` short-circuits `enabled()` — *the* ceiling implementation — so the guest read, the stranded-card scan and both invariant checks all see `home` as enabled; `V11` neutralises legacy rows; `MandatoryMenuItemTest` covers both scopes, the icon/label carve-out and the inert legacy row. The `404 screen-not-found` and `409 room-vacant` branches are **deleted**: `grep -rn "screen-not-found\|GuestEtagFilter" backend/app/src` is empty |
| **949** | ✅ | Structure, not size — see the dedicated section |
| **950** | ✅ | All 13 cases present with equal-or-stronger observable assertions; the two `409` cases converted correctly |
| **951** | ⚠️ ✅ | Not name-based; allow-list + device-token + `bearer`/`authorization` literals. **The "new wire field fails the test" half is not guaranteed** — 🟡 2 |
| **952** | ⚠️ ✅ | Threshold, contact-not-change, cold boot, tri-state, both variants, both languages, both hosts, no-scroll — all verified by my own runs. The **spec contradiction** is 🟠 1 |

---

## REQ-949 — judged as structure, and with the comments audited

The line budget was struck by the owner on 2026-08-27. `dev` reports 452 → 316;
I confirmed both numbers, and then did the check the amendment actually asks
for.

**The four structural properties hold.** One `fetchContent()`; one `etag` field;
one `CONTENT_CACHE_KEY`; one `_payload` signal. Neither `bootstrap()` nor
`refresh()` runs a two-call sequence. No branch handles a `409`.

**No comment was compressed or dropped to make a number.** I matched every
removed comment against the code it described:

| Comment removed | Described |
|---|---|
| the `If-None-Match`-mechanism paragraph (7 lines) | `withIfNoneMatch()`, deleted |
| the `fetchHome` overload note (4 lines) | `fetchHome()`, deleted |
| `asVacant()`'s doc block (14 lines) | `asVacant()`, deleted |
| the "`session !== this._session()` was a dead fourth term" note | that term, deleted with the two-call `refresh()` |
| the vacancy data-sparing note in `adopt()` | the home-cache branch, deleted |

And the file **gained** explanation where new behaviour appeared: a 5-line
doc on `LEGACY_CACHE_KEYS`, and the class comment's `VACANT` line grew from
two lines to three to state the new invariant. That is the opposite of the
perverse incentive the owner struck the metric to remove. `mapGuestContent`
likewise gained a doc paragraph for the new `OCCUPIED ⇒ home` invariant.

I therefore read `452 → 316` as information and not as a criterion, exactly as
instructed — and the structure passes on its own terms.

---

## Priority 1 — REQ-947, the sharp edge

`sha256(bootId | max(audit_entry.id) | roomId | stayFingerprint | 300 s bucket)`.
I traced each term to the invalidation it owns:

| Term | Covers | Verified |
|---|---|---|
| `bootId` (`UUID` fixed at construction) | migrations, seeders, the server-side settings-chrome constants, any released build | ✅ |
| `max(audit_entry.id)` | every manager mutation, because v1.8.1 makes the audit row an obligation and `AuditCoverageTest` derives-and-enforces it | ✅ |
| `roomId` | two rooms never share a stamp | ✅ `GuestEtagTest.twoRoomsNeverShareAStamp`, incl. the cross-tag behaviour |
| stay fingerprint `id:displayName:checkIn:checkOut:language:appearance` | clock-driven check-in **and** check-out, **and** the un-audited guest preference write — `StayControl.writePreference` mutates exactly `stay.language` / `stay.appearance`, both in the fingerprint | ✅ by reading; check-in tested, **preference write untested** (🟡 1) |
| 300 s bucket | the two documented holes | ✅ present and used |

**`STAMP_REFRESH_SECONDS = 300` is intact, is a named constant, is used in
`stampFor`, and carries both hazards in its Javadoc** — the INSERT-vs-COMMIT id
allocation race written out as a worked interleaving, and the fact that
`NOT_A_MANAGER_MUTATION` legitimately holds guest-side writes so "everything is
audited" is a claim about manager writes only. It ends with **"Do not delete
this term."** Nothing was simplified away.

I also checked the direction of the one race that is *not* closed: a manager
commit landing between `latestSequence()` and the compose gives the kiosk *new*
content under an *old* stamp, so the next poll is a `304` on content it already
has. That is the safe direction, and it is the mirror of the documented hazard.

---

## Priority 2 — the REQ-946 seam test

`GuestEtagTest.theNotModifiedAnswerResolvesNothing` proves what it claims.

1. Poll → `200` + ETag.
2. `update property set timezone = 'Not/AZone'` by **native SQL** — writes no
   audit row, so the stamp is unchanged **by construction**, not by assumption.
3. Conditional poll → `304`, **empty body**. Every resolve path runs
   `ZoneId.of(property.timezone)`, standby texts included, so a `304` here
   cannot have composed anything.
4. **The control**: the same request without `If-None-Match` → `≥ 500`. Without
   this step the case would prove only that the payload was cheap.
5. Restore — in **`finally`** *and* again in **`@AfterEach`**. Confirmed both.

`startInsideAFreshBucket()` **removes** the flake rather than hiding it. It
waits out the tail of the current 300 s bucket only when under 30 s remain, so
every case starts with ≥ 30 s of bucket ahead of two adjacent HTTP calls. It
weakens no assertion, stubs no clock, skips nothing, and never changes the
production term. In B2 the whole class ran in **2.9 s**. The residual is that a stall of
more than 30 s between two adjacent polls would surface as `expected 304 but
was 200` rather than as a timeout — on a box where I measured one 48 s HTTP call
today, that is worth knowing, but it is a diagnosis-quality issue, not a
correctness one, and the alternative (injecting a clock into
`GuestContentVersion`) adds production surface for a test.

---

## The queued banner fix round, measured

The owner's note says the estimate *"fits, but **measure it, do not assume it**"*.
I measured it, on the built app, at both reference viewports, in both languages,
on both hosts. **It does not fit as bounded, and the binding string is not the
one the estimate was reasoned from.**

### What one line actually costs

Single-line glyph runs, measured with a probe span carrying the body's computed
`font` and `letter-spacing`, plus the banner's own measured chrome (glyph +
gap + horizontal padding: **66 px** at 1024, **82 px** at 1280):

| String | 1024 × 768 text → panel | 1280 × 832 text → panel |
|---|---|---|
| `stale.banner.body` de | 488 → **554** | 615 → **697** |
| `stale.banner.bodyStandby` de | 486 → **552** | 611 → **693** |
| `stale.banner.body` en | 446 → **512** | 557 → **639** |
| **`stale.banner.bodyStandby` en** | **501 → 567** | **630 → 712** |

**The binding string is the English standby variant, not the German.** The
width budget in the note was reasoned from the German copy — reasonable, since
German is longer nearly everywhere in this app — but *"Please inform the property
manager."* is the longest of the four (96 chars, and English renders wider per
character here than the German at this size). Anyone sizing the bar against
`stale.banner.body` in German will be **13 px short at 1024 and 15 px short at
1280**, which is exactly enough to wrap and undo the whole change. Size against
`stale.banner.bodyStandby` in **English**.

### The width bound in the note is ~1.7× too generous

The note bounds the bar at *"the content-area edge (it must not cover the
sidebar)"*. That is **892 px** at 1024 and **1116 px** at 1280, and against it
everything fits with room to spare. But the sidebar is not the first thing the
bar meets. Home lays out in two columns:

| | 1024 × 768 | 1280 × 832 |
|---|---|---|
| `.home-content__screen` (heading column) | x138…**450** | x172…**562** |
| `.home-content__widgets` (the empty widget column — the banner's declared anchor) | **x475**…998 | **x594**…1248 |
| **genuinely free width** (widget column ← banner's right edge) | **523 px** | **654 px** |
| **one-line panel required** (en standby) | **567 px** | **712 px** |
| **shortfall** | **44 px** | **58 px** |

`docs/design/screens-inhouse/connection.md` and `02-design.md` §6.3 both justify
the anchor with *"the top-right region is the empty widget column"*. A one-line
bar leaves that column at both viewports and reaches **44 / 58 px into the
heading column**.

### And the clearance it currently has is an accident of the seed data

Rendered text extents, per line, from `Range.getClientRects()`:

| | headline line 1 | bar's left edge if one-line | clear by |
|---|---|---|---|
| 1024 × 768 de | x138…**427**, y93…142 | x**431** | **4 px** |
| 1280 × 832 de | x172…**531**, y116…177 | x**536** | **5 px** |

Four and five pixels. And `home.headline` is **manager-authored and
template-resolved** — it is `{stay.displayName}` interpolated into a property
template. The seed's *"Willkommen im Airbnb von Anna & Tom!"* happens to break
at x427; *"Anna-Maria & Thomas"* would not. The one-line bar does not clear the
hero headline by design, it clears it by four pixels of luck with one guest's
name, at a viewport where the design says that region belongs to the heading
column anyway.

### What this leaves the fix round

Not a veto — a measured constraint the note did not have. The gap is 44 / 58 px,
roughly 8 %, and there are several ways to close it. Offered for the fix round to
weigh, **not decided here**:

- **Trim the chrome.** `--size-icon-lg` (28.8 / 36 px) → `--size-icon-md`, and
  horizontal padding `--space-5` → `--space-4`, saves ~25 / 30 px. Over half the
  gap, and a smaller glyph suits a bar better than a panel.
- **Move the timestamp onto the bar's own row**, right-aligned before the
  panel's edge. It buys no width, but it makes the bar ~1 line shorter — and the
  4 px collision is only a collision because the bar's *band* (y58…133) reaches
  the headline's first line. A flatter bar drops out of that band entirely, which
  is the cheapest real fix.
- **Shorten `stale.banner.bodyStandby` in English only.** *"Please tell the
  property manager."* saves ~30 px. Copy is owner-approved, so this is his call,
  and it only helps the one string.
- **Bound the bar at the widget column (523 / 654 px) and accept that the
  longest of the four strings takes two lines.** Honest, keeps the design's own
  stated anchor, and makes the line count follow the geometry rather than the
  other way round.

Whatever is chosen, the fix round should **re-measure the four strings × two
viewports × two hosts**, because three of the four numbers above are within
60 px of the bound.

### On the rest of the fix-round decision

Recorded as reviewed and sound, not re-litigated: keeping `WifiSlash` and
carrying severity in the colour rather than the glyph is right and preserves the
severity order the spec argues for; deriving `--status-warning` from
`design-sheet.md` §Status's existing v1 `Warning #F8B84E` rather than placing a
second decision beside it is the correct instinct, and making it text-capable
(4.64 / 5.66) from the start is the right lesson from `--status-offline`'s
permanent "never a text colour" caveat. Rejecting the marquee on WCAG 2.2.2 and
*"Animationen ruhig und hochwertig"* grounds is well-reasoned. One note for
whoever writes it: the bar's `role="status"` region is created and destroyed by
an `@if`, so an entrance transition on it must not be the thing that also
changes its text, or the live region announces mid-animation.

**One consequence the note names and should not be lost:** `connection.md`'s
*"the pill grown into a panel"* rationale, and the anchor paragraph that
justifies it with the empty widget column, both stop being true. The aspect
ratio goes from 282 × 114 (2.5 : 1) to roughly 567 × 75 (7.5 : 1). That is a
different object, and §6.3 of `02-design.md` says *"one body line"* today while
`connection.md` says *"at most two"* — **neither describes what ships now, and
only one of them will describe what ships after the fix round.** Both need
rewriting in that round (finding 1).

---

## Findings

| # | Severity | Category | File | Finding | Fix |
|---|---|---|---|---|---|
| 1 | 🟠 | Design fidelity | `docs/design/screens-inhouse/connection.md`, `02-design.md` §6.3 | **Two design documents describe a banner that does not exist, and they disagree with each other.** `connection.md` says the body is *"at most two lines"*; `02-design.md` §6.3 says *"one body line"*; the shipped banner renders **three** in all 16 cells I measured. The owner has ruled (fix round, `docs/handoff.md`) and the geometry will change — but **both sentences are wrong today and only one of them will be right afterwards**, and the anchor rationale (*"the pill grown into a panel"*, *"the top-right region is the empty widget column"*) stops being true once the bar widens past x475 / x594. | Rewrite both in the fix round, against the measured numbers in the section above. **The width bound in the owner's note needs correcting first** — it is the sidebar edge (892 / 1116 px) where it should be the widget column (523 / 654 px), and the one-line requirement exceeds the latter by 44 / 58 px. Not mine to decide; measured and handed over. |
| 2 | 🟡 | Testing | `backend/.../guest/GuestEtagTest.java` | **REQ-947's preference-write term has no test.** The requirement names four things the stamp must track; three have a case (manager edit, clock-driven check-in, room scope). The fourth — the *un-audited* guest preference write — is covered only structurally, by `fingerprintOf()` happening to enumerate `language` and `appearance`. That enumeration is exactly what a later "tidy" reduces to `id:checkIn:checkOut`; `GuestEtagTest` would stay green while a kiosk served the wrong language for up to 300 s. It is the one term whose correctness rests on a line nothing defends. | Add one case (sketch below). ~15 lines. Not blocking on its own — the implementation *is* correct, and the bucket bounds the damage — but this is the cheapest protection in the whole feature. |
| 3 | 🟡 | Testing | `frontend/.../guest-data.service.spec.ts:333` | **REQ-951's allow-list is fixture-based, not contract-based.** The criterion says *"a new wire field fails the test until someone classifies it"*. The test compares `Object.keys(record.payload)` against the nine-key allow-list — but that payload is `contentFixture()`, a hand-built literal in the spec. A field added to `GuestContent` in `api/openapi.yaml` never reaches it, so the test stays green until someone independently adds the field to the fixture too. The *rule* asserted is right (allow-list + token/bearer literals, correctly **not** name-based); it is the ratchet that is one step weaker than the PRD claims. | Add the compile-time half (below), so the *contract* — not the fixture — is what fails the build. Two declarations. |
| 4 | 🟡 | Docs | `docs/backend/specification.md:69, 271–272` | **T-18's own validation criterion — "no document still claims two guest content endpoints" — is not met.** The "Guest (device token required, room-scoped)" table still lists `GET /api/guest/session` and `GET /api/guest/screens/home` as the live surface, and decision 7 ("Guest API is split: shell vs. screen") is now reversed by D7 with no marker — while decision 4 in the very same table carries *"Superseded 2026-08-26 by `auth-hardening`"*. The file's own convention is to mark supersessions inline. This is the same trap the lead named for `getSectionRoute()`: an agent finds the dead path documented and the live one nowhere. | Two small edits, below. `specification.md` is not in T-18's file list, so this is a gap in the plan rather than in `dev`'s execution. *(If the owner would rather treat the whole brief as a dated log — his 2026-08-27 ruling on `:511–512` — then one banner line under the Guest heading is enough; the endpoint table is the part that reads as current.)* |
| 5 | 🟡 | Plan hygiene | `docs/features/guest-payload-consolidation/03-todo.md` T-10, T-19 | **The struck 452-line budget is still cited twice** — T-10's `Validate:` (`` `wc -l` on the service is **< 452** ``) and T-19's *"Re-check REQ-949's two greps and the line count"*. `01-prd.md` was amended; the TODO was not, because `dev` held the file. | Exact replacement text below. |
| 6 | 🟡 | Instrument | `frontend/tools/measure-states.mjs` | **The instrument still aborts the whole run on a manager-API failure instead of `note()`-ing and skipping.** `managerToken()` now `throw`s (a real improvement — the failure is diagnosable instead of a bare `JSON.parse` crash), but the run still dies, and `stays.find(…)` / `active.id` have no guard either. The script's own header says *"it reports, always exits 0"*. This is the **second recorded instance of this defect class** — Part A's review raised the identical thing as 🟢 12 against `measure.mjs`'s `applyPreferences` — and it is precisely how STANDBY and ARTWORK stayed dead for a commit without anyone noticing. | Wrap each manager-driven block in `try { … } catch (e) { note('INSTRUMENT', …) }` so a broken cell costs its own cells and not the other 24. |
| 7 | 🟡 | Testing | `frontend/.../layout/shell/shell.component.spec.ts:170` | **Nothing asserts REQ-952's second half — that content keeps rendering behind the banner.** The acceptance criterion is *"the banner is shown, the pill is not, **and the current route still renders its content**"*. The new shell case asserts the first two and stops; the routed `TestHomeComponent` is right there in the harness and is never checked. That half is the one D13 argued hardest for — the hard cutoff was *explicitly rejected* — so it is the half most worth pinning. My 16-cell render confirms it behaviourally, so this is a coverage gap, not a defect. | One line in the existing case: `expect(element.textContent).toContain('home content')` beside the banner assertions. The standby case can do the same with `.standby__headline`. |
| 8 | 🟢 | Conventions | `frontend/CLAUDE.md` → Tests | The `measure-states.mjs` sentence still says *"the three full-screen states and the staleness pill"*. Now incomplete, not wrong. **Nested `CLAUDE.md` — proposing text and stopping**, per the constitution's approval gate. | Proposed text below. Do not write without the owner's word. |
| 9 | 🟢 | Correctness | `GuestControl.java:141` | The `304` no longer carries `Cache-Control: no-cache`; the retired `GuestEtagFilter` set the header **before** flipping the status, so it did. Harmless here — RFC 9111 §4.3.4 keeps the stored response's directives when a `304` omits them, the `200` still sends `no-cache`, and there is no intermediary on a LAN. Recorded because it is a silent difference from the code being replaced. | None needed. |
| 10 | 🟢 | Docs | `AuditControl.latestSequence()` Javadoc | *"the count is not monotonic — the sequence is"* overstates slightly: `coalesce(max(id), 0)` returns `0` if retention ever prunes **every** row. The effect is a spurious `200` (the safe direction), and the 300 s bucket bounds any repeat. | Optional: *"…the sequence is, as long as any row survives the prune; an emptied table restarts at 0, which costs one extra 200 and never a stale 304."* |
| 11 | 🟢 | Style | `guest-data.service.ts:3` | `OccupancyState` is imported as a **value** but, after the collapse, is only used as a type (`signal<OccupancyState \| null>`). No lint config exists to catch it. | `type OccupancyState`, matching `type GuestContent` in the same statement. |
| 12 | 🟢 | Record accuracy | `handoff.md` → deviation 3 | The pill spec's pinned timestamp `2026-08-20T14:05` is called *"over a year in the past"*. It is **seven days**. The deviation is sound and the fix is right — seven days is still far past a 60-minute threshold, so it genuinely would have resolved to `BANNER` — but the figure in the record is wrong. | Correct the figure when the handoff is next touched. |
| 13 | 🟢 | Style | `docs/design/screens-inhouse/standby.md:17` | The edited line now runs ~101 columns in a file wrapped at ~76. | Re-wrap. |
| 14 | 🟢 | Constitution | `docs/constitution.md` v1.9.0 vs. the standby variant | v1.9.0 says the banner directs *"the guest to their host"*; the vacant variant addresses staff. `02-design.md` §6.6 judged that nothing is contradicted — the clause's own stated rationale is operational alerting — and proposed no amendment. **I agree**, and it is already recorded as deferred question 5 in `docs/handoff.md`. Not re-raised as a finding. | None. Owner may still want the clause to name the staff case; that is an amendment and needs his exact-text approval. |

---

## Exact text for the reconciliations

### Finding 5 — `03-todo.md` T-10 `Validate:`

Replace:

> `- Validate: `wc -l` on the service is **< 452**; `grep -n "HOME_CACHE_KEY\|homeEtag\|heldHome\|fetchHome\|asVacant" frontend/src/app/core/guest/guest-data.service.ts` is empty; `ng build` green.`

with:

> `- Validate: `grep -n "HOME_CACHE_KEY\|homeEtag\|heldHome\|fetchHome\|asVacant" frontend/src/app/core/guest/guest-data.service.ts` is empty; the file holds **one** fetch, **one** ETag field, **one** cache key and **one** held copy; neither entry point runs a two-call sequence; no branch handles a `409`; `ng build` green. *(The `wc -l < 452` check was struck by the owner on 2026-08-27 — `01-prd.md` REQ-949. Judge the structure, not the size.)*`

### Finding 5 — `03-todo.md` T-19

Replace `Re-check REQ-949's two greps and the line count.` with:

> `Re-check REQ-949's grep and its four structural properties (one fetch, one ETag field, one cache key, one held copy; no two-call sequence; no `409` branch). **Not the line count** — struck by the owner 2026-08-27.`

### Finding 4 — `docs/backend/specification.md`

Decision 7 (line 69), mirroring decision 4's existing marker — prepend to the
Rationale cell:

> `*Superseded 2026-08-27 by `guest-payload-consolidation` D7 (Part B): the two guest reads are **one** resource, `GET /api/guest/content`, carrying `home` for an occupied room and omitting it for a vacant one. Original reasoning:* `

The Guest endpoint table (lines 271–272) — replace the two rows with one:

> `| `GET` | `/api/guest/content` | Room, property, brand primitives, logo asset id, resolved settings config, the stay's raw preference override, **occupancy state**, guest display name when occupied, the resolved menu and the standby texts — and, for an occupied room, the nested `home` (headline, subtitle, paragraph, artwork asset id, 0–4 resolved action cards). Merged from the retired `/api/guest/session` + `/api/guest/screens/home` by `guest-payload-consolidation`, 2026-08-27 |`

### Finding 8 — `frontend/CLAUDE.md` → Tests (**owner approval required; do not write**)

> `tools/measure-states.mjs` is the review instrument for the three full-screen states and for the staleness chrome — the discreet pill and its escalated banner, the latter driven by ageing the cached record's `storedAt` past the threshold; it needs the dev backend and drives its state via the manager API (and restores it).

### Finding 2 — the missing REQ-947 case

```java
@Test
void theGuestsOwnPreferenceWriteInvalidatesTheStamp() {
    // REQ-947's third term, and the one no other case reaches. The write is
    // deliberately NOT a manager mutation (AuditCoverageTest's
    // NOT_A_MANAGER_MUTATION holds it), so max(audit_entry.id) does not move:
    // only the stay fingerprint's language/appearance fields do. Delete them
    // from GuestContentVersion.fingerprintOf and this is the case that fails.
    QuarkusTransaction.requiringNew().run(() -> entityManager
            .createNativeQuery("insert into stay (room_id, display_name, check_in, check_out, status)"
                    + " values (:room, 'Anna', :in, :out, 'BOOKED')")
            .setParameter("room", roomId)
            .setParameter("in", OffsetDateTime.now().minusHours(1))
            .setParameter("out", OffsetDateTime.now().plusDays(1))
            .executeUpdate());
    String etag = content(null).header("ETag");

    given().header("Authorization", "Bearer " + deviceToken)
            .contentType("application/json")
            .body(Map.of("language", "en", "appearance", "dark"))
            .when().put("/api/guest/preferences")
            .then().statusCode(204);

    Response after = content(etag);
    assertEquals(200, after.statusCode(), "the guest's own write must invalidate its held ETag");
    assertEquals("en", after.path("preference.language"));
    assertNotEquals(etag, after.header("ETag"));
}
```

The existing `@AfterEach` already deletes the room's stays, so nothing new needs
tearing down.

### Finding 3 — the compile-time half of REQ-951's allow-list

In `guest-data.service.spec.ts`, beside `ALLOWED_PAYLOAD_KEYS`:

```ts
/**
 * The compile-time half of the allow-list (REQ-951). The runtime assertion
 * below only ever sees what `contentFixture()` puts in the cache, so on its
 * own it goes green on an unclassified new wire field. These two declarations
 * make `api/openapi.yaml` the thing that fails: a field added to
 * `GuestContent` breaks the build here until it is classified above, and a
 * key deleted from the allow-list breaks it too.
 */
type PayloadKey = keyof Required<GuestContent>;
const _allowListIsExhaustive: Record<PayloadKey, true> = {
  property: true, room: true, occupancy: true, guestDisplayName: true,
  settings: true, preference: true, menu: true, standby: true, home: true,
};
const _allowListMatchesTheContract: readonly PayloadKey[] = ALLOWED_PAYLOAD_KEYS;
void _allowListIsExhaustive;
void _allowListMatchesTheContract;
```

`ng test` type-checks specs through ngtsc (I verified both that and
`tsc -p tsconfig.spec.json`), so this fails the run, not just an editor.

---

## The six declared deviations — my judgement

| # | Deviation | Verdict |
|---|---|---|
| 1 | T-10 spilled into `app.routes.spec.ts`, `http-preference-gateway.spec.ts`, `preferences.persistence.spec.ts`; renamed under T-12 | **Correct and necessary.** I confirmed all three doubles are untyped object literals (`useValue: { session: …, sessionPreference: … }` and a hand-written `FakeGuestDataService`), so `session` → `payload` compiles and fails at runtime. Deferring them to T-12 kept T-10 honest. |
| 2 | T-14 touched eleven files | **Not scope creep.** The TODO's own `Files:` line for T-14 names three new banner files, both hosts' `.ts` + `.html`, both host specs and the pill — eleven. The cap was exceeded by the task's definition. |
| 3 | The pill's shell spec re-pinned to a relative timestamp | **Right fix, wrong figure in the record.** `2026-08-20T14:05` is **7 days** before today, not "over a year" (🟢 12). Seven days is still 168× the threshold, so it genuinely resolved to `BANNER` and the change was required. The replacement derives the expected label with the same `Intl.DateTimeFormat` the component uses, which is better than the literal it replaces. |
| 4 | Two pre-existing `measure-states.mjs` defects fixed | **Both real, both verified, both worth fixing here** — see the dedicated section below. The residual crash-instead-of-note behaviour is 🟡 6. |
| 5 | Three stale endpoint references corrected (`standby.md`, `property.model.ts` ×2) | **In scope** — T-18 is a documentation sweep and these are the sweep's own findings. Correct, minimal, and each one was factually wrong. (The `standby.md` line wraps badly now — 🟢 13.) |
| 6 | T-15 run before T-14 | **Sound.** A missing catalogue key fails `ng build` by design, so T-14's build check is meaningless without T-15's keys. Reordering two tasks inside one phase is squarely inside the autonomy grant. |

---

## The dead measurement cells — what, if anything, rested on them

**Verified: nothing previously signed off depended on them.**

The window is exact. `POST /api/manager/login` gained a required `username` in
**`d0a6ea6`** (2026-08-27, the authentication hardening) —
`git log -S "required: [username, password]" -- api/openapi.yaml` returns that
commit and no other. `measure-states.mjs` last changed in `ec33e49`, well before
it. So the STANDBY and ARTWORK cells were dead from `d0a6ea6` until now.

Exactly one UI feature was signed off inside that window: **Part A
(`sidebar-catalogue-overflow`, `33f745a` + `14c005e`)**. Part A's measurement
rested on the **other** instrument — `tools/measure.mjs`, 168 cells, quoted
verbatim in its `06-review.md`. I checked `measure.mjs` for the same defect:
**it never calls `/api/manager/login` at all** (`grep -n "manager/login\|password\|username"` is
empty), so it could not have been affected. Every other commit in the window
(`d0a6ea6`, `0d7e4bc`, `d6712c8`, `9f43a98`, `0ed4f03`, `c8081a8`) is backend or
docs only.

Both fixes check out:

- **The login fix.** All three call sites now go through one `managerToken()`
  that posts `{ username, password }` with `GXP_SMOKE_ADMIN_USER` defaulting to
  `admin`, and raises a named error instead of dying inside `JSON.parse`. Three
  sites, one helper, no duplication left.
- **The preference-axis fix.** This one is the more valuable of the two. The
  banner cells drive theme and language through the real status bar **after** the
  offline boot and never reload, because the preference override lives *in the
  payload* — a reload drops it, and while vacant the write `409`s by design. The
  first version silently measured `light/de` eight times. It is now asserted per
  cell (`PREFERENCE NOT APPLIED`), which is the correct response to discovering
  that an instrument can be silently axis-blind. My own 16-cell grid drives the
  axes a different way (through the seeded payload) and reproduces `dev`'s boxes
  to the pixel, which independently confirms the axes really are being varied.

The honest caveat on the headline number: **"40/40 cells `NO-SCROLL OK`, 0
overflow" is precisely true and precisely narrow.** The same run also emits
**16 `WRAP` findings** — `if (bn.body.lines > 2) note('WRAP', …)` fires in every
banner cell, because the instrument correctly encodes the spec clause that
finding 1 is about. `dev` disclosed the three-line fact separately and honestly;
I am recording that the instrument's own finding list is not empty, so the owner
reads the whole picture rather than the scroll line alone.

---

## Conventions, security, dependencies

- **BCE.** `GuestContentVersion` is package-private in `.control`, injects
  another feature's public `AuditControl` — the one crossing spec §4 allows.
  `GuestResource.getGuestContent` is one delegating line, inheriting the single
  class-level `@RolesAllowed("device")` and carrying nothing else.
  `@Transactional` is on the Control. `AuditEntryRepository.maxId()` is
  package-private and is the only new JPA touch. **Five architecture guards,
  untouched, no sixth** (`git status` on `de/gxp/architecture/` is empty).
- **`AuditCoverageTest`** carries exactly the intended swap and nothing else:
  `"session", "homeScreen"` → `"content"`. Confirmed by diff.
- **`ContractSecuredOperationsDeniedTest`'s floors were lowered 58/52 → 57/51**
  with the reason named in the Javadoc, as that class demands. Correct: it is
  the first deliberate drop and it is documented as one.
- **Security.** Nothing logged. No credential in the payload or the cache — I
  read the wire shape and the record. No string-built query with user input
  (the native SQL in tests is literal). Authorization unchanged and still on the
  Boundary. The ETag is an opaque hash; a device cannot derive another room's.
- **Dependencies.** `pom.xml`, `package.json`, `package-lock.json` all
  untouched — `git status` and `git diff --stat` both empty for them.
- **Constitution.** v1.9.0 cache clause satisfied (finding 14); "nothing
  scrolls" satisfied by construction (absolute overlay, `:host { display:
  contents }`) *and* by my own 16 measured cells; no primitive read in the new
  CSS, no raw hex, no `px`; no user-visible string in a template; both languages
  through `TranslationService`; no new icon, so `registered-icons.ts` stays a
  Part A file.
- **Copy** matches `connection.md`'s table verbatim in both catalogues — I
  diffed all six strings by hand.

---

## Top recommendations

1. **Carry the width measurement into the fix round before any code is written.**
   The owner's note bounds the bar at the sidebar (892 / 1116 px); the real bound
   is the widget column (523 / 654 px), and one line needs 567 / 712 px. Sizing
   against the German copy — the natural instinct — is 13–15 px short, because
   the binding string is **`stale.banner.bodyStandby` in English**. That is the
   whole content of finding 1, and it is cheaper to know now than after the CSS.
2. **Add the three test hardenings (findings 2, 3, 7).** ~25 lines together.
   They defend the three places where this feature's correctness currently rests
   on something nothing would notice losing: `fingerprintOf`'s
   `language`/`appearance` terms, REQ-951's ratchet against an unclassified new
   wire field, and REQ-952's "content keeps rendering" half.
3. **Close the documentation drift (findings 4, 5).** `specification.md`'s
   endpoint table is the one that matters — it reads as a current API reference
   and now describes an API that does not exist, which is exactly the failure
   the lead named for `getSectionRoute()`.

## Ready to commit?

**Not as it stands — but nothing is wrong with the code.** The implementation is
correct and complete against REQ-941…REQ-952, green on both suites in my own
runs, byte-identical on regeneration, and visually verified at 16 rendered cells.

What stops a commit today is not a defect but a **scheduling fact the owner has
already set**: `docs/handoff.md` queues a banner fix round that changes the
geometry, the tokens and the motion of the component this feature adds, and says
in as many words that it *"runs after the Part B review closes, together with
whatever that review finds"*. Committing now would land a banner geometry that
is already superseded, along with two design documents that describe neither the
old one nor the new one.

**The sequence that follows from that:** close this review → run the fix round
(the banner redesign **plus** findings 2–7, with finding 1's measurement as its
starting constraint) → re-measure → one commit carrying contract + backend +
`packages/api-client/` + frontend + docs. Everything in Part B proper is ready
to be part of that commit unchanged.

---

# Review — Round 2 (re-review after the fix round + banner redesign)

**Result:** **NEEDS CHANGES ❌** — 🔴 0 · 🟠 3 · 🟡 3 · 🟢 4.

Run 2026-08-27 by `reviewer` on `refactor/bce-subpackages-and-boundary-security`.
Nothing committed, nothing staged, no branch switched, no file fixed. The two
files I mutated for probes (`GuestContentVersion.java`,
`packages/api-client/model/guestContent.ts`) were restored and verified
byte-identical with `diff`; the working tree holds the same 75 entries it did
when I started.

**Every round-1 finding is closed, and I verified each one by my own hand rather
than by reading the diff.** The banner redesign is sound as *built* — I measured
it at 24 cells of my own plus the instrument's 40, looked at the renders, and
found no rendering defect at any of them. **What is wrong is the record of where
it lands**, in three artifacts, plus the new instrument check that was supposed
to protect it. One of those wrong figures is the basis of a question queued for
the owner, which is why this is not a PASS.

## My own runs

| # | Command | Result |
|---|---|---|
| B1 | `./mvnw -B verify` (full) | **441 run · 0 failures · 2 errors · 2 skipped** — `BUILD FAILURE`, both errors environmental (below) |
| B2 | `./mvnw -B test -Dtest='DevDataSeederTest,UntrustedPeerAttributionTest'` | **4 run · 0 failures · 0 errors** — `BUILD SUCCESS` |
| B3 | Mutation probe (finding 2) — see below | **fails, and only the intended case** |
| F1 | `ng test --watch=false` | **313 passed / 313, 34 files** |
| F2 | `ng build` | clean — `main` 460.28 kB, `styles` 9.27 kB, initial total 469.55 kB |
| F3 | `tsc -p tsconfig.spec.json --noEmit` | clean |
| F4 | Mutation probe (finding 3) — see below | **`tsc` AND `ng test` both fail** |
| M1 | `GXP_SMOKE_ADMIN_PW=… node tools/measure-states.mjs` (live dev backend + built app) | **40/40 cells NO-SCROLL OK**, `pillStillUp` false ×16, body 1 line ×16, **8 findings — all `COLLISION: banner x Home's heading column`** (finding 1) |
| V1 | My own probe — 2 viewports × 2 themes × 2 languages × 3 headline variants, plus reduced-motion and a standby cell | 25 cells, no scroll anywhere, geometry below |

**B1 is the first full backend run since the crash.** 441 run · **0 failures**.
The two errors are the established environmental mode, not defects:

| Class | Symptom | Verdict |
|---|---|---|
| `DevDataSeederTest.reseedingIsAFullNoOp` | `Failed to start quarkus` ← `FlywaySqlUnableToConnectToDbException: Acquisition timeout while waiting for new connection`, after 72.3 s | `@TestProfile` class, fails at boot. **2/2 green in B2** |
| `UntrustedPeerAttributionTest` | `ContainerLaunchException: Container startup failed for image postgres:17.11`, after 71.4 s | `@TestProfile` class, fails at boot. **2/2 green in B2** |

Both are `@TestProfile` classes, which restart Quarkus and start a fresh
Testcontainer — the documented failure mode on this 4-core aarch64 box.
**No assertion about Part B's behaviour failed in any run.** B1 is strictly
better than round 1's B1 (429 run · 1 failure · 4 errors): the two load-related
socket timeouts that hit `AuditCoverageTest` and `ManifestClientTest` last time
did not recur, and `AuditCoverageTest` was 5/5 in 5.2 s.

## The two mutation probes, done by my own hand

**Finding 2 — REQ-947's preference-write term.** I deleted
`+ ":" + stay.getLanguage() + ":" + stay.getAppearance()` from
`GuestContentVersion.fingerprintOf` and ran `GuestEtagTest`:

```
Tests run: 5, Failures: 1, Errors: 0
GuestEtagTest.theGuestsOwnPreferenceWriteInvalidatesTheStamp:257
  the guest's own write must invalidate its held ETag ==> expected: <200> but was: <304>
```

**One failure, and it is that case.** The other four stayed green, so the case
defends exactly the term it exists for and nothing else is doing the work for
it. File restored and diffed.

**Finding 3 — REQ-951's compile-time half.** I added `wifiPassword?: string` to
`GuestContent` in the generated client. Both halves bite:

```
TS2741: Property 'wifiPassword' is missing in type '{ property: true; … home: true; }'
        but required in type 'Record<keyof GuestContent, true>'.
        src/app/core/guest/guest-data.service.spec.ts:46:6
```

`tsc -p tsconfig.spec.json` exits 2, and **`ng test` itself fails the run**
(`Application bundle generation failed`, `[plugin angular-compiler]`) — so the
ratchet is in the suite, not just in an editor. File restored and diffed.

## The warning colour — both ratios re-measured

I recomputed all four from the primitives rather than trusting any record:
`--color-status-warning` over `--color-surface-glass` composited on the mesh.

| | Recorded | My value (backdrop rounded to 8-bit sRGB, as a browser does) |
|---|---|---|
| light `#895806` over `--mesh-6` | 4.64 | **4.643** ✅ |
| light `#895806` over `--mesh-8` | 5.17 | **5.171** ✅ |
| dark `#f9b84e` over `--night-6` | 5.63 | **5.634** ✅ |
| dark `#f9b84e` over `--night-8` | 4.86 | **4.859** ✅ |

**All four recorded figures are exactly right, and all four clear 4.5 : 1.**
(Unrounded they are 4.635 / 5.180 / 5.664 / 4.890 — which is where
`docs/handoff.md`'s "4.64 / 5.66" came from. `dev`'s numbers are the better
ones; the handoff's dark figure is the stale one. 🟢 9.) Hue confirmed at 37.6°
against `#F8B84E`'s 37.4°. Rendered `color` confirmed live: `rgb(137, 88, 6)`
light, `rgb(249, 184, 78)` dark, in every cell.

**All three required guardrails are written**, in `tokens.css`, `tokens.md` and
`design-sheet.md` alike: status-family naming (`--status-warning-raw` +
`--color-status-warning` + a `[data-theme='dark']` override of the role),
**foreground role only, never a surface fill**, and **warning is not error**
(`Danger #F26666` named beside it).

## Motion and the live region

Measured in the browser, not read off the CSS:

- entrance `staleness-banner-in 0.35s cubic-bezier(0.16, 1, 0.3, 1) both` on the
  bar; pulse `staleness-glyph-pulse 4s` on `.staleness-banner__glyph` **only**.
- `animationName: none` on the title, the body and the timestamp — **no marquee
  and nothing else crept onto text**.
- under `reducedMotion: 'reduce'`, both resolve to `animationName: none` at both
  viewports, and the bar sits at `opacity: 1`. Verified live, both viewports.
- the `role="status"` / `aria-live="polite"` region is created by the `@if` with
  its text already in place, and no animation rewrites it. **The round-1 caveat
  holds.** (See 🟢 10 for the residual.)

## The banner geometry — measured, string by string

Every cell: body **1 line**, title **1 line**, timestamp **1 line**, no scroll,
pill absent, wording follows occupancy and never crosses hosts.

| Cell | bar box | body w | chrome | left edge |
|---|---|---|---|---|
| 1024 shell **de** (`stale.banner.body`) | `x458.8 y57.6 w539.6 h59.9` | 488 | 51.6 | **458.8** |
| 1024 shell en | `x500.8 w497.6` | 446 | 51.6 | 500.8 |
| 1024 standby de (`bodyStandby`) | `x461 w538` | 486 | 51.6 | 461 |
| **1024 standby en — the binding string** | `x445.8 w552.6` | 501 | 51.6 | **445.8** |
| 1280 shell **de** | `x569 y71.6 w679 h74.4` | 615 | 64.0 | **569** |
| 1280 shell en | `x627 w621` | 557 | 64.0 | 627 |
| 1280 standby de | `x573 w675` | 611 | 64.0 | 573 |
| **1280 standby en — the binding string** | `x554 w694` | 630 | 64.0 | **554** |

**The chrome trim is confirmed arithmetically against my own round-1 figure.**
I required 567 / 712 px for the English standby string with the old chrome
(66 / 82 px); it now measures **553 / 694 px** with chrome at **51.6 / 64.0 px**.
567 − 553 = 14 = 66 − 51.6, and 712 − 694 = 18 = 82 − 64. `dev`'s numbers being
*smaller* than my requirement figure is fully explained by the trim and by
nothing else — **the English standby string was not shortened**, which I
confirmed in `en.ts`: it is still *"…Please inform the property manager."*

The title row is never the binding line: its width equals the body's in all 8
cells (488/488, 446/446, 615/615, 557/557), so `connection.md`'s *"the body is
always the widest line"* is correct.

The `max-width` bound resolves to **860.8 px** at 1024 (leftmost possible edge
x137.6). The widest bar is 552.6 px, so there are ~308 px of slack — the bar
cannot reach the sidebar, which stays fully visible and navigable in every
render I looked at. Standby clearance to the centred block: **230 px / 224 px**,
matching `connection.md` exactly.

## Visual check (`code-review` step 0) — performed

Screenshots read, not just measured, at 1024×768 and 1280×832, both themes, both
languages, both hosts. **The render is right and it is better than the panel it
replaced**: a flat 9 : 1 bar in the empty upper-right, one sentence, timestamp
right-aligned on the title row, the `WifiSlash` glyph legible-but-calm in the
warning colour on both meshes, content rendering unobstructed behind it on both
hosts, the sidebar untouched. *"The pill grown into a panel"* genuinely no
longer describes it, and the documents are right to retire the phrase.

## Findings

| # | Severity | Category | File | Finding | Fix |
|---|----------|----------|------|---------|-----|
| 1 | 🟠 | Instrument | `frontend/tools/measure-states.mjs:391` | **The round's new heading-column guard checks the wrong element, and now reports 8 false COLLISIONs on a clean tree.** It tests `banner.x < box('.home-content__screen').right`. `.home-content__screen` is the whole left flex item — measured **x137.6…793.6** at 1024 and **x172…992** at 1280, because its width is set by the four-card row at its bottom, not by the heading. The bar at x458.8 / x569 is therefore always "inside" it, so `measure-states.mjs` emits `COLLISION: banner x Home's heading column` in **all 8 shell cells** of a correct build. Two consequences: the instrument cries wolf on every run (the third recorded instance of this defect class — Part A's 🟢 12, this round's 🟡 6), and **the 9 px margin the fix round set out to protect is not actually guarded**, because a real regression is indistinguishable from the standing noise. | Test the headline's own bound: `.heading-block` (`width: var(--size-heading)`, measured x137.6…**449.6** / x172…**562**, constant in every cell and for every name), or better the headline's `Range.getClientRects()` lines. Re-run: the 8 findings should go to 0. |
| 2 | 🟠 | Design fidelity | `docs/design/screens-inhouse/connection.md` → "Where the bar lands"; `02-design.md` §6.3 | **Both documents state Home's column geometry, and both numbers are wrong.** They give `.home-content__screen` = x138…450 / x172…562 and `.home-content__widgets` = **x475…998 / x594…1248**. Measured live: `.home-content__screen` = **x137.6…793.6 / x172…992**, `.home-content__widgets` = **x819.2…998.4 / x1024…1248** — only **179 / 224 px** wide, not 523 / 654. The x450 / x562 figures are real but they belong to **`.heading-block`**, not to `.home-content__screen`. So the load-bearing sentence — *"The bar overlaps Home's empty widget column and, for the longest strings, the gutter beside it"* — is false: the bar lies **almost entirely over `.home-content__screen`**, in its deliberately empty middle band (`space-between`: heading top, cards bottom). Nothing renders wrongly; the as-built record is wrong, and `docs/design/**` is the as-built record. (Root cause is traceable: x475 = 449.6 + `--space-7`, i.e. the gap was measured from the *heading block* as if it were the flex item. The figures are correct only for a 0-card Home.) | Correct both tables to the measured values and rename the element: the headline's bound is `.heading-block` at x450 / x562 (fixed by `--size-heading`, independent of the card count), the widget column is x819 / x1024. Restate the anchor as "the empty band between Home's heading and its card row", which is what it actually is and is still a good reason for the anchor. |
| 3 | 🟠 | Owner decision input | `docs/features/guest-payload-consolidation/handoff.md` → "Open for the owner" | **A question is queued for the owner on a figure that is wrong by ~20×.** It asks him to rule on *"the bar reaching **16 / 25 px** past the widget column's left edge, into the gutter"*. Measured, the bar's left edge is **360 px** past the widget column's left edge at 1024 (458.8 vs 819.2) and **455 px** at 1280 (569 vs 1024). The alternative the note offers him — *"bound the bar at the widget column and let the longest strings take two lines"* — would mean bounding a 553 px bar at **179 px**, which is not a one-line `max-width` change and not two lines but five or six. He cannot answer this as written. | Re-pose the question against the measured numbers, or withdraw it: on the corrected geometry the bar sits in an empty band and clears both the heading block and the card row, so there may be nothing left to decide. **Not mine to decide** — measured and handed back. |
| 4 | 🟡 | Design fidelity | `connection.md`, `02-design.md` §6.3 | **"≥ 9 px / ≥ 7 px for *any* name" is falsified by an unbroken display name.** Both documents say the headline *"wraps inside its own column, so its lines can never pass x450 / x562"*. `.heading-block` sets `width` but nothing breaks a long word — no `overflow-wrap`, no `word-break`, no `overflow: hidden`. With a 37-character unbroken name first in the manager-authored template, the headline renders **one line to x878.6 (1024) / x1098 (1280)** — 429 / 536 px past its own box — and passes **under the bar**; I have the screenshot. The overflow itself is pre-existing and untouched by this round, but the round closes round 1's collision finding on the strength of the absolute wording. For every name that wraps the claim holds exactly: I measured 9.2 / 7.0 px to the box and **32.2 / 38.0 px with a 31-character name**, reproducing `dev`'s figures to the decimal. | Either make the claim true — `overflow-wrap: anywhere` on `.heading-block__headline`, one line, and it fixes a pre-existing overflow as well — or soften both documents to "any name that wraps within its column". Prefer the first; the second leaves a live edge undefended. |
| 5 | 🟡 | Plan hygiene | `docs/features/index.md:19` | **The registry row still describes the pre-redesign banner.** It says the banner is *"the pill's own anchor, grown into an overlay panel"*, and carries *"**One open finding for review:** `connection.md` asks for a banner body of at most two lines, and the owner-approved copy wraps to **three** at the specified `min(22rem, 40vw)`"* — a clamp that no longer exists, a line count that is now 1, and a finding that is closed. It also claims *"40/40 cells NO-SCROLL OK … with no collision"*, which the instrument no longer reports (finding 1). This is the clearest thing the crash left half-applied: the fix round rewrote both design documents and its own `handoff.md`, but not the registry. | Rewrite the row's status against the redesign, and the verification sentence against a re-run instrument once finding 1 is fixed. |
| 6 | 🟡 | Docs | `staleness-banner.component.ts:17`, `shell.component.html:43` | **Two code comments still call it a panel**, after `connection.md` explicitly retires the phrase (*"It is deliberately not 'the pill grown into a panel' any more"*). The component's own class doc opens *"The pill grown into a panel, in the pill's own anchor"*, and the shell template says *"the same anchor, grown into a panel"*. Both cite the spec they now contradict. The sibling CSS comment was updated correctly (*"grown into a BAR"*), which is what makes these two read as missed rather than deliberate. | Two one-line edits, to match the CSS comment's wording. |
| 7 | 🟢 | Record accuracy | `connection.md` → "Where the bar lands" | The geometry is said to be measured over *"24 cells; … plus a long interpolated guest name"*. The checked-in instrument runs **16** banner cells and drives no display name at all (`grep -n displayName tools/measure-states.mjs` reaches only the stay-restore payloads). The extra 8 were evidently an ad-hoc run, so the headline figure in the spec is not reproducible from the repo. The structural argument does not depend on it. | Either say "16 instrument cells plus an ad-hoc long-name check", or add the name axis to the instrument — the latter would also give finding 4 a ratchet. |
| 8 | 🟢 | Style | `staleness-banner.component.css:22` | The `max-width` subtracts `--size-sidebar` on **both** hosts, including standby, which has no sidebar. Harmless — the bound is 860.8 px at 1024 against a widest bar of 552.6 px — and the comment discloses it. Noting only that the bound is therefore ~80 / 100 px tighter than "the content area's left edge" on standby, which is what the comment and `connection.md` both call it. | None needed. |
| 9 | 🟢 | Record accuracy | `docs/handoff.md` → the warning-colour table | The table gives dark contrast as **5.66**; `tokens.css`, `tokens.md`, `design-sheet.md` and `connection.md` all give **5.63**. Both are the same measurement — 5.664 unrounded, 5.634 with the backdrop rounded to 8-bit sRGB. **`dev`'s 5.63 is the more faithful figure** (a browser composites in 8-bit), so the four documents are right and the handoff's is the stale one. | Correct the handoff figure when it is next touched, or leave it — it changes nothing. |
| 10 | 🟢 | Accessibility | `staleness-banner.component.html:2` | The `role="status"` region is inserted by the `@if` **with its text already present**. Most screen readers only announce mutations to a live region that was already in the accessibility tree, so this one may well not be announced at all. Correct as far as the round-1 caveat goes (no animation rewrites the text mid-motion), and out of scope for a kiosk with no assistive tech in the threat model — recorded so it is a known property rather than an assumption. | None now. If announcement ever matters, render the region empty and fill it on the next tick. |

## What the crash left half-applied

**Nothing in the code.** I audited for it specifically: no conflict markers
anywhere, no orphaned imports, no half-written test, both suites green, the
generated client untouched, and every one of round 1's thirteen findings landed
in full — including the two I could only settle by mutating the source myself.
The fix round's own `handoff.md` is complete and coherent, which is unusual for
a crashed agent and is good evidence the work itself finished.

**What did not land is the paperwork after the design documents**:
`docs/features/index.md` (finding 5) still describes the banner the round
replaced. That is exactly the shape of an interruption — the last file in the
sequence. Findings 1–3 are a different thing and would have been there with or
without the crash: one wrong CSS selector, propagated into two design documents
and one owner-facing question.

## Requirement coverage

REQ-941 … REQ-952 all remain met; nothing in the fix round touched a
requirement's behaviour. REQ-947 and REQ-951, which round 1 marked ⚠️ ✅ for
missing protection, are now **✅ outright** — both probed. REQ-952's geometry is
met as built at all 24 of my cells and all 16 of the instrument's.

## Escalation

None. No architecture, security, concurrency, integration or performance
surface is touched by the redesign — it is CSS, two tokens, a template row and
an instrument check.

## Next action

Fix 1–6, re-run `measure-states.mjs` and expect **0 findings**, then commit.
Findings 3 and 4 carry a question and a choice respectively that are the
owner's, not a reviewer's.

---

# Review — Round 3 (verification of fix round 2, and the 168-cell grid round 2 asked for)

**Result: PASS ✅ — 🔴 0 · 🟠 0 · 🟡 1 · 🟢 6.**

Run 2026-08-28 (00:0x–00:5x) by `reviewer` on
`refactor/bce-subpackages-and-boundary-security`. Nothing committed, nothing
staged, no branch switched. **Two things were changed by this review and both
are declared below**: one mutation probe (`heading-block.component.css`,
restored and `diff`-verified byte-identical, md5 `88849563…`) and one trivial
numeric correction to three documents (finding 1), which is the only fix this
round made rather than reported.

## The blocker round 2 raised: `tools/measure.mjs`, the full 168-cell grid

Round 2 warned — after its report, so the fix round never carried it — that
`overflow-wrap: anywhere` changes the headline's `min-content` contribution and
therefore intrinsic sizing on **every** Home render, not only the pathological
one, and that Home's baselines are **already committed** in Part A (`14c005e`).
That check had not been run. It has now.

```
node tools/measure.mjs http://localhost:4200      (dev backend + ng serve, warm)
168 route x theme x language x viewport combinations measured,
0 overflowing, 0 truncated strings.
0 finding(s)
```

**Home's two signed-off baselines, stated explicitly — unmoved in all four
1280×832 cells:**

```
baseline check (1280x832): heading.y 88 vs 88, cardRow.bottom 744 vs 744
```

and at 1024×768, in all four cells: `heading {x138 y70 w312 right450}`,
`cardRow {x138 y541 w656 right794 bottom698}`, `widgets w=179`, clearances
`bar.x − cardRow.right = 40` and `heading.y − bar.bottom = 16`. PASS 3
(dropdown), PASS 3B (overflow popover: panel 376×160 / 470×200, every tile
above `--size-touch-min`, document never scrolls) and PASS 4 (composites, band
scan 7.66–8.71 light / 11.80–12.39 dark) are all clean.

**And the property provably does nothing to a render that already wraps.** I
compared the headline's own `Range.getClientRects()` extent across the eight
`banner-shell` cells with and without the CSS line, seed copy:
`{lines:3,right:391}`, `{lines:3,right:446}`, `{lines:3,right:491}`,
`{lines:3,right:558}` — **identical, character for character, in both trees**.
The 37-character-name cells also leave Home's card row exactly where it was
(`y541…698` / `y548…744`), so even five headline lines do not move the
baselines. The risk round 2 named is closed by measurement, not by argument.

*(The first attempt at this grid died the way Part A's review predicted:
`applyPreferences`'s `page.goto` hit the default 30 s timeout against a cold
`ng serve` — `/aktivitaeten` alone needed 13 s to compile — and the uncaught
rejection took all 168 cells with it. See 🟢 4.)*

## My own runs

| # | Command | Result |
|---|---|---|
| V1 | `node tools/measure.mjs http://localhost:4200` (warm) | **168/168 · 0 overflowing · 0 truncated · 0 findings**; Home baselines 88 / 744 unmoved |
| V2 | `GXP_SMOKE_ADMIN_PW=… node tools/measure-states.mjs http://localhost:4200` | **44/44 cells `NO-SCROLL OK`, 0 findings** — all 8 blocks ran, none skipped |
| V3 | V2 again with `overflow-wrap: anywhere` deleted (mutation probe) | **8 findings, in exactly the 4 `banner-longname` cells**, 44/44 still no-scroll |
| V4 | Own probe — rendered colour, motion, live region, reduced-motion, screenshots | below |
| F1 | `ng test --watch=false` | **313 passed / 313, 34 files**, 19.0 s |
| F2 | `ng build` | clean — `main` 460.30 kB, `styles` 9.27 kB, initial total 469.58 kB |
| B0 | Backend — **not re-run, deliberately** | see below |

**V2's 44 cells, by block:** PAIRING 4, CONNECTION 4, STALE-pill 4,
BANNER-shell 8, BANNER-longname 4, BANNER-standby 8, STANDBY 8, ARTWORK 4 =
**44**, of which **20 are banner cells**. Every figure the fix round put in the
documents reproduces from my run: bar `x459 y58 w540 h60` / `x569 y72 w679 h74`
in the longest German shell cell, `x446 w553` / `x554 w694` in the binding
English standby cell, body/title/timestamp `1/1/1` in all 20, `pillStillUp`
false in all 20, standby clearance 230 / 224 px, card-row clearance 424 / 402 px.

**V3, the ratchet, verified by my own hand.** With the one CSS line deleted and
nothing else touched, the instrument printed exactly:

```
COLLISION: banner x Home's headline text (reaches x935)  at banner-longname 1024x768 light de
OVERFLOW:  Home's headline runs past its own column (x935 > x450)   … light de
COLLISION: banner x Home's headline text (reaches x953)  … 1024x768 light en
OVERFLOW:  … (x953 > x450)                               … light en
COLLISION: banner x Home's headline text (reaches x1173) … 1280x832 light de
OVERFLOW:  … (x1173 > x562)                              … light de
COLLISION: banner x Home's headline text (reaches x1195) … 1280x832 light en
OVERFLOW:  … (x1195 > x562)                              … light en
```

Eight, in exactly those four cells, and **no other cell moved**. With the line
present the same name takes five lines to x450 / x560 and the run is clean. The
ratchet bites, and it bites only where it should. `.heading-block` is guarded
at x450 / x562 and the headline's line rects tighter still; `.home-content__screen`
(x138…794 / x172…992) and `.home-content__widgets` (x819…998 / x1024…1248) are
logged and **not** guarded, so the corrected document figures stay reproducible
from a run — round 2's finding 1 is closed exactly as asked.

## Visual check (`code-review` step 0) — performed

Screenshots at 1024×768 and 1280×832, both themes, banner up, plus the same
four with a 37-character unbroken name injected into the headline node (which
reproduced the instrument's geometry to the decimal: five lines, right
**449.59** / **560.00**).

**Read, not just measured.** The bar sits in the empty upper-right band, the
sidebar is untouched and fully legible, the card row is where it always was,
and the headline's five lines stack inside their column and stop short of the
bar. `anywhere` breaks the name mid-token without a hyphen — `Konstanzevonu /
ndzuhohenzoller / nsteinaue!` — which is ugly and is the correct trade: the
alternative is the same name running to x935 straight under the bar, which is
what the tree did before this round. Nothing about the ordinary render changed.

Rendered values, live, not read off the CSS: glyph `rgb(137, 88, 6)` light /
`rgb(249, 184, 78)` dark, matching `--color-status-warning` in both themes;
`animationName` = the entrance on the bar and the pulse on the glyph **only**,
`none` on title and body; under `reducedMotion: 'reduce'` both resolve to
`none` with the bar at `opacity: 1`; `role="status"` / `aria-live="polite"`
present; `.staleness-pill` absent.

## Backend — not re-run, and why

`git diff --stat` and file mtimes both say fix round 2 touched **nothing** under
`backend/`, `api/` or `packages/`: the newest backend file is
`GuestContentVersion.java` at **22:32**, which is round 2's own mutation-probe
restore, and round 2's `./mvnw -B verify` (**441 run · 0 failures**, 2
environmental `@TestProfile` Testcontainers startup errors) ran after it. A
re-run could only re-measure this box's Testcontainers luck. Two things were
gained for free instead:

- **The generated client regenerates byte-identically, a third time.** My
  `quarkus:dev` boot ran `generate-code` over `packages/api-client/`; every file
  was rewritten (mtimes 23:44) and `git status` on `packages/` is unchanged.
- **The backend ran live for an hour** under three instrument runs — guest
  content with `If-None-Match`, manager login, stays `PUT`/`DELETE`/`POST`,
  asset upload and delete, property `PUT` — with five transient errors, all one
  200 ms burst, all the same `Acquisition timeout while waiting for new
  connection` mode this box shows in tests, and all absorbed by the frontend as
  the ordinary error transition (🟢 6).

## Findings

| # | Severity | Category | File | Finding | Fix |
|---|----------|----------|------|---------|-----|
| 1 | 🟡 | Design fidelity (arithmetic) | `connection.md` → "Where the bar lands, measured"; `02-design.md` §6.3; `handoff.md` → "Where it actually lands" | **The corrected decomposition is itself wrong at 1024, and does not sum.** All three say the bar puts *"360 px over the empty band, 25 over the gutter, 179 over the widget column"* of a 540 px bar — but 360 + 25 + 179 = **564**, and the band the sentence names (x459…794, its own parenthetical) is **335 px**: 793.6 − 458.8 = 334.8. **360 is a different measurement** — the distance from the bar's left edge to the *widget column's* left edge (819.2 − 458.8), which already contains the 25 px gutter. It is the right number where `index.md` and the withdrawal note use it and the wrong one here. The 1280 row is exactly right (423 + 32 + 224 = 679). Measured by me: bar x458.8 w539.6, `.home-content__screen` right 793.6, widget column x819.2 w179.2 → **335 + 25 + 179 = 539**. | **Fixed by this review** — the one class of edit a reviewer may make: three numerals, `360` → `335`, plus a one-sentence note in `connection.md` recording where 360 came from so the next reader does not re-derive it. Nothing else in any of the three files was touched. |
| 2 | 🟡 | Instrument | `measure-states.mjs` — the long-name block's `finally` | **The display-name restore is unchecked, and its log line claims a restore that may not have happened.** `await putStay(active)` ignores the response and then prints `(display name restored to "…")` unconditionally. Every other manager call in the round's new code checks `.ok` and throws (`managerToken`, the rename, the artwork upload); the restore — the one call whose failure outlives the run — does not. A 4xx there leaves the dev property with a 37-character guest name in every later run, on the kiosk, and in the next reviewer's screenshots, while the log says it was put back. The two `POST /api/manager/stays` recreates carry the same shape (`(stay recreated)`), pre-existing; this round adds a third instance rather than the pattern. **Not a defect in this run**: I verified the end state through the manager API — stay `Anna & Tom`, `status: BOOKED`, property artwork null. | Check the response and `note('INSTRUMENT', …)` on failure, in all three restores. `guarded()` already makes a noted failure impossible to mistake for a clean run — the restore just has to reach it. |
| 3 | 🟢 | Record accuracy | `handoff.md` → "Style note for whoever reviews the instrument diff" | The claim *"reverted line by line, so the diff carries no drive-by reformatting"* is **one line off**. I mechanically compared all 24 hunks of `git diff -U0 measure-states.mjs`, normalising whitespace: exactly one is a pure reflow — `@@ -249 +353,2 @@`, the STALE-pill `if (heading && …) note('COLLISION', …)` line broken across two, in a block the round did not otherwise touch. Semantically identical, and it moves the file *toward* Prettier-clean. Only the sentence is wrong; the other 23 hunks are intended changes. | Leave the line; correct the sentence if the note is ever rewritten. |
| 4 | 🟢 | Instrument (pre-existing, third occurrence) | `tools/measure.mjs` `applyPreferences` | **The 168-cell grid still dies whole on one `page.goto` timeout.** Part A's review raised this as its 🟢 12 and it is unfixed; `measure-states.mjs` gained `guarded()` in this round, `measure.mjs` did not. It cost me the first grid run tonight against a cold `ng serve` (30 s default timeout; `/aktivitaeten` alone took 13 s to compile). The warm re-run was clean. Out of Part B's scope — recorded so the next `measure.mjs` touch closes it, and so the next reviewer warms the routes first. | `try/catch` → `note()` and skip the cell, or raise that one `goto` timeout. |
| 5 | 🟢 | Record accuracy | `connection.md`, `02-design.md`, `index.md` | *"the same name renders one line to x935 / x1173"* is the **German** pair. The English cells are worse: **x953 / x1195**. The claim's point — it passes under the bar — holds either way, and the four cells are all in the instrument now. | Say "x935…953 / x1173…1195", or nothing. |
| 6 | 🟢 | Environment | — | Five `500`s on `/api/guest/content` inside one 200 ms burst at the very start of the grid run, then nothing for the rest of the hour: `GenericJDBCException: Unable to acquire JDBC Connection [Acquisition timeout while waiting for new connection]`. Same mode as this box's `@TestProfile` boot failures. The frontend treated them as the ordinary error transition (cache → `STALE`), the grid still measured 168/168 clean, and no cell was degraded. Recorded as a property of the box, not of Part B. | None. |
| 7 | 🟢 | Judgement | — | **`ng test`'s "Worker exited unexpectedly" is resource starvation and nothing else, and I reproduced it without `ng serve`.** With the Quarkus dev JVM and its Testcontainers Postgres up: 31/34 files, **264 passed, 0 assertion failures**, and three forks that failed to *start* — `[vitest-pool-runner]: Timeout waiting for worker to respond` for `settings.component.spec.ts`, `app.routes.spec.ts`, `sidebar.component.spec.ts`. With everything else stopped: **34/34, 313/313 in 19 s**. `dev`'s reading is right in substance; the trigger is any concurrent JVM or dev server on this 4-core box, not `ng serve` specifically. | Run the suite alone. |

## The two declared deviations — my judgement

**1. The Prettier drive-by, reverted.** *Accepted, with 🟢 3.* The revert is
complete but for one semantically-identical line break. I checked this
mechanically rather than by eye, and the file's existing style survives: 23 of
24 hunks are intended changes, the long template-literal log lines are
untouched, and no reformatting rides along in the commit. Reverting a
`prettier --write` by hand on a file that is *not* Prettier-clean at HEAD was
the right call — the alternative was ~50 lines of noise in a review-critical
diff — and disclosing it was better still.

**2. The skipped ARTWORK block.** *Healthy, and it is the guard working — but
it is a real flake and it should be tracked, not filed as normal.* The
mechanism is sound and I read it in the source: `guarded()` routes the failure
into `note('INSTRUMENT', …)`, `note()` pushes into `findings`, and the summary
prints the count — so a skipped block **cannot** hide inside a "0 findings"
run, which is the property that matters. The block's own `finally` restores
before the failure surfaces, and the other 40 cells still ran. In my run
ARTWORK completed normally at both viewports and both themes, so it is not
systemic. What it is: a 60 s `waitForSelector('.hero-artwork__img')` guarding
an upload, a server-side downscale of a 1550×999 PNG and a full Home render, on
a Pi that also runs ten unrelated containers. That is the one timeout in the
file with real work behind it and the one most likely to expire again. If it
recurs, raise that timeout rather than accept the skip: ARTWORK exists because
a horizontally scrolling Home once shipped past 72 clean cells (review 🔴-1),
and a run that skips it is measuring 40 cells with the interesting one missing.

## The three 🟠s of round 2, checked one by one

| Round 2 | Verdict |
|---|---|
| 🟠 1 — the instrument guarded the wrong element, 8 false COLLISIONs | **Closed.** `.heading-block` + the headline's own line rects are guarded; the two flex items are logged only. My run: 44/44, 0 findings. The false positives are gone and the real one is caught (V3). |
| 🟠 2 — both design documents' geometry wrong | **Closed in substance**, with one residual arithmetic slip at 1024 which this review fixed (finding 1). Every other figure in both tables reproduces from my run to the pixel. |
| 🟠 3 — an owner question resting on a ~20× wrong figure | **Closed correctly, by withdrawal.** The withdrawal note states the corrected 360 / 455 px (that use is right), says the alternative would have meant bounding a 553 px bar at 179 px, and records that the question was *answered by measurement, not by the owner* — which is exactly what happened. Nothing is queued for him that he cannot answer. |

**On the two "superseded" markers rather than rewrites (round 2 finding 3's
tail):** *honest, and the better choice.* Both passages are dated, say which
half is wrong and which half stands, and point at the corrected figures. A
reader who greps `523` lands on the marker, not on the stale claim. Rewriting
them would have deleted the evidence that a measurement error existed and was
caught — and this feature's paper trail is now the main artefact defending
against the same mistake a fourth time.

## Requirement coverage

REQ-941 … REQ-952 all still met; fix round 2 touched no requirement's
behaviour. Re-verified rather than assumed this round: REQ-947's
preference-write term (round 2's probe stands — no backend file has changed
since it), **REQ-951's compile-time ratchet is present and correct**
(`type PayloadKey = keyof Required<GuestContent>` + `Record<PayloadKey, true>`
+ `readonly PayloadKey[]` in `guest-data.service.spec.ts` — my earlier grep for
`keyof GuestContent` missed it because the form is stronger than round 2 quoted),
REQ-952's geometry at 44 instrument cells plus 8 screenshots of my own, and
REQ-941's byte-identical regeneration a third time.

Nothing else regressed: the three warning-colour guardrails are in `tokens.css`,
`tokens.md` and `design-sheet.md` alike (status-family naming, foreground-only,
warning-is-not-error), the `frontend/CLAUDE.md` Tests sentence is the
owner-approved text **verbatim**, `specification.md`'s decision 7 and endpoint
table carry their supersession markers, `index.md:19` describes the bar and the
44/44 run, and both code comments say **BAR**. `--color-status-warning` is
referenced in exactly one place in `src/` — the glyph's `color` — so the
foreground-only rule is not merely written but true.

**Not written, as instructed:** the proposed `frontend/CLAUDE.md` → Layout
entry about `overflow-wrap` on manager-supplied strings (queued for the owner
in `docs/handoff.md`) and the `getSectionRoute()` retirement. I duplicated
neither. No constitution amendment, no new dependency, no approval gate opened.

## Escalation

None. CSS, an instrument, and documents.

## Next action

**Commit — the owner commits.** Finding 1 is already fixed; finding 2 and 🟢 4
are instrument hygiene for the next touch, not commit blockers. After the
commit: the `getSectionRoute()` retirement round, and the owner's ruling on the
queued `frontend/CLAUDE.md` → Layout entry.
