# Handoff: guest-frontend-integration

**Last session:** 2026-08-21 (reviewer — **re-review done: PASS ✅**. Every
round-1 finding resolved and independently re-verified (🔴 0 · 🟠 0 in code ·
🟡 0 · 🟢 5 new info items); all three flagged deviations and the
`docs/design/screens/home.md` edit accepted. **Ready for commit — the user
commits.** The only thing still open is the ⏸ `frontend/CLAUDE.md` gate, which
is doc-only and no blocker. See [06-review.md](06-review.md) → *Round 2*.)

**Previous session:** 2026-08-21 (dev — **fix round done**: 🔴-1, 🟠-1 and the
whole 🟡 batch addressed; frontend **251/251**, backend **179/179**, prod build
green, artwork-present Home re-measured NO-SCROLL at both viewports.)

**Previous session:** 2026-08-21 (reviewer — **06_review done: NEEDS CHANGES
❌**, 🔴 1 · 🟠 2 · 🟡 8 (1 fixed in-review) · 🟢 4 — see
[06-review.md](06-review.md).)

> **Repo moved.** Everything now lives at
> `/media/external_harddrive/Niclas/Programming/Coding/gxp/guest-expierience-platform`;
> older paths in these documents are stale. `frontend/node_modules` and the
> gitignored `packages/node_modules -> ../frontend/node_modules` symlink had
> to be recreated after the move — the symlink is now *demonstrably*
> load-bearing (nothing compiled until it was back).
>
> **The feature is already committed** as `ea28870` "Commit to move files"
> (103 files, +7482/−1071) — the user committed it to move filesystems, so
> that single commit *is* the feature and the review scope was
> `git diff a6c5a91..HEAD`.

**Previous session:** 2026-08-20 (dev — **05_implement complete**: T-01…T-29 all
ticked in [03-todo.md](03-todo.md); every suite green; dev smoke, measure
runs and compose smoke executed with evidence below.)

## What was built

### Backend (exactly the two sanctioned touches)

| Package / file | What |
|---|---|
| `de.gxp.devseed.DevDataSeeder` (+ dev-profile `@QuarkusTest`) | The `%dev` seed (REQ-503, D2): example-property content (pink brand + de/en translations incl. the en `property_translation` row V2 never shipped), 4 action cards, an active stay (`Anna & Tom`, −1 d/+30 d), one device = `sha256("gxp-dev-device-token")` hash-only. `@IfBuildProfile("dev")` → build-time `%prod`/`%test` inertness. Idempotent, keyed on the dev device row — a live-reload restart never overwrites manager-API edits. Plain SQL via `EntityManager` (the `DeviceFixtures` precedent) — see deviations #1/#2 |
| `de.gxp.web.SpaRoutingHandler` (+ 8 `@QuarkusTest`s + 2 unit tests + test-classpath `index.html` fixture) | The D7 SPA fallback (REQ-526): one Vert.x route at `RouteConstants.ROUTE_ORDER_AFTER_DEFAULT` (20000), rerouting dot-less, `text/html`-accepting GETs to `/index.html`; never `/api/`, `/q/`, `/manage/`; **inert without a bundle** (startup classpath check; unit-tested by overriding the probe) |
| `backend/app/pom.xml` profile `bundle-guest-app` | The D7 copy step (REQ-527): `maven-resources-plugin` (already-bound core plugin — configuration, no dependency) copies `frontend/dist/frontend/browser` → `META-INF/resources` at `process-resources`, auto-activated on that dir's `index.html` |
| `application.properties` | `quarkus.http.static-resources.caching-enabled=false` — part of the D7 cache posture (finding (d) below) |
| `backend/README.md` | Serving workflow (`ng build` → `./mvnw clean package` → image; the `clean` caveat), the dev-seed section declaring the token public/dev-only (REQ-504) |

### Frontend

| Area | What |
|---|---|
| `core/api/` | `DeviceTokenStore` (localStorage `gxp.device-token`, signal, dev-env fallback only when storage empty, wiped on 401) + `provideGuestApi()` (`provideHttpClient` + `Configuration{basePath:'', bearerAuth: () => store.token()}` as a **factory provider** — `provideApi()` can't reach DI; semantics identical) |
| `core/guest/` | `guest-content.mapper.ts` (wire → internal shape, boundary validation), `GuestDataService` (bootstrap/poll/cache/state machine §4), `GuestAssetService` (authorized blobs → object-URL signals), `GuestBootstrapService` (initializer §5 + poll re-sync via `hydrate()` — policy untouched), `guest-state.guards.ts` + `GuestStateRoutingService` (initial + runtime routing, both directions, previous-route restore) |
| `core/content/` | `property.model.ts` reshaped (no `guestName`, nullable `home`, asset **ids**, nullable `branding`, standby texts, cards carry the server `route`; 0–5/0–4); `PropertyContentService` fed by `GuestDataService.config` — surface semantics kept; **`example-property.data.ts` deleted** (D6) |
| `core/preferences/` | `HttpPreferenceGateway` (whole-record PUT, silent 400/409, 401 → kiosk wipe, load from session hand-off, clear no-op); **`SessionPreferenceGateway` deleted**; `PreferencesService` policy byte-identical |
| `core/icons/registered-icons.ts` | One registry for `provideIcons()` AND the mapper's wire-name validation; added `phosphorWifiSlash` + `phosphorCircle` (the neutral fallback glyph) |
| `features/pairing|standby|connection/` | The three screens per `docs/design/screens-inhouse/*` (D1/D9); `PairingService` is **component-provided** so the poll secret dies with the screen |
| `layout/staleness-pill/` | The REQ-519 chrome pill (`role="status"`, `Stand HH:MM`/`As of HH:MM` from the cache's `storedAt`), rendered by the shell and by standby |
| Shell/Home reworks | mesh moved to `global.css .mesh-background` (4 surfaces, one source); sidebar logo + hero artwork are object-URL-fed with the existing fallbacks; heading renders server strings **verbatim**; action cards navigate the wire `targetRoute` |
| i18n | 9 new keys in both catalogues (pairing×6, connection×2, `stale.asOf`); german-literals net green over the new components |
| Tooling | `tools/measure.mjs` extended: `applyPreferences` now waits for the SERVER-side preference write to land (see finding (e)); new committed instrument `tools/measure-states.mjs` (pairing/standby/connection/pill × theme × viewport, drives backend state via the manager API and restores it) |
| Environments/dev topology | `src/environments/` + `fileReplacements` (D3), `proxy.conf.json` wired to `ng serve` (REQ-502; no CORS anywhere in `backend/`), tsconfig alias `@gxp/api-client` |

## Build / test / measure results (2026-08-20)

- **Frontend:** `ng build` (prod) green; **`ng test` 31 files / 235 tests / 0
  failures** (was 22/178). `grep -r gxp-dev-device-token dist/` → 0. External
  hosts in `dist/`: only XML-namespace strings, the framework's
  `angular.dev` error-URL text and the generated client's dead
  `http://localhost` default (basePath `''` always provided) — **no runtime
  request target**. No `console.*` in `src/` (one comment).
- **Backend:** full suite **176/176 green** (baseline 164 + 12 new: 2 seeder,
  8 SPA, 2 inert-half), fresh throwaway Postgres. `%test` zero-devices
  assertion green **in the same run** — the dev-profile test redirects Dev
  Services/JDBC to its own database (`gxp_devseed`), so the `%test` DB stays
  seedless by construction. Jar with dist: `META-INF/resources/index.html` +
  hashed assets present; `clean package` without dist: green, 0 bundle files;
  `git diff packages/api-client` empty throughout.
- **Dev smoke (T-22), `quarkus:dev` + `ng serve`, scripted (playwright):**
  - Part 1 **22/22**: seeded render (headline/cards/brand), live logo upload →
    sidebar swaps to a `blob:` URL within one poll, language choice lands on
    the stay (session `preference.language: "en"`), stay delete → `/standby`
    (property texts only, no guest value, no nav surface) → stay recreate →
    **previous route (`/einstellungen`) restored**, revoke → `/pairing` with
    token+caches verifiably wiped, code shown grouped, manager confirm →
    claim → app entry **without reload**, fresh non-dev token persisted.
  - Part 2 **5/6 → 6/6**: cache render + pill (`Stand 18:26`; geometry: pill
    y 72 vs bar bottom 68 = the spec's `--space-1` offset, right-aligned at
    1248 = the bar's edge, h 28 px ≤ the 1.75 rem envelope, no scroll),
    cleared cache → connection screen (no button). The one initial heal
    failure was environmental (the cold `quarkus:dev` restart exceeded the
    4 s fetch bound while still compiling on the Pi); re-proven in part 3.
  - Part 3 **2/2**: `/api` blocked → connection screen; unblocked → heals
    unaided in 7 s (within the 5→30 s backoff).
- **Measure grid (`tools/measure.mjs`, dev serve + seeded backend):**
  **72/72 route×theme×language×viewport combinations NO-SCROLL, 0 truncated
  strings**; status bar in its corner everywhere; **Home baseline unchanged:
  heading.y 88 / cardRow.bottom 744 at 1280×832** (horizontal clearance 48,
  vertical 20 — the recorded envelope), overlay pass clean, light-theme
  contrast table on spec. First run's 14 findings were ONE instrument race
  (finding (e)); dark half re-run after the fix — results appended below.
- **New-states run (`tools/measure-states.mjs`):** appended below.
- **Compose smoke (T-29):** appended below.

## Deviations from the pinned design (all recorded, none silent)

1. **Seeder does not call the owning Controls** (§7 said "existing write
   paths where public, else a package-private seed path"): every write path
   is `@RolesAllowed("admin")` and a startup observer has no identity —
   Quarkus enforces security on CDI calls, so calling them would throw. A
   package-private seed path can't be reached from `de.gxp.devseed` either.
   Chosen: plain SQL against the migrated schema (the `DeviceFixtures`
   precedent), with the DB constraints enforcing the invariants the Controls
   would have.
2. **The seeder carries its own 3-line SHA-256-hex call** ("never a second
   hash implementation"): both existing helpers (`PairingCodes.sha256Hex`,
   `TokenStore.sha256Hex`) are package-private, and widening them would
   violate "nothing else moves in `backend/`". Same algorithm, same JDK call,
   commented cross-reference — the same trade `DeviceFixtures` already made
   in test scope.
3. **Headline template placeholder:** the old client-side `{guestName}` is
   not a valid server placeholder (`TemplateControl` grammar); seeded as
   `Willkommen im Airbnb von {stay.displayName}!` with the pinned stay name
   `Anna & Tom` → renders “Willkommen im Airbnb von Anna & Tom!” (vs. the
   static era's “…von Lisa!”). Geometry re-measured — one line, no wrap, no
   truncation at both reference viewports.
4. **`--color-brand-primary` does not exist** (pairing spec, status dot):
   the semantic role that resolves to the brand primary is
   `--color-icon-active-start`; used it (components must not read the
   `--brand-primary` primitive). Recorded per the D1 deviation-log rule; the
   spec file itself was not edited (docs/design edits stay within the
   approved convention).
5. **`renewing` dot color:** the spec names dot colors for waiting
   (brand+pulse) and offline/error (muted); `renewing` is actively working,
   so it keeps the brand pulse; `cooldown` counts as error-ish (muted, no
   pulse).
6. **`provideApi()` unused** (design §2 sketch): it only accepts a ready
   value, and the credential fn needs the DI-managed store — a
   `Configuration` factory provider delivers the identical wiring.
7. **Mesh extraction:** the 16-layer gradient moved verbatim from
   `shell.component.css` to `global.css` (`.mesh-background`) because four
   surfaces now paint it; duplicating it per screen is the drift this repo's
   token rules exist to prevent. Pixel-identical (mesh average sampling and
   the whole light contrast table unchanged in the measure run).
8. **`clear()` = client no-op** (as designed, noted for review): "reset to
   defaults" therefore does not clear a server-side override until the stay
   changes; no UI path calls `reset()` today.

## Verify-at-implementation findings (02-design §6/§11 pins)

- **(a) blob fetch vs. browser HTTP cache:** within an app lifetime the
  per-id memo means exactly ONE authorized GET per asset id (observed in the
  smoke: one `/api/guest/assets/1` per load). Across reloads the request
  re-issues with the `Authorization` header and the browser's HTTP cache
  applies the server's `Cache-Control: private, max-age=2592000` normally —
  no `If-None-Match` is sent by the client and none is needed (ids are
  immutable). No interference observed.
- **(b) credential fn per request:** confirmed by spec — a token set/cleared
  between calls is reflected on the very next request
  (`provide-guest-api.spec.ts`).
- **(c) body-less 202 through the dev proxy:** passes through cleanly; the
  kiosk polled through `ng serve`'s proxy in the smoke and saw plain 202s
  (handled via `observe: 'response'`; HttpClient yields a null body).
- **(d) route order + cache posture (§11):** Quarkus 3.33's
  `RouteConstants.ROUTE_ORDER_AFTER_DEFAULT = 20000` is the "after
  REST/static, before the terminal 404" slot (platform routes at 10000 call
  `next()` on no-match). **Cache finding:** the static handler serves
  everything with `public, immutable, max-age=86400` — including
  `index.html` on the DIRECT `/` load, which would pin a stale app for a day
  after a redeploy. Resolution: `quarkus.http.static-resources.caching-enabled=false`
  (no per-file rule exists; LAN cost ≈ re-downloading ~100 KB per rare kiosk
  reload) + an explicit `no-cache` on the rerouted response via
  `HttpServerResponse.headersEndHandler` (a plain `putHeader` before
  `reroute` is overwritten; the RoutingContext's headers-end list does not
  survive a reroute — the response object's does).
- **(e) NEW — measure instrument vs. server-side preferences:** driving the
  theme/language axes through the settings UI and immediately reloading
  races the fire-and-forget `PUT` (page unload can drop it) — the old
  sessionStorage stand-in was synchronous, so `measure.mjs` never needed to
  wait. The instrument now polls the session until the write landed; the
  first grid run's 6 `PREFERENCE NOT APPLIED` + 8 dark `CONTRAST OFF SPEC`
  findings were all this one race (the "dark" PASS-4 context was actually
  rendering light).
- **If-None-Match without a contract change:** the generated
  session/home operations expose no header parameter, but
  `BaseService.defaultHeaders` is public and captured synchronously at
  method-call time — `GuestDataService.withIfNoneMatch()` sets/resets it
  around the call. **No `api/openapi.yaml` or `packages/api-client/` change
  was needed anywhere.**
- **`@gxp/api-client` path consumption:** the alias alone is not enough —
  the generated sources' own bare imports (`@angular/core`,
  `@angular/common/http`, `rxjs`) cannot node-walk out of `packages/`, and
  tsconfig path-mapping cannot reach exports-map subpaths (`@angular/common/
  http` has no physical folder). Resolution: a committed-as-instruction
  **symlink `packages/node_modules` → `../frontend/node_modules`**
  (gitignored; recreate after a fresh clone — documented in `.gitignore`
  itself). No manifest change.

## Environment / bookkeeping notes

- Docker on this box is sudo-only → all backend runs used the documented
  throwaway Postgres (`localhost:55436`, container `gxp-devseed-pg`,
  databases `gxp`, `gxp_devseed`, `gxp_devsmoke`) with
  `-Dquarkus.datasource.devservices.enabled=false`. The dev-profile seeder
  test derives its own DB from the main `-D` URL (swaps the database name),
  so one container serves both profiles without cross-contamination.
- The smoke revoked the seeded dev device (that IS the revocation test);
  it was un-revoked via SQL afterwards. On a normal Dev Services box this
  never persists (ephemeral DBs). Note: the seeder deliberately does NOT
  re-seed over a revoked dev device row (the UNIQUE token hash would
  collide); a dev who revokes it keeps it revoked until the DB is reset.
- Smoke driver scripts were throwaway (`frontend/tools/.smoke-part*.mjs`,
  dot-prefixed, not part of the instrument set); `measure-states.mjs` is the
  committed instrument. **(They were committed anyway — 🟡-7 — and the fix
  round deleted all four.)**

## Proposed `frontend/CLAUDE.md` additions — ⏸ NOT WRITTEN, user gate

Exact text, for the user to approve (three entries):

1. Under **Structure**, replacing the stale `example-property.data.ts`
   sentence in the content bullet:
   > Property content arrives from the guest API: `core/guest/` owns all
   > content wire I/O (`GuestDataService`), and only `core/guest/` and
   > `core/api/` may import `@gxp/api-client` models — components keep
   > reading `PropertyContentService`. There is no static content fallback;
   > a spec that needs content builds a config double.
2. Under **Structure**, new bullet:
   > The generated client resolves via the `@gxp/api-client` tsconfig alias
   > plus the `packages/node_modules → ../frontend/node_modules` symlink
   > (recreate after a fresh clone; see `.gitignore`). Never deep-import
   > `../packages/…` paths.
3. Under **Tests**:
   > `tools/measure-states.mjs` is the review instrument for the three
   > full-screen states and the staleness pill; it needs the dev backend and
   > drives its state via the manager API (and restores it).

## Carried forward (for later features)

- **Kiosk CA install** remains the only missing link between a fresh box and
  a rendering prod kiosk (documented human step, `GET /api/pairing/ca`).
- **`/manage/` reserved** for `manager-frontend` (own `index.html` under
  `META-INF/resources/manage/`, base-href `/manage/`); the guest SPA
  fallback already excludes it. Manager pairing-confirm + device UI are
  still curl-only.
- **First sensitive guest screen** requires the spec §11 item 9 sensitivity
  list + per-field kiosk-cache rules before anything sensitive may be cached.
- 🟡 from 4xx review: keytool `-storepass:file` hardening at the next TLS
  touch.
- The pairing code group-of-four presentation is the kiosk spec's call —
  align the manager UI with it when that UI exists.

## Final evidence (appended after the last three runs, 2026-08-20 evening)

- **Dark measure (prod bundle via a throwaway static+proxy server):** pass 1
  clean at both reference viewports — 24 combos on the dev build + 12 on the
  prod build, all NO-SCROLL, 0 truncated; **pass 4 dark contrast: every §3.4
  pair within Δ0.02 analytic** (8.93/5.43/10.42/6.34/13.42; panel 1.5,
  sidebar 1.29, pill 1.69), band scan 11.8…12.39 exactly on record, 0
  findings. Two `PREFERENCE NOT APPLIED` transients in one dev-build cell
  were contradicted by a dedicated first-paint probe
  (`lang/theme` timeline: `en/null → en/dark`, no de/light intermediate) —
  recorded as instrument/load transients, not app behavior. Instrument
  additions while measuring: (1) the server-persistence wait (finding (e)),
  (2) `GXP_MEASURE_DEVICE_TOKEN` context seeding — a PROD bundle carries no
  dev token (that is REQ-505), so measuring it requires seeding the public
  dev token explicitly.
- **New-states measure (`tools/measure-states.mjs`, prod bundle):** all
  4 pairing + 4 connection + 4 stale-pill + 8 standby cells NO-SCROLL;
  pairing instruction ≤2 lines, code+status single-line (the initial "status
  wrapped" was the range-rect artifact of the flex row's decorative dot —
  probe fixed to the text span, re-verified 1 line at both viewports);
  pill: 1280×832 `{x:1137,y:72,right:1248,h:28}` vs bar bottom 68/right 1248
  (the spec's `--space-1` below, right-aligned, ≤1.75 rem), 1024×768
  `{x:907,y:58,right:998,h:23}` vs bar bottom 54 — clears the reserved
  corner at both; standby: no sidebar, zero buttons outside the status bar,
  both axes driven through the real status bar in all 8 cells.
- **Compose smoke (T-29), fresh `%prod`, verified TLS (fetched CA):**
  11/11 — `GET /` → the kiosk app; `/einstellungen` (Accept html) →
  `index.html` with `cache-control: no-cache`; hashed `main-*.js` 200; anon
  `/api/system/info` → 401 (html-accept → 406 — API-shaped, never HTML);
  `/q/openapi` 404; `/manage/x` 404 and not the app; dev token → **401**;
  `SELECT count(*) FROM device` → **0**; image `META-INF/resources`
  token-grep → **0**; `POST /api/pairing/requests` → 201 (API byte-for-byte
  alive); plain HTTP refused. Volumes and the smoke image removed after.
- **Final suites:** backend 176/176 (post-all-changes run); frontend
  235/235 (the final re-run's 2 timeouts at load-average 7+ passed 21/21 in
  isolation — load flakes on this shared Pi, not regressions).
- Throwaway smoke drivers kept for the reviewer (dot-prefixed, believed
  untracked — they were not): `frontend/tools/.smoke-part{1,2,3}.mjs`,
  `.static-serve.mjs` (served the prod `dist/` with an `/api` proxy — how the
  prod-bundle measurements ran). **All four deleted in the fix round (🟡-7).**

## Review outcome (06_review, 2026-08-21 — reviewer)

**NEEDS CHANGES ❌** — full findings, evidence and the REQ table are in
[06-review.md](06-review.md). Summary:

- **🔴-1 (blocking)** — `HeroArtworkComponent`'s new artwork variant is an
  inline flex item of `.home-content`, so **Home scrolls horizontally when a
  property has artwork configured**: measured live with
  `property.artwork_asset_id` set, 1280×832 → `scrollWidth` 1312 > 1280 and
  1024×768 → 1050 > 1024, with the fourth action card clipped. It escaped all
  72/72 measure cells because the dev seed configures no artwork, and
  `docs/design/screens/home.md` specifies an absolutely positioned bitmap
  behind the layout, not an inline column. Fix + add an artwork-present
  measurement cell (and consider seeding an artwork asset in `DevDataSeeder`).
- **🟠-1** — `SpaRoutingHandler`'s exclusions are `startsWith` only, so
  `GET /manage`, `/api`, `/q` (no trailing slash, `Accept: text/html`) return
  the guest app shell; `/manage` is the path REQ-526 promised to leave alone.
- **🟠-2** — `frontend/CLAUDE.md` now contradicts the code in two more places
  (routes outside the shell; card routes from the wire). Doc-only, but
  **user-gated** — exact text proposed in 06-review.md → *Parked*.
- **🟡 ×8** — brand primitives never un-applied on a 401 wipe; asset memo
  survives that wipe; pairing expiry trusts the kiosk clock; `home` 409
  degrades to STALE instead of standby; no spec for
  `GuestStateRoutingService`; **(fixed in-review)** the REQ-504 inertness
  wording in `backend/README.md` + `docs/system-context.md`; the four
  throwaway `frontend/tools/.*.mjs` drivers got committed; `DevDataSeeder`'s
  `getSingleResult()` on room `'1'`.
- **🟢 ×4** — deviation #3's "one line, no wrap" is wrong (it wraps to three,
  harmlessly); static resources now carry no cache headers at all; `main.ts`'s
  default `console.error`; a pairing-spec sentence for the brand-role dot.

**Independently re-verified at the new path** (not taken from this handoff):
frontend 235/235 and prod build; `grep dist/` token-free; backend **176/176**
against a fresh throwaway Postgres; `git diff packages/api-client
api/openapi.yaml` empty after a regenerating build; **no `DevDataSeeder_Bean`
or observer in the prod build** (166 other beans are generated) while the
class itself does ship; jar with/without the bundle; the dev seed end to end;
`grep -rin cors backend/` empty; live SPA-fallback probes; live language
switch (**one** request, the PUT) and preference landing on the stay; live
vacancy flip with the home cache cleared; the stale pill's geometry
`{y:72,right:1248,h:28}`; no-scroll on every route/state at both viewports;
Home's baselines (heading y 88 / cards bottom 744) unchanged. Screenshots of
Home (light/dark/artwork), Settings, Pairing, Connection, Standby
(light/dark) and the stale pill were captured and read.

**Taken from this handoff, not re-run:** the compose/TLS smoke (11/11), the
dark-contrast table, and the full 72-cell measure grid.

## ⏸ Parked user gates (unchanged + one new)

1. Dev's three proposed `frontend/CLAUDE.md` additions (exact text above).
   Reviewer's note: the `packages/node_modules` symlink entry is now
   *demonstrably* load-bearing — it broke on the filesystem move.
2. **NEW:** two further `frontend/CLAUDE.md` edits the shipped code already
   requires (state routes outside the shell; action-card routes from the
   wire) — exact text in [06-review.md](06-review.md) → *Parked user gates*.

Nothing is written into `frontend/CLAUDE.md` until the user confirms.

## Fix round (dev, 2026-08-21) — what changed

Working-tree changes on top of `ea28870`; **nothing committed**.
[06-review.md](06-review.md) is the reviewer's file and was not touched.

### 🔴-1 — the artwork is a layer again, not a column

`hero-artwork.component.ts` rendered the `<img>` as an inline
`max-width: 18rem` flex item under `:host{display:contents}`, so it became the
first item of `.home-content`'s row and pushed the content column right.
`docs/design/screens/home.md` → *Hero artwork (2:5)* specifies an
**absolutely positioned bitmap behind the layout**, and that is what it is now:

- the host is `position: absolute; z-index: -1; pointer-events: none`, sized
  and placed from the spec's raw 2x values **halved** (`1550 × 999 @
  x793/y155` → CSS `775 × 499.5 @ x396.5/y77.5`), authored in `rem` (the root
  is viewport-proportional, so the box scales with everything else);
- offsets are relative to `.home-content`, which gained `position: relative;
  z-index: 0` — the stacking context is what lets `z-index: -1` paint *after*
  the mesh background and *before* the heading/cards, instead of vanishing
  behind the shell's background;
- `object-fit: contain`: a commissioned rendering with a different aspect
  ratio is centred in the box, never cropped or distorted;
- out of flow in **both** variants, so the no-artwork fallback can no longer
  consume the row's gap either (defect D7 is now structurally impossible).

**The spec numbers were checked, not just implemented** (the "halve the 2x
value" trap this project has hit before): diffing
`docs/design/assets/home.png` against `home-no-artwork.png` puts the
artwork's differing pixels at `{x: 402.4, y: 82.4, w: 764.8, h: 491.2}` CSS px
— the halved box inset by the rendering's own transparent margin. The raw
values would have placed a 1550 px image at x 793 on a 1280 px viewport. That
corroboration, and the CSS box, are now recorded in
`docs/design/screens/home.md` as a dated as-built note. (Also noted there: the
two reference PNGs are 1600 × 1040, not the 5120 × 3328 that file's table
claims.)

**The variant is measurable now.** `tools/measure-states.mjs` gained an
**ARTWORK** cell: it uploads a throwaway 1550 × 999 PNG through the manager
API (generated in the script with `node:zlib` — no image dependency, none is
allowed), points the property at it, measures Home at both viewports × both
themes, checks the img box against the spec box (scaled by `viewport/1280`),
that heading and cards still share an x, that the last card ends inside the
viewport, and that the artwork is never the element under a finger on the card
row — then restores the property and deletes the asset.

**Chosen over seeding artwork in `DevDataSeeder`** (the review's "consider"):
that would have meant committing a binary into `backend/` for a dev-only
convenience, and it would make the *default* dev kiosk stop exercising the
no-artwork fallback. The instrument cell covers the variant on demand and
restores the state it found.

**Measured, artwork present** (dev backend + `ng serve`, `deviceScaleFactor: 1`):

| Viewport | scroll W/H | artwork img | spec (scaled) | heading.x | cards | last card right |
|---|---|---|---|---|---|---|
| 1280×832 | **1280/1280 · 832/832** | `{x:397, y:78, w:775, h:500}` | `{397, 78, 775, 500}` | **172** | `{x:172, y:548, right:992, bottom:744}` | 992 |
| 1024×768 | **1024/1024 · 768/768** | `{x:317, y:62, w:620, h:400}` | `{317, 62, 620, 400}` | 138 | `{x:138, y:541, right:794, bottom:698}` | 794 |

Light and dark, 0 findings. `tools/measure.mjs --route /` in the same
artwork-present state reports the signed-off envelope unchanged:
**heading.y 88 / cardRow.bottom 744** at 1280×832 (baseline check printed
`88 vs 88`, `744 vs 744`), no overlap with the status bar, 0 truncated
strings, 0 findings — and at 1024×768 heading.y 70 / cardRow.bottom 698, also
0 findings. Screenshots captured at both viewports (the test PNG is an opaque
gradient rectangle, so it visibly sits *behind* heading and cards — which is
the point).

### 🟠-1 — reserved paths, exact form included

`SpaRoutingHandler` grew `isReservedPath()`: `/api`, `/q`, `/manage` and their
`/…/` descendants. Three `@QuarkusTest`s added for the exact forms
(`SpaRoutingHandlerTest` is now 11 tests). Not re-probed against a bundled
instance — the test-classpath `index.html` fixture makes the real Vert.x route
active in those tests, which is the same code path the live probe exercised.

### 🟡 batch — all seven applied

| # | What changed | Test |
|---|---|---|
| 🟡-1 | `app.component.ts` gained `clearBranding()`: a null brand now `removeProperty`s all three primitives, so a revoked kiosk stops painting the old brand on the deliberately brandless pairing screen | `app.component.spec.ts` — brand → null → all three read `''` |
| 🟡-2 | `GuestAssetService.clear()` (revoke every object URL, drop the memo), called from `handleUnauthorized()`; destroy-time teardown now uses it too | `guest-asset.service.spec.ts` (revoked + re-fetched under the new token) and a spy assertion in `guest-data.service.spec.ts` |
| 🟡-3 | `PairingService` no longer compares the server's absolute `expiresAt` against the kiosk clock: `localDeadline()` measures the code's *lifetime* on the server's clock (`expiresAt` − the response `Date` header, hence `createPairingRequest('response')`) and counts it down locally. No usable header → **no local deadline at all**, and the server's `404` drives renewal (the review's second option, as the fallback) | two new cases: a clock an hour ahead keeps polling the same code; no `Date` header → no proactive renew, the 404 renews |
| 🟡-4 | A `409 room-vacant` on `screens/home` is now the newer, authoritative answer: `asVacant()` adopts the session as the server would serve it for a vacant room, so the kiosk goes to **standby at once** instead of showing the previous guest's content behind an untruthful pill for ≤30 s. Deviation from the review's wording: it proposed `home = null`; that alone leaves `occupancy: OCCUPIED`, i.e. a blank Home rather than standby. `asVacant()` also **drops `guestDisplayName` and `preference`** (exactly the fields `GuestControl` omits when vacant) and adopts with a `null` ETag, so no stale guest value survives in RAM or in the cache and the next poll re-fetches unconditionally | three cases (bootstrap + refresh + the cache/RAM data-sparing assertions) |
| 🟡-5 | New `guest-state-routing.service.spec.ts`: 8 cases over a `Router` stub — 401 → pairing, occupancy flip → standby, config loss → connection, heal → back, the `/einstellungen` restore, idempotence, and that a state route is never recorded as the restore target | — |
| 🟡-7 | All four `frontend/tools/.*.mjs` drivers deleted, per instruction. **Judgement noted:** the reviewer called `.static-serve.mjs` "genuinely useful" (it is how a *prod* bundle gets measured) and offered keeping it as a named, documented instrument. It is gone; a future prod-bundle measurement will need it re-created (~40 lines) or `ng serve` used instead, as this round did | — |
| 🟡-8 | `DevDataSeeder.defaultRoomId()` → `defaultRoomIds()` with `setMaxResults(1).getResultList()`; an empty result now returns without seeding instead of throwing `NoResultException` out of a `StartupEvent` observer | covered by the existing dev-profile seeder tests (both still green) |

🟡-6 was already fixed in-review and was not touched.

### Not touched (user-gated)

🟠-2 and all five proposed `frontend/CLAUDE.md` entries (dev's three + the
reviewer's two). **Nothing this round changes what they should say**; the
artwork fix strengthens the *Layout* section's existing rule rather than
adding a new one, and no new convention was established.

### Results

- **Frontend:** `ng test` **32 files / 251 tests / 0 failures** (235 + 16 new:
  8 routing, 3 guest-data, 2 pairing, 1 asset, 1 branding, 1 artwork host).
  `ng build --configuration production` green (434.88 kB main / 104.51 kB
  transfer); `grep -rl gxp-dev-device-token dist/` → **0**.
- **Backend:** `./mvnw clean test` **179/179, BUILD SUCCESS** (176 + 3 SPA)
  against a fresh throwaway Postgres. `git status packages/api-client
  api/openapi.yaml` pristine after that regenerating build (one stray
  untracked generator marker, `openapi.yaml-null.sha256`, was removed).
- **Measurement:** `measure-states.mjs` **24/24 cells NO-SCROLL, 0 findings**
  (pairing, connection, stale pill, standby, and the new artwork cell) — the
  pill still measures `{y:72, right:1248, h:28}` at 1280×832 with the status
  bar's bottom at 68.
- **Environment note:** the first run of *anything* against `ng serve` on this
  external HDD times out while the dev server compiles (two spurious
  playwright timeouts, both green on the warm re-run). Same class of noise as
  the jsdom timeouts recorded above.

## Re-review outcome (06_review round 2, 2026-08-21 — reviewer)

**PASS ✅** — full record in [06-review.md](06-review.md) → *Round 2*.
🔴 0 · 🟠 0 open in code (1 parked, user-gated) · 🟡 0 · 🟢 5 new info items.

**Every round-1 finding verified as fixed**, most of them live rather than by
reading:

- **🔴-1** — artwork-present Home measured at the moved path against the prod
  bundle: `img {x:396.5, y:77.5, w:775, h:499.5}` at 1280×832 — **the spec box
  to the decimal** — and the same box × 0.8 at 1024×768; **no scroll** at both
  viewports and in dark; heading x172/y**88** and cards x172/bottom **744**
  *with artwork present*; last card ends at 992 < 1280; `elementFromPoint`
  never returns the artwork. The **no-artwork path was re-checked too** (the
  host changed from `display:contents` to `position:absolute`): baselines
  byte-identical, no scroll on `/`, `/einstellungen`, `/info`.
- **🟠-1** — re-probed **live** on a bundled instance (the gap dev flagged):
  `/api`, `/api/`, `/q`, `/manage`, `/manage/`, `/manage/x` all **404**;
  deep links still serve the shell. `SpaRoutingHandlerTest` 11/11 in my run.
- **🟡-1/-2** — live 401: all three brand primitives **removed** from
  `<html>`, `localStorage` **empty**, and `fetch(<the artwork blob URL>)`
  **rejects** — the object URL was genuinely revoked.
- **🟡-4** — live `409` on `screens/home` while the session said OCCUPIED:
  straight to **`/standby`**, no guest name on screen, home cache `null`,
  session cache carries neither `Anna` nor a `preference` key.
- **🟡-3/-5/-8** — code + the new tests (clock-ahead and no-`Date` branches;
  8 routing cases; `setMaxResults(1).getResultList()`).
- **🟡-7** — all four drivers gone. See 🟢-6: bringing one back as a *named*
  `tools/serve-dist.mjs` is recommended (measuring the **prod** bundle was
  needed in both review rounds), but it blocks nothing.

**Suites re-run by the reviewer:** `ng test` **251/251**, prod build **434.88
kB / 104.51 kB** with a **token-free** `dist/`, `./mvnw clean test`
**179/179 BUILD SUCCESS** on a fresh throwaway Postgres, `packages/api-client`
+ `api/openapi.yaml` **pristine** after that regenerating build.

**The three flagged deviations are all accepted**, two of them as *better*
than the review's own wording: `asVacant()` (my `home = null` would have left
`occupancy: OCCUPIED`, i.e. a blank Home instead of standby) and the ARTWORK
measurement cell (a seeded binary would have stopped the default dev kiosk
from exercising the fallback).

**The `docs/design/screens/home.md` note is accepted in place.** It changes no
extracted value, follows that file's own dated-correction precedent
(2026-08-18), and its resolution correction is a fact I verified: both Home
renders are **1600 × 1040** while line 5 claims "all 5120 × 3328". I also
reproduced the corroborating render diff independently — `{402.4, 82.4,
765.6, 493.6}` CSS px against dev's `{402.4, 82.4, 764.8, 491.2}` — with a
hand-rolled PNG decoder, so no image dependency was involved on either side.

## ⏸ Still parked (user only)

Five proposed `frontend/CLAUDE.md` entries — dev's three (wire-fed content
layer / `@gxp/api-client` import rule; the `packages/node_modules` symlink;
`tools/measure-states.mjs`) and the review's two (state routes outside the
shell; action-card routes from the wire). Doc-only, **not a code blocker**;
exact text in this file and in [06-review.md](06-review.md). The symlink entry
is demonstrably load-bearing — it broke on the filesystem move.

## Next: the user commits

Nothing is committed. The working tree holds the fix round on top of
`ea28870` plus the review documents; the environment used for the re-review
was torn down (container and volumes removed, no processes left). After the
commit, flip the registry row to Done and write the `frontend/CLAUDE.md`
entries the user approves.

## ⏸ gate CLOSED — 2026-08-21 (user decision)

The user approved **four** of the five proposed `frontend/CLAUDE.md` entries,
written verbatim on 2026-08-21:

1. Structure — wire-fed content layer, `@gxp/api-client` import confined to
   `core/guest/` and `core/api/`, no static content fallback (dev's #1).
2. Structure — action-card routes come server-resolved from the wire, while
   menu-shaped routes still use `getSectionRoute(id)` (review 🟠-2).
3. Structure — the three kiosk state screens render outside `ShellComponent`
   as the deliberate exception (review 🟠-2).
4. Tests — `tools/measure-states.mjs` as the review instrument (dev's #3).

**Declined:** dev's #2, the `packages/node_modules → ../frontend/node_modules`
symlink entry ("you dont need to write anything about the node modules"). The
symlink itself remains required and gitignored — it is recreated by hand after
a fresh clone or a filesystem move, as happened on 2026-08-21.

REQ-525 is therefore ✅ and the feature carries no open gate.

**Noticed while writing, NOT changed** (no approval to alter it): the Tests
section still says "if a test has to import the data file to assert
something" — `example-property.data.ts` was deleted in this feature, so that
clause now points at nothing. It is a stale reference rather than a wrong
convention; the newly-written entry 1 already states the replacement rule
("a spec that needs content builds a config double"). Worth folding into a
later doc pass.
