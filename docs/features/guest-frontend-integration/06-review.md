# Review: Guest Frontend Integration
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md) | **Handoff:** [handoff.md](handoff.md)

**Result: round 1 — NEEDS CHANGES ❌** (🔴 1 · 🟠 2 · 🟡 8 (1 fixed in-review) ·
🟢 4) → **round 2 — PASS ✅** (2026-08-21: every finding resolved and
independently re-verified; 🔴 0 · 🟠 0 open in code · 🟡 0 · 🟢 5 new info
items). One approval-gated doc item (🟠-2) stays **parked for the user** — it
is doc-only and nobody but the user may action it. Reviewed against
constitution **v1.5.1**, `frontend/CLAUDE.md`, `backend/CLAUDE.md` and the
three in-repo screen specs.

> **Read round 2 first:** [Round 2 — fix-round re-review](#round-2--fix-round-re-review-2026-08-21)
> at the end of this file carries the current verdict. Everything before it is
> the round-1 record, kept verbatim as the trace of what was found and why.

> **Review scope note.** Unlike previous features the work was already
> committed — `ea28870` "Commit to move files" (103 files, +7482/−1071),
> made by the user to move the repo off a failing disk. That single commit
> *is* the feature; this review covers `git diff a6c5a91..HEAD`. The repo now
> lives at `/media/external_harddrive/Niclas/…/guest-expierience-platform`;
> everything below was executed there.

One finding blocks: **with a property artwork configured, Home scrolls
horizontally at both reference viewports and clips the fourth action card**
(🔴-1). It escaped all 72/72 measurement cells because the dev seed
configures no artwork — precisely the failure mode the `code-review` skill's
step-0 note exists for. Everything else in the feature is in very good shape:
the state machine, token path, cache and D7 serving all behaved exactly as
designed under independent probing.

---

## What I verified independently (not taken from the handoff)

| Claim | How I checked it | Result |
|---|---|---|
| Frontend suite | `npx ng test` at the new path | **31 files / 235 tests / 0 failures** ✅ |
| Frontend prod build | `npx ng build --configuration production` | green, 434 kB main / 104 kB transfer ✅ |
| REQ-505 token-free bundle | `grep -rl gxp-dev-device-token frontend/dist/` | **0 hits** ✅ |
| Offline principle | `grep -roE 'https?://…' dist/browser/` | only `w3.org` XML namespaces, Angular's `angular.dev` error URL, and the generated client's dead `http://localhost` default — and `BaseService` keeps `basePath: ''` because it tests `typeof … !== 'string'` (not falsiness), so that default is genuinely unreachable ✅ |
| Backend suite | `./mvnw clean test` against a fresh throwaway Postgres | **176/176, BUILD SUCCESS** ✅ |
| Contract untouched | `git diff packages/api-client api/openapi.yaml` after the generator ran in that build | **empty** — byte-identical regeneration ✅ |
| No new dependency | `git show ea28870 -- backend/app/pom.xml \| grep -c '<dependency>'` = 0; `package.json`/`package-lock.json`/`pom.xml` deps untouched in the diff | ✅ |
| REQ-504 build-time inertness | unpacked `target/quarkus-app/quarkus/generated-bytecode.jar`: 166 `*_Bean.class` entries, `SpaRoutingHandler_Bean`/`_ClientProxy`/`_Observer_install_*` present, **no `DevDataSeeder_Bean`/observer at all** | ✅ (see 🟡-6 for the doc wording) |
| REQ-527 both halves | `package` with `frontend/dist` present → jar carries `META-INF/resources/{index.html, main-*.js, styles-*.css, assets/fonts/**, favicon}`; `package -P '!bundle-guest-app'` → **0** `META-INF/resources` entries, green, no Node | ✅ |
| REQ-503 dev seed | `quarkus:dev` on a fresh DB → `GET /api/guest/session` with the dev token = 200, `OCCUPIED`, `Anna & Tom`, `#f45aa8`, 5 menu items, 4 cards; content compared line-by-line against the deleted `example-property.data.ts` (git history) | ✅ verbatim fidelity |
| REQ-502 no CORS | `grep -rin cors backend/` (java/properties/yaml) | **empty** ✅ |
| SPA fallback behaviour | live probes against a bundled instance (see 🟠-1 for the defect): `/einstellungen` → 200 app shell + `cache-control: no-cache`; `/info` → 200 shell; `/api/nonsense` → 404 API; `/q/health` → 404; `/manage/x` → 404; `/logo.png` → 404 | ✅ except the exact-prefix leak |
| REQ-514 language switch fetches nothing | browser network log across a real switch through the settings UI | **1 request total, the `PUT /api/guest/preferences`** ✅ |
| REQ-518 preference round-trip | same run: boot issued exactly `GET /api/guest/session` + `GET /api/guest/screens/home` (**no** preference GET); after the switch `GET /api/guest/session` reports `preference: {language: 'en'}` | ✅ D8 as designed |
| Kiosk storage inventory | live: `localStorage` = `gxp.device-token, gxp.cache.session, gxp.cache.home`; `sessionStorage` = **empty** | ✅ stand-in truly gone |
| REQ-517 vacancy | expired the stay in the DB → kiosk moved to `/standby` unaided, rendered server standby texts, **no guest-derived value**, `gxp.cache.home` **cleared**, no sidebar/nav (2 buttons = the status bar's own) | ✅ |
| REQ-519 staleness pill | loaded online, then reloaded with `/api` blocked → cache-rendered Home + pill at `{y:72, right:1248, h:28}` reading `Stand 07:56` — matches the spec's `--space-1` offset and the reserved-corner clearance | ✅ |
| REQ-520 connection screen | valid token + `/api` blocked + empty cache → `/connection`, no button, no status bar | ✅ |
| REQ-509 revocation path | seeded an invalid token → 401 → wiped → `/pairing` with a fresh grouped code | ✅ |
| REQ-522 no-scroll | measured `scrollWidth/clientWidth`, `scrollHeight/clientHeight` at 1280×832 on `/`, `/einstellungen`, `/pairing`, `/connection`, `/standby` (light+dark) and at 1024×768 | ✅ **except** the artwork case (🔴-1) |
| Home baseline | `.heading-block` y **88**, `.home-content__cards` bottom **744**, cards x **172** at 1280×832 with server-fed content | ✅ unchanged |
| Logging surface | `grep -rn 'console\.' frontend/src` → only the CLI's own `main.ts` bootstrap catch; no German literal in any template | ✅ |

**Screenshots read (step 0 of `code-review`, mandatory):** Home light/dark,
Settings, Pairing, Connection, Stale-pill, Standby light/dark, and Home
**with an artwork asset** — the last of which is the blocking finding. Home
was compared against `docs/design/assets/home-no-artwork.png`; the three new
screens against `docs/design/screens-inhouse/*.md`.

**Taken from the handoff, not re-run:** the compose/TLS smoke (T-29, 11/11 —
fresh `%prod` device count 0, dev token → 401, image `META-INF/resources`
grep 0, plain HTTP refused), the dark-theme contrast table (Δ0.02 vs
design-sheet §3.4), and the full 72-cell measure grid. I re-verified the
load-bearing subset of each locally (bundle contents, SPA behaviour, the
no-scroll measurements above, the pill geometry).

---

## Findings

| # | Sev | Category | File / evidence | Finding | Fix |
|---|-----|----------|-----------------|---------|-----|
| 🔴-1 | 🔴 Critical | Correctness / Visual fidelity / REQ-513+522 | `frontend/src/app/features/home/hero-artwork/hero-artwork.component.ts:31-52` + `features/home/home-content.component.html:3` | **Home scrolls horizontally when the property has artwork.** The new artwork variant renders `<img class="hero-artwork__img">` with `:host{display:contents}`, so the image becomes the **first flex item** of `.home-content` (a `row` with `gap: --space-7`) and pushes the whole content column right. Measured live with `property.artwork_asset_id` set: **1280×832 → `scrollWidth` 1312 > `clientWidth` 1280**; **1024×768 → 1050 > 1024**; heading x 172 → **492**, card row right edge **1312** — the fourth action card is clipped off-screen (screenshot captured). Violates constitution → Constraints ("nothing scrolls"), `frontend/CLAUDE.md` → Layout, and REQ-522. It also contradicts `docs/design/screens/home.md` → *Hero artwork (2:5)*, which specifies an **absolutely positioned bitmap (1550×999 at x793/y155) behind the layout**, not an inline column. Undetected because the dev seed sets no artwork, so all 72/72 measure cells and the dev/compose smokes rendered the no-artwork fallback, and jsdom asserts no geometry. | Make the artwork non-participating in the content flex row per the design (absolute/`background`-style layer behind `.home-content`, or an explicit reserved column that is part of the measured envelope), then **re-measure with an artwork asset present at both viewports** and extend `tools/measure.mjs`/`measure-states.mjs` with an artwork-present cell so the variant can never regress silently again. Record the chosen treatment in `docs/design/screens/home.md` (or a `screens-inhouse` deviation note) — today no spec describes an inline 18 rem artwork column. |
| 🟠-1 | 🟠 Major | Correctness / REQ-526 | `backend/app/src/main/java/de/gxp/web/SpaRoutingHandler.java:77-79` | **The SPA fallback claims the reserved prefixes' exact form.** The guard is `path.startsWith("/api/") \|\| startsWith("/q/") \|\| startsWith("/manage/")`, so the trailing-slash-less paths fall through to the rewrite. Probed live on a bundled instance with `Accept: text/html`: **`GET /manage` → 200 guest app shell**, `GET /api` → 200 shell, `GET /q` → 200 shell (each with `cache-control: no-cache`). REQ-526 requires the fallback to never rewrite these and promises the future manager app "slots in with zero rework"; a human typing `https://host/manage` will get the kiosk app. (Percent-encoded variants such as `/api%2Fx` behave the same way.) | Extract a `isReservedPath(String p)` returning `p.equals("/api") \|\| p.startsWith("/api/") \|\| p.equals("/q") \|\| p.startsWith("/q/") \|\| p.equals("/manage") \|\| p.startsWith("/manage/")`, and add three `@QuarkusTest` cases (`/api`, `/q`, `/manage` with `Accept: text/html` → not the fixture marker). |
| 🟠-2 | 🟠 Major | Conventions | `frontend/CLAUDE.md` → *Structure* (two bullets) vs. `app.routes.ts:75-99` and `features/home/action-card/action-card.component.ts:29` | **The binding nested `CLAUDE.md` now contradicts the shipped code in two places that dev's three proposed entries do not cover:** (a) *"Every route renders inside `ShellComponent`"* — `/pairing`, `/standby`, `/connection` deliberately render outside it (02-design §5, user-approved D1/D9); (b) *"A component that needs a route for a card resolves it with `getSectionRoute(targetSectionId)` instead of carrying a route of its own"* — `ActionCardComponent` now navigates the server-resolved `card().route` (REQ-511/515, correct). Left as-is, the file will mis-instruct the next session. | Doc-only, but **user-gated** (a nested-`CLAUDE.md` entry is an approval item). Exact proposed text is recorded under *Parked* below; nobody writes it until the user confirms. |
| 🟡-1 | 🟡 Minor | Correctness / REQ-509+512 | `frontend/src/app/app.component.ts:146-151` | **Branding is applied but never un-applied.** The effect writes `--brand-primary/-deep/-secondary` when branding is non-null and does nothing when it becomes null, so after a runtime 401 wipe (or a rejected wire brand) the previous property's primitives stay inline on `<html>`. The pairing screen is specified *"deliberately brandless"* and its status dot reads `--color-icon-active-start` → `--brand-primary`, so a revoked kiosk keeps painting the old brand until the next reload. (Not visible in my capture: the dev property's brand equals the token default.) | Add the `else` branch: `root.style.removeProperty('--brand-primary')` ×3, and assert it in `app.component.spec.ts` beside the existing "a null brand writes nothing" case. |
| 🟡-2 | 🟡 Minor | Correctness / REQ-509 | `frontend/src/app/core/guest/guest-data.service.ts:141-155` + `core/guest/guest-asset.service.ts:31-54` | **The 401 wipe does not clear the asset memo.** `handleUnauthorized()` clears token, both caches and every signal, but `GuestAssetService.urls` keeps the object URLs the now-revoked device fetched (logo/artwork blobs stay in memory, and a re-pair would re-serve them from the memo for the same asset ids). "A revoked device renders nothing it learned before" is not quite true. | Give `GuestAssetService` a `clear()` (revoke every URL, clear the map) and call it from `handleUnauthorized()`; unit-test it beside the existing revoke-on-destroy case. |
| 🟡-3 | 🟡 Minor | Correctness / REQ-507 | `frontend/src/app/features/pairing/pairing.service.ts:98-105` | **Pairing expiry compares the server's `expiresAt` against the kiosk clock** — the exact risk the PRD flags ("must use server-supplied `expiresAt` defensively"). A kiosk clock running ahead makes every poll take the proactive-renew branch: create → 3 s → renew → create …, until the backend's rate limit answers 429 and the screen sits in `cooldown`. Self-limiting, but it burns the global pairing rate limit and never shows a usable code. | Derive a local deadline once: `deadline = Date.now() + (Date.parse(expiresAt) - Date.parse(serverDateHeader ?? expiresAtIssuedNow))`, or simply drop the proactive branch and let the server's `404` drive renewal (it already does). |
| 🟡-4 | 🟡 Minor | Correctness / REQ-517 | `frontend/src/app/core/guest/guest-data.service.ts:170-195` | **A `409 room-vacant` on `screens/home` is treated as a generic fetch failure.** If the stay disappears between the session call and the home call of the same poll, the service goes STALE and keeps rendering the *previous guest's* home content (plus the staleness pill, which is untrue — the backend is reachable) until the next poll, ≤30 s later. Data-sparing wants that value gone immediately. | In `refresh()`/`bootstrap()`, treat `isStatus(error, 409)` from `fetchHome` as "vacant now": adopt the session with `home = null` (which already clears the home cache) instead of falling into the STALE branch. |
| 🟡-5 | 🟡 Minor | Testing / REQ-524 | `frontend/src/app/core/guest/guest-state-routing.service.ts` (no `.spec.ts`) | **The runtime state→route effect has no unit test.** The guards' initial arbitration is well covered (`app.routes.spec.ts`, 7 cases), but the effect that moves a *running* kiosk (401 → pairing, occupancy flip → standby, heal → back) and the `lastShellUrl` previous-route restore are covered only by dev's manual smoke. REQ-524 asks for a Vitest test per implemented behaviour. | Add `guest-state-routing.service.spec.ts` driving writable-signal doubles of `DeviceTokenStore`/`GuestDataService` against a `RouterTestingHarness`; assert the restore case (`/einstellungen` → standby → `/einstellungen`). |
| 🟡-6 | 🟡 Minor **(fixed in-review)** | Docs accuracy / REQ-504 | `backend/README.md:242-246`, `docs/system-context.md:229` | Both said the seeder "does not exist in the `%prod` (or `%test`) artifact at all". The **bean** doesn't (I verified: no `DevDataSeeder_Bean`/observer is generated while 166 other beans are) — but `de/gxp/devseed/DevDataSeeder.class`, its seed SQL and the `gxp-dev-device-token` literal *do* ship inside the jar, and the compose smoke's grep only covers `META-INF/resources`, so it never tested this. No security impact (the token is public by decision; safety comes from the hash's absence from a prod DB, which the smoke *does* assert). | **Fixed in-review:** both sentences now distinguish bean-absence from class-presence and point at the runtime assertions as the actual proof. |
| 🟡-7 | 🟡 Minor | Scope / dead code | `frontend/tools/.smoke-part1.mjs`, `.smoke-part2.mjs`, `.smoke-part3.mjs`, `.static-serve.mjs` (tracked in `ea28870`) | **Throwaway smoke drivers were committed.** The handoff calls them "throwaway (dot-prefixed, not part of the instrument set)", but the move-commit added all four (≈370 lines) to the repo. Either they are instruments (then they need a name, a header charter and a README line like `measure-states.mjs`) or they are scratch (then they should not be tracked). `.static-serve.mjs` is genuinely useful — it is how a **prod** bundle gets measured, and I used it for this review. | User's call at commit time: `git rm --cached frontend/tools/.smoke-part*.mjs` and keep/rename `.static-serve.mjs` as a documented instrument, or drop all four. |
| 🟡-8 | 🟡 Minor | Error handling (dev-only) | `backend/app/src/main/java/de/gxp/devseed/DevDataSeeder.java:112-116` | `defaultRoomId()` uses `getSingleResult()` on `select id from room where code = '1'`. A developer who deletes/renames that room through the manager API turns the next `quarkus:dev` start into a `NoResultException` thrown from a `StartupEvent` observer — the app will not boot, with a stack trace that points at seeding rather than at the cause. | `setMaxResults(1).getResultList()`; empty → return without seeding (the seed is convenience, never a hard dependency). |
| 🟢-1 | 🟢 Info | Records | `handoff.md` → deviation #3 | The recorded text says the seeded headline renders *"one line, no wrap"*. It renders **three lines** at 1280×832 (`Willkommen im Airbnb von Anna & Tom!`) — screenshot attached to this review's evidence. No truncation, no overflow, and the signed-off envelope is intact (heading block y 88, cards bottom 744), so the substance of the deviation holds; only the wording is wrong. | Correct the sentence when the deviations are ratified into 02-design. |
| 🟢-2 | 🟢 Info | Performance | `application.properties` (`quarkus.http.static-resources.caching-enabled=false`) | Measured on a bundled instance: static resources now carry **no `Cache-Control`, no `ETag`, no `Last-Modified`** at all — so the hashed `main-*.js` (104 kB transfer) is re-fetched on every kiosk reload, not just `index.html`. This is the documented trade-off and it is right for a LAN appliance; noting the measured shape so a future "why is there no ETag" question has an answer. | None. If it ever matters, re-enable caching and stamp `no-cache` on `/index.html` from a dedicated Vert.x route instead. |
| 🟢-3 | 🟢 Info | Security | `frontend/src/main.ts:6` | The Angular CLI's default `console.error(err)` on bootstrap rejection is the only `console.*` in `src/`. `GuestBootstrapService.run()` resolves on every failure path (no payload, no token can reach it), so this stays a non-issue — worth keeping in mind if the initializer ever starts rejecting. | None. |
| 🟢-4 | 🟢 Info | Design | `frontend/src/app/features/pairing/pairing.component.css:71-78` (deviation #4) | Using `--color-icon-active-start` where the spec names a non-existent `--color-brand-primary` is the right call (components must not read the `--brand-primary` primitive) — but it makes the pairing dot **brand-coloured**, which is what 🟡-1 makes visible after a revocation. Worth one sentence in `screens-inhouse/pairing.md` when that file is next touched. | None (covered by 🟡-1's fix). |

### Reproducing 🔴-1 (2 minutes, no manager UI needed)

```bash
# dev backend on a throwaway Postgres + the prod bundle behind an /api proxy
cd backend/app && ./mvnw quarkus:dev -Dgxp.admin.password=… &
cd frontend && npx ng build && node tools/.static-serve.mjs 4300 &

# give the property an artwork asset (any PNG; the seed configures none)
docker cp docs/design/assets/home-no-artwork.png <pg>:/tmp/a.png
psql -c "insert into asset (mime_type, byte_size, width, height, sha256, kind, data)
         select 'image/png', length(d), 1600, 1040, 'x', 'ARTWORK', d
         from (select pg_read_binary_file('/tmp/a.png') as d) t"
psql -c "update property set artwork_asset_id = 1 where id = 1"
```

Then open `http://localhost:4300/` with `localStorage['gxp.device-token'] =
'gxp-dev-device-token'` at 1280×832 and read
`document.documentElement.scrollWidth` (**1312**) against `clientWidth`
(**1280**). Measured values at review:
`img {x:172, y:24, w:288, h:784}`, `.heading-block {x:492, y:88}`,
`.home-content__cards {x:492, bottom:744, right:1312}`; at 1024×768:
`1050 > 1024`, `img {x:138, y:19, w:230, h:730}`.

### Judgement on the 8 recorded deviations + the verify-at-implementation pins

| # | Deviation | Verdict |
|---|---|---|
| 1 | Seeder uses plain SQL instead of the owning Controls | **Accepted.** Every write path is `@RolesAllowed("admin")` and a `StartupEvent` observer has no identity; the `DeviceFixtures` precedent applies and the DB constraints (hex shape, scope uniqueness, FK) do enforce the invariants. |
| 2 | Local 3-line SHA-256 hex helper | **Accepted.** Both existing helpers are package-private; widening them would break "nothing else moves in `backend/`". Identical algorithm, cross-referenced in the comment. |
| 3 | `{guestName}` → `{stay.displayName}`, "Anna & Tom" | **Accepted** (see 🟢-1 for the inaccurate wrap claim). Content fidelity with the deleted static file is otherwise verbatim — I diffed them. |
| 4 | `--color-icon-active-start` instead of the spec's `--color-brand-primary` | **Accepted** (see 🟢-4). |
| 5 | `renewing` keeps the brand pulse, `cooldown` is muted | **Accepted** — reads correctly on screen. |
| 6 | `Configuration` factory provider instead of `provideApi()` | **Accepted and verified**: the credential fn is resolved per request (`provide-guest-api.spec.ts` + live bearer observed), and `basePath: ''` survives `BaseService`'s `typeof` check. |
| 7 | Mesh moved verbatim to `global.css .mesh-background` | **Accepted** — four surfaces now paint it; roles only, no primitive read; light/dark renders match. |
| 8 | `clear()` = client no-op | **Accepted as designed** (no UI path calls `reset()`); worth re-checking when a reset affordance appears. |
| (a) | Blob fetch vs. browser HTTP cache | **Confirmed** — one authorized GET per asset id per app lifetime (observed: a single `/api/guest/assets/1` per load). |
| (b) | Credential fn per request | **Confirmed** by spec and by the live bearer. |
| (c) | Body-less 202 through the proxy | **Confirmed** by dev's smoke; the poll path is unit-tested. |
| (d) | Route order + cache posture | **Confirmed** — order 20000 runs after REST/static; deep links carry `no-cache` (measured). The exclusion set is the 🟠-1 defect, not the order. |
| (e) | measure instrument vs. server-side preferences | **Accepted** — instrument-only; the underlying write is correct (I re-verified the PUT lands on the stay). |
| — | `If-None-Match` without a contract change | **Confirmed**: `git diff packages/api-client api/openapi.yaml` empty after a full regenerating build. The `defaultHeaders` set/call/reset is synchronous, so no interleaving is possible with the shared root `GuestService`. |
| — | `packages/node_modules` symlink | **Confirmed load-bearing** — it did not survive the filesystem move and had to be recreated before anything compiled. Strong evidence for the parked `frontend/CLAUDE.md` entry. |

---

## REQ coverage (REQ-501 … REQ-528)

| REQ | Status | Evidence |
|---|---|---|
| REQ-501 | ✅ | `@gxp/api-client` alias + `provideGuestApi()`; prod build + 235 tests green; client diff empty |
| REQ-502 | ✅ | `proxy.conf.json` wired in `angular.json`; `grep -rin cors backend/` empty |
| REQ-503 | ✅ | live `quarkus:dev` session 200/OCCUPIED with verbatim example content; idempotency test |
| REQ-504 | ✅ (docs corrected) | no `DevDataSeeder_Bean`/observer in the prod build; `%test` zero-devices assertion green in my 176/176 run; compose assertions from dev. See 🟡-6 |
| REQ-505 | ✅ | `grep dist/` = 0; `environment.ts` = `null`, replacement only on the `development` configuration |
| REQ-506 | ✅ | pairing screenshot (code `A46M HV8H`, grouped), claim path unit-tested, no-reload entry |
| REQ-507 | ⚠️ | all four states implemented and tested; **🟡-3** clock-skew caveat |
| REQ-508 | ✅ | `localStorage` key present after pairing; bearer observed on `/api/**` only |
| REQ-509 | ⚠️ | invalid token → wipe → `/pairing` verified live; **🟡-2** (asset memo) and **🟡-1** (brand) leave residue |
| REQ-510 | ✅ | `PropertyContentService` fed by `GuestDataService`; `example-property.data.ts` deleted; boundary validation in the mapper (11 spec cases) |
| REQ-511 | ✅ | headline/subtitle/paragraph rendered verbatim (screenshot); `{guestName}` gone; 0–4 cards |
| REQ-512 | ⚠️ | hex shape validated before any inline write (`mapBranding` + spec); **🟡-1** on the un-apply path |
| REQ-513 | ❌ | authorized blob fetch + object URLs + fallbacks are right, but the **artwork variant breaks the layout** — 🔴-1 |
| REQ-514 | ✅ | live: language switch issued exactly one request (the preference PUT) |
| REQ-515 | ✅ | 5 wire items + fixed settings entry rendered in server order (screenshot); client never re-derives enablement |
| REQ-516 | ✅ | `If-None-Match` sent and 304 handled (spec cases); 30 s cadence pinned in code |
| REQ-517 | ✅ | live vacancy flip to `/standby`, home cache cleared, no guest value. **🟡-4** covers the 409 race only |
| REQ-518 | ✅ | live: no preference GET at boot; PUT lands on the stay; `sessionStorage` empty; `PreferencesService` byte-identical (untouched in the diff) |
| REQ-519 | ✅ | live stale render + pill geometry `{y:72,right:1248,h:28}` |
| REQ-520 | ✅ | live connection screen + automatic backoff retry; a network flap never reaches `/pairing` (guard precedence tested) |
| REQ-521 | ✅ | three specs under `screens-inhouse/` with provenance banners; `design-sheet.md` 📋 rows; `--color-status-offline` row in `tokens.md`; no MCP request spent |
| REQ-522 | ❌ | all routes/states no-scroll at both viewports **except** artwork-present Home (🔴-1); Home baselines unchanged |
| REQ-523 | ✅ | 9 new keys in **both** catalogues (pairing×6, connection×2, `stale.asOf`); no literal in any template; no external runtime host in `dist/` |
| REQ-524 | ⚠️ | 235 tests incl. every new unit; **🟡-5** is the one behaviour without a spec |
| REQ-525 | ⚠️ | system-context/index/READMEs all updated; **🟠-2** is the remaining doc gap (user-gated) |
| REQ-526 | ⚠️ | serving + fallback work; **🟠-1** exact-prefix leak |
| REQ-527 | ✅ | verified both halves (bundle present / profile off) |
| REQ-528 | ✅ (dev evidence) | compose smoke 11/11 from the handoff; I re-verified the non-TLS equivalents locally |

**Orphans:** none — every changed file traces to a REQ; T-01…T-29 all map to
≥1 REQ (`04-analyze.md` table re-checked against the diff).

---

## ⏸ Parked user gates (nobody writes these until the user confirms)

**1. Dev's three proposed `frontend/CLAUDE.md` entries** (exact text in
[handoff.md](handoff.md) → *Proposed `frontend/CLAUDE.md` additions*):
wire-fed content layer / `@gxp/api-client` import rule; the
`packages/node_modules` symlink; `tools/measure-states.mjs`.
**Reviewer's note for the decision:** the symlink entry is now demonstrably
load-bearing — it is gitignored, it did **not** survive the filesystem move,
and nothing in `frontend/` compiled until it was recreated. That is the
strongest possible argument for writing it down.

**2. NEW (🟠-2) — two further `frontend/CLAUDE.md` edits the code already
requires.** Proposed exact text, for the same approval round:

> *Under **Structure**, replacing "Every route renders inside `ShellComponent`…":*
> Every in-app route renders inside `ShellComponent`; a section without a real
> design yet gets `SectionPlaceholderComponent`, never a standalone stub page.
> The three kiosk **state** screens — `/pairing`, `/standby`, `/connection` —
> are the deliberate exception: they render outside the shell (no sidebar, no
> chrome), are entered only by the state guards in `core/guest/`, and read no
> property content that the guards have not already guaranteed.

> *Under **Structure**, replacing "A component that needs a route for a card
> resolves it with `getSectionRoute(targetSectionId)`…":*
> Routes for menu-shaped things are resolved with
> `PropertyContentService.getSectionRoute(id)`. Action cards are not
> menu-shaped any more: they navigate the server-resolved `route` the wire
> delivers, because enablement/ceiling resolution lives on the server.

---

## Top 3 recommendations

1. **Fix 🔴-1 before anything else, and make the artwork case measurable.**
   The layout defect is one CSS/structure decision; the durable fix is the
   measurement cell, because the dev seed will keep hiding this variant
   otherwise. Consider seeding an artwork asset in `DevDataSeeder` so the dev
   kiosk renders the same shape a real property does.
2. **Close 🟠-1 with the exact-path exclusions + three tests** — three words of
   code, and it protects a path (`/manage`) this feature explicitly promised
   to leave untouched.
3. **Batch the small state-machine residues (🟡-1, 🟡-2, 🟡-4)** — all three are
   "the wipe/flip is not quite total", all three are a few lines with an
   obvious unit test, and together they finish the honesty of the revocation
   and vacancy stories.

## Next phase

`05_implement` (fix round) → re-review the changed surface (🔴-1 needs a fresh
screenshot + measurement at both viewports), then commit. **Not ready for
commit as it stands.**

---

# Round 2 — fix-round re-review (2026-08-21)

**Result: PASS ✅** — 🔴 0 · 🟠 0 open in code (1 parked, user-gated) · 🟡 0 ·
🟢 5 new info items. Dev's fix round is in
[handoff.md](handoff.md) → *Fix round*. Everything below was re-verified at
the moved repo path on a fresh throwaway Postgres; the environment was torn
down afterwards (container + volumes removed, no processes left).

## Verdict per round-1 finding

| # | Round-1 finding | Status | How I verified it (independently, unless stated) |
|---|---|---|---|
| 🔴-1 | Artwork as an inline flex item scrolled Home | **RESOLVED ✅** | Live, prod bundle + dev backend, artwork configured in the DB: **1280×832** `img {x:396.5, y:77.5, w:775, h:499.5}` — the spec box **to the decimal** (Δ x0 y0 w0 h0), `scrollW 1280 = clientW`, `scrollH 832 = clientH`; **1024×768** `{317.2, 62, 620, 399.6}` = the same box × 0.8, no scroll; dark at 1280 also no scroll. Baselines intact **with artwork present**: `.heading-block` x **172** / y **88**, `.home-content__cards` x **172** / bottom **744**, last card right **992 < 1280**. `elementFromPoint` over the card row returns the card row / a card and over the artwork's own area returns `.home-content__screen` — the layer is never hit-tested. **Fallback path re-checked too** (the host changed from `display:contents` to `position:absolute`): with no artwork, no `<img>` renders and the baseline is byte-identical (x172/y88, x172/744 at 1280; x138/y70, x138/698 at 1024), no scroll on `/`, `/einstellungen`, `/info` at both viewports. Screenshot read: the artwork sits behind the heading and behind the card row, exactly as the reference composition does. |
| 🟠-1 | SPA fallback claimed `/api`, `/q`, `/manage` exactly | **RESOLVED ✅** | Re-probed **live** against a bundled instance (the gap dev flagged): `/api` **404**, `/api/` 404, `/api/nonsense` 404, `/q` **404**, `/q/health` 404, `/manage` **404**, `/manage/` 404, `/manage/x` 404 — while `/einstellungen` and `/info` still return the app shell. `SpaRoutingHandlerTest` is 11/11 in my own backend run. |
| 🟠-2 | `frontend/CLAUDE.md` contradicts the code in two places | **PARKED ⏸ (user-gated, not a code blocker)** | Correctly untouched. Dev confirms nothing this round changes what the entries should say; I re-read the fix-round diff and agree — the artwork fix *strengthens* the existing *Layout* rule rather than adding a convention. |
| 🟡-1 | Brand primitives never un-applied | **RESOLVED ✅** | Live: before a 401 `<html style>` carried `--brand-primary: #f45aa8`; after forcing a 401 through the preference write, all three read **`""`** (removed, not overwritten) and the kiosk sat on `/pairing` with the dot painting the token-layer default. |
| 🟡-2 | Asset memo survived the 401 wipe | **RESOLVED ✅** | Live: captured the artwork's `blob:` URL before the wipe; after it, `fetch(blobUrl)` **rejects** → the object URL was genuinely revoked, and `localStorage` is **completely empty** (token + both caches). |
| 🟡-3 | Pairing expiry trusted the kiosk clock | **RESOLVED ✅** (code + tests) | `localDeadline()` measures the code's lifetime on the server's clock (`expiresAt` − response `Date`) and counts it down locally; no usable header → **no local deadline**, the server's 404 renews. Both branches have a test ("clock an hour ahead keeps polling", "no `Date` header → no proactive renew"). Matches the review's option 1 with option 2 as the fallback. |
| 🟡-4 | `409` on home degraded to STALE | **RESOLVED ✅** | Live, by fulfilling `GET /api/guest/screens/home` with `409 room-vacant` while the session still said OCCUPIED: kiosk goes **straight to `/standby`** (not a stale Home behind a pill), no scroll, **no guest name on screen**, `gxp.cache.home` **null**, and the session cache contains **neither** `Anna` **nor** a `preference` key. See the deviation judgement below. |
| 🟡-5 | No spec for `GuestStateRoutingService` | **RESOLVED ✅** | `guest-state-routing.service.spec.ts`, 8 cases over a `Router` stub — 401 → pairing, VACANT → standby, config loss → connection, heal → back, the `/einstellungen` restore, idempotence, and "a state screen is never recorded as the restore target". Green in my run. |
| 🟡-6 | REQ-504 inertness wording | **RESOLVED** (fixed in-review, round 1) | Untouched this round, as expected. |
| 🟡-7 | Throwaway drivers committed | **RESOLVED ✅** (with a recommendation) | All four deleted. See 🟢-6 on bringing one back by name. |
| 🟡-8 | `DevDataSeeder` `getSingleResult()` | **RESOLVED ✅** | `defaultRoomIds()` → `setMaxResults(1).getResultList()`, empty → return without seeding. Dev-profile seeder tests still 2/2 green in my run. |

## Judgement on the three flagged deviations + the `docs/design` edit

**1. `asVacant()` instead of the review's `home = null` — accepted, and it is
the better call.** My wording would have left `occupancy: OCCUPIED`, i.e. a
blank Home rather than standby; dev caught that. The synthesized payload
mirrors exactly what `GuestControl` serves for a vacant room (drops
`guestDisplayName` and `preference`) and is adopted with a **null ETag**, so
the next poll re-fetches unconditionally rather than revalidating a payload
the server never sent. I verified live that it reaches standby and that
nothing stay-derived survives in RAM or in `localStorage`.

**2. The ARTWORK case in `measure-states.mjs` rather than in
`DevDataSeeder` — accepted, and better than my "consider".** Seeding artwork
would have committed a binary into `backend/` for a dev-only convenience and,
worse, would have stopped the *default* dev kiosk from exercising the
no-artwork fallback that every not-yet-onboarded property uses. The cell
uploads a `node:zlib`-generated PNG through the real manager API, checks the
box against the spec scaled by `viewport/1280`, checks heading and cards still
share an x, checks the last card ends inside the viewport, checks the artwork
is never the element under a finger on the card row, and restores property +
asset in a `finally`. That is a stronger regression net than a seeded asset
would have been. (See 🟢-7 on its skip behaviour.)

**3. Deleting `.static-serve.mjs` — correct per instruction; see 🟢-6.** My
round-1 finding did offer "keep it as a named, documented instrument", and
dev took the other branch and said so. No objection: it is 25–40 lines to
recreate (I recreated one in `/tmp` for this round) and `ng serve` covers
layout measurement. I record a recommendation rather than a finding.

**4. The `docs/design/screens/home.md` as-built note — accept, in place.**
This is the one edit that deserved a hard look, because `docs/design/**` is
the **as-built Figma record**. My judgement:

- It **changes no extracted value.** It states the CSS interpretation of one
  (`1550 × 999 @ x793/y155` on the 2x canvas → `775 × 499.5 @ x396.5/y77.5`),
  which the file's own global 2x convention already implies. That is
  clarification of the record, not a new design decision.
- **Precedent exists in this very file**: the dated ⚠️ note added 2026-08-18
  ("Read the two middle bullets together, not separately… after they were
  misimplemented") is the same species — a dated, banner-marked correction of
  how an extracted value was misread. `dark-theme.md` and `tokens.md` →
  *In-house tokens* carry the same provenance-banner pattern.
- **The resolution correction belongs there most of all.** I verified it:
  `home.png` and `home-no-artwork.png` are both **1600 × 1040**, while line 5
  of that file claims "all 5120 × 3328". An as-built record with a wrong
  fact about its own assets is worse than one carrying a dated correction.
- **I reproduced dev's corroboration independently** (a hand-rolled PNG
  decoder, no image dependency added): diffing the two reference renders at
  threshold 8 gives a differing-pixel box of `{x: 402.4, y: 82.4, w: 765.6,
  h: 493.6}` CSS px against dev's `{402.4, 82.4, 764.8, 491.2}` — the same
  box, and the same conclusion: the halved spec box inset by the rendering's
  own transparent margin. Dev did not trust the spec numbers, and the numbers
  survive the check.
- **Residual risk, recorded not blocking:** a future `/design-import` could
  overwrite that file and take the note with it. The same numbers now also
  live in `hero-artwork.component.ts`'s header comment and in
  `measure-states.mjs`'s `ARTWORK_SPEC`, so the knowledge is triply held.

## New info items (round 2)

| # | Sev | File / evidence | Finding |
|---|-----|-----------------|---------|
| 🟢-5 | 🟢 | `SpaRoutingHandler.java:72-82` (live probe) | `GET /api%2Fnonsense` with `Accept: text/html` still returns the app shell: Vert.x's `normalizedPath()` deliberately does not decode `%2F`, so the string is not `/api/…`. Harmless — no client builds such a URL, an API client's `Accept` never contains `text/html`, and the bundle behind it is public by design. Recorded so the next reader knows it was checked, not missed. |
| 🟢-6 | 🟢 | `frontend/tools/` (deleted `.static-serve.mjs`) | **Recommendation:** bring back a *named* prod-bundle server — `tools/serve-dist.mjs`, charter header like `measure.mjs`'s, one line under `frontend/README.md` → *Measurement instruments*. Measuring the **production** bundle (not the dev build) is what proves REQ-505's token-freeness and D7's served artifact, and it was needed in both review rounds. A new file in an existing directory with an existing documentation slot — no convention gate. |
| 🟢-7 | 🟢 | `frontend/tools/measure-states.mjs` (ARTWORK cell) | The cell **skips silently-ish without `GXP_SMOKE_ADMIN_PW`** (it prints "ARTWORK skipped", but a run summary of "24/24 NO-SCROLL" reads clean either way). Consider making the final summary name the skipped cells, so a future reviewer cannot mistake a skipped artwork cell for a passing one — that is precisely how 🔴-1 shipped. |
| 🟢-8 | 🟢 | `docs/design/screens/home.md:5` | The new note corrects the resolution for the two Home renders; the blanket line 5 ("all 5120 × 3328") is still wrong for `home-concept-ai.png` too, which is **1672 × 941**. One-line follow-up whenever that file is next touched. |
| 🟢-9 | 🟢 | `guest-data.service.ts` → `asVacant()` | Dropping `preference` mirrors the server exactly, so during a vacancy episode the held session carries no preference to re-hydrate from. Identical to a genuine `VACANT` poll, therefore **not** a regression from this fix — noted only because a future "the guest's choice should survive a vacancy blip" requirement would have to change both paths together. |

## Re-verification evidence (round 2)

| Check | Result |
|---|---|
| `npx ng test` | **32 files / 251 tests / 0 failures** ✅ (235 + 16 new; no flakes on the warm run) |
| `npx ng build --configuration production` | green — **434.88 kB** main / **104.51 kB** transfer ✅ (matches dev's figures) |
| `grep -rl gxp-dev-device-token frontend/dist/` | **0 hits** ✅ |
| External hosts in `dist/` | unchanged: `w3.org` namespaces, `angular.dev`, the dead `http://localhost` default ✅ |
| `./mvnw clean test` (fresh throwaway Postgres, `gxp_devseed` pre-created) | **179/179, BUILD SUCCESS** ✅ — `SpaRoutingHandlerTest` **11**, `DevDataSeederTest` **2**, `SpaRoutingHandlerInertTest` 2 |
| `git status packages/ api/` after that regenerating build | **pristine** ✅ (the generator's `openapi.yaml-null.sha256` marker removed again) |
| Live SPA probe, bundled instance | reserved paths 404, deep links serve the shell ✅ |
| Live artwork measurement, both viewports + dark | spec box to the decimal, no scroll, baselines intact ✅ |
| Live no-artwork regression check | baselines and no-scroll unchanged on `/`, `/einstellungen`, `/info` ✅ |
| Live 401 wipe | brand primitives removed, `localStorage` empty, object URL revoked, kiosk on `/pairing` ✅ |
| Live `409` on home | straight to `/standby`, nothing stay-derived in RAM or cache ✅ |
| Reference-PNG resolutions | both **1600 × 1040** (line 5's "5120 × 3328" is wrong) ✅ |
| Independent reference-render diff | `{402.4, 82.4, 765.6, 493.6}` CSS px — corroborates the spec box ✅ |
| Environment | container + volumes removed, `quarkus:dev` and the throwaway server stopped, working tree carries only the fix round + review docs ✅ |

**Screenshots read this round:** artwork-present Home at 1280×832 (light and
dark) and 1024×768, the no-artwork Home, the post-401 pairing screen, and the
`409`-driven standby screen.

## REQ coverage — corrections to the round-1 table

| REQ | Round 1 | Round 2 |
|---|---|---|
| REQ-513 | ❌ (artwork broke the layout) | **✅** — authorized blob fetch unchanged; the artwork now renders as the specified out-of-flow layer, `object-fit: contain` so a differently-proportioned commissioned rendering is never cropped |
| REQ-522 | ❌ (artwork case scrolled) | **✅** — no-scroll holds on every route and state at both reference viewports, **including** artwork-present Home, and Home's signed-off geometry is unchanged in both variants |
| REQ-507 | ⚠️ (clock skew) | **✅** |
| REQ-509 | ⚠️ (brand + asset residue) | **✅** |
| REQ-512 | ⚠️ (un-apply path) | **✅** |
| REQ-517 | ✅ (409 race noted) | **✅** — the race now resolves to standby immediately |
| REQ-524 | ⚠️ (routing spec missing) | **✅** — 251 tests, every behaviour covered |
| REQ-525 | ⚠️ | ⚠️ **unchanged — user-gated** (🟠-2; the `frontend/CLAUDE.md` entries) |
| REQ-526 | ⚠️ (exact-prefix leak) | **✅** |

All other rows stand as recorded in round 1. **27 of 28 REQs fully ✅; REQ-525
waits only on the user's approval of the `frontend/CLAUDE.md` entries.**

## Still open

1. **⏸ Five `frontend/CLAUDE.md` entries** — dev's three (wire-fed content
   layer; the `packages/node_modules` symlink; `measure-states.mjs`) plus the
   review's two (state routes outside the shell; action-card routes from the
   wire). Doc-only, approval-gated, **not a code blocker**. The symlink entry
   is demonstrably load-bearing (it broke on the filesystem move).
2. 🟢-6 (name and document a prod-bundle server), 🟢-7 (name skipped cells in
   the measure summary), 🟢-8 (the resolution line) — all optional follow-ups,
   none blocking.

## Ready for commit: **yes**

The code is clean by this review's standards: every 🔴/🟠/🟡 is resolved and
re-verified, both suites are green from my own runs, the contract and the
generated client are pristine, and the working tree holds exactly the fix
round plus these review documents. The user commits (fix round on top of
`ea28870`); the `frontend/CLAUDE.md` entries follow as a separate doc change
once the user answers the gate.
