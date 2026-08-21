# Handoff: guest-frontend-integration

**Last session:** 2026-08-20 (dev — **05_implement complete**: T-01…T-29 all
ticked in [03-todo.md](03-todo.md); every suite green; dev smoke, measure
runs and compose smoke executed with evidence below. **Ready for
`reviewer` (06_review).** Nothing committed.)

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
- Smoke driver scripts are throwaway (`frontend/tools/.smoke-part*.mjs`,
  dot-prefixed, not part of the instrument set); `measure-states.mjs` is the
  committed instrument.

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
- Throwaway smoke drivers kept for the reviewer (dot-prefixed, untracked):
  `frontend/tools/.smoke-part{1,2,3}.mjs`, `.static-serve.mjs` (serves the
  prod `dist/` with an `/api` proxy — how the prod-bundle measurements ran).

## Next: 06_review

Review the changes (frontend + the two backend touches + docs). Everything
executable was executed; evidence above. One ⏸ user gate open: the proposed
`frontend/CLAUDE.md` additions (exact text above) — write only after
approval.
